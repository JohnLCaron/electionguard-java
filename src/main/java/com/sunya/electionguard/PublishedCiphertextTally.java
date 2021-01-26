package com.sunya.electionguard;

import com.google.common.collect.ImmutableMap;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;
import java.util.Map;
import java.util.Objects;
import static com.sunya.electionguard.Group.ElementModQ;

/* The encrypted representation of the counts of some collection of ballots */
@Immutable
public class PublishedCiphertextTally extends ElectionObjectBase {
    /** A collection of each contest and selection in an election. Retains an encrypted representation of a tally for each selection. */
    public final ImmutableMap<String, CiphertextTallyContest> contests; // Map(CONTEST_ID, CiphertextTallyContest)

    public PublishedCiphertextTally(String object_id, Map<String, CiphertextTallyContest> contests) {
      super(object_id);
      this.contests = ImmutableMap.copyOf(contests);
    }

  /**
   * A CiphertextTallyContest groups the CiphertextTallySelection's for a specific Election.ContestDescription.
   * The object_id is the Election.ContestDescription.object_id.
   */
  @Immutable
  public static class CiphertextTallyContest extends ElectionObjectBase {
    /** The ContestDescription hash. */
    public final ElementModQ description_hash;

    /** A collection of CiphertextTallySelection mapped by SelectionDescription.object_id. */
    public final ImmutableMap<String, CiphertextTallySelection> tally_selections; // Map(SELECTION_ID, CiphertextTallySelection)

    public CiphertextTallyContest(String object_id, ElementModQ description_hash, Map<String, CiphertextTallySelection> tally_selections) {
      super(object_id);
      this.description_hash = description_hash;
      this.tally_selections = ImmutableMap.copyOf(tally_selections);
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
   * A CiphertextTallySelection is a homomorphic accumulation of all of the
   * CiphertextBallotSelection instances for a specific selection and contest in an election.
   * The object_id is the Election.SelectionDescription.object_id.
   */
  @Immutable
  public static class CiphertextTallySelection extends Ballot.CiphertextSelection {
    public CiphertextTallySelection(String selectionDescriptionId, ElementModQ description_hash, @Nullable ElGamal.Ciphertext ciphertext) {
      super(selectionDescriptionId, description_hash, ciphertext);
    }
  }


}
