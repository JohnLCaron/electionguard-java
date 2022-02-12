package com.sunya.electionguard;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableMap;

import javax.annotation.concurrent.Immutable;
import java.util.Formatter;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static com.sunya.electionguard.Group.ElementModP;

/**
 * The decrypted plaintext representation of the counts of a collection of ballots.
 */
@Immutable
public class PlaintextTally {
  /**
   * Matches the CiphertextTally object_id.
   */
  public final String object_id;

  /**
   * The list of contests for this tally, keyed by contest_id.
   */
  public final ImmutableMap<String, Contest> contests;

  public PlaintextTally(String object_id,
                        Map<String, Contest> contests) {
    this.object_id = Preconditions.checkNotNull(object_id);
    this.contests = ImmutableMap.copyOf(Preconditions.checkNotNull(contests));
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    PlaintextTally that = (PlaintextTally) o;
    return object_id.equals(that.object_id) &&
            contests.equals(that.contests);
  }

  @Override
  public int hashCode() {
    return Objects.hash(object_id, contests);
  }

  @Override
  public String toString() {
    return "PlaintextTally{" +
            "object_id='" + object_id + '\'' +
            ", contests=" + contests +
            '}';
  }

  /**
   * The plaintext representation of the counts of one contest in the election.
   * The object_id is the same as the Manifest.ContestDescription.object_id or PlaintextBallotContest object_id.
   *
   * @param selections The collection of selections in the contest, keyed by selection.object_id.
   */
  public record Contest(
          String object_id,
          Map<String, Selection> selections) {

    public Contest {
      Preconditions.checkArgument(!Strings.isNullOrEmpty(object_id));
      Preconditions.checkNotNull(selections);
      selections = Map.copyOf(selections);
    }
  }

  /**
   * The plaintext representation of the counts of one selection of one contest in the election.
   * The object_id is the same as the encrypted selection (Ballot.CiphertextSelection) object_id.
   *
   * @param tally   the actual count.
   * @param value   g^tally or M in the spec.
   * @param message The encrypted vote count.
   * @param shares  The Guardians' shares of the decryption of a selection. `M_i` in the spec. Must be nguardians of them.
   */
  public record Selection(
          String object_id,
          Integer tally,
          ElementModP value,
          ElGamal.Ciphertext message,
          List<DecryptionShare.CiphertextDecryptionSelection> shares) {

    public Selection {
      Preconditions.checkArgument(!Strings.isNullOrEmpty(object_id));
      Preconditions.checkNotNull(tally);
      Preconditions.checkNotNull(value);
      Preconditions.checkNotNull(message);
      shares = List.copyOf(shares);
    }

    @Override
    public String toString() {
      Formatter f = new Formatter();
      f.format("Selection{%n object_id= '%s'%n tally    = %d%n value    = %s%n message  = %s%n shares=%n",
              object_id(), tally(), value().toShortString(), message());
      for (DecryptionShare.CiphertextDecryptionSelection sel : shares()) {
        f.format("   %s share = %s", sel.guardian_id(), sel.share().toShortString());
        if (sel.proof().isPresent()) {
          f.format(" %s", sel.proof().get().name);
        }
        if (sel.recovered_parts().isPresent()) {
          f.format(" recovered_parts=%n");
          f.format("     %30s %12s %12s %20s %s%n", "object_id", "guardian", "missing", "proof", "share");
          Map<String, DecryptionShare.CiphertextCompensatedDecryptionSelection> m = sel.recovered_parts().get();
          for (DecryptionShare.CiphertextCompensatedDecryptionSelection r : m.values()) {
            f.format("     %30s %12s %12s %20s %s%n", r.object_id(), r.guardian_id(), r.missing_guardian_id(),
                    r.proof().name, r.share().toShortString());
          }
        } else {
          f.format("%n");
        }
      }
      f.format("%n}");
      return f.toString();
    }
  }

}
