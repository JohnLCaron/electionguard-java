package com.sunya.electionguard.verifier;

import com.google.common.base.Preconditions;
import com.sunya.electionguard.ChaumPedersen;
import com.sunya.electionguard.Group;
import com.sunya.electionguard.Hash;

import java.math.BigInteger;

import static com.sunya.electionguard.Ballot.CiphertextAcceptedBallot;
import static com.sunya.electionguard.Ballot.CiphertextBallotContest;
import static com.sunya.electionguard.Ballot.CiphertextBallotSelection;
import static com.sunya.electionguard.Group.ElementModP;
import static com.sunya.electionguard.Group.ElementModQ;

/** This verifies specification section "5. Adherence to Vote Limits". */
public class ContestVoteLimitsVerifier {
  private static final boolean show = false;

  private final ElectionRecord electionRecord;

  ContestVoteLimitsVerifier(ElectionRecord electionRecord) {
    this.electionRecord = electionRecord;
  }

  boolean verify_all_contests() {
    boolean error = false;

    for (CiphertextAcceptedBallot ballot : electionRecord.castBallots) {
      if (show) System.out.printf("Ballot %s.%n", ballot.object_id);
      for (CiphertextBallotContest contest : ballot.contests) {
        if (show) System.out.printf(" Contest %s.%n", contest.object_id);
        ContestVerifier cv = new ContestVerifier(contest);
        if (!cv.verifyContest()) {
          error = true;
        }
      }
    }

    if (error) {
      System.out.printf(" ***Adherence to Vote Limits failure.%n");
    } else {
      System.out.printf(" Adherence to Vote Limits success.%n");
    }
    return !error;
  }

  class ContestVerifier {
    CiphertextBallotContest contest;
    ChaumPedersen.ConstantChaumPedersenProof proof;
    ElementModP contest_alpha;
    ElementModP contest_beta;
    ElementModQ contest_response;
    ElementModQ contest_challenge;
    String contest_id;

    ContestVerifier(CiphertextBallotContest contest) {
      this.contest = contest;
      this.proof = contest.proof.orElseThrow();
      this.contest_alpha = contest.encrypted_total.pad; // LOOK changed see Issue #280
      this.contest_beta = contest.encrypted_total.data; // LOOK changed see Issue #280
      this.contest_response = proof.response;
      this.contest_challenge = proof.challenge;
      this.contest_id = contest.object_id;
    }

    boolean verifyContest() {
      boolean limit_error = false;

      // 5.C The given value V is in Z q
      if (!proof.response.is_in_bounds()) {
        System.out.printf("5.C V not in Zq for contest %s.%n", contest);
        limit_error = true;
      }

      int placeholder_count = 0;
      ElementModP selection_alpha_product = Group.ONE_MOD_P;
      ElementModP selection_beta_product = Group.ONE_MOD_P;

      for (CiphertextBallotSelection selection : contest.ballot_selections) {
        if (show) System.out.printf("   Selection %s.%n", selection.object_id);
        ElementModP alpha = selection.ciphertext().pad;
        ElementModP beta = selection.ciphertext().data;

        // get alpha, beta products
        selection_alpha_product = Group.mult_p(selection_alpha_product, alpha);
        selection_beta_product = Group.mult_p(selection_beta_product, beta);
        if (show) {
          System.out.printf("     alpha %s%n", alpha);
          System.out.printf("     beta %s%n", beta);
          System.out.printf("     accum_alpha %s%n", selection_alpha_product);
          System.out.printf("     accum_beta %s%n", selection_beta_product);
        }

        // 5.D The given values a and b are each in Zr_p.
        if (!alpha.is_valid_residue()) {
          System.out.printf("5.D alpha not in Zr_p for selection %s.%n", selection.object_id);
          limit_error = true;
        }
        if (!beta.is_valid_residue()) {
          System.out.printf("5.D beta not in Zr_p for selection %s.%n", selection.object_id);
          limit_error = true;
        }

        // get placeholder counts
        if (selection.is_placeholder_selection) {
          placeholder_count++;
        }
      }

      // 5.A verify the placeholder numbers match the maximum votes allowed
      Integer vote_limit = electionRecord.getVoteLimitForContest(this.contest.object_id);
      Preconditions.checkNotNull(vote_limit);
      if (vote_limit != placeholder_count) {
        System.out.printf("5.A Contest placeholder %d != %d vote limit for contest %s.%n", placeholder_count,
                vote_limit, contest.object_id);
        limit_error = true;
      }

      // 5.B The contest total (A, B) satisfies A = ∏ αi mod p and B = ∏ βi mod p where the (αi, βi)
      // represent all possible selections (including placeholder selections) for the contest.
      if (!this.contest_alpha.equals(selection_alpha_product)) {
        System.out.printf("5.B Contest total A fails verification for contest %s.%n", contest.object_id);
        limit_error = true;
      }
      if (!this.contest_beta.equals(selection_beta_product)) {
        System.out.printf("5.B Contest total B fails verification for contest %s.%n", contest.object_id);
        limit_error = true;
      }

      // 5.E calculate c = H(Q-bar, (A,B), (a,b))
      ChaumPedersen.ConstantChaumPedersenProof proof = contest.proof.orElseThrow(IllegalStateException::new);
      ElementModP a = proof.pad;
      ElementModP b = proof.data;
      ElementModQ challenge_computed =
              Hash.hash_elems(electionRecord.extended_hash(),
                      selection_alpha_product, // LOOK BigInteger or ModP ?
                      selection_beta_product, a, b);
      if (!challenge_computed.equals(this.contest_challenge)) {
        System.out.printf("5.E Challenge fails verification for contest %s.%n", contest.object_id);
        limit_error = true;
      }

      // check equations
      boolean equ1_check = this.check_cp_proof_alpha(selection_alpha_product);
      boolean equ2_check = this.check_cp_proof_beta(selection_beta_product, vote_limit);

      if (!equ1_check || !equ2_check) {
        limit_error = true;
      }

      return !limit_error;
    }

    /**
     * 5.F check if equation g ^ v mod p = a * A ^ c mod p is satisfied,
     *
     * @param alpha_product: the accumulative product of all the alpha/pad values on all selections within a contest
     * @return True if the equation is satisfied, False if not
     */
    private boolean check_cp_proof_alpha(ElementModP alpha_product) {
      ElementModP left = Group.pow_p(electionRecord.generatorP(), this.contest_response);
      ElementModP right = Group.mult_p(this.proof.pad, Group.pow_p(alpha_product, this.contest_challenge));

      if (!left.equals(right)) {
        System.out.printf("5.F fails.%n");
        return false;
      }
      return true;
    }

    /**
     * 5.G check if equation g ^ (L * c) * K ^ v mod p = b * B ^ C mod p is satisfied
     *
     * @param beta_product:  the accumulative product of pad/beta values of all the selections within a contest
     * @param votes_allowed: the maximum votes allowed for this contest
     * @return True if the equation is satisfied, False if not
     */
    private boolean check_cp_proof_beta(ElementModP beta_product, Integer votes_allowed) {
      ElementModQ votes_big = Group.int_to_q_unchecked(BigInteger.valueOf(votes_allowed));
      ElementModP leftTerm1 = Group.pow_p(electionRecord.generatorP(), Group.mult_q(votes_big, this.contest_challenge));
      ElementModP leftTerm2 = Group.pow_p(electionRecord.elgamal_key(), this.contest_response);
      ElementModP left = Group.mult_p(leftTerm1, leftTerm2);

      ElementModP right = Group.mult_p(this.proof.data, Group.pow_p(beta_product, this.contest_challenge));

      if (!left.equals(right)) {
        System.out.printf("5.G fails.%n");
        return false;
      }
      return true;
    }
  }

}
