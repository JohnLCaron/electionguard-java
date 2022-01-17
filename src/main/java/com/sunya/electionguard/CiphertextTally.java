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
public class CiphertextTally extends ElectionObjectBase {
  /** The collection of contests in the election, keyed by contest_id. */
  public final ImmutableMap<String, Contest> contests; // Map(CONTEST_ID, CiphertextTallyContest)

  public CiphertextTally(String object_id, Map<String, Contest> contests) {
    super(object_id);
    this.contests = ImmutableMap.copyOf(contests);
  }

  public String toStringOld() {
    return "CiphertextTally{" +
            "object_id='" + object_id + '\'' +
            "} ";
  }

  @Override
  public String toString() {
    return "CiphertextTally{" +
            "contests=" + contests +
            ", object_id='" + object_id + '\'' +
            '}';
  }

  /**
   * The encrypted selections for a specific contest.
   * The object_id is the Manifest.ContestDescription.object_id.
   */
  @Immutable
  public static class Contest implements OrderedObjectBaseIF {
    public final String object_id;
    public final int sequence_order;

    /** The ContestDescription crypto_hash. */
    public final ElementModQ contestDescriptionHash;

    /** The collection of selections in the contest, keyed by selection.object_id. */
    public final ImmutableMap<String, Selection> selections; // Map(SELECTION_ID, CiphertextTallySelection)

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
      if (!super.equals(o)) return false;
      Contest that = (Contest) o;
      return contestDescriptionHash.equals(that.contestDescriptionHash) &&
              selections.equals(that.selections);
    }

    @Override
    public int hashCode() {
      return Objects.hash(super.hashCode(), contestDescriptionHash, selections);
    }

    @Override
    public String toString() {
      return "Contest{" +
              "\n object_id='" + object_id + '\'' +
              "\n contestDescriptionHash=" + contestDescriptionHash +
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
   * The homomorphic accumulation of all of the CiphertextBallotSelection for a specific selection and contest.
   * The object_id is the Manifest.SelectionDescription.object_id.
   */
  @Immutable
  public static class Selection extends CiphertextSelection {
    public Selection(String selectionDescriptionId, int sequence_order, ElementModQ description_hash, ElGamal.Ciphertext ciphertext) {
      super(selectionDescriptionId, sequence_order, description_hash, ciphertext, false);
    }

    @Override
    public String toString() {
      return "Selection{" +
              "\n object_id       ='" + object_id + '\'' +
              "\n description_hash=" + description_hash +
              "\n ciphertext()    =" + ciphertext() +
              '}';
    }
  }

}
