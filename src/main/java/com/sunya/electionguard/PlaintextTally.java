package com.sunya.electionguard;

import com.google.auto.value.AutoValue;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;
import java.util.Formatter;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static com.sunya.electionguard.Group.ElementModP;

/** The decrypted plaintext representation of the counts of a collection of ballots. */
@Immutable
public class PlaintextTally {
  /** Matches the CiphertextTally object_id. */
  public final String object_id;

  /** The list of contests for this tally, keyed by contest_id. */
  public final ImmutableMap<String, Contest> contests;

  /** The lagrange coefficients w_ij for verification of section 10. */
  public final ImmutableMap<String, Group.ElementModQ> lagrange_coefficients;

  /** The state of the Guardian when decrypting: missing or available. */
  public final ImmutableList<GuardianState> guardianStates;

  public PlaintextTally(String object_id,
                        Map<String, Contest> contests,
                        @Nullable Map<String, Group.ElementModQ> lagrange_coefficients,
                        @Nullable List<GuardianState> guardianState) {
    this.object_id = Preconditions.checkNotNull(object_id);
    this.contests =  ImmutableMap.copyOf(Preconditions.checkNotNull(contests));
    this.lagrange_coefficients = (lagrange_coefficients != null) ? ImmutableMap.copyOf(lagrange_coefficients) : ImmutableMap.of();
    this.guardianStates = (guardianState != null) ? ImmutableList.copyOf(guardianState) : ImmutableList.of();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    PlaintextTally that = (PlaintextTally) o;
    return object_id.equals(that.object_id) &&
            contests.equals(that.contests) &&
            Objects.equals(lagrange_coefficients, that.lagrange_coefficients) &&
            Objects.equals(guardianStates, that.guardianStates);
  }

  @Override
  public int hashCode() {
    return Objects.hash(object_id, contests, lagrange_coefficients, guardianStates);
  }

  @Override
  public String toString() {
    Formatter out = new Formatter();
    out.format("PlaintextTally{%n  object_id='%s'", object_id);
    guardianStates.forEach(g -> out.format("\n    %s", g.toString()));
    return out.toString();
  }

  /**
   * The plaintext representation of the counts of one contest in the election.
   * The object_id is the same as the Election.ContestDescription.object_id or PlaintextBallotContest object_id.
   */
  @AutoValue
  public static abstract class Contest implements ElectionObjectBaseIF {
    /** The collection of selections in the contest, keyed by selection.object_id. */
    public abstract ImmutableMap<String, Selection> selections(); // Map(SELECTION_ID, PlaintextTallySelection)

    public static Contest create(String object_id, Map<String, Selection> selections) {
      return new AutoValue_PlaintextTally_Contest(
              Preconditions.checkNotNull(object_id),
              ImmutableMap.copyOf(Preconditions.checkNotNull(selections)));
    }

    @Override
    public String toString() {
      Formatter out = new Formatter();
      out.format("Contest %s%n", object_id());
      int sum = selections().values().stream().mapToInt(s -> s.tally()).sum();
      out.format("   %-40s = %d%n", "Total votes", sum);
      return out.toString();
    }
  }

  /**
   * The plaintext representation of the counts of one selection of one contest in the election.
   * The object_id is the same as the encrypted selection (Ballot.CiphertextSelection) object_id.
   */
  @AutoValue
  public static abstract class Selection implements ElectionObjectBaseIF {
    /** The actual count. */
    public abstract Integer tally();
    /** g^tally or M in the spec. */
    public abstract ElementModP value();
    /** The encrypted vote count. */
    public abstract ElGamal.Ciphertext message();
    /** The Guardians' shares of the decryption of a selection. `M_i` in the spec. Must be quorum of them. */
    public abstract ImmutableList<DecryptionShare.CiphertextDecryptionSelection> shares();

    public static Selection create(String object_id, Integer tally, ElementModP value, ElGamal.Ciphertext message,
                                   List<DecryptionShare.CiphertextDecryptionSelection> shares) {
      return new AutoValue_PlaintextTally_Selection(
              Preconditions.checkNotNull(object_id),
              Preconditions.checkNotNull(tally),
              Preconditions.checkNotNull(value),
              Preconditions.checkNotNull(message),
              ImmutableList.copyOf(shares));
    }

    @Override
    public String toString() {
      return "Selection{" +
              "\n object_id='" + object_id() + '\'' +
              "\n tally    =" + tally() +
              "\n value    =" + value().toShortString() +
              "\n message  =" + message() +
              // LOOK shares
              '}';
    }
  }

}
