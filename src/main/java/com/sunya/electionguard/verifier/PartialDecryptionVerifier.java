package com.sunya.electionguard.verifier;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;
import com.google.common.flogger.FluentLogger;
import com.sunya.electionguard.Group;
import com.sunya.electionguard.AvailableGuardian;
import com.sunya.electionguard.PlaintextTally;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.sunya.electionguard.DecryptionShare.CiphertextCompensatedDecryptionSelection;
import static com.sunya.electionguard.DecryptionShare.CiphertextDecryptionSelection;
import static com.sunya.electionguard.Group.ElementModP;
import static com.sunya.electionguard.Group.ElementModQ;

/**
 * This verifies specification section "10 Correctness of Construction of Replacement Partial Decryptions".
 * @see <a href="https://www.electionguard.vote/spec/0.95.0/9_Verifier_construction/#correctness-of-substitute-data-for-missing-guardians">Decryption with missing Guardians validation</a>
 */
public class PartialDecryptionVerifier {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  final ElectionRecord electionRecord;
  final PlaintextTally tally;

  // Map(AVAILABLE_GUARDIAN_ID, ElementModQ)
  final Map<String, Group.ElementModQ> lagrange_coefficients;

  PartialDecryptionVerifier(ElectionRecord electionRecord, PlaintextTally decryptedTally) {
    this.electionRecord = electionRecord;
    this.tally = Preconditions.checkNotNull(decryptedTally);
    this.lagrange_coefficients = electionRecord.availableGuardians.stream().collect(Collectors.toMap(
            g -> g.guardian_id, g -> g.lagrangeCoordinate));
  }

  /** Verify 10.A for available guardians, if there are missing guardians. */
  boolean verify_replacement_partial_decryptions() {
    if (this.lagrange_coefficients.size() == 0) {
      System.out.printf(" ***Replacement Partial Decryptions failure : lagrange_coefficients not found in election record. %n");
      return false;
    }
    // Verify 10.A for available guardians, if there are missing guardians.
    boolean error = !this.verify_lagrange_coefficients();

    // Verify 10.B for available guardians, if there are missing guardians.
    error |= !this.make_all_contest_verification(this.tally.contests);

    if (error) {
      System.out.printf(" ***Replacement Partial Decryptions failure. %n");
    } else {
      System.out.printf(" Replacement Partial Decryptions success. %n");
    }
    return !error;
  }

  /** Verify 10.A for available guardians lagrange coefficients, if there are missing guardians. */
  boolean verify_lagrange_coefficients() {
    boolean error = false;
    List<AvailableGuardian> guardians = electionRecord.availableGuardians;

    for (AvailableGuardian guardian : guardians) {
      List<Integer> seq_others = new ArrayList<>();
      for (AvailableGuardian other : guardians) {
        if (!other.guardian_id.equals(guardian.guardian_id)) {
          seq_others.add(other.sequence);
        }
      }
      error |= !this.verify_lagrange_coefficient(guardian.sequence, seq_others, guardian.lagrangeCoordinate);
    }

    if (error) {
      System.out.printf(" *** 10.A Lagrange coefficients failure. %n");
    }
    return !error;
  }

  // 10.A. An election verifier should confirm that for each trustee T_l serving to help compute a missing
  // share of a tally, that its Lagrange coefficient w_l is correctly computed by confirming the equation
  //     (∏ j∈(U−{l}) j) mod q = (w_l ⋅ (∏ j∈(U−{l}) (j − l) )) mod q
  boolean verify_lagrange_coefficient(int coordinate, List<Integer> degrees, ElementModQ lagrange) {
    int product = degrees.stream().reduce(1, (a, b)  -> a * b);
    ElementModQ numerator = Group.int_to_q_unchecked(BigInteger.valueOf(product).mod(Group.getPrimes().small_prime));
    List<Integer> diff = degrees.stream().map(degree -> degree - coordinate).collect(Collectors.toList());
    int productDiff = diff.stream().reduce(1, (a, b)  -> a * b);
    ElementModQ denominator = Group.int_to_q_unchecked(BigInteger.valueOf(productDiff).mod(Group.getPrimes().small_prime));
    return numerator.equals(Group.mult_q(lagrange, denominator));
  }

  private boolean make_all_contest_verification(Map<String, PlaintextTally.Contest> contests) {
    boolean error = false;
    for (PlaintextTally.Contest contest : contests.values()) {
      DecryptionContestVerifier tcv = new DecryptionContestVerifier(contest);
      if (!tcv.verify_a_contest()) {
        error = true;
      }
    }
    return !error;
  }

  private class DecryptionContestVerifier {
    PlaintextTally.Contest contest;

    DecryptionContestVerifier(PlaintextTally.Contest contest) {
      this.contest = contest;
    }

    boolean verify_a_contest() {
      boolean error = false;
      for (PlaintextTally.Selection selection : this.contest.selections().values()) {
        String id = contest.object_id() + "-" + selection.object_id();
        DecryptionSelectionVerifier tsv = new DecryptionSelectionVerifier(id, selection);
        if (!tsv.verify_a_selection()) {
          System.out.printf("  Selection %s decryption failure.%n", id);
          error = true;
        }
      }
      return !error;
    }
  }

  private class DecryptionSelectionVerifier {
    final String id;
    final PlaintextTally.Selection selection;
    final String selection_id;
    final ElementModP pad;
    final ElementModP data;

    DecryptionSelectionVerifier(String id, PlaintextTally.Selection selection) {
      this.id = id; // contest/selection
      this.selection = selection;
      this.selection_id = selection.object_id();
      this.pad = selection.message().pad;
      this.data = selection.message().data;
    }

    /** Verify a selection at a time. Combine all the checks separated by guardian shares. */
    boolean verify_a_selection() {
      List<CiphertextDecryptionSelection> shares = this.selection.shares();
      ShareVerifier sv = new ShareVerifier(this.id, shares, this.pad, this.data);
      boolean res = sv.verify_all_shares();
      if (!res) {
        System.out.printf(" '%s' tally verification error.%n", this.selection_id);
      }
      return res;
    }
  }

  private class ShareVerifier {
    final String id; // contest/selection
    List<CiphertextDecryptionSelection> shares;
    ElementModP selection_pad;
    ElementModP selection_data;
    ImmutableMap<String, ElementModP> public_keys;

    ShareVerifier(String id, List<CiphertextDecryptionSelection> shares, ElementModP selection_pad, ElementModP selection_data) {
      this.id = id;
      this.shares = shares;
      this.selection_pad = selection_pad;
      this.selection_data = selection_data;
      this.public_keys = electionRecord.public_keys_of_all_guardians();
    }

    /** Verify all shares of a tally decryption, when there are missing guardians */
    boolean verify_all_shares() {
      boolean error = false;
      for (CiphertextDecryptionSelection share : this.shares) {
        if (share.recovered_parts().isPresent()) {
          if (!this.verify_share_replacement_lagrange(share)) {
            error = true;
            System.out.printf(" 10. ShareVerifier verify replacement lagrangian Guardian %s failed for %s.%n",
                    share.guardian_id(), id);
          }
        }
      }
      return !error;
    }

    // 10.B Confirm the correct missing tally share for each (non-placeholder) option
    // in each contest in the ballot coding file for each missing trustee T_i as
    //    M_i = ∏ l∈U (M_i,l)^w_l mod p
    private boolean verify_share_replacement_lagrange(CiphertextDecryptionSelection share) {
      Preconditions.checkArgument(share.recovered_parts().isPresent());
      ElementModP M_i = share.share(); // M_i in the spec, i is missing

      List<ElementModP> partial = new ArrayList<>();
      for (CiphertextCompensatedDecryptionSelection compShare : share.recovered_parts().get().values()) {
        ElementModP M_il = compShare.share(); // M_i,l in the spec
        ElementModQ lagrange = lagrange_coefficients.get(compShare.guardian_id());
        if (lagrange == null) {
          throw new IllegalStateException("Cant find lagrange coefficient for " + compShare.guardian_id());
        } else {
          partial.add(Group.int_to_p_unchecked(Group.pow_pi(M_il.getBigInt(), lagrange.getBigInt())));
        }
      }
      ElementModP product = Group.mult_p(partial);
      return M_i.equals(product);
    }
  }
}
