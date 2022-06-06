package com.sunya.electionguard.verifier;

import com.sunya.electionguard.*;
import com.sunya.electionguard.publish.ElectionRecord;

import static com.sunya.electionguard.Group.ElementModQ;
import static com.sunya.electionguard.Group.ElementModP;

/**
 * This verifies specification section "4 Correctness of Selection Encryptions".
 * @see <a href="https://www.electionguard.vote/spec/0.95.0/9_Verifier_construction/#correctness-of-selection-encryptions">Encryption validation</a>
 */
public class SelectionEncryptionVerifier {
  private final ElectionRecord electionRecord;

  SelectionEncryptionVerifier(ElectionRecord electionRecord) {
    this.electionRecord = electionRecord;
  }

  boolean verify_all_selections() {
    boolean error = false;

    int nballots  = 0;
    int ncontests  = 0;
    int nselections  = 0;
    for (SubmittedBallot ballot : electionRecord.submittedBallots()) {
      nballots++;
      for (CiphertextBallot.Contest contest : ballot.contests) {
        ncontests++;
        for (CiphertextBallot.Selection selection : contest.selections) {
          nselections++;
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
      System.out.printf(" All Selection Encryptions validate for %d ballots, %d contests, %d selections: success.%n",
              nballots, ncontests, nselections);
    }
    return !error;
  }

  public class SelectionVerifier {
    final CiphertextBallot.Selection selection;
    final ElementModP alpha;
    final ElementModP beta;

    SelectionVerifier(CiphertextBallot.Selection selection) {
      this.selection = selection;
      this.alpha = selection.ciphertext().pad();
      this.beta = selection.ciphertext().data();
    }

    boolean verifySelection() {
      ChaumPedersen.DisjunctiveChaumPedersenProof proof = this.selection.proof.orElseThrow();
      if (proof.name.endsWith("2")) {
        return verifySelectionVer2(proof);
      } else {
        return verifySelectionVer1(proof);
      }
    }

    boolean verifySelectionVer1(ChaumPedersen.DisjunctiveChaumPedersenProof proof) {
      boolean error = false;

      // get values
      String selection_id = this.selection.object_id();
      ElementModP a0 = proof.proof0.pad; // a0
      ElementModP a1 = proof.proof1.pad; // a1
      ElementModP b0 = proof.proof0.data; // b0
      ElementModP b1 = proof.proof1.data; // b1
      ElementModQ c0 = proof.proof0.challenge; // c0
      ElementModQ c1 = proof.proof1.challenge; // c1
      ElementModQ v0 = proof.proof0.response; // v0
      ElementModQ v1 = proof.proof1.response; // v1
      ElementModQ challenge = proof.challenge; // c

      // 4.A: check alpha, beta, a0, b0, a1, b1 are all in set Zrp
      if (!(this.check_params_within_zrp(alpha, beta, a0, a1, b0, b1))) {
        error = true;
      }

      // 4.C: check if the given values, c0, c1, v0, v1 are each in the set zq
      if (!this.check_params_within_zq(c0, c1, v0, v1)) {
        error = true;
      }

      // 4.B: conduct hash computation, c = H(Q-bar, (alpha, beta), (a0, b0), (a1, b1))
      ElementModQ computedChallenge = Hash.hash_elems(electionRecord.extendedHash(),
              this.alpha, this.beta, a0, b0, a1, b1);
      if (!challenge.equals(computedChallenge)) {
        System.out.printf("4.B selection challenge failed for %s.%n", selection.object_id());
        error = true;
      }

      // 4.D:  c = (c0 + c1) mod q
      ElementModQ expected = Group.add_q(c0, c1);
      if (!challenge.equals(expected)) {
        System.out.printf("4.D c = (c0 + c1) mod q failed for %s.%n", selection.object_id());
        error = true;
      }

      // 4.E check chaum-pedersen zero proof: g ^ v0 = a0 * alpha ^ c0 mod p
      ElementModP equE_left = Group.pow_p(electionRecord.generatorP(), v0);
      ElementModP equE_right = Group.mult_p(a0, Group.pow_p(alpha, c0));
      if (!equE_left.equals(equE_right)) {
        System.out.printf("4.E check chaum-pedersen zero proof failed for %s.%n", selection.object_id());
        error = true;
      }

      // 4.F check chaum-pedersen one proof: g ^ v1 = a1 * alpha ^ c1 mod p
      ElementModP equF_left = Group.pow_p(electionRecord.generatorP(), v1);
      ElementModP equF_right = Group.mult_p(a1, Group.pow_p(alpha, c1));
      if (!equF_left.equals(equF_right)) {
        System.out.printf("4.F check chaum-pedersen one proof failed for %s.%n", selection.object_id());
        error = true;
      }

      // 4.G (G) K ^ v0 = b0 * beta ^ c0 mod p
      ElementModP K = electionRecord.electionPublicKey();
      ElementModP equG_left = Group.pow_p(K, v0);
      ElementModP equG_right = Group.mult_p(b0, Group.pow_p(beta, c0));
      if (!equG_left.equals(equG_right)) {
        System.out.printf("4.G check chaum-pedersen zero proof failed for %s.%n", selection.object_id());
        error = true;
      }

      // 4.H (H) g ^ c1 * K ^ v1 = b1 * beta ^ c1 mod p
      ElementModP equH_left = Group.mult_p(Group.pow_p(electionRecord.generatorP(), c1),
              Group.pow_p(electionRecord.electionPublicKey(), v1));
      ElementModP equH_right = Group.mult_p(b1, Group.pow_p(beta, c1));
      if (!equH_left.equals(equH_right)) {
        System.out.printf("4.H check chaum-pedersen one proof failed for %s.%n", selection.object_id());
        error = true;
      }

      // just for fun we want to know about is_valid()
      // ElGamal.Ciphertext message, ElementModP k, ElementModP m, ElementModQ extBaseHash
      boolean isValid = proof.is_valid(selection.ciphertext(), K, electionRecord.extendedHash());

      if (error) {
        System.out.printf("%s validity verification failure.%n", selection_id);
      }
      return !error;
    }

    /**
     * check if the given values are each in set Zrp
     */
    private boolean check_params_within_zrp(ElementModP... params) {
      boolean error = false;
      for (ElementModP param : params) {
        if (!param.is_valid_residue()) {
          error = true;
        }
      }
      return !error;
    }

    /**
     * check if the given values are each in the set zq
     */
    private boolean check_params_within_zq(ElementModQ... params) {
      boolean error = false;
      for (ElementModQ param : params) {
        if (!param.is_in_bounds()) {
          error = true;
        }
      }
      return !error;
    }

    boolean verifySelectionVer2(ChaumPedersen.DisjunctiveChaumPedersenProof proof) {
      // ElGamal.Ciphertext message, ElementModP k, ElementModQ qbar
      return proof.is_valid(
              selection.ciphertext(),
              electionRecord.electionPublicKey(),
              electionRecord.extendedHash());
    }
  }

}
