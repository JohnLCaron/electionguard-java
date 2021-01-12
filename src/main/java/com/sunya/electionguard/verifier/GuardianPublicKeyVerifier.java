package com.sunya.electionguard.verifier;

import com.sunya.electionguard.Grp;
import com.sunya.electionguard.Hash;
import com.sunya.electionguard.KeyCeremony;
import com.sunya.electionguard.SchnorrProof;

import java.math.BigInteger;
import java.util.List;

import static com.sunya.electionguard.Group.ElementModQ;
import static com.sunya.electionguard.Group.ElementModP;

/**
 * This class checks the key generation information are given correctly for each guardian. (box 2).
 */
public class GuardianPublicKeyVerifier {
  private final ElectionParameters electionParameters;
  private final Grp grp;

  GuardianPublicKeyVerifier(ElectionParameters electionParameters) {
    this.electionParameters = electionParameters;
    this.grp = new Grp(electionParameters.large_prime(), electionParameters.small_prime());
  }

  /**
   * verify all guardians" key generation info by examining challenge values and equations.
   */
  boolean verify_all_guardians() {
    boolean error = false;

    int count = 0;
    for (KeyCeremony.CoefficientValidationSet coeff : this.electionParameters.coefficients()) {
      boolean res = this.verify_one_guardian(coeff);
      if (!res) {
        error = true;
        System.out.printf("guardian %d key generation verification failure. %n", count);
      }
      count++;
    }

    if (!error) {
      System.out.printf("All guardians key generation verification success. %n");
    }

    return !error;
  }

  boolean verify_one_guardian(KeyCeremony.CoefficientValidationSet coeff) {
    boolean error = false;
    List<SchnorrProof> coefficient_proofs = coeff.coefficient_proofs();

    // loop through every proof TODO why only quorum, why not all??
    for (int i = 0; i < this.electionParameters.quorum(); i++) {
      SchnorrProof proof = coefficient_proofs.get(i);
      ElementModP commitment = proof.commitment; // h
      ElementModP public_key = proof.public_key; // k
      ElementModQ challenge = proof.challenge;   // c
      ElementModQ response = proof.response;     // u

      // compute challenge
      ElementModQ challenge_computed = this.compute_guardian_challenge_threshold_separated(public_key, commitment);

      // check if the computed challenge value matches the given
      if (!challenge_computed.equals(challenge)) {
        error = true;
        System.out.printf("guardian %d challenge number error. %n", i);
      }
      // check equation
      if (!this.verify_individual_key_computation(response, commitment, public_key, challenge)) {
        error = true;
        System.out.printf("guardian %d equation error. %n", i);
      }
    }
    return !error;
  }

  /**
   * computes challenge (c_ij) with hash, H(cij) = H(base hash, public key, commitment) % q.
   * Each guardian has quorum number of these challenges.
   * @param public_key: public key, under each guardian, previously listed as k
   * @param commitment: commitment, under each guardian, previously listed as h
   * @return a challenge value of a guardian, separated by quorum
   */
  ElementModQ compute_guardian_challenge_threshold_separated(ElementModP public_key, ElementModP commitment) {
    //ElementModQ hash = Hash.hash_elems(this.electionParameters.base_hash(), public_key, commitment);
    // return grp.mod_q(hash.getBigInt());

    return Hash.hash_elems(public_key, commitment);
  }

  /**
   * check the equation = generator ^ response mod p = (commitment * public key ^ challenge) mod p
   * @param response: response given by a guardian, ui,j
   * @param commitment: commitment given by a guardian, hi,j
   * @param public_key: public key of a guardian, Ki,j
   * @param challenge: challenge of a guardian, ci,j
   * @return True if both sides of the equations are equal, False otherwise
   */
  boolean verify_individual_key_computation(ElementModQ response, ElementModP commitment, ElementModP public_key, ElementModQ challenge) {
    BigInteger left = grp.pow_p(this.electionParameters.generator(), response.getBigInt());
    BigInteger right = grp.mult_p(commitment.getBigInt(), grp.pow_p(public_key.getBigInt(), challenge.getBigInt()));
    return left.equals(right);
  }
}
