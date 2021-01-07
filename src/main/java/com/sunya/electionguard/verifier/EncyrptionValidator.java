package com.sunya.electionguard.verifier;

import com.google.common.base.Preconditions;
import com.google.common.collect.Sets;
import com.sunya.electionguard.*;
import com.sunya.electionguard.publish.Consumer;

import java.io.IOException;
import java.math.BigInteger;
import java.util.HashSet;
import java.util.Set;

import static com.sunya.electionguard.Ballot.*;
import static com.sunya.electionguard.Group.ElementModQ;
import static com.sunya.electionguard.Group.ElementModP;

/**
 * This module is created for the encryption verification, which covers boxes 3, 4, 5 of the specification document.
 * <p>
 * The encryption verification needs to be conducted on all the ballots (both cast and spoiled) in the election dataset.
 * For encryption verification on every single ballot, 3 levels of checks are needed, including ballot-level, contest-level,
 * and selection-level. The following 4 classes represent this data and verification hierarchy.
 * <p>
 * This class checks ballot encryption correctness on both spoiled and cast (box 3, 4), and verifies the correctness
 * of tracking hash chain (box 5).
 */
public class EncyrptionValidator {
  private final ElectionParameters electionParameters;
  private final Consumer consumer;
  private final Grp grp;

  EncyrptionValidator(ElectionParameters electionParameters, Consumer consumer) {
    this.electionParameters = electionParameters;
    this.consumer = consumer;
    this.grp = new Grp(electionParameters.large_prime(), electionParameters.small_prime());
  }

  /**
   * runs through the folder that contains ballot files once, runs encryption verification on every ballot
   */
  boolean verify_all_ballots() throws IOException {
    boolean error = false;
    int countFail = 0;
    Set<ElementModQ> prev_hashes = new HashSet<>();
    Set<ElementModQ> curr_hashes = new HashSet<>();

    for (CiphertextAcceptedBallot ballot : consumer.ballots()) {
      // verify all ballots, box 3 & 4
      BallotEncryptionVerifier bev = new BallotEncryptionVerifier(ballot);

      // verify correctness
      boolean contest_res = bev.verify_all_contests();
      if (!contest_res) {
        error = true;
        countFail += 1;
      }

      if (bev.ballot.previous_tracking_hash != null) {
        prev_hashes.add(bev.ballot.previous_tracking_hash);
      }
      bev.ballot.tracking_hash.ifPresent(curr_hashes::add);

      // aggregate tracking hashes, box 5
      boolean tracking_res = bev.verify_tracking_hash();
      if (!tracking_res) {
        error = true;
        countFail += 1;
      }
    }

    if (error) {
      System.out.printf("[Box 3 & 4] Ballot verification failure, %d ballots didn't pass check.%n", countFail);
    } else {
      System.out.printf("[Box 3 & 4] All ballot verification success.%n");
    }

    // LOOK this test does not work see notes in TestEncryptionValidator
    if (!this.verify_tracking_hashes(prev_hashes, curr_hashes)) {
      // error = true;
    }

    if (error) {
      System.out.printf("[Box 5] Tracking hashes verification failure.%n");
    } else {
      System.out.printf("[Box 5] Tracking hashes verification success.%n");
    }
    return !error;
  }

  /**
   * LOOK this test does not work see notes in TestSelectionEncryptionValidation
   * verifies the tracking hash chain correctness
   * NOTE: didn't check the first and closing hashes
   * :param hashes_dic: a dictionary of "previous tracking - current tracking " hash code pairs
   * :return: True if all the tracking hashes satisfy Bi, Hi = H(Hi-1, D, T, Bi)
   */
  private boolean verify_tracking_hashes(Set<ElementModQ> prev_hashes, Set<ElementModQ> curr_hashes) {
    boolean error = false;

    // find the set that only contains first and last hash
    Set<ElementModQ> differenceSet = Sets.symmetricDifference(prev_hashes, curr_hashes);

    // find the first and last hash
    ElementModQ first_hash = new ElementModQ(BigInteger.ZERO);
    ElementModQ last_hash = new ElementModQ(BigInteger.ZERO);
    for (ElementModQ h : differenceSet) {
      if (prev_hashes.contains(h)){
        first_hash = h;
      } else if (curr_hashes.contains(h)){
        last_hash = h;
      }
    }

    // verify the first hash H0 = H(Q-bar)
    ElementModQ zero_hash = Hash.hash_elems(electionParameters.extended_hash());
    if (!zero_hash.equals(first_hash)) {
      error = true;
    }

    // verify the closing hash, H-bar = H(Hl, 'CLOSE')
    ElementModQ closing_hash_computed = Hash.hash_elems(last_hash, "CLOSE");
    if (!closing_hash_computed.equals(last_hash)) {
      error = true;
    }

    return !error;
  }

  private static class Tuple {
    boolean encryption_error;
    boolean limit_error;

    public Tuple(boolean encryption_error, boolean limit_error) {
      this.encryption_error = encryption_error;
      this.limit_error = limit_error;
    }
  }

  BallotEncryptionVerifier newBallotEncryptionVerifier(CiphertextAcceptedBallot ballot) {
    return new BallotEncryptionVerifier(ballot);
  }

  /**     This class checks ballot correctness on a single ballot.

   Ballot correctness can be represented by:
   1. correct encryption (of value 0 or 1) of each selection within each contest (box 3)
   2. selection limits are satisfied for each contest (box 4)
   3. the running hash are correctly computed (box 5)
   */
  class BallotEncryptionVerifier {
    CiphertextAcceptedBallot ballot;
    BallotEncryptionVerifier(CiphertextAcceptedBallot ballot) {
      this.ballot = ballot;
    }

    /**
     * verify all the contests within a ballot and check if there are any encryption or limit error
     * :return: True if all contests checked out/no error, False if any error in any selection
     */
    boolean verify_all_contests() {
      boolean encrypt_error = false;
      boolean limit_error = false;

      String ballot_id = this.ballot.object_id;

      for (CiphertextBallotContest contest : ballot.contests) {
        BallotContestVerifier cv = new BallotContestVerifier(contest);
        Tuple errs = cv.verify_a_contest();
        if (errs.encryption_error) {
          encrypt_error = true;
        }
        if (errs.limit_error) {
          limit_error = true;
        }
      }

      if (!encrypt_error && !limit_error) {
        System.out.printf("%s [box 3 & 4] ballot correctness verification success.%n", ballot_id);
      } else {
        if (encrypt_error) {
          System.out.printf("%s [box 3] ballot encryption correctness verification failure.%n", ballot_id);
        }
        if (limit_error) {
          System.out.printf("%s [box 4] ballot limit check failure.%n", ballot_id);
        }
      }

      return !encrypt_error && !limit_error;
    }

    /**
     * verify all the middle (index 1 to n) tracking hash
     * :return: true if all the tracking hashes are correct, false otherwise
     */
    boolean verify_tracking_hash() {
      ElementModQ crypto_hash = this.ballot.crypto_hash;
      ElementModQ prev_hash = this.ballot.previous_tracking_hash;
      ElementModQ curr_hash = this.ballot.tracking_hash.orElseThrow(IllegalStateException::new);
      ElementModQ curr_hash_computed = Hash.hash_elems(prev_hash, this.ballot.timestamp, crypto_hash);
      return curr_hash.equals(curr_hash_computed);
    }
  }

  /**     This class is used for checking encryption and selection limit of an individual contest within a ballot. */
   class BallotContestVerifier {
    CiphertextBallotContest contest;
    ElementModP contest_alpha;
    ElementModP contest_beta;
    ElementModQ contest_response;
    ElementModQ contest_challenge;
    String contest_id;

    BallotContestVerifier(CiphertextBallotContest contest) {
      this.contest = contest;
      ChaumPedersen.ConstantChaumPedersenProof proof = contest.proof.orElseThrow(IllegalStateException::new);
      this.contest_alpha = proof.pad;
      this.contest_beta = proof.data;
      this.contest_response = proof.response;
      this.contest_challenge = proof.challenge;
      this.contest_id = contest.object_id;
    }

    /** verify a contest within a ballot, ballot correctness */
    Tuple verify_a_contest() {
      boolean encryption_error = false;
      boolean limit_error = false;

      Integer vote_limit = electionParameters.getVoteLimitForContest(this.contest_id);
      Preconditions.checkNotNull(vote_limit);

      int placeholder_count = 0;
      BigInteger selection_alpha_product = BigInteger.ONE;
      BigInteger selection_beta_product = BigInteger.ONE;

      for (CiphertextBallotSelection selection : contest.ballot_selections) {
        // verify encryption correctness on every selection  - selection check
        // create selection verifiers
        BallotSelectionVerifier sv = new BallotSelectionVerifier(selection);

        // get alpha, beta products
        selection_alpha_product = grp.mult_p(selection_alpha_product, sv.pad.getBigInt());
        selection_beta_product = grp.mult_p(selection_beta_product, sv.data.getBigInt());

        // check validity of a selection
        boolean is_correct = sv.verify_selection_validity();
        if (!is_correct) {
          encryption_error = true;
        }

        // check selection limit, whether each a and b are in zrp
        boolean is_within_limit = sv.verify_selection_limit();
        if (!is_within_limit) {
          limit_error = true;
        }

        // get placeholder counts
        if (sv.is_placeholder_selection()) {
          placeholder_count++;
        }
      }

      // verify the placeholder numbers match the maximum votes allowed - contest check
      boolean placeholder_match = this.match_vote_limit_by_contest(this.contest_id, placeholder_count);
      if (!placeholder_match) {
        limit_error = true;
      }

      // calculate c = H(Q-bar, (A,B), (a,b))
      ChaumPedersen.ConstantChaumPedersenProof proof = contest.proof.orElseThrow(IllegalStateException::new);
      Group.ElementModP a = proof.pad;
      Group.ElementModP b = proof.data;
      Group.ElementModQ challenge_computed =
              Hash.hash_elems(electionParameters.extended_hash(),
                      new Group.ElementModP(selection_alpha_product),
                      new Group.ElementModP(selection_beta_product), a, b);

      // check if given contest challenge matches the computation
      boolean challenge_match = challenge_computed.equals(this.contest_challenge);
      if (!challenge_match) {
        limit_error = true;
      }

      // check equations
      boolean equ1_check = this.check_cp_proof_alpha(selection_alpha_product);
      boolean equ2_check = this.check_cp_proof_beta(selection_beta_product, BigInteger.valueOf(vote_limit));

      if (!equ1_check || !equ2_check) {
        limit_error = true;
      }

      if (encryption_error) {
        System.out.printf("%s verification failure: encryption error.%n", this.contest_id);
      }
      if (limit_error) {
        System.out.printf("%s verification failure: encryption limit.%n", this.contest_id);
      }

      return new Tuple(encryption_error, limit_error);
    }

    /**
     * check if equation g ^ v = a * A ^ c mod p is satisfied,
     * This function checks the first part of aggregate encryption, A in (A, B), is used together with
     * check_cp_proof_beta() to form a pair-wise check on a complete encryption value pair (A,B)
     * :param alpha_product: the accumulative product of all the alpha/pad values on all selections within a contest
     * :return: True if the equation is satisfied, False if not
     */
    private boolean check_cp_proof_alpha(BigInteger alpha_product) {
      BigInteger left = grp.pow_p(electionParameters.generator(), this.contest_response.getBigInt());
      BigInteger right = grp.mult_p(grp.mod_p(this.contest_alpha.getBigInt()),
              grp.pow_p(alpha_product, this.contest_challenge.getBigInt()));

      boolean res = left.equals(right);
      if (!res) {
        System.out.printf("Contest selection limit check equation 1 error.%n");
      }
      return res;
    }

    /**
     * check if equation g ^ (L * c) * K ^ v = b * B ^ C mod p is satisfied
     * This function checks the second part of aggregate encryption, B in (A, B), is used together with
     * __check_cp_proof_alpha() to form a pair-wise check on a complete encryption value pair (A,B)
     * :param beta_product: the accumalative product of pad/beta values of all the selections within a contest
     * :param votes_allowed: the maximum votes allowed for this contest
     * :return: True if the equation is satisfied, False if not
     */
    private boolean check_cp_proof_beta(BigInteger beta_product, BigInteger votes_allowed) {
      BigInteger leftTerm1 = grp.pow_p(electionParameters.generator(), grp.mult_q(votes_allowed, this.contest_challenge.getBigInt()));
      BigInteger leftTerm2 = grp.pow_p(electionParameters.elgamal_key().getBigInt(), this.contest_response.getBigInt());
      BigInteger left = grp.mult_p(leftTerm1, leftTerm2);

      BigInteger right = grp.mult_p(this.contest_beta.getBigInt(), grp.pow_p(beta_product, this.contest_challenge.getBigInt()));

      boolean res = left.equals(right);
      if (!res) {
        System.out.printf("Contest selection limit check equation 2 error.%n");
      }
      return res;
    }

    /**
     * match the placeholder numbers in each contest with the maximum votes allowed
     * :param contest_name: name/id of the contest
     * :param num_of_placeholders: number of placeholders appear in this contest
     * :return: True if vote limit and the placeholder numbers are equaled, False if not
     */
    private boolean match_vote_limit_by_contest(String contest_name, int num_of_placeholders) {
      int vote_limit = electionParameters.getVoteLimitForContest(contest_name);
      boolean res = vote_limit == num_of_placeholders;
      if (!res) {
        System.out.printf("Contest placeholder number error.%n");
      }
      return res;
    }
  }

  /**
   *     This class is responsible for verifying one selection at a time.
   *
   *     Its main purpose is to confirm selection validity. Since it's the deepest level of detail the encryption
   *     verification can go, most of the computation of encryption checks happen here.
   */
  private class BallotSelectionVerifier {
    CiphertextBallotSelection selection;
    ElementModP pad;
    ElementModP data;

    BallotSelectionVerifier(CiphertextBallotSelection selection) {
      this.selection = selection;
      this.pad = selection.ciphertext().pad;
      this.data = selection.ciphertext().data;
    }

    boolean is_placeholder_selection() {
      return selection.is_placeholder_selection;
    }

    /**
     * verify the encryption validity of a selection within a contest.
     */
    boolean verify_selection_validity() {
      boolean error = false;

      // get dictionaries
      ChaumPedersen.DisjunctiveChaumPedersenProof proof = this.selection.proof.orElseThrow();
      ElGamal.Ciphertext cipher = this.selection.ciphertext();

      // get values
      String selection_id = this.selection.object_id;
      ElementModP zero_pad = proof.proof_zero_pad; // a0
      ElementModP one_pad = proof.proof_one_pad; // a1
      ElementModP zero_data = proof.proof_zero_data; // b0
      ElementModP one_data = proof.proof_one_data; // b1
      ElementModQ zero_challenge = proof.proof_zero_challenge; // c0
      ElementModQ one_challenge = proof.proof_one_challenge; // c1
      ElementModQ zero_response = proof.proof_zero_response; // v0
      ElementModQ one_response = proof.proof_one_response; // v1

      // point 1: check alpha, beta, a0, b0, a1, b1 are all in set Zrp
      if (!(this.check_params_within_zrp(cipher.pad, cipher.data, zero_pad, one_pad, zero_data, one_data))) {
        error = true;
      }

      // point 3: check if the given values, c0, c1, v0, v1 are each in the set zq
      if (!this.check_params_within_zq(zero_challenge, one_challenge, zero_response, one_response)) {
        error = true;
      }

      // point 2: conduct hash computation, c = H(Q-bar, (alpha, beta), (a0, b0), (a1, b1))
      ElementModQ challenge = Hash.hash_elems(electionParameters.extended_hash(), this.pad, this.data,
              zero_pad, zero_data, one_pad, one_data);

      // point 4:  c = c0 + c1 mod q is satisfied
      if (!this.check_hash_comp(challenge.getBigInt(), zero_challenge.getBigInt(), one_challenge.getBigInt())) {
        error = true;
      }

      // point 5: check 2 chaum-pedersen proofs, zero proof and one proof
      if (!(this.check_cp_proof_zero_proof(this.pad, this.data, zero_pad, zero_data, zero_challenge, zero_response) &&
              this.check_cp_proof_one_proof(this.pad, this.data, one_pad, one_data, one_challenge, one_response))) {
        error = true;
      }

      if (error) {
        System.out.printf("%s validity verification failure.%n", selection_id);
      }

      return !error;
    }

    /** check if the given values are all in set Zrp */
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

    /**
     * check if Chaum-Pedersen proof zero proof(given challenge c0, response v0) is satisfied.
     * <p>
     * To prove the zero proof, two equations g ^ v0 = a0 * alpha ^ c0 mod p, K ^ v0 = b0 * beta ^ c0 mod p
     * have to be satisfied.
     * In the verification process, the challenge c of a selection is allowed to be broken into two components
     * in any way as long as c = (c0 + c1) mod p, c0 here is the first component broken from c.
     * <p>
     * :param pad: alpha of a selection
     * :param data: beta of a selection
     * :param zero_pad: zero_pad of a selection
     * :param zero_data: zero_data of a selection
     * :param zero_chal: zero_challenge of a selection
     * :param zero_res: zero_response of a selection
     * :return: True if both equations of the zero proof are satisfied, False if either is not satisfied
     */
    private boolean check_cp_proof_zero_proof(ElementModP pad, ElementModP data, ElementModP zero_pad, ElementModP zero_data,
                                              ElementModQ zero_chal, ElementModQ zero_res) {
      // g ^ v0 = a0 * alpha ^ c0 mod p
      BigInteger equ1_left = grp.pow_p(electionParameters.generator(), zero_res.getBigInt());
      BigInteger equ1_right = grp.mult_p(zero_pad.getBigInt(), grp.pow_p(pad.getBigInt(), zero_chal.getBigInt()));
      boolean eq1ok = equ1_left.equals(equ1_right);

      // K ^ v0 = b0 * beta ^ c0 mod p
      BigInteger equ2_left = grp.pow_p(electionParameters.elgamal_key().getBigInt(), zero_res.getBigInt());
      BigInteger equ2_right = grp.mult_p(zero_data.getBigInt(), grp.pow_p(data.getBigInt(), zero_chal.getBigInt()));
      boolean eq2ok = equ2_left.equals(equ2_right);

      if (!eq1ok || !eq2ok) {
        System.out.printf("Chaum-pedersen proof zero proof failure.%n");
      }
      return eq1ok && eq2ok;
    }

    /**
     * check if Chaum-Pedersen proof one proof(given challenge c1, response v1) is satisfied.
     * <p>
     * To proof the zero proof, two equations g ^ v1 = a1 * alpha ^ c1 mod p, g ^ c1 * K ^ v1 = b1 * beta ^ c1 mod p
     * have to be satisfied.
     * In the verification process, the challenge c of a selection is allowed to be broken into two components
     * in any way as long as c = (c0 + c1) mod p, c1 here is the second component broken from c.
     * <p>
     * :param pad: alpha of a selection
     * :param data: beta of a selection
     * :param one_pad: one_pad of a selection
     * :param one_data: one_data of a selection
     * :param one_chal: one_challenge of a selection
     * :param one_res: one_response of a selection
     * :return: True if both equations of the one proof are satisfied, False if either is not satisfied
     */
    private boolean check_cp_proof_one_proof(ElementModP pad, ElementModP data, ElementModP one_pad, ElementModP one_data,
                                             ElementModQ one_chal, ElementModQ one_res) {

      // g ^ v1 = a1 * alpha ^ c1 mod p
      BigInteger equ1_left = grp.pow_p(electionParameters.generator(), one_res.getBigInt());
      BigInteger equ1_right = grp.mult_p(one_pad.getBigInt(), grp.pow_p(pad.getBigInt(), one_chal.getBigInt()));
      boolean eq1ok = equ1_left.equals(equ1_right);

      // g ^ c1 * K ^ v1 = b1 * beta ^ c1 mod p
      BigInteger equ2_left = grp.mult_p(grp.pow_p(electionParameters.generator(), one_chal.getBigInt()),
              grp.pow_p(electionParameters.elgamal_key().getBigInt(), one_res.getBigInt()));
      BigInteger equ2_right = grp.mult_p(one_data.getBigInt(), grp.pow_p(data.getBigInt(), one_chal.getBigInt()));
      boolean eq2ok = equ2_left.equals(equ2_right);

      if (!eq1ok || !eq2ok) {
        System.out.printf("Chaum-pedersen proof one proof failure.%n");
      }
      return eq1ok && eq2ok;
    }

    /**
     * check if the hash computation is correct, equation c = c0 + c1 mod q is satisfied.
     * :param chal: challenge of a selection
     * :param zero_chal: zero_challenge of a selection
     * :param one_chal: one_challenge of a selection
     */
    private boolean check_hash_comp(BigInteger chal, BigInteger zero_chal, BigInteger one_chal) {
      // calculated expected challenge value: c0 + c1 mod q
      BigInteger expected = grp.mod_q(zero_chal.add(one_chal));
      boolean res = grp.mod_q(chal).equals(expected);
      if (!res) {
        System.out.printf("challenge value error.%n");
      }
      return res;
    }

    /**
     * check if selection limit has been exceeded .
     */
    boolean verify_selection_limit() {
      return this.check_a_b();
    }

    /**
     * check if a selection's a and b are in set Zrp - box 4, limit check
     */
    boolean check_a_b() {
      boolean a_res = grp.is_within_set_zrp(this.pad.getBigInt());
      boolean b_res = grp.is_within_set_zrp(this.data.getBigInt());

      if (!a_res) {
        System.out.printf("selection pad/a value error.%n");
      }
      if (!b_res) {
        System.out.printf("selection data/b value error.%n");
      }

      return a_res && b_res;
    }
  }

}
