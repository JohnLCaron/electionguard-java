package com.sunya.electionguard;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.common.flogger.FluentLogger;
import com.sunya.electionguard.publish.CloseableIterable;
import com.sunya.electionguard.publish.CloseableIterator;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;

import static com.sunya.electionguard.BallotBox.State;

import static com.sunya.electionguard.Group.ElementModQ;
import static com.sunya.electionguard.Group.ONE_MOD_P;
import static com.sunya.electionguard.ElectionWithPlaceholders.ContestWithPlaceholders;


/** A mutable builder of CiphertextTally */
public class CiphertextTallyBuilder {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  public final String object_id;
  private final ElectionWithPlaceholders metadata;
  private final CiphertextElectionContext encryption;

  /** Local cache of ballots id's that have already been cast. */
  private final Set<String> cast_ballot_ids;
  private final Set<String> spoiled_ballot_ids;

  /** An encrypted representation of each contest and selection for all the cast ballots. */
  public final Map<String, Contest> contests; // Map(CONTEST_ID, CiphertextTallyContest)

  public CiphertextTallyBuilder(String object_id, ElectionWithPlaceholders metadata, CiphertextElectionContext encryption) {
    this.object_id = object_id;
    this.metadata = metadata;
    this.encryption = encryption;
    this.cast_ballot_ids = new HashSet<>();
    this.spoiled_ballot_ids = new HashSet<>();
    this.contests = build_contests(this.metadata);
  }

  /** Build the object graph for the tally from the ElectionWithPlaceholders. */
  private Map<String, Contest> build_contests(ElectionWithPlaceholders metadata) {
    Map<String, Contest> cast_collection = new HashMap<>();
    for (ContestWithPlaceholders contest : metadata.contests) {
      // build a collection of valid selections for the contest description, ignoring the Placeholder Selections.
      Map<String, Selection> contest_selections = new HashMap<>();
      for (Election.SelectionDescription selection : contest.ballot_selections) {
        contest_selections.put(selection.object_id,
                new Selection(selection.object_id, selection.crypto_hash(), null));
      }
      cast_collection.put(contest.object_id, new Contest(contest.object_id, contest.crypto_hash(), contest_selections));
    }
    return cast_collection;
  }

  /** Append a collection of Ballots to the tally. Potentially parellizable over ballots. */
  public int batch_append(CloseableIterable<CiphertextAcceptedBallot> ballots) {
    // Map(SELECTION_ID, Map(BALLOT_ID, Ciphertext)
    Map<String, Map<String, ElGamal.Ciphertext>> cast_ballot_selections = new HashMap<>();

    // Find all the ballots for each selection.
    int count = 0;
    try (CloseableIterator<CiphertextAcceptedBallot> ballotsIter = ballots.iterator()) {
      while (ballotsIter.hasNext()) {
        CiphertextAcceptedBallot ballot = ballotsIter.next();
        if (ballot.state != State.CAST) {
          continue;
        }
        // make sure no double counting
        if (cast_ballot_ids.contains(ballot.object_id)) {
          continue;
        }
        if (!BallotValidations.ballot_is_valid_for_election(ballot, this.metadata, this.encryption)) {
          continue;
        }
        // collect the selections so they can be accumulated in parallel
        for (CiphertextBallot.Contest contest : ballot.contests) {
          for (CiphertextBallot.Selection selection : contest.ballot_selections) {
            Map<String, ElGamal.Ciphertext> map2 = cast_ballot_selections.computeIfAbsent(selection.object_id, map1 -> new HashMap<>());
            map2.put(ballot.object_id, selection.ciphertext());
          }
        }
        this.cast_ballot_ids.add(ballot.object_id);
        count++;
      }
    } catch (IOException e) {
      throw new RuntimeException(e);
    }

    // heres where the tallies are actually accumulated
    boolean ok = this.execute_accumulate(cast_ballot_selections);
    return ok ? count : 0;
  }

  /** Append a ballot to the tally. Potentially parellizable over this ballot's selections. */
  public boolean append(CiphertextAcceptedBallot ballot) {
    if (ballot.state == State.UNKNOWN) {
      logger.atWarning().log("append cannot add %s with invalid state", ballot.object_id);
      return false;
    }

    if (this.cast_ballot_ids.contains(ballot.object_id) || this.spoiled_ballot_ids.contains(ballot.object_id)) {
      logger.atWarning().log("append cannot add %s that is already tallied", ballot.object_id);
      return false;
    }

    if (!BallotValidations.ballot_is_valid_for_election(ballot, this.metadata, this.encryption)) {
      return false;
    }

    if (ballot.state == State.CAST) {
      return this.add_cast(ballot);
    }

    if (ballot.state == State.SPOILED) {
      // LOOK we dont do anything other than record its been spoiled, so it wont be processed again
      this.spoiled_ballot_ids.add(ballot.object_id);

      return true;
    }

    logger.atWarning().log("append cannot add %s", ballot);
    return false;
  }

  /** Add a single cast ballot to the tally. Potentially parellizable over this ballot's selections. */
  private boolean add_cast(CiphertextAcceptedBallot ballot) {
    // iterate through the contests and elgamal add
    for (CiphertextBallot.Contest contest : ballot.contests) {
      // This should never happen since the ballot is validated against the election metadata
      // but it's possible the local dictionary was modified so we double check.
      if (!this.contests.containsKey(contest.object_id)) {
        logger.atWarning().log("add cast missing contest in valid set %s", contest.object_id);
        return false;
      }

      Contest use_contest = this.contests.get(contest.object_id);
      // Potentially parellizable over this ballot's selections.
      if (!use_contest.accumulate_contest(contest.ballot_selections)) {
        return false;
      }
      this.contests.put(contest.object_id, use_contest);
    }
    this.cast_ballot_ids.add(ballot.object_id);
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
  }

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
      ElGamal.Ciphertext ciphertext = ElGamal.elgamal_add(Iterables.toArray(ballot_selections.values(), ElGamal.Ciphertext.class));
      return new AccumOverBallotsTuple(selection_id, ciphertext);
    }
  }

  /**
   * Called from batch_append(), process each inner map in parallel.
   *
   * @param ciphertext_selections_by_selection_id For each selection, the set of ballots for that selection.
   *                                              Map(SELECTION_ID, Map(BALLOT_ID, Ciphertext)
   * @return always return true.
   */
  private boolean execute_accumulate(
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

    for (Contest contest : this.contests.values()) {
      for (Map.Entry<String, Selection> entry2 : contest.tally_selections.entrySet()) {
        String selection_id = entry2.getKey();
        Selection selection = entry2.getValue();
        if (result_dict.containsKey(selection_id)) {
          selection.elgamal_accumulate(result_dict.get(selection_id));
        }
      }
    }

    return true; // always true
  }

  public CiphertextTally build() {
    return new CiphertextTally(
            this.object_id,
            this.contests.entrySet().stream().collect(Collectors.toMap(e -> e.getKey(), e -> e.getValue().build())));
  }

  ///////////////////////////////////////////////////////////////////////////////////////////////////////////

  /**
   * A CiphertextTallyContest groups the CiphertextTallySelection's for a specific Election.ContestDescription.
   * The object_id is the Election.ContestDescription.object_id.
   */
  static class Contest extends ElectionObjectBase {
    /** The ContestDescription hash. */
    final ElementModQ description_hash;

    /** A collection of CiphertextTallySelection mapped by SelectionDescription.object_id. */
    final Map<String, Selection> tally_selections; // Map(SELECTION_ID, CiphertextTallySelection)

    public Contest(String object_id, ElementModQ description_hash, Map<String, Selection> tally_selections) {
      super(object_id);
      this.description_hash = description_hash;
      this.tally_selections = tally_selections;
    }

    /** Accumulate the contest selections of an individual ballot into this tally. Potentially parellizable over selections.*/
    private boolean accumulate_contest(List<CiphertextBallot.Selection> contest_selections) {
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
          Selection selection = this.tally_selections.get(tuple.selection_id);
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
      final Selection selection_tally;
      final ImmutableList<CiphertextBallot.Selection> contest_selections;

      public RunAccumulateSelections(String id, Selection selection_tally, List<CiphertextBallot.Selection> contest_selections) {
        this.selection_id = id;
        this.selection_tally = selection_tally;
        this.contest_selections = ImmutableList.copyOf(contest_selections);
      }

      @Override
      public AccumSelectionsTuple call() {
        Optional<CiphertextBallot.Selection> use_selection = contest_selections.stream()
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
      Contest that = (Contest) o;
      return description_hash.equals(that.description_hash) &&
              tally_selections.equals(that.tally_selections);
    }

    @Override
    public int hashCode() {
      return Objects.hash(super.hashCode(), description_hash, tally_selections);
    }

    CiphertextTally.Contest build() {
      // String object_id, ElementModQ description_hash, Map<String, CiphertextTallySelection> tally_selections
      return new CiphertextTally.Contest(
              this.object_id, this.description_hash,
              this.tally_selections.entrySet().stream().collect(Collectors.toMap(e -> e.getKey(), e -> e.getValue().build())));
    }
  }

  static class Selection extends CiphertextSelection {
    private ElGamal.Ciphertext ciphertext_accumulate; // default = ElGamalCiphertext(ONE_MOD_P, ONE_MOD_P)

    public Selection(String selectionDescriptionId, ElementModQ description_hash, @Nullable ElGamal.Ciphertext ciphertext) {
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
      return this.ciphertext_accumulate;
    }

    CiphertextTally.Selection build() {
      return new CiphertextTally.Selection(
              this.object_id, this.description_hash, this.ciphertext_accumulate);
    }
  }
}
