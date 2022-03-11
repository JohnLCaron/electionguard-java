package com.sunya.electionguard;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.flogger.FluentLogger;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * The plaintext representation of a voter's ballot selections for all the contests in an election.
 * The ballotId is a unique Ballot ID created by the external system.
 */
@Immutable
public class PlaintextBallot {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  /** Unique internal identifier used by other elements to reference this element. */
  private final String ballotId;
  /** The object_id of the Manifest.BallotStyle. */
  public final String ballotStyleId;
  /** The list of contests for this ballot. */
  public final ImmutableList<Contest> contests;

  public PlaintextBallot(String ballotId, String ballotStyleId, List<Contest> contests) {
    Preconditions.checkArgument(!Strings.isNullOrEmpty(ballotId));
    this.ballotId = ballotId;
    Preconditions.checkArgument(!Strings.isNullOrEmpty(ballotStyleId));
    this.ballotStyleId = ballotStyleId;
    this.contests = ImmutableList.copyOf(contests);
  }

  public String object_id() {
    return ballotId;
  }

  /**
   * Is the ballot style valid?
   *
   * @param expected_ballot_style_id: Expected ballot style id
   */
  public boolean is_valid(String expected_ballot_style_id) {
    if (!this.ballotStyleId.equals(expected_ballot_style_id)) {
      logger.atWarning().log("invalid ballot_style_id: for: %s expected(%s) actual(%s)",
              this.ballotId, expected_ballot_style_id, this.ballotStyleId);
      return false;
    }
    return true;
  }

  @Override
  public String toString() {
    return "PlaintextBallot{" +
            "object_id='" + ballotId + '\'' +
            ", style_id='" + ballotStyleId + '\'' +
            '}';
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    PlaintextBallot that = (PlaintextBallot) o;
    return ballotId.equals(that.ballotId) &&
            ballotStyleId.equals(that.ballotStyleId) &&
            contests.equals(that.contests);
  }

  @Override
  public int hashCode() {
    return Objects.hash(ballotId, ballotStyleId, contests);
  }

  /**
   * The plaintext representation of a voter's selections for one contest.
   * <p>
   * This can be either a partial or a complete representation of a contest dataset.  Specifically,
   * a partial representation must include at a minimum the "affirmative" selections of a contest.
   * A complete representation of a ballot must include both affirmative and negative selections of
   * the contest, AND the placeholder selections necessary to satisfy the ConstantChaumPedersen proof
   * in the CiphertextBallotContest.
   * <p>
   * Typically partial contests are passed into Electionguard for memory constrained systems,
   * while complete contests are passed into ElectionGuard when running encryption on an existing dataset.
   */
  @Immutable
  public static class Contest {
    /** The ContestDescription.object_id. */
    public final String contestId;
    public final int sequenceOrder;
    /** The Collection of ballot selections. */
    public final ImmutableList<Selection> selections;

    public Contest(String contestId, int sequenceOrder, List<Selection> selections) {
      Preconditions.checkArgument(!Strings.isNullOrEmpty(contestId));
      this.contestId = contestId;
      this.sequenceOrder = sequenceOrder;
      this.selections = ImmutableList.copyOf(selections);
    }

    /**
     * Are all of the selections valid?
     * Note: because this class supports partial representations, undervotes are considered a valid state.
     */
    public boolean is_valid(
            String expected_contest_id,
            int expected_number_selections,
            int expected_number_elected,
            Integer votes_allowed) {

      if (!this.contestId.equals(expected_contest_id)) {
        logger.atWarning().log("invalid contest_id: expected(%s) actual(%s)", expected_contest_id, this.contestId);
        return false;
      }

      if (this.selections.size() > expected_number_selections) {
        logger.atWarning().log("invalid number_selections: expected(%s) actual(%s)", expected_number_selections, this.selections);
        return false;
      }

      int number_elected = 0;
      int votes = 0;

      // Verify the selections are well-formed
      for (Selection selection : this.selections) {
        int selection_count = selection.vote;
        votes += selection_count;
        if (selection_count >= 1) { // LOOK I dont understand this
          number_elected += 1;
        }
      }

      if (number_elected > expected_number_elected) {
        logger.atWarning().log("invalid number_elected: expected(%s) actual(%s)", expected_number_elected, number_elected);
        return false;
      }
      if (votes > votes_allowed) {
        logger.atWarning().log("invalid number of votes: allowed(%s) actual(%s)", votes_allowed, votes);
        return false;
      }
      return true;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      Contest that = (Contest) o;
      return contestId.equals(that.contestId) &&
              selections.equals(that.selections);
    }

    @Override
    public int hashCode() {
      return Objects.hash(contestId, selections);
    }

    @Override
    public String toString() {
      return "PlaintextBallotContest{" +
              "contest_id='" + contestId + '\'' +
              '}';
    }
  }

  /**
   * The plaintext representation of one selection for a particular contest.
   * <p>
   * This can also be designated as `is_placeholder_selection` which has no
   * context to the data specification but is useful for running validity checks internally
   * <p>
   * An `extended_data` field exists to support any arbitrary data to be associated
   * with the selection.  In practice, this field is the cleartext representation
   * of a write-in candidate value.
   * LOOK In the current implementation these write-in are discarded when encrypting.
   */
  @Immutable
  public static class Selection {
    /** Matches the SelectionDescription.selectionId. */
    public final String selectionId;
    public final int sequenceOrder;
    /** The vote count. */
    public final int vote;
    /** Is this a placeholder? */
    public final boolean isPlaceholderSelection; // default false
    /** Optional write-in candidate. */
    public final Optional<ExtendedData> extendedData; // default None

    public Selection(String selectionId, int sequenceOrder, int vote, boolean isPlaceholderSelection,
                     @Nullable ExtendedData extendedData) {
      Preconditions.checkArgument(!Strings.isNullOrEmpty(selectionId));
      this.selectionId = selectionId;
      this.sequenceOrder = sequenceOrder;
      this.vote = vote;
      this.isPlaceholderSelection = isPlaceholderSelection;
      this.extendedData = Optional.ofNullable(extendedData);
    }

    public boolean is_valid(String expected_selection_id) {
      if (!expected_selection_id.equals(selectionId)) {
        logger.atWarning().log("invalid selection_id: expected %s actual %s",
                expected_selection_id, this.selectionId);
        return false;
      }
      if (vote < 0 || vote > 1) {
        logger.atWarning().log("Vote must be a 0 or 1: %s", this);
        return false;
      }
      return true;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      Selection selection = (Selection) o;
      return // sequence_order == selection.sequence_order &&
              vote == selection.vote &&
              isPlaceholderSelection == selection.isPlaceholderSelection &&
              selectionId.equals(selection.selectionId) &&
              extendedData.equals(selection.extendedData);
    }

    @Override
    public int hashCode() {
      return Objects.hash(selectionId, sequenceOrder, vote, isPlaceholderSelection, extendedData);
    }


    @Override
    public String toString() {
      return "Selection{" +
              "selection_id='" + selectionId + '\'' +
              ", sequence_order=" + sequenceOrder +
              ", vote=" + vote +
              ", is_placeholder_selection=" + isPlaceholderSelection +
              ", extended_data=" + extendedData +
              '}';
    }
  }

  /** Used to indicate a write-in candidate. */
  @Immutable
  public static class ExtendedData {
    public final String value;
    public final int length;

    public ExtendedData(String value, int length) {
      this.value = value;
      this.length = length;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      ExtendedData that = (ExtendedData) o;
      return length == that.length &&
              value.equals(that.value);
    }

    @Override
    public int hashCode() {
      return Objects.hash(value, length);
    }

    @Override
    public String toString() {
      return "ExtendedData{" +
              "value='" + value + '\'' +
              ", length=" + length +
              '}';
    }
  }

  /**
   * Create a PlaintextBallot from a CiphertextBallot and its decrypted tally.
   * Need CiphertextBallot for the style_id.
   * LOOK not needed I think
   */
  static PlaintextBallot from(CiphertextBallot cballot, PlaintextTally tally) {
    List<Contest> contests = new ArrayList<>();
    for (CiphertextBallot.Contest ccontest : cballot.contests) {
      PlaintextTally.Contest tcontest = tally.contests.get(ccontest.contestId);
      Preconditions.checkNotNull(tcontest);
      List<Selection> selections = new ArrayList<>();
      for (CiphertextBallot.Selection cselection : ccontest.selections) {
        if (!cselection.is_placeholder_selection) {
          PlaintextTally.Selection tselection = tcontest.selections().get(cselection.object_id());
          selections.add(new Selection(cselection.object_id(), -1, tselection.tally(), false, null));
        }
      }
      contests.add(new Contest(ccontest.object_id(), ccontest.sequence_order(), selections));
    }
    return new PlaintextBallot(cballot.object_id(), cballot.ballotStyleId, contests);
  }

  /* experimental: get ballot from tally alone.
  static PlaintextBallot from(PlaintextTally tally) {
    List<Contest> contests = new ArrayList<>();
    for (PlaintextTally.Contest tcontest : tally.contests.values()) {
      List<Selection> selections = new ArrayList<>();
      for (PlaintextTally.Selection tselection : tcontest.selections().values()) {
        selections.add(new Selection(tselection.object_id(), tselection.tally(), false, null));
      }
      contests.add(new Contest(tcontest.object_id(), selections));
    }
    return new PlaintextBallot(tally.object_id, cballot.style_id, contests);
  } */

}
