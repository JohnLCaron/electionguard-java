package com.sunya.electionguard.verifier;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;
import com.google.common.flogger.FluentLogger;
import com.sunya.electionguard.ChaumPedersen;
import com.sunya.electionguard.Group;
import com.sunya.electionguard.Hash;
import com.sunya.electionguard.PlaintextTally;

import java.math.BigInteger;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.sunya.electionguard.DecryptionShare.CiphertextCompensatedDecryptionSelection;
import static com.sunya.electionguard.DecryptionShare.CiphertextDecryptionSelection;
import static com.sunya.electionguard.Group.ElementModP;
import static com.sunya.electionguard.Group.ElementModQ;
import static com.sunya.electionguard.Group.Q;

/**
 * This verifies specification section "10. Correctness of Construction of Replacement Partial Decryptions"
 */
public class PartialDecryptionVerifier {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  final ElectionRecord electionRecord;
  final PlaintextTally tally;
  final Grp grp;
  final Map<String, PlaintextTally.GuardianState> guardianStateMap;

  PartialDecryptionVerifier(ElectionRecord electionRecord) {
    this.electionRecord = electionRecord;
    this.tally = electionRecord.decryptedTally;
    this.grp = new Grp(electionRecord.large_prime(), electionRecord.small_prime());
    this.guardianStateMap = new HashMap<>();
    tally.guardianStates.forEach(gs -> guardianStateMap.put(gs.guardian_id(), gs));
  }

  /** Verify 10.A for available guardians, if there are missing guardians. */
  boolean verify_replacement_partial_decryptions() {
    /** Verify 10.A for available guardians, if there are missing guardians. */
    boolean error = !this.verify_lagrange_coefficients();

    /** Verify 10.B for available guardians, if there are missing guardians. */
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

    for (Map.Entry<String, ImmutableMap<String, Group.ElementModQ>> entry : tally.lagrange_coefficients.entrySet()) {
      PlaintextTally.GuardianState missing_state = guardianStateMap.get(entry.getKey());
      if (missing_state == null || !missing_state.is_missing()) {
        System.out.printf(" ***Inconsistent Guardian missing state. %n");
        return false;
      }

      for (Map.Entry<String, Group.ElementModQ> entry2 : entry.getValue().entrySet()) {
        PlaintextTally.GuardianState available_state = guardianStateMap.get(entry2.getKey());
        if (available_state == null || available_state.is_missing()) {
          System.out.printf(" ***Inconsistent Guardian available state. %n");
          return false;
        }

        List<Integer> seq_others = tally.guardianStates.stream()
                .filter(gs -> !gs.is_missing() && !gs.guardian_id().equals(available_state.guardian_id()))
                .map(gs -> gs.sequence()).collect(Collectors.toList());

        error |= !this.verify_lagrange_coefficient(available_state.sequence(), seq_others, entry2.getValue());
      }
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
    ElementModQ numerator = Group.int_to_q_unchecked(BigInteger.valueOf(product).mod(Q));
    List<Integer> diff = degrees.stream().map(degree -> degree - coordinate).collect(Collectors.toList());
    int productDiff = diff.stream().reduce(1, (a, b)  -> a * b);
    ElementModQ denominator = Group.int_to_q_unchecked(BigInteger.valueOf(productDiff).mod(Q));
    return numerator.equals(Group.mult_q(lagrange, denominator));
  }

  private boolean make_all_contest_verification(Map<String, PlaintextTally.PlaintextTallyContest> contests) {
    boolean error = false;
    for (PlaintextTally.PlaintextTallyContest contest : contests.values()) {
      DecryptionContestVerifier tcv = new DecryptionContestVerifier(contest);
      if (!tcv.verify_a_contest()) {
        error = true;
      }
    }
    return !error;
  }

  private class DecryptionContestVerifier {
    PlaintextTally.PlaintextTallyContest contest;

    DecryptionContestVerifier(PlaintextTally.PlaintextTallyContest contest) {
      this.contest = contest;
    }

    boolean verify_a_contest() {
      boolean error = false;
      for (PlaintextTally.PlaintextTallySelection selection : this.contest.selections().values()) {
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
    final PlaintextTally.PlaintextTallySelection selection;
    final String selection_id;
    final ElementModP pad;
    final ElementModP data;

    DecryptionSelectionVerifier(String id, PlaintextTally.PlaintextTallySelection selection) {
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
        System.out.printf(" %s tally verification error.%n", this.selection_id );
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
      for (CiphertextDecryptionSelection share : this.shares){
        if (share.recovered_parts().isPresent()) {
          if (!this.verify_share_replacement_lagrange(share)) {
            error = true;
            System.out.printf("10. ShareVerifier verify replacement lagrangian Guardian %s failed for %s.%n",
                    share.guardian_id(), id);
          }
        } else {
          error = true;
          System.out.printf("ShareVerifier Guardian %s has no proof or recovery for %s.%n", share.guardian_id(), id);
        }
      }
      return !error;
    }

    private boolean verify_share_replacement_lagrange(CiphertextDecryptionSelection share) {
      Preconditions.checkArgument(share.recovered_parts().isPresent());
      ElementModP partial_decryption = share.share(); // M_i in the spec

      List<ElementModP> M_il = share.recovered_parts().get().values().stream().map(comp -> comp.share()).collect(Collectors.toList());
      // LOOK dunno what lagrange is, is it constant across missing guardians? so being stored redunantly??
      return verify_missing_tally_share(partial_decryption, M_il, Group.ONE_MOD_Q);
    }

    // 10.B Confirm the correct missing tally share for each (non-placeholder) option
    // in each contest in the ballot coding file for each missing trustee T_i as
    //    M_i = ∏ l∈U (M_i,l)^w_l mod p
    boolean verify_missing_tally_share(ElementModP M_i, List<ElementModP> M_il, ElementModQ lagrange) {
      ElementModP product = Group.mult_p(M_il);
      return M_i.equals(Group.int_to_p_unchecked(Group.pow_p(product.getBigInt(), lagrange.getBigInt())));
    }
  }
}
