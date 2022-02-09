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
public class CiphertextTally implements ElectionObjectBaseIF {
  /** Unique internal identifier used by other elements to reference this element. */
  private final String object_id;
  /** The collection of contests in the election, keyed by contest_id. */
  public final ImmutableMap<String, Contest> contests; // Map(CONTEST_ID, CiphertextTally.Contest)

  public CiphertextTally(String object_id, Map<String, Contest> contests) {
    Preconditions.checkArgument(!Strings.isNullOrEmpty(object_id));
    this.object_id = object_id;
    this.contests = ImmutableMap.copyOf(contests);
  }

  @Override
  public String object_id() {
    return object_id;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    CiphertextTally that = (CiphertextTally) o;
    return object_id.equals(that.object_id) && contests.equals(that.contests);
  }

  @Override
  public int hashCode() {
    return Objects.hash(object_id, contests);
  }

  @Override
  public String toString() {
    return "CiphertextTally{" +
            "object_id='" + object_id + '\'' +
            ", contests=" + contests +
            '}';
  }

  /**
   * The encrypted selections for a specific contest.
   * The object_id is the Manifest.ContestDescription.object_id.
   */
  @Immutable
  public static class Contest implements OrderedObjectBaseIF {
    private final String object_id;
    private final int sequence_order;

    /** The ContestDescription crypto_hash. */
    public final ElementModQ contestDescriptionHash;

    /** The collection of selections in the contest, keyed by selection.object_id. */
    public final ImmutableMap<String, Selection> selections; // Map(SELECTION_ID, CiphertextTally.Selection)

    public Contest(String object_id, int sequence_order, ElementModQ contestDescriptionHash, Map<String, Selection> selections) {
      Preconditions.checkArgument(!Strings.isNullOrEmpty(object_id));
      this.object_id = object_id;
      this.sequence_order = sequence_order;
      this.contestDescriptionHash = contestDescriptionHash;
      this.selections = ImmutableMap.copyOf(selections);
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      Contest contest = (Contest) o;
      return sequence_order == contest.sequence_order &&
              object_id.equals(contest.object_id) &&
              contestDescriptionHash.equals(contest.contestDescriptionHash) &&
              selections.equals(contest.selections);
    }

    @Override
    public int hashCode() {
      return Objects.hash(object_id, sequence_order, contestDescriptionHash, selections);
    }

    @Override
    public String toString() {
      return "Contest{" +
              "object_id='" + object_id + '\'' +
              ", sequence_order=" + sequence_order +
              ", contestDescriptionHash=" + contestDescriptionHash +
              ", selections=" + selections +
              '}';
    }

    @Override
    public String object_id() {
      return object_id;
    }

    @Override
    public int sequence_order() {
      return sequence_order;
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
