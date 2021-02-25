package com.sunya.electionguard;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.flogger.FluentLogger;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * The plaintext representation of a voter's ballot selections for all the contests in an election.
 * The object_id is a unique Ballot ID created by the external system.
 * This is used both as input, and for the roundtrip: input -&gt; encrypt -&gt; decrypt -&gt; output.
 */
@Immutable
public class PlaintextBallot extends ElectionObjectBase {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();
  /** The object_id of the Election.BallotStyle. */
  public final String ballot_style;
  /** The list of contests for this ballot. */
  public final ImmutableList<Contest> contests;

  public PlaintextBallot(String object_id, String ballot_style, List<Contest> contests) {
    super(object_id);
    Preconditions.checkArgument(!Strings.isNullOrEmpty(ballot_style));
    this.ballot_style = ballot_style;
    this.contests = ImmutableList.copyOf(contests);
  }

  /**
   * Is the ballot style valid?
   *
   * @param expected_ballot_style_id: Expected ballot style id
   */
  public boolean is_valid(String expected_ballot_style_id) {
    if (!this.ballot_style.equals(expected_ballot_style_id)) {
      logger.atWarning().log("invalid ballot_style: for: %s expected(%s) actual(%s)",
              this.object_id, expected_ballot_style_id, this.ballot_style);
      return false;
    }
    return true;
  }

  @Override
  public String toString() {
    return "PlaintextBallot{" +
            "object_id='" + object_id + '\'' +
            ", ballot_style='" + ballot_style + '\'' +
            '}';
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    if (!super.equals(o)) return false;
    PlaintextBallot that = (PlaintextBallot) o;
    return ballot_style.equals(that.ballot_style) &&
            contests.equals(that.contests);
  }

  @Override
  public int hashCode() {
    return Objects.hash(super.hashCode(), ballot_style, contests);
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
    /** The Collection of ballot selections. */
    public final ImmutableList<Selection> ballot_selections;

    public Contest(String contest_id, List<Selection> ballot_selections) {
      Preconditions.checkArgument(!Strings.isNullOrEmpty(contest_id));
      this.contest_id = contest_id;
      this.ballot_selections = ImmutableList.copyOf(ballot_selections);
    }

    /**
     * Are all of the selections valid?
     * Note: because this class supports partial representations, undervotes are considered a valid state.
     */
    boolean is_valid(
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
    /** The vote count. */
    public final int vote;
    /** Is this a placeholder? */
    public final boolean is_placeholder_selection; // default false
    /** Optional write-in candidate. */
    public final Optional<ExtendedData> extended_data; // default None

    public Selection(String selection_id, int vote, boolean is_placeholder_selection,
                     @Nullable ExtendedData extended_data) {
      Preconditions.checkArgument(!Strings.isNullOrEmpty(selection_id));
      this.selection_id = selection_id;
      this.vote = vote;
      this.is_placeholder_selection = is_placeholder_selection;
      this.extended_data = Optional.ofNullable(extended_data);
    }

    boolean is_valid(String expected_selection_id) {
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
      Selection that = (Selection) o;
      return vote == that.vote &&
              is_placeholder_selection == that.is_placeholder_selection &&
              selection_id.equals(that.selection_id) &&
              extended_data.equals(that.extended_data);
    }

    @Override
    public int hashCode() {
      return Objects.hash(selection_id, vote, is_placeholder_selection, extended_data);
    }

    @Override
    public String toString() {
      return "Selection{" +
              "\n selection_id='" + selection_id + '\'' +
              "\n vote=" + vote +
              "\n is_placeholder_selection=" + is_placeholder_selection +
              "\n extended_data=" + extended_data +
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
}
