package com.sunya.electionguard;

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

  @Override
  public String toString() {
    return "CiphertextTally{" +
            "object_id='" + object_id + '\'' +
            "} ";
  }

  /**
   * The encrypted selections for a specific contest.
   * The object_id is the Manifest.ContestDescription.object_id.
   */
  @Immutable
  public static class Contest extends ElectionObjectBase {
    /** The ContestDescription crypto_hash. */
    public final ElementModQ contestDescriptionHash;

    /** The collection of selections in the contest, keyed by selection.object_id. */
    public final ImmutableMap<String, Selection> selections; // Map(SELECTION_ID, CiphertextTallySelection)

    public Contest(String object_id, ElementModQ contestDescriptionHash, Map<String, Selection> selections) {
      super(object_id);
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
  }

  /**
   * The homomorphic accumulation of all of the CiphertextBallotSelection for a specific selection and contest.
   * The object_id is the Manifest.SelectionDescription.object_id.
   */
  @Immutable
  public static class Selection extends CiphertextSelection {
    public Selection(String selectionDescriptionId, ElementModQ description_hash, ElGamal.Ciphertext ciphertext) {
      super(selectionDescriptionId, description_hash, ciphertext, false);
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
