package com.sunya.electionguard.verifier;

import com.google.common.base.Preconditions;
import com.google.common.collect.Sets;
import com.sunya.electionguard.*;
import com.sunya.electionguard.publish.Consumer;
import net.jqwik.api.Example;
import net.jqwik.api.lifecycle.BeforeContainer;

import java.io.IOException;
import java.math.BigInteger;
import java.util.HashSet;
import java.util.Set;

import static com.google.common.truth.Truth.assertThat;

public class TestEncryptionValidator {
  static ElectionParameters electionParameters;
  static Consumer consumer;
  static EncyrptionValidator sev;
  static Grp grp;

  @BeforeContainer
  public static void setUp() throws IOException {
    String topdir = "src/test/data/java-publish";

    // set up
    consumer = new Consumer(topdir);
    electionParameters = new ElectionParameters(consumer);
    System.out.println("set up finished. ");

    System.out.println(" ------------ [box 3, 4, 5] ballot encryption check ------------");
    sev = new EncyrptionValidator(electionParameters, consumer);

    grp = new Grp(electionParameters.large_prime(), electionParameters.small_prime());
  }

  @Example
  public void testSelectionEncryptionValidation() throws IOException {
    boolean sevOk = sev.verify_all_ballots();
    assertThat(sevOk).isTrue();
  }

  @Example
  public void testVerifyAllContests() throws IOException {
    for (Ballot.CiphertextAcceptedBallot ballot : consumer.ballots()) {
      for (Ballot.CiphertextBallotContest contest : ballot.contests) {
        verify_a_contest(contest);
      }
    }
  }

  void verify_a_contest(Ballot.CiphertextBallotContest contest) {
    Integer vote_limit = electionParameters.getVoteLimitForContest(contest.object_id);
    Preconditions.checkNotNull(vote_limit);

    int placeholder_count = 0;
    BigInteger selection_alpha_product = BigInteger.ONE;
    BigInteger selection_beta_product = BigInteger.ONE;

    for (Ballot.CiphertextBallotSelection selection : contest.ballot_selections) {
      Group.ElementModP this_pad = selection.ciphertext().pad;
      Group.ElementModP this_data = selection.ciphertext().data;

      // get alpha, beta products
      selection_alpha_product = grp.mult_p(selection_alpha_product, this_pad.getBigInt());
      selection_beta_product = grp.mult_p(selection_beta_product, this_data.getBigInt());

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
    boolean placeholder_match = match_vote_limit_by_contest(contest.object_id, placeholder_count);
    assertThat(placeholder_match).isTrue();

    // calculate c = H(Q-bar, (A,B), (a,b))
    ChaumPedersen.ConstantChaumPedersenProof proof = contest.proof.orElseThrow(IllegalStateException::new);
    Group.ElementModP a = proof.pad;
    Group.ElementModP b = proof.data;
    Group.ElementModQ this_contest_challenge = proof.challenge;
    Group.ElementModQ challenge_computed =
            Hash.hash_elems(electionParameters.extended_hash(),
                    new Group.ElementModP(selection_alpha_product),
                    new Group.ElementModP(selection_beta_product), a, b);

    // check if given contest challenge matches the computation
    assertThat(challenge_computed).isEqualTo(this_contest_challenge);

    // check equations
    boolean equ1_check = this.check_cp_proof_alpha(contest, selection_alpha_product);
    boolean equ2_check = this.check_cp_proof_beta(contest, selection_beta_product, BigInteger.valueOf(vote_limit));
    assertThat(equ1_check).isTrue();
    assertThat(equ2_check).isTrue();
  }

  boolean verify_selection_validity(Ballot.CiphertextBallotSelection selection) {
    boolean error = false;
    Group.ElementModP this_pad = selection.ciphertext().pad;
    Group.ElementModP this_data = selection.ciphertext().data;

    // get dictionaries
    ChaumPedersen.DisjunctiveChaumPedersenProof proof = selection.proof.orElseThrow();
    ElGamal.Ciphertext cipher = selection.ciphertext();

    // get values
    String selection_id = selection.object_id;
    Group.ElementModP zero_pad = proof.proof_zero_pad; // a0
    Group.ElementModP one_pad = proof.proof_one_pad; // a1
    Group.ElementModP zero_data = proof.proof_zero_data; // b0
    Group.ElementModP one_data = proof.proof_one_data; // b1
    Group.ElementModQ zero_challenge = proof.proof_zero_challenge; // c0
    Group.ElementModQ one_challenge = proof.proof_one_challenge; // c1
    Group.ElementModQ zero_response = proof.proof_zero_response; // v0
    Group.ElementModQ one_response = proof.proof_one_response; // v1

    // point 1: check alpha, beta, a0, b0, a1, b1 are all in set Zrp
    if (!(this.check_params_within_zrp(cipher.pad, cipher.data, zero_pad, one_pad, zero_data, one_data))) {
      error = true;
    }

    // point 3: check if the given values, c0, c1, v0, v1 are each in the set zq
    if (!this.check_params_within_zq(zero_challenge, one_challenge, zero_response, one_response)) {
      error = true;
    }

    // point 2: conduct hash computation, c = H(Q-bar, (alpha, beta), (a0, b0), (a1, b1))
    Group.ElementModQ challenge = Hash.hash_elems(electionParameters.extended_hash(), this_pad, this_data,
            zero_pad, zero_data, one_pad, one_data);

    // point 4:  c = c0 + c1 mod q is satisfied
    if (!this.check_hash_comp(challenge.getBigInt(), zero_challenge.getBigInt(), one_challenge.getBigInt())) {
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

  /** check if the given values are all in set Zrp */
  private boolean check_params_within_zrp(Group.ElementModP... params) {
    boolean error = false;
    for (Group.ElementModP param : params) {
      if (!grp.is_within_set_zrp(param.getBigInt())) {
        error = true;
      }
    }
    return !error;
  }

  /** check if the given values are each in the set zq */
  private boolean check_params_within_zq(Group.ElementModQ... params) {
    boolean error = false;
    for (Group.ElementModQ param : params) {
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
  private boolean check_cp_proof_zero_proof(Group.ElementModP pad, Group.ElementModP data, Group.ElementModP zero_pad, Group.ElementModP zero_data,
                                            Group.ElementModQ zero_chal, Group.ElementModQ zero_res) {
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
  private boolean check_cp_proof_one_proof(Group.ElementModP pad, Group.ElementModP data, Group.ElementModP one_pad, Group.ElementModP one_data,
                                           Group.ElementModQ one_chal, Group.ElementModQ one_res) {

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
   * check if a selection's a and b are in set Zrp - box 4, limit check
   */
  boolean verify_selection_limit(Ballot.CiphertextBallotSelection selection) {
    Group.ElementModP this_pad = selection.ciphertext().pad;
    Group.ElementModP this_data = selection.ciphertext().data;

    boolean a_res = grp.is_within_set_zrp(this_pad.getBigInt());
    boolean b_res = grp.is_within_set_zrp(this_data.getBigInt());

    if (!a_res) {
      System.out.printf("selection pad/a value error.%n");
    }
    if (!b_res) {
      System.out.printf("selection data/b value error.%n");
    }

    return a_res && b_res;
  }

  private boolean match_vote_limit_by_contest(String contest_name, int num_of_placeholders) {
    int vote_limit = electionParameters.getVoteLimitForContest(contest_name);
    boolean res = vote_limit == num_of_placeholders;
    if (!res) {
      System.out.printf("Contest placeholder number error.%n");
    }
    return res;
  }

  /**
   * check if equation g ^ v = a * A ^ c mod p is satisfied,
   * This function checks the first part of aggregate encryption, A in (A, B), is used together with
   * check_cp_proof_beta() to form a pair-wise check on a complete encryption value pair (A,B)
   * :param alpha_product: the accumulative product of all the alpha/pad values on all selections within a contest
   * :return: True if the equation is satisfied, False if not
   */
  private boolean check_cp_proof_alpha(Ballot.CiphertextBallotContest contest, BigInteger alpha_product) {
    ChaumPedersen.ConstantChaumPedersenProof proof = contest.proof.orElseThrow(IllegalStateException::new);

    BigInteger left = grp.pow_p(electionParameters.generator(), proof.response.getBigInt());
    BigInteger right = grp.mult_p(grp.mod_p(proof.pad.getBigInt()),
            grp.pow_p(alpha_product, proof.challenge.getBigInt()));

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
  private boolean check_cp_proof_beta(Ballot.CiphertextBallotContest contest, BigInteger beta_product, BigInteger votes_allowed) {
    ChaumPedersen.ConstantChaumPedersenProof proof = contest.proof.orElseThrow(IllegalStateException::new);

    BigInteger leftTerm1 = grp.pow_p(electionParameters.generator(), grp.mult_q(votes_allowed, proof.challenge.getBigInt()));
    BigInteger leftTerm2 = grp.pow_p(electionParameters.elgamal_key().getBigInt(), proof.response.getBigInt());
    BigInteger left = grp.mult_p(leftTerm1, leftTerm2);

    BigInteger right = grp.mult_p(proof.data.getBigInt(), grp.pow_p(beta_product, proof.challenge.getBigInt()));

    boolean res = left.equals(right);
    if (!res) {
      System.out.printf("Contest selection limit check equation 2 error.%n");
    }
    return res;
  }

  @Example
  public void testTrackingHashes() throws IOException {
    Set<Group.ElementModQ> prev_hashes = new HashSet<>();
    Set<Group.ElementModQ> curr_hashes = new HashSet<>();

    for (Ballot.CiphertextAcceptedBallot ballot : consumer.ballots()) {
      verify_tracking_hash(ballot);
      if (ballot.previous_tracking_hash != null) {
        prev_hashes.add(ballot.previous_tracking_hash);
      }
      ballot.tracking_hash.ifPresent(curr_hashes::add);
    }

    // withdraw this test for now
    boolean trackOk = verify_tracking_hashes(prev_hashes, curr_hashes);
    // assertThat(trackOk).isTrue();
  }

  void verify_tracking_hash(Ballot.CiphertextAcceptedBallot ballot) {
    Group.ElementModQ crypto_hash = ballot.crypto_hash;
    Group.ElementModQ prev_hash = ballot.previous_tracking_hash;
    Group.ElementModQ curr_hash = ballot.tracking_hash.orElseThrow(IllegalStateException::new);
    Group.ElementModQ curr_hash_computed = Hash.hash_elems(prev_hash, ballot.timestamp, crypto_hash);
    assertThat(curr_hash).isEqualTo(curr_hash_computed);
  }

  private boolean verify_tracking_hashes(Set<Group.ElementModQ> prev_hashes, Set<Group.ElementModQ> curr_hashes) {
    boolean error = false;

    // find the set that only contains first and last hash
    Set<Group.ElementModQ> differenceSet = Sets.symmetricDifference(prev_hashes, curr_hashes);

    // find the first and last hash
    Group.ElementModQ first_hash = new Group.ElementModQ(BigInteger.ZERO);
    Group.ElementModQ last_hash = new Group.ElementModQ(BigInteger.ZERO);
    for (Group.ElementModQ h : differenceSet) {
      if (prev_hashes.contains(h)){
        first_hash = h;
      } else if (curr_hashes.contains(h)){
        last_hash = h;
      }
    }

    /*
    LOOK: The zero_hash is given to the EncryptionMediator. For TestEndtoEndElectionIntegration, this is the device hash.
    I dont think this is captured in the ElectionCntext, so cant be tested.
     */

    // verify the first hash H0 = H(Q-bar)
    Group.ElementModQ zero_hash = Hash.hash_elems(electionParameters.description_hash());
    if (!zero_hash.equals(first_hash)) {
      error = true;
    }

    /* LOOK "CLOSE" is not used in library; anyway, its impossible for last_hash = Hash.hash_elems(last_hash, "CLOSE") */

    // verify the closing hash, H-bar = H(Hl, 'CLOSE')
    Group.ElementModQ closing_hash_computed = Hash.hash_elems(last_hash, "CLOSE");
    if (!closing_hash_computed.equals(last_hash)) {
      error = true;
    }

    return !error;
  }

}
