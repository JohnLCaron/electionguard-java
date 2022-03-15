package com.sunya.electionguard;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableMap;

import javax.annotation.concurrent.Immutable;
import java.util.Map;
import java.util.Objects;
import static com.sunya.electionguard.Group.ElementModQ;

/** The encrypted representation of the summed votes for a collection of ballots */
@Immutable
public class CiphertextTally {
  /** Unique internal identifier used by other elements to reference this element. */
  private final String tallyId;
  /** The collection of contests in the election, keyed by contest_id. */
  public final ImmutableMap<String, Contest> contests; // Map(CONTEST_ID, CiphertextTally.Contest)

  public CiphertextTally(String tallyId, Map<String, Contest> contests) {
    Preconditions.checkArgument(!Strings.isNullOrEmpty(tallyId));
    this.tallyId = tallyId;
    this.contests = ImmutableMap.copyOf(contests);
  }

  public String object_id() {
    return tallyId;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    CiphertextTally that = (CiphertextTally) o;
    return tallyId.equals(that.tallyId) && contests.equals(that.contests);
  }

  @Override
  public int hashCode() {
    return Objects.hash(tallyId, contests);
  }

  @Override
  public String toString() {
    return "CiphertextTally{" +
            "tallyId='" + tallyId + '\'' +
            ", contests=" + contests.size() +
            '}';
  }

  /**
   * The encrypted selections for a specific contest.
   * The object_id is the Manifest.ContestDescription.object_id.
   */
  @Immutable
  public static class Contest {
    private final String contestId;
    private final int sequenceOrder;

    /** The ContestDescription crypto_hash. */
    public final ElementModQ contestDescriptionHash;

    /** The collection of selections in the contest, keyed by selection.object_id. */
    public final ImmutableMap<String, Selection> selections; // Map(SELECTION_ID, CiphertextTally.Selection)

    public Contest(String contestId, int sequence_order, ElementModQ contestDescriptionHash, Map<String, Selection> selections) {
      Preconditions.checkArgument(!Strings.isNullOrEmpty(contestId));
      this.contestId = contestId;
      this.sequenceOrder = sequence_order;
      this.contestDescriptionHash = contestDescriptionHash;
      this.selections = ImmutableMap.copyOf(selections);
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      Contest contest = (Contest) o;
      return sequenceOrder == contest.sequenceOrder &&
              contestId.equals(contest.contestId) &&
              contestDescriptionHash.equals(contest.contestDescriptionHash) &&
              selections.equals(contest.selections);
    }

    @Override
    public int hashCode() {
      return Objects.hash(contestId, sequenceOrder, contestDescriptionHash, selections);
    }

    @Override
    public String toString() {
      return "Contest{" +
              "contestId='" + contestId + '\'' +
              ", sequenceOrder=" + sequenceOrder +
              ", contestDescriptionHash=" + contestDescriptionHash +
              ", selections=" + selections.size() +
              '}';
    }

    public String object_id() {
      return contestId;
    }

    public int sequence_order() {
      return sequenceOrder;
    }
  }

  /**
   * The homomorphic accumulation of all of the CiphertextBallot.Selection for a specific selection and contest.
   * The object_id is the Manifest.SelectionDescription.object_id.
   */
  @Immutable
  public static class Selection extends CiphertextSelection {
    public Selection(String selectionDescriptionId, int sequence_order, ElementModQ description_hash, ElGamal.Ciphertext ciphertext) {
      super(selectionDescriptionId, sequence_order, description_hash, ciphertext, false);
    }
  }

}
