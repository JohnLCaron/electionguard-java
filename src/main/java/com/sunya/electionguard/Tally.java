package com.sunya.electionguard;

import com.google.auto.value.AutoValue;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.common.flogger.FluentLogger;
import com.google.gson.Gson;
import com.google.gson.TypeAdapter;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;

import static com.sunya.electionguard.Ballot.BallotBoxState;
import static com.sunya.electionguard.Ballot.CiphertextAcceptedBallot;
import static com.sunya.electionguard.Group.*;

/** A Tally accumulates the results of many ballots, contests, and selections. */
public class Tally {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  /**
   * The plaintext representation of the counts of one selection of one contest in the election.
   * The object_id is the same as the encrypted selection (Ballot.CiphertextSelection) object_id.
   */
  @AutoValue
  public static abstract class PlaintextTallySelection implements ElectionObjectBaseIF {
    /** The actual count. */
    public abstract Integer tally();
    /** g^tally or M in the spec. */
    public abstract ElementModP value();
    public abstract ElGamal.Ciphertext message();
    public abstract ImmutableList<DecryptionShare.CiphertextDecryptionSelection> shares();

    public static PlaintextTallySelection create(String object_id, Integer tally, ElementModP value, ElGamal.Ciphertext message, List<DecryptionShare.CiphertextDecryptionSelection> shares) {
      return new AutoValue_Tally_PlaintextTallySelection(
              Preconditions.checkNotNull(object_id),
              Preconditions.checkNotNull(tally),
              Preconditions.checkNotNull(value),
              Preconditions.checkNotNull(message),
              ImmutableList.copyOf(shares));
    }

    public static TypeAdapter<PlaintextTallySelection> typeAdapter(Gson gson) {
      return new AutoValue_Tally_PlaintextTallySelection.GsonTypeAdapter(gson);
    }
  } // PlaintextTallySelection

  /**
   * The plaintext representation of the counts of one contest in the election.
   * The object_id is the same as the Election.ContestDescription.object_id or PlaintextBallotContest object_id.
   */
  @AutoValue
  public static abstract class PlaintextTallyContest implements ElectionObjectBaseIF {
    public abstract Map<String, PlaintextTallySelection> selections(); // Map(SELECTION_ID, PlaintextTallySelection)

    public static PlaintextTallyContest create(String object_id, Map<String, PlaintextTallySelection> selections) {
      return new AutoValue_Tally_PlaintextTallyContest(
              Preconditions.checkNotNull(object_id),
              Preconditions.checkNotNull(selections));
    }

    public static TypeAdapter<PlaintextTallyContest> typeAdapter(Gson gson) {
      return new AutoValue_Tally_PlaintextTallyContest.GsonTypeAdapter(gson);
    }
  } // PlaintextTallyContest

  /**
   * The plaintext representation of the counts of all contests in the election.
   * The object_id is the same as the CiphertextTally object_id.
   */
  @AutoValue
  public static abstract class PlaintextTally implements ElectionObjectBaseIF {
    /** The cast ballots. */
    public abstract Map<String, PlaintextTallyContest> contests(); // Map(CONTEST_ID, PlaintextTallyContest)
    /** The spoiled ballots. */
    public abstract Map<String, Map<String, PlaintextTallyContest>> spoiled_ballots(); // Map(BALLOT_ID, Map(CONTEST_ID, PlaintextTallyContest))

    public static PlaintextTally create(String object_id, Map<String, PlaintextTallyContest> contests,
                                        Map<String, Map<String, PlaintextTallyContest>> spoiled_ballots) {
      return new AutoValue_Tally_PlaintextTally(
              Preconditions.checkNotNull(object_id),
              Preconditions.checkNotNull(contests),
              Preconditions.checkNotNull(spoiled_ballots));
    }
    public static TypeAdapter<PlaintextTally> typeAdapter(Gson gson) {
      return new AutoValue_Tally_PlaintextTally.GsonTypeAdapter(gson);
    }

  } // PlaintextTally

  //////////////////////////////////////////////////////////////////////////////////////////////

  /**
   * A CiphertextTallySelection is a homomorphic accumulation of all of the
   * CiphertextBallotSelection instances for a specific selection and contest in an election.
   * The object_id is the Election.SelectionDescription.object_id.
   * Note this class is mutable, since we need to do the accumulation here.
   */
  public static class CiphertextTallySelection extends Ballot.CiphertextSelection {
    // Note mutable
    private ElGamal.Ciphertext ciphertext_accumulate; // default = ElGamalCiphertext(ONE_MOD_P, ONE_MOD_P)

    public CiphertextTallySelection(String selectionDescriptionId, ElementModQ description_hash, @Nullable ElGamal.Ciphertext ciphertext) {
      super(selectionDescriptionId, description_hash, ciphertext == null ? new ElGamal.Ciphertext(ONE_MOD_P, ONE_MOD_P) : ciphertext);
      this.ciphertext_accumulate = (ciphertext == null) ? new ElGamal.Ciphertext(ONE_MOD_P, ONE_MOD_P) : ciphertext;
    }

    @Override
    public ElGamal.Ciphertext ciphertext() {
      return ciphertext_accumulate;
    }

    /**
     * Homomorphically add the specified value to the accumulation.
     * Note that this method may be called by multiple threads.
     */
    private synchronized ElGamal.Ciphertext elgamal_accumulate(ElGamal.Ciphertext elgamal_ciphertext) {
      this.ciphertext_accumulate = ElGamal.elgamal_add(this.ciphertext_accumulate, elgamal_ciphertext);
      return this.ciphertext();
    }
  } // CiphertextTallySelection

  /**
   * A CiphertextTallyContest groups the CiphertextTallySelection's for a specific Election.ContestDescription.
   * The object_id is the Election.ContestDescription.object_id.
   */
  public static class CiphertextTallyContest extends ElectionObjectBase {
    /** The ContestDescription hash. */
    private final ElementModQ description_hash;

    /** A collection of CiphertextTallySelection mapped by SelectionDescription.object_id. */
    private final Map<String, CiphertextTallySelection> tally_selections; // Map(SELECTION_ID, CiphertextTallySelection)

    public CiphertextTallyContest(String object_id, ElementModQ description_hash, Map<String, CiphertextTallySelection> tally_selections) {
      super(object_id);
      this.description_hash = description_hash;
      this.tally_selections = tally_selections;
    }

    public ElementModQ description_hash() {
      return description_hash;
    }

    public ImmutableMap<String, CiphertextTallySelection> tally_selections() {
      return ImmutableMap.copyOf(tally_selections);
    }

    /** Accumulate the contest selections of an individual ballot into this tally. */
    private boolean accumulate_contest(List<Ballot.CiphertextBallotSelection> contest_selections) {
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

    @Immutable
    static class RunAccumulateSelections implements Callable<AccumSelectionsTuple> {
      final String selection_id;
      final CiphertextTallySelection selection_tally;
      final ImmutableList<Ballot.CiphertextBallotSelection> contest_selections;

      public RunAccumulateSelections(String id, CiphertextTallySelection selection_tally, List<Ballot.CiphertextBallotSelection> contest_selections) {
        this.selection_id = id;
        this.selection_tally = selection_tally;
        this.contest_selections = ImmutableList.copyOf(contest_selections);
      }

      @Override
      public AccumSelectionsTuple call() {
        Optional<Ballot.CiphertextBallotSelection> use_selection = contest_selections.stream()
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

  /**
   * The homomorphically-combined and encrypted representation of all selections made for each option on
   * every contest in the election. Mutable.
   */
  public static class CiphertextTally extends ElectionObjectBase {
    private final Election.InternalElectionDescription _metadata;
    private final Election.CiphertextElectionContext _encryption;

    /** A local cache of ballots id's that have already been cast. */
    private final Set<String> _cast_ballot_ids;

    /** A collection of each contest and selection in an election. Retains an encrypted representation of a tally for each selection. */
    private final Map<String, CiphertextTallyContest> cast; // Map(CONTEST_ID, CiphertextTallyContest)

    /** All of the ballots marked spoiled in the election. */
    private final Map<String, CiphertextAcceptedBallot> spoiled_ballots; // Map(BALLOT_ID, CiphertextTallyContest)

    public CiphertextTally(String object_id, Election.InternalElectionDescription metadata, Election.CiphertextElectionContext encryption) {
      super(object_id);
      this._metadata = metadata;
      this._encryption = encryption;
      this.spoiled_ballots = new HashMap<>();

      this._cast_ballot_ids = new HashSet<>();
      this.cast = _build_tally_collection(this._metadata);
    }

    /** Build the object graph for the tally from the InternalElectionDescription. */
    private static Map<String, CiphertextTallyContest> _build_tally_collection(Election.InternalElectionDescription description) {
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

    ImmutableMap<String, CiphertextTallyContest> cast() {
      return ImmutableMap.copyOf(cast);
    }

    ImmutableMap<String, CiphertextAcceptedBallot> spoiled_ballots() {
      return ImmutableMap.copyOf(spoiled_ballots);
    }

    int len() {
      return this._cast_ballot_ids.size() + this.spoiled_ballots.size();
    }

    /** Get the number of cast ballots (just the count, not the tally). */
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

    /** Publish a ciphertext tally with simpler format. */
    public PublishedCiphertextTally publish_ciphertext_tally() {
      return PublishedCiphertextTally.create(object_id, cast);
    }

    /** Append a collection of Ballots to the tally and recalculate. */
    private boolean batch_append(Iterable<CiphertextAcceptedBallot> ballots) {
      // Map(SELECTION_ID, Map(BALLOT_ID, Ciphertext)
      Map<String, Map<String, ElGamal.Ciphertext>> cast_ballot_selections = new HashMap<>();

      // Find all the ballots for each selection.
      for (CiphertextAcceptedBallot ballot : ballots) {
        if (!this.contains(ballot) &&
                BallotValidations.ballot_is_valid_for_election(ballot, this._metadata, this._encryption)) {

          if (ballot.state == BallotBoxState.CAST) {
            // collect the selections so they can can be accumulated in parallel
            for (Ballot.CiphertextBallotContest contest : ballot.contests) {
              for (Ballot.CiphertextBallotSelection selection : contest.ballot_selections) {
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

      if (!BallotValidations.ballot_is_valid_for_election(ballot, this._metadata, this._encryption)) {
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

    /** Add a single cast ballot to the tally, synchronously. */
    private boolean _add_cast(CiphertextAcceptedBallot ballot) {
      // iterate through the contests and elgamal add
      for (Ballot.CiphertextBallotContest contest : ballot.contests) {
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
    @Immutable
    static class RunAccumulateOverBallots implements Callable<AccumOverBallotsTuple> {
      final String selection_id;
      final ImmutableMap<String, ElGamal.Ciphertext> ballot_selections; // Map(BALLOT_ID, Ciphertext)

      public RunAccumulateOverBallots(String selection_id, Map<String, ElGamal.Ciphertext> ballot_selections) {
        this.selection_id = selection_id;
        this.ballot_selections = ImmutableMap.copyOf(ballot_selections);
      }

      @Override
      public AccumOverBallotsTuple call() {
        // ok to do this is parallel , all state is local to this class
        ElGamal.Ciphertext ciphertext =  ElGamal.elgamal_add(Iterables.toArray(ballot_selections.values(), ElGamal.Ciphertext.class));
        return new AccumOverBallotsTuple(selection_id, ciphertext);
      }
    } // RunAccumulate

    /**
     * Called from batch_append(), process each inner map in parallel.
     * @param ciphertext_selections_by_selection_id For each selection, the set of ballots for that selection.
     *                                              Map(SELECTION_ID, Map(BALLOT_ID, Ciphertext)
     * @return always return true.
     */
    private boolean _execute_accumulate(
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

  /**
   * The published Ciphertext representation of all contests in the election.
   * The object_id is the same as from the CiphertextTally
   * LOOK not immutable, because CiphertextTallySelection is not: make an immutable version of it.
   */
  @AutoValue
  public static abstract class PublishedCiphertextTally implements ElectionObjectBaseIF {
    // LOOK should be Immutable, fix json serialization
    public abstract Map<String, CiphertextTallyContest> cast(); // Map(CONTEST_ID, CiphertextTallyContest)

    public static PublishedCiphertextTally create(String object_id, Map<String, CiphertextTallyContest> cast) {
      return new AutoValue_Tally_PublishedCiphertextTally(object_id, cast);
    }

    public static TypeAdapter<PublishedCiphertextTally> typeAdapter(Gson gson) {
      return new AutoValue_Tally_PublishedCiphertextTally.GsonTypeAdapter(gson);
    }
  } // PublishedCiphertextTally


  //////////////////////////////////////////////////////////////////////////////

  // static publish_ciphertext_tally use CiphertextTally.publish_ciphertext_tally().
  // tally_ballot use CiphertextTally.append().

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
