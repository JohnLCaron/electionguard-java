package com.sunya.electionguard.verifier;

import com.sunya.electionguard.Group;
import com.sunya.electionguard.Hash;
import com.sunya.electionguard.KeyCeremony;
import com.sunya.electionguard.SchnorrProof;

import java.util.List;

import static com.sunya.electionguard.Group.ElementModQ;
import static com.sunya.electionguard.Group.ElementModP;

/** This verifies specification section "2. Guardian Public-Key Validation". */
public class GuardianPublicKeyVerifier {
  private final ElectionRecord electionRecord;

  GuardianPublicKeyVerifier(ElectionRecord electionRecord) {
    this.electionRecord = electionRecord;
  }

  /** verify all guardians" key generation info by examining challenge values and equations. */
  boolean verify_all_guardians() {
    boolean error = false;

    int count = 0;
    for (KeyCeremony.CoefficientValidationSet coeff : this.electionRecord.guardianCoefficients) {
      boolean res = this.verify_one_guardian(coeff);
      if (!res) {
        error = true;
        System.out.printf(" Guardian %d key generation verification failure. %n", count);
      }
      count++;
    }

    if (!error) {
      System.out.printf(" All guardians: key generation verification success. %n");
    }
    return !error;
  }

  boolean verify_one_guardian(KeyCeremony.CoefficientValidationSet coeffSet) {
    boolean error = false;
    List<SchnorrProof> coefficient_proofs = coeffSet.coefficient_proofs();

    // loop through every proof TODO why only quorum, why not all??
    for (int i = 0; i < this.electionRecord.quorum(); i++) {
      SchnorrProof proof = coefficient_proofs.get(i);
      ElementModP commitment = proof.commitment; // h
      ElementModP public_key = proof.public_key; // k
      ElementModQ challenge = proof.challenge;   // c
      ElementModQ response = proof.response;     // u

      // Changed validation spec 2.A. see issue #278
      ElementModQ challenge_computed =  Hash.hash_elems(public_key, commitment);
      if (!challenge_computed.equals(challenge)) {
        error = true;
        System.out.printf("Guardian %s coefficient_proof %d: equation 2A challenge validation failed.%n", coeffSet.owner_id(), i);
      }

      // check equation 2.B
      if (!this.verify_individual_key_computation(response, commitment, public_key, challenge)) {
        error = true;
        System.out.printf("Guardian %s coefficient_proof %d: equation 2B validation failed.%n", coeffSet.owner_id(), i);
      }
    }
    return !error;
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
    ElementModP left = Group.pow_p(Group.int_to_p_unchecked(this.electionRecord.generator()), response);
    ElementModP right = Group.mult_p(commitment, Group.pow_p(public_key, challenge));
    return left.equals(right);
  }

}
