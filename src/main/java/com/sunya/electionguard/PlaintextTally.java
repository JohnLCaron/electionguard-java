package com.sunya.electionguard;

import com.google.auto.value.AutoValue;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

import javax.annotation.concurrent.Immutable;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static com.sunya.electionguard.Group.ElementModP;

/* The decrypted plaintext representation of the counts of some collection of ballots */
@Immutable
public class PlaintextTally {
  public final String object_id; // matches the CiphertextTally object_id
  public final ImmutableMap<String, PlaintextTallyContest> contests; // Map(CONTEST_ID, PlaintextTallyContest)
  // LOOK what is the point of storing this? We want to store the decrypted ballot in the election record. Why store the same
  //  information in the PlaintextTally? Perhaps this is the object we want to store in the
  public final ImmutableMap<String, Map<String, PlaintextTallyContest>> spoiled_ballots; // Map(BALLOT_ID, Map(CONTEST_ID, PlaintextTallyContest))

  public PlaintextTally(String object_id, Map<String, PlaintextTallyContest> contests,
                                      Map<String, Map<String, PlaintextTallyContest>> spoiled_ballots) {
    this.object_id = Preconditions.checkNotNull(object_id);
    this.contests =  ImmutableMap.copyOf(Preconditions.checkNotNull(contests));
    this.spoiled_ballots =  ImmutableMap.copyOf(Preconditions.checkNotNull(spoiled_ballots));
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    PlaintextTally that = (PlaintextTally) o;
    return object_id.equals(that.object_id) &&
            contests.equals(that.contests) &&
            spoiled_ballots.equals(that.spoiled_ballots);
  }

  @Override
  public int hashCode() {
    return Objects.hash(object_id, contests, spoiled_ballots);
  }

  /**
   * The plaintext representation of the counts of one contest in the election.
   * The object_id is the same as the Election.ContestDescription.object_id or PlaintextBallotContest object_id.
   */
  @AutoValue
  public static abstract class PlaintextTallyContest implements ElectionObjectBaseIF {
    public abstract ImmutableMap<String, PlaintextTallySelection> selections(); // Map(SELECTION_ID, PlaintextTallySelection)

    public static PlaintextTallyContest create(String object_id, Map<String, PlaintextTallySelection> selections) {
      return new AutoValue_PlaintextTally_PlaintextTallyContest(
              Preconditions.checkNotNull(object_id),
              ImmutableMap.copyOf(Preconditions.checkNotNull(selections)));
    }
  } // PlaintextTallyContest

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

    public static PlaintextTallySelection create(String object_id, Integer tally, ElementModP value, ElGamal.Ciphertext message,
                                                 List<DecryptionShare.CiphertextDecryptionSelection> shares) {
      return new AutoValue_PlaintextTally_PlaintextTallySelection(
              Preconditions.checkNotNull(object_id),
              Preconditions.checkNotNull(tally),
              Preconditions.checkNotNull(value),
              Preconditions.checkNotNull(message),
              ImmutableList.copyOf(shares));
    }
  } // PlaintextTallySelection

}
