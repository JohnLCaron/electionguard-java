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
 * The object_id is a unique Ballot ID created by the external system.
 * This is used both as input, and for the roundtrip: input -&gt; encrypt -&gt; decrypt -&gt; output.
 */
@Immutable
public class PlaintextBallot implements ElectionObjectBaseIF {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  /** Unique internal identifier used by other elements to reference this element. */
  private final String object_id;
  /** The object_id of the Manifest.BallotStyle. */
  public final String style_id;
  /** The list of contests for this ballot. */
  public final ImmutableList<Contest> contests;

  public PlaintextBallot(String object_id, String style_id, List<Contest> contests) {
    Preconditions.checkArgument(!Strings.isNullOrEmpty(object_id));
    this.object_id = object_id;
    Preconditions.checkArgument(!Strings.isNullOrEmpty(style_id));
    this.style_id = style_id;
    this.contests = ImmutableList.copyOf(contests);
  }

  @Override
  public String object_id() {
    return object_id;
  }

  /**
   * Is the ballot style valid?
   *
   * @param expected_ballot_style_id: Expected ballot style id
   */
  public boolean is_valid(String expected_ballot_style_id) {
    if (!this.style_id.equals(expected_ballot_style_id)) {
      logger.atWarning().log("invalid ballot_style_id: for: %s expected(%s) actual(%s)",
              this.object_id, expected_ballot_style_id, this.style_id);
      return false;
    }
    return true;
  }

  @Override
  public String toString() {
    return "PlaintextBallot{" +
            "object_id='" + object_id + '\'' +
            ", style_id='" + style_id + '\'' +
            '}';
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    PlaintextBallot that = (PlaintextBallot) o;
    return object_id.equals(that.object_id) &&
            style_id.equals(that.style_id) &&
            contests.equals(that.contests);
  }

  @Override
  public int hashCode() {
    return Objects.hash(object_id, style_id, contests);
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
    public final String contest_id;
    public final int sequence_order;
    /** The Collection of ballot selections. */
    public final ImmutableList<Selection> ballot_selections;

    public Contest(String contest_id, int sequence_order, List<Selection> ballot_selections) {
      Preconditions.checkArgument(!Strings.isNullOrEmpty(contest_id));
      this.contest_id = contest_id;
      this.sequence_order = sequence_order;
      this.ballot_selections = ImmutableList.copyOf(ballot_selections);
    }

    /**
     * Are all of the selections valid?
     * Note: because this class supports partial representations, undervotes are considered a valid state.
     */
    public boolean is_valid(
            String expected_contest_id,
            int expected_number_selections,
            int expected_number_elected,
            Optional<Integer> votes_allowed) {

      if (!this.contest_id.equals(expected_contest_id)) {
        logger.atWarning().log("invalid contest_id: expected(%s) actual(%s)", expected_contest_id, this.contest_id);
        return false;
      }

      if (this.ballot_selections.size() > expected_number_selections) {
        logger.atWarning().log("invalid number_selections: expected(%s) actual(%s)", expected_number_selections, this.ballot_selections);
        return false;
      }

      int number_elected = 0;
      int votes = 0;

      // Verify the selections are well-formed
      for (Selection selection : this.ballot_selections) {
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
      if (votes_allowed.isPresent() && votes > votes_allowed.get()) {
        logger.atWarning().log("invalid number of votes: allowed(%s) actual(%s)", votes_allowed.get(), votes);
        return false;
      }
      return true;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      Contest that = (Contest) o;
      return contest_id.equals(that.contest_id) &&
              ballot_selections.equals(that.ballot_selections);
    }

    @Override
    public int hashCode() {
      return Objects.hash(contest_id, ballot_selections);
    }

    @Override
    public String toString() {
      return "PlaintextBallotContest{" +
              "contest_id='" + contest_id + '\'' +
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
    /** Matches the SelectionDescription.object_id. */
    public final String selection_id;
    public final int sequence_order;
    /** The vote count. */
    public final int vote;
    /** Is this a placeholder? */
    public final boolean is_placeholder_selection; // default false
    /** Optional write-in candidate. */
    public final Optional<ExtendedData> extended_data; // default None

    public Selection(String selection_id, int sequence_order, int vote, boolean is_placeholder_selection,
                     @Nullable ExtendedData extended_data) {
      Preconditions.checkArgument(!Strings.isNullOrEmpty(selection_id));
      this.selection_id = selection_id;
      this.sequence_order = sequence_order;
      this.vote = vote;
      this.is_placeholder_selection = is_placeholder_selection;
      this.extended_data = Optional.ofNullable(extended_data);
    }

    public boolean is_valid(String expected_selection_id) {
      if (!expected_selection_id.equals(selection_id)) {
        logger.atWarning().log("invalid selection_id: expected %s actual %s",
                expected_selection_id, this.selection_id);
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
      return sequence_order == selection.sequence_order &&
              vote == selection.vote &&
              is_placeholder_selection == selection.is_placeholder_selection &&
              selection_id.equals(selection.selection_id) &&
              extended_data.equals(selection.extended_data);
    }

    @Override
    public int hashCode() {
      return Objects.hash(selection_id, sequence_order, vote, is_placeholder_selection, extended_data);
    }


    @Override
    public String toString() {
      return "Selection{" +
              "selection_id='" + selection_id + '\'' +
              ", sequence_order=" + sequence_order +
              ", vote=" + vote +
              ", is_placeholder_selection=" + is_placeholder_selection +
              ", extended_data=" + extended_data +
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
      PlaintextTally.Contest tcontest = tally.contests.get(ccontest.object_id);
      List<Selection> selections = new ArrayList<>();
      for (CiphertextBallot.Selection cselection : ccontest.ballot_selections) {
        if (!cselection.is_placeholder_selection) {
          PlaintextTally.Selection tselection = tcontest.selections().get(cselection.object_id());
          selections.add(new Selection(cselection.object_id(), -1, tselection.tally(), false, null));
        }
      }
      contests.add(new Contest(ccontest.object_id(), ccontest.sequence_order(), selections));
    }
    return new PlaintextBallot(cballot.object_id(), cballot.style_id, contests);
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
