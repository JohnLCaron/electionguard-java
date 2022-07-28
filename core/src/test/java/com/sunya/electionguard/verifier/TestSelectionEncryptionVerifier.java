package com.sunya.electionguard.verifier;

import com.google.common.base.Preconditions;
import com.sunya.electionguard.*;
import com.sunya.electionguard.ballot.EncryptedBallot;
import com.sunya.electionguard.json.JsonConsumer;
import com.sunya.electionguard.publish.Consumer;
import com.sunya.electionguard.publish.ElectionRecord;
import net.jqwik.api.Example;
import java.io.IOException;
import java.math.BigInteger;

import static com.google.common.truth.Truth.assertThat;

import static com.google.common.truth.Truth.assertWithMessage;
import static com.sunya.electionguard.Group.ElementModP;
import static com.sunya.electionguard.Group.ElementModQ;

public class TestSelectionEncryptionVerifier {

  @Example
  public void testSelectionEncryptionValidationProto() throws IOException {
    Consumer consumer = new Consumer(TestParameterVerifier.topdirProto);
    testSelectionEncryptionValidation(consumer.readElectionRecord());
  }

  @Example
  public void testSelectionEncryptionValidationJson() throws IOException {
    JsonConsumer consumer = new JsonConsumer(TestParameterVerifier.topdirJsonExample);
    testSelectionEncryptionValidation(consumer.readElectionRecordJson());
  }

  static void testSelectionEncryptionValidation(ElectionRecord electionRecord) {
    SelectionEncryptionVerifier sev = new SelectionEncryptionVerifier(electionRecord);
    boolean sevOk = sev.verify_all_selections();
    // assertThat(sevOk).isTrue();

    // not sure how different this is from SelectionEncryptionVerifier ??
    Tester tester = new Tester(electionRecord);
    for (EncryptedBallot ballot : electionRecord.submittedBallots()) {
      for (CiphertextBallot.Contest contest : ballot.contests) {
        tester.verify_a_contest(contest);
      }
    }

    // not currently part of SelectionEncryptionVerifier
    for (EncryptedBallot ballot : electionRecord.submittedBallots()) {
      assertThat(ballot.is_valid_encryption(electionRecord.manifest().cryptoHash(),
              electionRecord.electionPublicKey(),
              electionRecord.extendedHash()));
    }

    // ballot chaining (box 6)
    for (EncryptedBallot ballot : electionRecord.submittedBallots()) {
      ElementModQ crypto_hash = ballot.crypto_hash;
      ElementModQ prev_hash = ballot.code_seed;
      ElementModQ curr_hash = ballot.code;
      ElementModQ curr_hash_computed = Hash.hash_elems(prev_hash, ballot.timestamp, crypto_hash);
      assertWithMessage("Ballot chaining on '%s' fails", ballot.ballotId).that(curr_hash).isEqualTo(curr_hash_computed);
    }
  }

  static class Tester {
    ElectionRecord electionRecord;

    public Tester(ElectionRecord electionRecord) {
      this.electionRecord = electionRecord;
    }

    void verify_a_contest(CiphertextBallot.Contest contest) {
      Integer vote_limit = electionRecord.manifest().findVoteLimit(contest.contestId);
      Preconditions.checkNotNull(vote_limit);

      int placeholder_count = 0;
      ElementModP selection_alpha_product = Group.ONE_MOD_P;
      ElementModP selection_beta_product = Group.ONE_MOD_P;

      for (CiphertextBallot.Selection selection : contest.selections) {
        ElementModP this_pad = selection.ciphertext().pad();
        ElementModP this_data = selection.ciphertext().data();

        // get alpha, beta products
        selection_alpha_product = Group.mult_p(selection_alpha_product, this_pad);
        selection_beta_product = Group.mult_p(selection_beta_product, this_data);

        // check validity of a selection
        boolean is_correct = verify_selection_validity(selection);
        assertThat(is_correct).isTrue();

        // check selection limit, whether each a and b are in zrp
        boolean is_within_limit = verify_selection_limit(selection);
        assertThat(is_within_limit).isTrue();

        // get placeholder counts
        if (selection.is_placeholder_selection) {
          placeholder_count++;
        }
      }

      // verify the placeholder numbers match the maximum votes allowed - contest check
      boolean placeholder_match = match_vote_limit_by_contest(contest.contestId, placeholder_count);
      assertThat(placeholder_match).isTrue();

      // calculate c = H(Q-bar, (A,B), (a,b))
      ChaumPedersen.ConstantChaumPedersenProof proof = contest.proof.orElseThrow(IllegalStateException::new);
      ElementModP a = proof.pad;
      ElementModP b = proof.data;
      ElementModQ this_contest_challenge = proof.challenge;
      ElementModQ challenge_computed =
              Hash.hash_elems(electionRecord.extendedHash(),
                      selection_alpha_product,
                      selection_beta_product, a, b);

      // check if given contest challenge matches the computation
      assertThat(challenge_computed).isEqualTo(this_contest_challenge);

      // check equations
      boolean equ1_check = this.check_cp_proof_alpha(contest, selection_alpha_product);
      boolean equ2_check = this.check_cp_proof_beta(contest, selection_beta_product,
              Group.int_to_q_unchecked(BigInteger.valueOf(vote_limit)));
      assertThat(equ1_check).isTrue();
      assertThat(equ2_check).isTrue();
    }

    boolean verify_selection_validity(CiphertextBallot.Selection selection) {
      boolean error = false;
      ElementModP this_pad = selection.ciphertext().pad();
      ElementModP this_data = selection.ciphertext().data();

      // get dictionaries
      ChaumPedersen.DisjunctiveChaumPedersenProof proof = selection.proof.orElseThrow();
      ElGamal.Ciphertext cipher = selection.ciphertext();

      // get values
      String selection_id = selection.object_id();
      ElementModP zero_pad = proof.proof0.pad; // a0
      ElementModP one_pad = proof.proof1.pad; // a1
      ElementModP zero_data = proof.proof0.data; // b0
      ElementModP one_data = proof.proof1.data; // b1
      ElementModQ zero_challenge = proof.proof0.challenge; // c0
      ElementModQ one_challenge = proof.proof1.challenge; // c1
      ElementModQ zero_response = proof.proof0.response; // v0
      ElementModQ one_response = proof.proof1.response; // v1

      // point 1: check alpha, beta, a0, b0, a1, b1 are all in set Zrp
      if (!(this.check_params_within_zrp(cipher.pad(), cipher.data(), zero_pad, one_pad, zero_data, one_data))) {
        error = true;
      }

      // point 3: check if the given values, c0, c1, v0, v1 are each in the set zq
      if (!this.check_params_within_zq(zero_challenge, one_challenge, zero_response, one_response)) {
        error = true;
      }

      // point 2: conduct hash computation, c = H(Q-bar, (alpha, beta), (a0, b0), (a1, b1))
      ElementModQ challenge = Hash.hash_elems(electionRecord.extendedHash(), this_pad, this_data,
              zero_pad, zero_data, one_pad, one_data);

      // point 4:  c = c0 + c1 mod q is satisfied
      if (!this.check_hash_comp(challenge, zero_challenge, one_challenge)) {
        error = true;
      }

      // point 5: check 2 chaum-pedersen proofs, zero proof and one proof
      if (!(this.check_cp_proof_zero_proof(this_pad, this_data, zero_pad, zero_data, zero_challenge, zero_response) &&
              this.check_cp_proof_one_proof(this_pad, this_data, one_pad, one_data, one_challenge, one_response))) {
        error = true;
      }

      if (error) {
        System.out.printf("%s validity verification failure.%n", selection_id);
      }

      return !error;
    }

    /**
     * check if the given values are all in set Zrp
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

    private boolean check_params_within_zq(ElementModQ... params) {
      boolean error = false;
      for (ElementModQ param : params) {
        if (!param.is_in_bounds()) {
          error = true;
        }
      }
      return !error;
    }


    private boolean check_cp_proof_zero_proof(ElementModP pad, ElementModP data, ElementModP zero_pad, ElementModP zero_data,
                                              ElementModQ zero_chal, ElementModQ zero_res) {
      // g ^ v0 = a0 * alpha ^ c0 mod p
      ElementModP equ1_left = Group.pow_p(electionRecord.generatorP(), zero_res);
      ElementModP equ1_right = Group.mult_p(zero_pad, Group.pow_p(pad, zero_chal));
      boolean eq1ok = equ1_left.equals(equ1_right);

      // K ^ v0 = b0 * beta ^ c0 mod p
      ElementModP equ2_left = Group.pow_p(electionRecord.electionPublicKey(), zero_res);
      ElementModP equ2_right = Group.mult_p(zero_data, Group.pow_p(data, zero_chal));
      boolean eq2ok = equ2_left.equals(equ2_right);

      if (!eq1ok || !eq2ok) {
        System.out.printf("Chaum-pedersen proof zero proof failure.%n");
      }
      return eq1ok && eq2ok;
    }


    private boolean check_cp_proof_one_proof(ElementModP pad, ElementModP data, ElementModP one_pad, ElementModP one_data,
                                             ElementModQ one_chal, ElementModQ one_res) {

      // g ^ v1 = a1 * alpha ^ c1 mod p
      ElementModP equ1_left = Group.pow_p(electionRecord.generatorP(), one_res);
      ElementModP equ1_right = Group.mult_p(one_pad, Group.pow_p(pad, one_chal));
      boolean eq1ok = equ1_left.equals(equ1_right);

      // g ^ c1 * K ^ v1 = b1 * beta ^ c1 mod p
      ElementModP equ2_left = Group.mult_p(Group.pow_p(electionRecord.generatorP(), one_chal),
              Group.pow_p(electionRecord.electionPublicKey(), one_res));
      ElementModP equ2_right = Group.mult_p(one_data, Group.pow_p(data, one_chal));
      boolean eq2ok = equ2_left.equals(equ2_right);

      if (!eq1ok || !eq2ok) {
        System.out.printf("Chaum-pedersen proof one proof failure.%n");
      }
      return eq1ok && eq2ok;
    }

    private boolean check_hash_comp(ElementModQ chal, ElementModQ zero_chal, ElementModQ one_chal) {
      // calculated expected challenge value = c0 + c1 mod q
      ElementModQ expected = Group.add_q(zero_chal, one_chal);
      boolean res = chal.equals(expected);
      if (!res) {
        System.out.printf("challenge value error.%n");
      }
      return res;
    }

    boolean verify_selection_limit(CiphertextBallot.Selection selection) {
      ElementModP this_pad = selection.ciphertext().pad();
      ElementModP this_data = selection.ciphertext().data();

      boolean a_res = this_pad.is_valid_residue();
      boolean b_res = this_data.is_valid_residue();

      if (!a_res) {
        System.out.printf("selection pad/a value error.%n");
      }
      if (!b_res) {
        System.out.printf("selection data/b value error.%n");
      }

      return a_res && b_res;
    }

    private boolean match_vote_limit_by_contest(String contestName, int num_of_placeholders) {
      Integer vote_limit = electionRecord.manifest().findVoteLimit(contestName);
      boolean res = vote_limit == num_of_placeholders;
      if (!res) {
        System.out.printf("Contest placeholder number error.%n");
      }
      return res;
    }


    private boolean check_cp_proof_alpha(CiphertextBallot.Contest contest, ElementModP alpha_product) {
      ChaumPedersen.ConstantChaumPedersenProof proof = contest.proof.orElseThrow(IllegalStateException::new);

      ElementModP left = Group.pow_p(electionRecord.generatorP(), proof.response);
      ElementModP right = Group.mult_p(proof.pad, Group.pow_p(alpha_product, proof.challenge));

      boolean res = left.equals(right);
      if (!res) {
        System.out.printf("Contest selection limit check equation 1 error.%n");
      }
      return res;
    }

    private boolean check_cp_proof_beta(CiphertextBallot.Contest contest, ElementModP beta_product, ElementModQ votes_allowed) {
      ChaumPedersen.ConstantChaumPedersenProof proof = contest.proof.orElseThrow(IllegalStateException::new);

      ElementModP leftTerm1 = Group.pow_p(electionRecord.generatorP(), Group.mult_q(votes_allowed, proof.challenge));
      ElementModP leftTerm2 = Group.pow_p(electionRecord.electionPublicKey(), proof.response);
      ElementModP left = Group.mult_p(leftTerm1, leftTerm2);

      ElementModP right = Group.mult_p(proof.data, Group.pow_p(beta_product, proof.challenge));

      boolean res = left.equals(right);
      if (!res) {
        System.out.printf("Contest selection limit check equation 2 error.%n");
      }
      return res;
    }
  }

}
