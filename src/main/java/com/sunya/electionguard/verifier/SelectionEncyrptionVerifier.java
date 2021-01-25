package com.sunya.electionguard.verifier;

import com.sunya.electionguard.*;

import java.io.IOException;
import java.math.BigInteger;

import static com.sunya.electionguard.Ballot.*;
import static com.sunya.electionguard.Group.ElementModQ;
import static com.sunya.electionguard.Group.ElementModP;

/** This verifies specification section "4. Correctness of Selection Encryptions". */
public class SelectionEncyrptionVerifier {
  private final ElectionRecord electionRecord;
  private final Grp grp;

  SelectionEncyrptionVerifier(ElectionRecord electionRecord) {
    this.electionRecord = electionRecord;
    this.grp = new Grp(electionRecord.large_prime(), electionRecord.small_prime());
  }

  boolean verify_all_selections() throws IOException {
    boolean error = false;

    for (CiphertextAcceptedBallot ballot : electionRecord.castBallots) {
      for (CiphertextBallotContest contest : ballot.contests) {
        for (CiphertextBallotSelection selection : contest.ballot_selections) {
          SelectionVerifier sv = new SelectionVerifier(selection);
          // check validity of a selection
          if (!sv.verifySelection()) {
            error = true;
          }
        }
      }
    }

    if (error) {
      System.out.printf(" ***Selection Encryptions failure.%n");
    } else {
      System.out.printf(" All Selection Encryptions validate: success.%n");
    }
    return !error;
  }

  private class SelectionVerifier {
    CiphertextBallotSelection selection;
    ElementModP alpha;
    ElementModP beta;

    SelectionVerifier(CiphertextBallotSelection selection) {
      this.selection = selection;
      this.alpha = selection.ciphertext().pad;
      this.beta = selection.ciphertext().data;
    }

    boolean verifySelection() {
      boolean error = false;

      // get dictionaries
      ChaumPedersen.DisjunctiveChaumPedersenProof proof = this.selection.proof.orElseThrow();
      ElGamal.Ciphertext cipher = this.selection.ciphertext();

      // get values
      String selection_id = this.selection.object_id;
      ElementModP a0 = proof.proof_zero_pad; // a0
      ElementModP a1 = proof.proof_one_pad; // a1
      ElementModP b0 = proof.proof_zero_data; // b0
      ElementModP b1 = proof.proof_one_data; // b1
      ElementModQ c0 = proof.proof_zero_challenge; // c0
      ElementModQ c1 = proof.proof_one_challenge; // c1
      ElementModQ v0 = proof.proof_zero_response; // v0
      ElementModQ v1 = proof.proof_one_response; // v1
      ElementModQ challenge = proof.challenge; // c

      // 4.A: check alpha, beta, a0, b0, a1, b1 are all in set Zrp
      if (!(this.check_params_within_zrp(cipher.pad, cipher.data, a0, a1, b0, b1))) {
        error = true;
      }

      // 4.C: check if the given values, c0, c1, v0, v1 are each in the set zq
      if (!this.check_params_within_zq(c0, c1, v0, v1)) {
        error = true;
      }

      // 4.B: conduct hash computation, c = H(Q-bar, (alpha, beta), (a0, b0), (a1, b1))
      ElementModQ expected_challenge = Hash.hash_elems(electionRecord.extended_hash(),
              this.alpha, this.beta, a0, b0, a1, b1);
      if (!challenge.equals(expected_challenge)) {
        System.out.printf("4.B selection challenge failed for %s.%n", selection);
        error = true;
      }

      // 4.D:  c = c0 + c1 mod q
      BigInteger expected = grp.mod_q(c0.getBigInt().add(c1.getBigInt()));
      if (!challenge.getBigInt().equals(expected)) {
        System.out.printf("4.D c = c0 + c1 mod q failed for %s.%n", selection);
        error = true;
      }

      // 4.E check chaum-pedersen zero proof: g ^ v0 = a0 * alpha ^ c0 mod p
      BigInteger equE_left = grp.pow_p(electionRecord.generator(), v0.getBigInt());
      BigInteger equE_right = grp.mult_p(a0.getBigInt(), grp.pow_p(alpha.getBigInt(), c0.getBigInt()));
      if (!equE_left.equals(equE_right)) {
        System.out.printf("4.E check chaum-pedersen zero proof failed for %s.%n", selection);
        error = true;
      }

      // 4.F check chaum-pedersen one proof: g ^ v1 = a1 * alpha ^ c1 mod p
      BigInteger equF_left = grp.pow_p(electionRecord.generator(), v1.getBigInt());
      BigInteger equF_right = grp.mult_p(a1.getBigInt(), grp.pow_p(alpha.getBigInt(), c1.getBigInt()));
      if (!equF_left.equals(equF_right)) {
        System.out.printf("4.F check chaum-pedersen one proof failed for %s.%n", selection);
        error = true;
      }

      // 4.G (G) K ^ v0 = b0 * beta ^ c0 mod p
      ElementModP K = electionRecord.elgamal_key();
      BigInteger equG_left = grp.pow_p(K.getBigInt(), v0.getBigInt());
      BigInteger equG_right = grp.mult_p(b0.getBigInt(), grp.pow_p(beta.getBigInt(), c0.getBigInt()));
      if (!equG_left.equals(equG_right)) {
        System.out.printf("4.G check chaum-pedersen zero proof failed for %s.%n", selection);
        error = true;
      }

      // 4.H (H) g ^ c1 * K ^ v1 = b1 * beta ^ c1 mod p
      BigInteger equH_left = grp.mult_p(grp.pow_p(electionRecord.generator(), c1.getBigInt()),
              grp.pow_p(electionRecord.elgamal_key().getBigInt(), v1.getBigInt()));
      BigInteger equH_right = grp.mult_p(b1.getBigInt(), grp.pow_p(beta.getBigInt(), c1.getBigInt()));
      if (!equH_left.equals(equH_right)) {
        System.out.printf("4.H check chaum-pedersen one proof failed for %s.%n", selection);
        error = true;
      }

      if (error) {
        System.out.printf("%s validity verification failure.%n", selection_id);
      }
      return !error;
    }

    /** check if the given values are each in set Zrp */
    private boolean check_params_within_zrp(ElementModP... params) {
      boolean error = false;
      for (ElementModP param : params) {
        if (!grp.is_within_set_zrp(param.getBigInt())) {
          error = true;
        }
      }
      return !error;
    }

    /** check if the given values are each in the set zq */
    private boolean check_params_within_zq(ElementModQ... params) {
      boolean error = false;
      for (ElementModQ param : params) {
        if (!grp.is_within_set_zq(param.getBigInt())) {
          error = true;
        }
      }
      return !error;
    }
  }

}
