package com.sunya.electionguard.verifier;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;
import com.google.common.flogger.FluentLogger;
import com.sunya.electionguard.ChaumPedersen;
import com.sunya.electionguard.Group;
import com.sunya.electionguard.Hash;
import com.sunya.electionguard.PlaintextTally;
import com.sunya.electionguard.publish.CloseableIterable;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

import static com.sunya.electionguard.DecryptionShare.CiphertextDecryptionSelection;
import static com.sunya.electionguard.DecryptionShare.CiphertextCompensatedDecryptionSelection;
import static com.sunya.electionguard.Group.ElementModQ;
import static com.sunya.electionguard.Group.ElementModP;

/**
 * This verifies specification sections "8 Correctness of Partial Decryptions",
 * "9 Correctness of Substitute Data for Missing Data", and "12 Correct Decryption of Spoiled Ballots".
 * @see <a href="https://www.electionguard.vote/spec/0.95.0/9_Verifier_construction/#correctness-of-partial-decryptions">Decryption validation</a>
 * @see <a href="https://www.electionguard.vote/spec/0.95.0/9_Verifier_construction/#validation-of-correct-decryption-of-tallies">Tally decryption validation</a>
 */
public class DecryptionVerifier {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();
  private static final boolean show = false;

  final ElectionRecord electionRecord;
  final PlaintextTally decryptedTally;

  DecryptionVerifier(ElectionRecord electionRecord, PlaintextTally decryptedTally) {
    this.electionRecord = electionRecord;
    this.decryptedTally = decryptedTally;
  }

  /** Verify 8,9 for the election tally. */
  boolean verify_election_tally() {
    boolean error = !this.make_all_contest_verification(this.decryptedTally.object_id, this.decryptedTally.contests);
    if (error) {
      System.out.printf(" ***Decryptions of cast ballots failure. %n");
    } else {
      System.out.printf(" Decryptions of cast ballots success. %n");
    }
    return !error;
  }

  /**
   * Verify spoiled ballot tallies.
   * 12. An election verifier should confirm the correct decryption of each spoiled ballot using the same
   * process that was used to confirm the election tallies.
   */
  boolean verify_spoiled_tallies(CloseableIterable<PlaintextTally> talliesIterable) {
    AtomicBoolean valid = new AtomicBoolean(true);
    try (Stream<PlaintextTally> tallies = talliesIterable.iterator().stream()) {
      tallies.forEach(spoiled_tally ->  {
        if (show) System.out.printf(" Spoiled tally %s %n", spoiled_tally.object_id);
        boolean ok = this.make_all_contest_verification(spoiled_tally.object_id, spoiled_tally.contests);
        valid.compareAndSet(true, ok); // AND
      });
    }
    if (!valid.get()) {
      System.out.printf(" *** 12.A Spoiled ballot decryption failure. %n");
    } else {
      System.out.printf(" 12.A Spoiled ballot decryption success. %n");
    }
    return valid.get();
  }

  private boolean make_all_contest_verification(String name, Map<String, PlaintextTally.Contest> contests) {
    boolean error = false;
    for (PlaintextTally.Contest contest : contests.values()) {
      DecryptionContestVerifier tcv = new DecryptionContestVerifier(contest);
      if (!tcv.verify_a_contest()) {
        System.out.printf(" Contest %s decryption failure for %s. %n", contest.object_id(), name);
        error = true;
      }
    }
    return !error;
  }

  class DecryptionContestVerifier {
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

  class DecryptionSelectionVerifier {
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
        System.out.printf(" %s tally verification error.%n", this.selection_id );
      }
      return res;
    }
  }

  // verify section 8 and 9
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

    /** Verify all shares of a tally decryption */
    boolean verify_all_shares() {
      boolean error = false;
      for (CiphertextDecryptionSelection share : this.shares){
        ElementModP curr_public_key = this.public_keys.get(share.guardian_id());
        if (share.proof().isPresent()) {
          if (!this.verify_share_guardian_present(share, curr_public_key)) {
            error = true;
            System.out.printf("8. ShareVerifier verify present Guardian %s failed for %s.%n", share.guardian_id(), id);
          }
        } else if (share.recovered_parts().isPresent()) {
          if (!this.verify_share_guardian_missing(share)) {
            error = true;
            System.out.printf("9. ShareVerifier verify missing Guardian %s failed for %s.%n", share.guardian_id(), id);
          }
        } else {
          error = true;
          System.out.printf("ShareVerifier Guardian %s has no proof or recovery for %s.%n", share.guardian_id(), id);
        }
      }
      return !error;
    }

    // Verify a share that does not contains a proof (because the corresponding Guardian is missing).
    // 9. An election verifier must confirm for each (non-placeholder) option in each contest in the ballot
    // coding file the following for each missing trustee T i and for each surrogate trustee T l .
    // (A) The given value v i,l is in the set Zq .
    // (B) The given values a i,l and b i,l are both in the set Zr_p.
    // (C) The challenge value c i,l = H(Q̅ , (A, B), (a i,l , b i,l ), M i,l ).
    // (D) The equation g^v i,l mod p = (a i,l * (∏ k−1 j=0 K i,j ) ) mod p is satisfied.
    // (E) The equation A^v i,l mod p = (b i,l * M i,l ^ c i,l ) mod p is satisfied.
    private boolean verify_share_guardian_missing(CiphertextDecryptionSelection share) {
      boolean error = false;

      // get values
      Preconditions.checkArgument(share.recovered_parts().isPresent());
      for (CiphertextCompensatedDecryptionSelection compSelection : share.recovered_parts().get().values()) {
        String missing_guardian_id = compSelection.missing_guardian_id();
        ChaumPedersen.ChaumPedersenProof proof = compSelection.proof();
        ElementModP pad = proof.pad;
        ElementModP data = proof.data;
        ElementModQ response = proof.response;
        ElementModQ challenge = proof.challenge;
        ElementModP partial_decryption = compSelection.share();
        ElementModP recovery_key = compSelection.recovery_key();

        // 9.A check if the response vi is in the set Zq
        if (!response.is_in_bounds()) {
          System.out.printf("  9.A response not in Zq for missing_guardian %s for %s%n", missing_guardian_id, this.id);
          error = true;
        }

        // 9.B check if the given ai, bi are both in set Zr_p
        if (!pad.is_valid_residue()) {
          System.out.printf("  9.B ai not in Zr_p for missing_guardian %s for %s%n", missing_guardian_id, this.id);
          error = true;
        }
        if (!data.is_valid_residue()) {
          System.out.printf("  9.B bi not in Zr_p for missing_guardian %s for %s%n", missing_guardian_id, this.id);
          error = true;
        }

        // 9.C Check if the given challenge ci = H(Q-bar, (A,B), (ai, bi), M_i,l)
        ElementModQ challenge_computed = Hash.hash_elems(electionRecord.extendedHash(),
                this.selection_pad, this.selection_data, pad, data, partial_decryption);
        if (!challenge_computed.equals(challenge)) {
          System.out.printf("  9.C ci != H(Q-bar, (A,B), (ai, bi), M_i,l) for missing_guardian %s for %s%n", missing_guardian_id, this.id);
          error = true;
        }

        // 9.D g^vi mod p = ai * Ki^ci mod p
        if (!this.check_equation1(response, pad, challenge, recovery_key)) {
          System.out.printf("  9.D g^vi mod p != ai * Ki^ci mod p for missing_guardian %s for %s%n", missing_guardian_id, this.id);
          error = true;
        }

        // 9.E A^vi mod p = bi * M_i,l ^ ci mod p
        if (!this.check_equation2(response, data, challenge, partial_decryption)) {
          System.out.printf("  9.E A^vi mod p = bi * M_i,l ^ ci mod p for missing_guardian %s for %s%n", missing_guardian_id, this.id);
          error = true;
        }
      }
      return !error;
    }

    // Verify a share that contains a proof (because the corresponding Guardian is present).
    // 8. An election verifier must then confirm for each (non-placeholder) option in each contest in the
    // ballot coding file the following for each decrypting trustee T i .
    // (A) The given value vi is in the set Zq .
    // (B) The given values ai and bi are both in the set Zr_p .
    // (C) The challenge value c i satisfies c i = H(Q̅ , (A, B), (a i , b i ), M i ).
    // (D) The equation g v i mod p = (a i K i i ) mod p is satisfied.
    // (E) The equation A v i mod p = (b i M i c i ) mod p is satisfied.
    private boolean verify_share_guardian_present(CiphertextDecryptionSelection share, ElementModP public_key) {
      boolean error = false;
      String guardian_id = share.guardian_id();

      // get values
      Preconditions.checkArgument(share.proof().isPresent());
      ChaumPedersen.ChaumPedersenProof proof = share.proof().get();
      ElementModP pad = proof.pad;
      ElementModP data = proof.data;
      ElementModQ response = proof.response;
      ElementModQ challenge = proof.challenge;
      ElementModP partial_decryption = share.share(); // M_i in the spec

      // 8.A check if the response vi is in the set Zq
      if (!response.is_in_bounds()) {
        System.out.printf("  8.A response not in Zq for guardian %s for %s%n", guardian_id, this.id);
        error = true;
      }

      // 8.B check if the given ai, bi are both in set Zr_p
      if (!pad.is_valid_residue()) {
        System.out.printf("  8.B ai not in Zr_p for guardian %s for %s%n", guardian_id, this.id);
        error = true;
      }
      if (!data.is_valid_residue()) {
        System.out.printf("  8.B bi not in Zr_p for guardian %s for %s%n", guardian_id, this.id);
        error = true;
      }

      // 8.C Check if the given challenge ci = H(Q-bar, (A,B), (ai, bi), Mi)
      ElementModQ challenge_computed = Hash.hash_elems(electionRecord.extendedHash(),
              this.selection_pad, this.selection_data, pad, data, partial_decryption);
      if (!challenge_computed.equals(challenge)) {
        System.out.printf("  8.C ci != H(Q-bar, (A,B), (ai, bi), Mi) for guardian %s for %s%n", guardian_id, this.id);
        error = true;
      } else if (show) {
        System.out.printf("  8.C ok for guardian %s for %s%n", guardian_id, this.id);
      }

      // 8.D g^vi mod p = ai * Ki^ci mod p
      if (!this.check_equation1(response, pad, challenge, public_key)) {
        System.out.printf("  8.D g^vi mod p != ai * Ki^ci mod p for guardian %s for %s%n", guardian_id, this.id);
        error = true;
      }

      // 8.E A^vi mod p = bi * Mi ^ ci mod p
      if (!this.check_equation2(response, data, challenge, partial_decryption)) {
        System.out.printf("  8.E A^vi mod p = bi * Mi ^ ci mod p for guardian %s for %s%n", guardian_id, this.id);
        error = true;
      }

      return !error;
    }

    /**
     * 8.D Check if equation g ^ vi mod p = ai * (Ki ^ ci) mod p is satisfied.
     * <p>
     * @param response: response of a share, vi
     * @param pad: pad of a share, ai
     * @param public_key: public key of a guardian, Ki
     * @param challenge: challenge of a share, ci
     */
    private boolean check_equation1(ElementModQ response, ElementModP pad, ElementModQ challenge, ElementModP public_key) {
      // g ^ vi = ai * (Ki ^ ci) mod p
      ElementModP left = Group.pow_p(electionRecord.generatorP(), response);
      ElementModP right = Group.mult_p(pad, Group.pow_p(public_key, challenge));
      return left.equals(right);
    }

    /**
     * 8.E Check if equation A ^ vi = bi * (Mi^ ci) mod p is satisfied.
     * <p>
     * @param response: response of a share, vi
     * @param data: data of a share, bi
     * @param challenge: challenge of a share, ci
     * @param partial_decrypt: partial decryption of a guardian, M_i,l
     */
    boolean check_equation2(ElementModQ response, ElementModP data, ElementModQ challenge, ElementModP partial_decrypt) {
      // A ^ vi = (bi * (M_i,l)^ ci) mod p
      ElementModP left = Group.pow_p(this.selection_pad, response);
      ElementModP right = Group.mult_p(data, Group.pow_p(partial_decrypt, challenge));
      return left.equals(right);
    }
  }
}
