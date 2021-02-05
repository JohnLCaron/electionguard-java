package com.sunya.electionguard;

import com.google.auto.value.AutoValue;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

import javax.annotation.concurrent.Immutable;
import java.util.Formatter;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static com.sunya.electionguard.Group.ElementModP;

/* The decrypted plaintext representation of the counts of some collection of ballots */
@Immutable
public class PlaintextTally {
  public final String object_id; // matches the CiphertextTally object_id

  // Map(CONTEST_ID, PlaintextTallyContest)
  public final ImmutableMap<String, PlaintextTallyContest> contests;

  // Map(BALLOT_ID, Map(CONTEST_ID, PlaintextTallyContest))
  public final ImmutableMap<String, ImmutableMap<String, PlaintextTallyContest>> spoiled_ballots;

  /** The lagrange coefficients w_ij for verification of section 10. */
  public final ImmutableMap<String, Group.ElementModQ> lagrange_coefficients;

  /** The state of the Guardian when decrypting: missing or available. */
  public final ImmutableList<GuardianState> guardianStates;

  public PlaintextTally(String object_id,
                        Map<String, PlaintextTallyContest> contests,
                        Map<String, Map<String, PlaintextTallyContest>> spoiled_ballots,
                        Map<String, Group.ElementModQ> lagrange_coefficients,
                        List<GuardianState> guardianState) {
    this.object_id = Preconditions.checkNotNull(object_id);
    this.contests =  ImmutableMap.copyOf(Preconditions.checkNotNull(contests));

    ImmutableMap.Builder<String, ImmutableMap<String, PlaintextTallyContest>> builder = ImmutableMap.builder();
    for (Map.Entry<String, Map<String, PlaintextTallyContest>> entry : spoiled_ballots.entrySet()) {
      ImmutableMap.Builder<String, PlaintextTallyContest> builder2 = ImmutableMap.builder();
      for (Map.Entry<String, PlaintextTallyContest> entry2 : entry.getValue().entrySet()) {
        builder2.put(entry2.getKey(), entry2.getValue());
      }
      builder.put(entry.getKey(), builder2.build());
    }
    this.spoiled_ballots = builder.build();

    this.lagrange_coefficients = ImmutableMap.copyOf(lagrange_coefficients);
    this.guardianStates = ImmutableList.copyOf(guardianState);
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

  @Override
  public String toString() {
    Formatter out = new Formatter();
    contests.values().forEach(contest -> out.format("%s%n", contest.toString()));
    return out.toString();
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

    @Override
    public String toString() {
      Formatter out = new Formatter();
      out.format("Contest %s%n", object_id());
      int sum = selections().values().stream().mapToInt(s -> s.tally()).sum();
      selections().values().forEach(selection -> out.format("%s%n", selection.toString()));
      out.format("   %-40s = %d%n", "Total votes", sum);
      return out.toString();
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

    @Override
    public String toString() {
      return String.format("   %-40s = %d", object_id(), tally());
    }
  } // PlaintextTallySelection

  /** The state of the Guardian when decrypting: missing or available. */
  @AutoValue
  public static abstract class GuardianState {
    public abstract String guardian_id();
    public abstract int sequence();
    public abstract boolean is_missing();

    public static GuardianState create(String guardianId, int sequence, boolean isMissing) {
      return new AutoValue_PlaintextTally_GuardianState(
              Preconditions.checkNotNull(guardianId),
              sequence,
              isMissing);
    }
  }

}
