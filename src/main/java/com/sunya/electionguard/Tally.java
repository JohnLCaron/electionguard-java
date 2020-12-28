package com.sunya.electionguard;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.common.flogger.FluentLogger;
import com.google.gson.Gson;
import com.google.gson.TypeAdapter;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;
import java.math.BigInteger;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;

import static com.sunya.electionguard.Ballot.*;
import static com.sunya.electionguard.Group.*;

public class Tally {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  @Immutable
  private static class Tuple {
    final String id;
    final ElGamal.Ciphertext ciphertext;

    public Tuple(String id, ElGamal.Ciphertext ciphertext) {
      this.id = id;
      this.ciphertext = ciphertext;
    }
  }

  /**
   * A plaintext Tally Selection is a decrypted selection of a contest.
   */
  @AutoValue
  public static abstract class PlaintextTallySelection implements ElectionObjectBaseIF {
    // g^tally or M in the spec
    abstract BigInteger tally();
    abstract ElementModP value();
    abstract ElGamal.Ciphertext message();
    abstract List<DecryptionShare.CiphertextDecryptionSelection> shares();

    public static PlaintextTallySelection create(String object_id, BigInteger tally, ElementModP value, ElGamal.Ciphertext message, List<DecryptionShare.CiphertextDecryptionSelection> shares) {
      return new AutoValue_Tally_PlaintextTallySelection(object_id, tally, value, message, shares);
    }

    public static TypeAdapter<PlaintextTallySelection> typeAdapter(Gson gson) {
      return new AutoValue_Tally_PlaintextTallySelection.GsonTypeAdapter(gson);
    }
  }

  /**
   * a CiphertextTallySelection is a homomorphic accumulation of all of the
   * CiphertextBallotSelection instances for a specific selection in an election.
   */
  static class CiphertextTallySelection extends CiphertextSelection {
    // Note not immutable - override superclass
    ElGamal.Ciphertext ciphertext_mutable; // default=ElGamalCiphertext(ONE_MOD_P, ONE_MOD_P)

    public CiphertextTallySelection(String object_id, ElementModQ description_hash, @Nullable ElGamal.Ciphertext ciphertext) {
      super(object_id, description_hash, ciphertext == null ? new ElGamal.Ciphertext(ONE_MOD_P, ONE_MOD_P) : ciphertext);
      this.ciphertext_mutable = (ciphertext == null) ? new ElGamal.Ciphertext(ONE_MOD_P, ONE_MOD_P) : ciphertext;
    }

    ElGamal.Ciphertext ciphertext() {
      return ciphertext_mutable;
    }

    /**
     * Homomorphically add the specified value to the message.
     */
    ElGamal.Ciphertext elgamal_accumulate(ElGamal.Ciphertext elgamal_ciphertext) {
      // Note not immutable
      this.ciphertext_mutable = ElGamal.elgamal_add(this.ciphertext_mutable, elgamal_ciphertext);
      return this.ciphertext();
    }
  }

  /**
   * A plaintext Tally Contest is a collection of plaintext selections
   */
  @AutoValue
  public static abstract class PlaintextTallyContest implements ElectionObjectBaseIF {
    abstract Map<String, PlaintextTallySelection> selections();

    public static PlaintextTallyContest create(String object_id, Map<String, PlaintextTallySelection> selections) {
      return new AutoValue_Tally_PlaintextTallyContest(object_id, selections);
    }

    public static TypeAdapter<PlaintextTallyContest> typeAdapter(Gson gson) {
      return new AutoValue_Tally_PlaintextTallyContest.GsonTypeAdapter(gson);
    }
  }

  /**
   * A CiphertextTallyContest is a container for associating a collection of CiphertextTallySelection
   * to a specific ContestDescription
   */
  @AutoValue
  public static abstract class CiphertextTallyContest implements ElectionObjectBaseIF {
    abstract ElementModQ description_hash(); // The ContestDescription hash
    abstract Map<String, CiphertextTallySelection> tally_selections(); // A collection of CiphertextTallySelection mapped by SelectionDescription.object_id

    public static CiphertextTallyContest create(String object_id, ElementModQ description_hash, Map<String, CiphertextTallySelection> tally_selections) {
      return new AutoValue_Tally_CiphertextTallyContest(object_id, description_hash, tally_selections);
    }

    public static TypeAdapter<CiphertextTallyContest> typeAdapter(Gson gson) {
      return new AutoValue_Tally_CiphertextTallyContest.GsonTypeAdapter(gson);
    }

    /**
     * Accumulate the contest selections of an individual ballot into this tally.
     */
    boolean accumulate_contest(List<CiphertextBallotSelection> contest_selections) {
      if (contest_selections.isEmpty()) {
        logger.atWarning().log("accumulate cannot add missing selections for contest %s", this.object_id());
        return false;
      }

      // Validate the input data by comparing the selection id's provided
      // to the valid selection id's for this tally contest
      Set<String> selection_ids = contest_selections.stream()
              .filter(s -> !s.is_placeholder_selection)
              .map(s -> s.object_id)
              .collect(Collectors.toSet());

      // if (any(set(this.tally_selections).difference(selection_ids))) {
      // Set<String> have = this.tally_selections.keySet();
      // if (!selection_ids.stream().anyMatch(id -> have.contains(id))) {
      if (!selection_ids.equals(this.tally_selections().keySet())) {
        logger.atWarning().log("accumulate cannot add mismatched selections for contest %s", this.object_id());
        return false;
      }

      // iterate through the tally selections and add the new value to the total
      List<Callable<Tuple>> tasks =
              this.tally_selections().entrySet().stream()
                      .map(entry -> new RunAccumulate(entry.getKey(), entry.getValue(), contest_selections))
                      .collect(Collectors.toList());
      Scheduler<Tuple> scheduler = new Scheduler<>();
      List<Tuple> results = scheduler.schedule(tasks, true);

      for (Tuple tuple : results) {
        if (tuple.ciphertext == null) {
          return false;
        } else {
          CiphertextTallySelection selection = this.tally_selections().get(tuple.id);
          selection.ciphertext_mutable = tuple.ciphertext;
        }
      }

      return true;
    }

    static class RunAccumulate implements Callable<Tuple> {
      final String id;
      final CiphertextTallySelection selection_tally;
      final List<CiphertextBallotSelection> contest_selections;

      public RunAccumulate(String id, CiphertextTallySelection selection_tally, List<CiphertextBallotSelection> contest_selections) {
        this.id = id;
        this.selection_tally = selection_tally;
        this.contest_selections = contest_selections;
      }

      @Override
      public Tuple call() {
        return _accumulate_selections(id, selection_tally, contest_selections);
      }
    }

    static Tuple _accumulate_selections(
            String key,
            CiphertextTallySelection selection_tally,
            List<CiphertextBallotSelection> contest_selections) {

      Optional<CiphertextBallotSelection> use_selection = contest_selections.stream()
              .filter(s -> key.equals(s.object_id)).findFirst();

      // a selection on the ballot that is required was not found
      // this should never happen when using the `CiphertextTally`, but sanity check anyway
      if (use_selection.isEmpty()) {
        logger.atWarning().log("add cannot accumulate for missing selection %s ", key);
        throw new RuntimeException("cant happen");
      }

      return new Tuple(key, selection_tally.elgamal_accumulate(use_selection.get().ciphertext()));
    }
  }

  /**
   * The plaintext representation of all contests in the election
   */
  @AutoValue
  public static abstract class PlaintextTally implements ElectionObjectBaseIF {
    abstract Map<String, PlaintextTallyContest> contests();
    abstract Map<String, Map<String, PlaintextTallyContest>> spoiled_ballots();

    public static PlaintextTally create(String object_id, Map<String, PlaintextTallyContest> contests, Map<String, Map<String, PlaintextTallyContest>> spoiled_ballots) {
      return new AutoValue_Tally_PlaintextTally(object_id, contests, spoiled_ballots);
    }
    public static TypeAdapter<PlaintextTally> typeAdapter(Gson gson) {
      return new AutoValue_Tally_PlaintextTally.GsonTypeAdapter(gson);
    }

  } // PlaintextTally

  /**
   * A `CiphertextTally` accepts cast and spoiled ballots and accumulates a tally on the cast ballots.
   */
  public static class CiphertextTally extends ElectionObjectBase {
    private final Election.InternalElectionDescription _metadata;
    private final Election.CiphertextElectionContext _encryption;

    // TODO TestTallyProperties needs to change this, make mutable version?
    // A local cache of ballots id's that have already been cast
    final Set<String> _cast_ballot_ids;

    //    A collection of each contest and selection in an election.
    //    Retains an encrypted representation of a tally for each selection
    final Map<String, CiphertextTallyContest> cast;
    // All of the ballots marked spoiled in the election
    final Map<String, Ballot.CiphertextAcceptedBallot> spoiled_ballots;

    public CiphertextTally(String object_id, Election.InternalElectionDescription metadata, Election.CiphertextElectionContext encryption) {
      super(object_id);
      this._metadata = metadata;
      this._encryption = encryption;
      this.spoiled_ballots = new HashMap<>();

      this._cast_ballot_ids = new HashSet<>();
      this.cast = _build_tally_collection(this._metadata);
    }

    int len() {
      return this._cast_ballot_ids.size() + this.spoiled_ballots.size();
    }

    /** Get a Count of the cast ballots. */
     int count() {
      return this._cast_ballot_ids.size();
    }

    private boolean contains(Object item) {
      if (!(item instanceof CiphertextAcceptedBallot)) {
        return false;
      }
      CiphertextAcceptedBallot ballot = (CiphertextAcceptedBallot) item;
      return this._cast_ballot_ids.contains(ballot.object_id) || this.spoiled_ballots.containsKey(ballot.object_id);
    }

    /**
     * Append a ballot to the tally and recalculate the tally. .
     */
    boolean append(CiphertextAcceptedBallot ballot) {
      if (ballot.state == BallotBoxState.UNKNOWN) {
        logger.atWarning().log("append cannot add %s with invalid state", ballot.object_id);
        return false;
      }

      if (this.contains(ballot)) {
        logger.atWarning().log("append cannot add %s that is already tallied", ballot.object_id);
        return false;
      }


      if (!BallotValidator.ballot_is_valid_for_election(ballot, this._metadata, this._encryption)) {
        return false;
      }

      if (ballot.state == BallotBoxState.CAST) {
        return this._add_cast(ballot);
      }

      if (ballot.state == BallotBoxState.SPOILED) {
        return this._add_spoiled(ballot);
      }

      logger.atWarning().log("append cannot add %s", ballot);
      return false;
    }

    /**
     * Append a collection of Ballots to the tally and recalculate.
     */
    boolean batch_append(Iterable<CiphertextAcceptedBallot> ballots) {
      Map<String, Map<String, ElGamal.Ciphertext>> cast_ballot_selections = new HashMap<>();

      for (CiphertextAcceptedBallot ballot : ballots) {
        if (!this.contains(ballot) &&
                BallotValidator.ballot_is_valid_for_election(ballot, this._metadata, this._encryption)) {
          if (ballot.state == BallotBoxState.CAST) {
            // collect the selections so they can can be accumulated in parallel
            for (CiphertextBallotContest contest : ballot.contests) {
              for (CiphertextBallotSelection selection : contest.ballot_selections) {
                if (!cast_ballot_selections.containsKey(selection.object_id)) {
                  cast_ballot_selections.put(selection.object_id, new HashMap<>());
                }
                // cast_ballot_selections[selection.object_id][ ballot_value.object_id] = selection.ciphertext
                cast_ballot_selections.get(selection.object_id).put(ballot.object_id, selection.ciphertext());
              }
            }

            // just append the spoiled ballots
          } else if (ballot.state == BallotBoxState.SPOILED) {
            this._add_spoiled(ballot);
          }
        }
      }

      // cache the cast ballot id's so they are not double counted
      if (this._execute_accumulate(cast_ballot_selections)) {
        for (CiphertextAcceptedBallot ballot : ballots) {
          if (ballot.state == BallotBoxState.CAST) {
            this._cast_ballot_ids.add(ballot.object_id);
          }
          return true;
        }
      }
      return false;
    }

    /**
     * Add a cast ballot to the tally, synchronously.
     */
    boolean _add_cast(CiphertextAcceptedBallot ballot) {

      // iterate through the contests and elgamal add
      for (CiphertextBallotContest contest : ballot.contests) {
        // This should never happen since the ballot is validated against the election metadata
        // but it's possible the local dictionary was modified so we double check.
        if (!this.cast.containsKey(contest.object_id)) {
          logger.atWarning().log("add cast missing contest in valid set %s", contest.object_id);
          return false;
        }

        CiphertextTallyContest use_contest = this.cast.get(contest.object_id);
        if (!use_contest.accumulate_contest(contest.ballot_selections)) {
          return false;
        }
        this.cast.put(contest.object_id, use_contest);
      }
      this._cast_ballot_ids.add(ballot.object_id);
      return true;
    }


    /**
     * Add a spoiled ballot.
     */
    boolean _add_spoiled(CiphertextAcceptedBallot ballot) {
      this.spoiled_ballots.put(ballot.object_id, ballot);
      return true;
    }

    /**
     * Build the object graph for the tally from the InternalElectionDescription.
     */
    static Map<String, CiphertextTallyContest> _build_tally_collection(Election.InternalElectionDescription description) {

      Map<String, CiphertextTallyContest> cast_collection = new HashMap<>();
      for (Election.ContestDescriptionWithPlaceholders contest : description.contests) {
        // build a collection of valid selections for the contest description
        // note: we explicitly ignore the Placeholder Selections.
        Map<String, CiphertextTallySelection> contest_selections = new HashMap<>();
        for (Election.SelectionDescription selection : contest.ballot_selections) {
          contest_selections.put(selection.object_id,
                  new CiphertextTallySelection(selection.object_id, selection.crypto_hash(), null));
        }
        cast_collection.put(contest.object_id, CiphertextTallyContest.create(contest.object_id, contest.crypto_hash(), contest_selections));
      }
      return cast_collection;
    }

    // Allow _accumulate to be run in parellel

    static class RunAccumulate implements Callable<Tuple> {
      final String id;
      final Map<String, ElGamal.Ciphertext> ballot_selections;

      public RunAccumulate(String id, Map<String, ElGamal.Ciphertext> ballot_selections) {
        this.id = id;
        this.ballot_selections = ballot_selections;
      }

      @Override
      public Tuple call() {
        return _accumulate(id, ballot_selections);
      }
    }

    static Tuple _accumulate(String id, Map<String, ElGamal.Ciphertext> ballot_selections) {
      //  *[ciphertext for ciphertext in ballot_selections.values()]
      return new Tuple(id, ElGamal.elgamal_add(Iterables.toArray(ballot_selections.values(), ElGamal.Ciphertext.class)));
    }

    boolean _execute_accumulate(
            Map<String, Map<String, ElGamal.Ciphertext>> ciphertext_selections_by_selection_id) {

      List<Callable<Tuple>> tasks =
              ciphertext_selections_by_selection_id.entrySet().stream()
                      .map(entry -> new RunAccumulate(entry.getKey(), entry.getValue()))
                      .collect(Collectors.toList());
      Scheduler<Tuple> scheduler = new Scheduler<>();
      List<Tuple> result_set = scheduler.schedule(tasks, true);

      Map<String, ElGamal.Ciphertext> result_dict = result_set.stream()
              .collect(Collectors.toMap(t -> t.id, t -> t.ciphertext));

      for (CiphertextTallyContest contest : this.cast.values()) {
        for (Map.Entry<String, CiphertextTallySelection> entry2 : contest.tally_selections().entrySet()) {
          String selection_id = entry2.getKey();
          CiphertextTallySelection selection = entry2.getValue();
          if (result_dict.containsKey(selection_id)) {
            selection.elgamal_accumulate(result_dict.get(selection_id));
          }
        }
      }

      return true;
    }

  } // class CiphertextTally

  /**
   * The published Ciphertext representation of all contests in the election.
   */
  @AutoValue
  public static abstract class PublishedCiphertextTally implements ElectionObjectBaseIF {
    abstract Map<String, CiphertextTallyContest> cast();

    public static PublishedCiphertextTally create(String object_id, Map<String, CiphertextTallyContest> cast) {
      return new AutoValue_Tally_PublishedCiphertextTally(object_id, cast);
    }

    public static TypeAdapter<PublishedCiphertextTally> typeAdapter(Gson gson) {
      return new AutoValue_Tally_PublishedCiphertextTally.GsonTypeAdapter(gson);
    }
  }

  /**
   * Publish a ciphertext tally with simpler format.
   */
  public static PublishedCiphertextTally publish_ciphertext_tally(CiphertextTally tally) {
    return PublishedCiphertextTally.create(tally.object_id, tally.cast);
  }

  /**
   * Tally a ballot that is either Cast or Spoiled
   * @return The mutated CiphertextTally or None if there is an error
   */
  static Optional<CiphertextTally> tally_ballot(CiphertextAcceptedBallot ballot, CiphertextTally tally) {

    if (ballot.state == BallotBoxState.UNKNOWN) {
      logger.atWarning().log("tally ballots error tallying unknown state for ballot %s", ballot.object_id);
      return Optional.empty();
    }

    if (tally.append(ballot)) {
      return Optional.of(tally);
    }

    return Optional.empty();
  }

  /**
   * Tally all of the ballots in the ballot store.
   * @return a CiphertextTally or None if there is an error
   */
  static Optional<CiphertextTally> tally_ballots(
          Iterable<CiphertextAcceptedBallot> store,
          Election.InternalElectionDescription metadata,
          Election.CiphertextElectionContext context) {

    // TODO: ISSUE #14: unique Id for the tally
    CiphertextTally tally = new CiphertextTally("election-results", metadata, context);
    if (tally.batch_append(store)) {
      return Optional.of(tally);
    }
    return Optional.empty();
  }

}
