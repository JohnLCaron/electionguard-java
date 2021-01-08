package com.sunya.electionguard;

import com.google.auto.value.AutoValue;
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

  /** A plaintext Tally Selection is a decrypted selection of a contest. */
  @AutoValue
  public static abstract class PlaintextTallySelection implements ElectionObjectBaseIF {
    // g^tally or M in the spec
    public abstract BigInteger tally();
    public abstract ElementModP value();
    public abstract ElGamal.Ciphertext message();
    public abstract List<DecryptionShare.CiphertextDecryptionSelection> shares();

    public static PlaintextTallySelection create(String object_id, BigInteger tally, ElementModP value, ElGamal.Ciphertext message, List<DecryptionShare.CiphertextDecryptionSelection> shares) {
      return new AutoValue_Tally_PlaintextTallySelection(object_id, tally, value, message, shares);
    }

    public static TypeAdapter<PlaintextTallySelection> typeAdapter(Gson gson) {
      return new AutoValue_Tally_PlaintextTallySelection.GsonTypeAdapter(gson);
    }
  } // PlaintextTallySelection

  /**
   * A CiphertextTallySelection is a homomorphic accumulation of all of the
   * CiphertextBallotSelection instances for a specific selection in an election.
   * The object_id is the Election.SelectionDescription.object_id of the selection.
   * Note this class is mutable, since we need to accumulate the ciphertext.
   */
  static class CiphertextTallySelection extends CiphertextSelection {
    // Note not immutable
    private ElGamal.Ciphertext ciphertext_accumulate; // default=ElGamalCiphertext(ONE_MOD_P, ONE_MOD_P)

    public CiphertextTallySelection(String selectionDescriptionId, ElementModQ description_hash, @Nullable ElGamal.Ciphertext ciphertext) {
      super(selectionDescriptionId, description_hash, ciphertext == null ? new ElGamal.Ciphertext(ONE_MOD_P, ONE_MOD_P) : ciphertext);
      this.ciphertext_accumulate = (ciphertext == null) ? new ElGamal.Ciphertext(ONE_MOD_P, ONE_MOD_P) : ciphertext;
    }

    @Override
    public ElGamal.Ciphertext ciphertext() {
      return ciphertext_accumulate;
    }

    /**
     * Homomorphically add the specified value to the message.
     * Note that this method is called by separate threads.
     */
    private synchronized ElGamal.Ciphertext elgamal_accumulate(ElGamal.Ciphertext elgamal_ciphertext) {
      this.ciphertext_accumulate = ElGamal.elgamal_add(this.ciphertext_accumulate, elgamal_ciphertext);
      return this.ciphertext();
    }
  } // CiphertextTallySelection

  /** A plaintext Tally Contest is a collection of plaintext selections. */
  @AutoValue
  public static abstract class PlaintextTallyContest implements ElectionObjectBaseIF {
    public abstract Map<String, PlaintextTallySelection> selections();

    public static PlaintextTallyContest create(String object_id, Map<String, PlaintextTallySelection> selections) {
      return new AutoValue_Tally_PlaintextTallyContest(object_id, selections);
    }

    public static TypeAdapter<PlaintextTallyContest> typeAdapter(Gson gson) {
      return new AutoValue_Tally_PlaintextTallyContest.GsonTypeAdapter(gson);
    }
  } // PlaintextTallyContest

  /**
   * A CiphertextTallyContest is a container for associating a collection of CiphertextTallySelection
   * to a specific ContestDescription.
   */
  public static class CiphertextTallyContest extends ElectionObjectBase {
    /** The ContestDescription hash. */
    private final ElementModQ description_hash;

    /** A collection of CiphertextTallySelection mapped by SelectionDescription.object_id. */
    private final Map<String, CiphertextTallySelection> tally_selections;

    public CiphertextTallyContest(String object_id, ElementModQ description_hash, Map<String, CiphertextTallySelection> tally_selections) {
      super(object_id);
      this.description_hash = description_hash;
      this.tally_selections = tally_selections;
    }

    ElementModQ description_hash() {
      return description_hash;
    }

    ImmutableMap<String, CiphertextTallySelection> tally_selections() {
      return ImmutableMap.copyOf(tally_selections);
    }

    /** Accumulate the contest selections of an individual ballot into this tally. */
    private boolean accumulate_contest(List<CiphertextBallotSelection> contest_selections) {
      if (contest_selections.isEmpty()) {
        logger.atWarning().log("accumulate cannot add missing selections for contest %s", this.object_id);
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
      if (!selection_ids.equals(this.tally_selections.keySet())) {
        logger.atWarning().log("accumulate cannot add mismatched selections for contest %s", this.object_id);
        return false;
      }

      // Accumulate the tally selections in parallel tasks
      List<Callable<AccumSelectionsTuple>> tasks =
              this.tally_selections.entrySet().stream()
                      .map(entry -> new RunAccumulateSelections(entry.getKey(), entry.getValue(), contest_selections))
                      .collect(Collectors.toList());

      Scheduler<AccumSelectionsTuple> scheduler = new Scheduler<>();
      // this line is the only parallel processing
      List<AccumSelectionsTuple> results = scheduler.schedule(tasks, true);

      for (AccumSelectionsTuple tuple : results) {
        if (tuple.ciphertext == null) {
          return false;
        } else {
          CiphertextTallySelection selection = this.tally_selections.get(tuple.selection_id);
          selection.ciphertext_accumulate = tuple.ciphertext;
        }
      }

      return true;
    }

    @Immutable
    private static class AccumSelectionsTuple {
      final String selection_id;
      final ElGamal.Ciphertext ciphertext;

      public AccumSelectionsTuple(String selection_id, ElGamal.Ciphertext ciphertext) {
        this.selection_id = selection_id;
        this.ciphertext = ciphertext;
      }
    }

    static class RunAccumulateSelections implements Callable<AccumSelectionsTuple> {
      final String selection_id;
      final CiphertextTallySelection selection_tally;
      final List<CiphertextBallotSelection> contest_selections;

      public RunAccumulateSelections(String id, CiphertextTallySelection selection_tally, List<CiphertextBallotSelection> contest_selections) {
        this.selection_id = id;
        this.selection_tally = selection_tally;
        this.contest_selections = contest_selections;
      }

      @Override
      public AccumSelectionsTuple call() {
        Optional<CiphertextBallotSelection> use_selection = contest_selections.stream()
                .filter(s -> selection_id.equals(s.object_id)).findFirst();

        // a selection on the ballot that is required was not found
        // this should never happen when using the `CiphertextTally`, but sanity check anyway
        if (use_selection.isEmpty()) {
          logger.atWarning().log("add cannot accumulate for missing selection %s ", selection_id);
          throw new RuntimeException("cant happen");
        }

        ElGamal.Ciphertext ciphertext = selection_tally.elgamal_accumulate(use_selection.get().ciphertext());
        return new AccumSelectionsTuple(selection_id, ciphertext);
      }
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      if (!super.equals(o)) return false;
      CiphertextTallyContest that = (CiphertextTallyContest) o;
      return description_hash.equals(that.description_hash) &&
              tally_selections.equals(that.tally_selections);
    }

    @Override
    public int hashCode() {
      return Objects.hash(super.hashCode(), description_hash, tally_selections);
    }
  } // CiphertextTallyContest

  /** The plaintext representation of all contests in the election. */
  @AutoValue
  public static abstract class PlaintextTally implements ElectionObjectBaseIF {
    public abstract Map<String, PlaintextTallyContest> contests();
    public abstract Map<String, Map<String, PlaintextTallyContest>> spoiled_ballots();

    public static PlaintextTally create(String object_id, Map<String, PlaintextTallyContest> contests, Map<String, Map<String, PlaintextTallyContest>> spoiled_ballots) {
      return new AutoValue_Tally_PlaintextTally(object_id, contests, spoiled_ballots);
    }
    public static TypeAdapter<PlaintextTally> typeAdapter(Gson gson) {
      return new AutoValue_Tally_PlaintextTally.GsonTypeAdapter(gson);
    }

  } // PlaintextTally

  /** A `CiphertextTally` accepts cast and spoiled ballots and accumulates a tally on the cast ballots. */
  public static class CiphertextTally extends ElectionObjectBase {
    private final Election.InternalElectionDescription _metadata;
    private final Election.CiphertextElectionContext _encryption;

    /** A local cache of ballots id's that have already been cast. */
    private final Set<String> _cast_ballot_ids;

    /** A collection of each contest and selection in an election. Retains an encrypted representation of a tally for each selection. */
    private final Map<String, CiphertextTallyContest> cast;

    /** All of the ballots marked spoiled in the election. */
    private final Map<String, Ballot.CiphertextAcceptedBallot> spoiled_ballots;

    public CiphertextTally(String object_id, Election.InternalElectionDescription metadata, Election.CiphertextElectionContext encryption) {
      super(object_id);
      this._metadata = metadata;
      this._encryption = encryption;
      this.spoiled_ballots = new HashMap<>();

      this._cast_ballot_ids = new HashSet<>();
      this.cast = _build_tally_collection(this._metadata);
    }

    ImmutableMap<String, CiphertextTallyContest> cast() {
      return ImmutableMap.copyOf(cast);
    }

    ImmutableMap<String, Ballot.CiphertextAcceptedBallot> spoiled_ballots() {
      return ImmutableMap.copyOf(spoiled_ballots);
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

    /** Append a ballot to the tally and recalculate the tally. */
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

    /** Append a collection of Ballots to the tally and recalculate. */
    private boolean batch_append(Iterable<CiphertextAcceptedBallot> ballots) {
      // Map(SELECTION_ID, Map(BALLOT_ID, Ciphertext)
      Map<String, Map<String, ElGamal.Ciphertext>> cast_ballot_selections = new HashMap<>();

      // Find all the ballots for each selection.
      for (CiphertextAcceptedBallot ballot : ballots) {
        if (!this.contains(ballot) &&
                BallotValidator.ballot_is_valid_for_election(ballot, this._metadata, this._encryption)) {

          if (ballot.state == BallotBoxState.CAST) {
            // collect the selections so they can can be accumulated in parallel
            for (CiphertextBallotContest contest : ballot.contests) {
              for (CiphertextBallotSelection selection : contest.ballot_selections) {
                Map<String, ElGamal.Ciphertext> map2 = cast_ballot_selections.computeIfAbsent(selection.object_id, map1 -> new HashMap<>());
                map2.put(ballot.object_id, selection.ciphertext());
              }
            }
          } else if (ballot.state == BallotBoxState.SPOILED) {
            // just append the spoiled ballots
            this._add_spoiled(ballot);
          }
        }
      }

      // LOOK i dont think this is being tested.
      // cache the cast ballot id's so they are not double counted
      if (this._execute_accumulate(cast_ballot_selections)) {
        for (CiphertextAcceptedBallot ballot : ballots) {
          if (ballot.state == BallotBoxState.CAST) {
            this._cast_ballot_ids.add(ballot.object_id);
          }
        }
        return true;
      }

      return false;
    }

    /** Add a single cast ballot to the tally, synchronously. */
    private boolean _add_cast(CiphertextAcceptedBallot ballot) {
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


    /** Add a spoiled ballot. */
    boolean _add_spoiled(CiphertextAcceptedBallot ballot) {
      this.spoiled_ballots.put(ballot.object_id, ballot);
      return true;
    }

    /** Build the object graph for the tally from the InternalElectionDescription. */
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
        cast_collection.put(contest.object_id, new CiphertextTallyContest(contest.object_id, contest.crypto_hash(), contest_selections));
      }
      return cast_collection;
    }

    @Immutable
    private static class AccumOverBallotsTuple {
      final String selection_id;
      final ElGamal.Ciphertext ciphertext; // accumulation over ballots

      public AccumOverBallotsTuple(String selection_id, ElGamal.Ciphertext ciphertext) {
        this.selection_id = selection_id;
        this.ciphertext = ciphertext;
      }
    } // AccumBallots

    //// Allow _accumulate to be run in parallel
    static class RunAccumulateOverBallots implements Callable<AccumOverBallotsTuple> {
      final String selection_id;
      final Map<String, ElGamal.Ciphertext> ballot_selections; // Map(BALLOT_ID, Ciphertext)

      public RunAccumulateOverBallots(String selection_id, Map<String, ElGamal.Ciphertext> ballot_selections) {
        this.selection_id = selection_id;
        this.ballot_selections = ballot_selections;
      }

      @Override
      public AccumOverBallotsTuple call() {
        // ok to do this is parallel , all state is local to this class
        ElGamal.Ciphertext ciphertext =  ElGamal.elgamal_add(Iterables.toArray(ballot_selections.values(), ElGamal.Ciphertext.class));
        return new AccumOverBallotsTuple(selection_id, ciphertext);
      }
    } // RunAccumulate

    /**
     * Called from batch_append(), process each inner map asynchronously.
     * @param ciphertext_selections_by_selection_id For each selection, the set of ballots for that selection.
     *                                              Map(SELECTION_ID, Map(BALLOT_ID, Ciphertext)
     * @return always return true.
     */
    boolean _execute_accumulate(
            Map<String, Map<String, ElGamal.Ciphertext>> ciphertext_selections_by_selection_id) {

      List<Callable<AccumOverBallotsTuple>> tasks =
              ciphertext_selections_by_selection_id.entrySet().stream()
                      .map(entry -> new RunAccumulateOverBallots(entry.getKey(), entry.getValue()))
                      .collect(Collectors.toList());

      Scheduler<AccumOverBallotsTuple> scheduler = new Scheduler<>();
      // This line is the only parallel processing
      List<AccumOverBallotsTuple> result_set = scheduler.schedule(tasks, true);

      // Turn result_set into a Map(SELECTION_ID, Ciphertext)
      Map<String, ElGamal.Ciphertext> result_dict = result_set.stream()
              .collect(Collectors.toMap(t -> t.selection_id, t -> t.ciphertext));

      for (CiphertextTallyContest contest : this.cast.values()) {
        for (Map.Entry<String, CiphertextTallySelection> entry2 : contest.tally_selections().entrySet()) {
          String selection_id = entry2.getKey();
          CiphertextTallySelection selection = entry2.getValue();
          if (result_dict.containsKey(selection_id)) {
            selection.elgamal_accumulate(result_dict.get(selection_id));
          }
        }
      }

      return true; // always true
    }

  } // class CiphertextTally

  /** The published Ciphertext representation of all contests in the election. */
  @AutoValue
  public static abstract class PublishedCiphertextTally implements ElectionObjectBaseIF {
    public abstract Map<String, CiphertextTallyContest> cast();

    public static PublishedCiphertextTally create(String object_id, Map<String, CiphertextTallyContest> cast) {
      return new AutoValue_Tally_PublishedCiphertextTally(object_id, cast);
    }

    public static TypeAdapter<PublishedCiphertextTally> typeAdapter(Gson gson) {
      return new AutoValue_Tally_PublishedCiphertextTally.GsonTypeAdapter(gson);
    }
  } // PublishedCiphertextTally


  //////////////////////////////////////////////////////////////////////////////
  // testing

  /** Publish a ciphertext tally with simpler format. */
  public static PublishedCiphertextTally publish_ciphertext_tally(CiphertextTally tally) {
    return PublishedCiphertextTally.create(tally.object_id, tally.cast);
  }

  /**
   * Tally a ballot that is either Cast or Spoiled.
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
