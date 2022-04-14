package com.sunya.electionguard.verifier;

import com.sunya.electionguard.Group;
import com.sunya.electionguard.GuardianRecord;
import com.sunya.electionguard.SchnorrProof;

import static com.sunya.electionguard.Group.ElementModQ;
import static com.sunya.electionguard.Group.ElementModP;

/**
 * This verifies specification section "2 Guardian Public-Key Validation".
 * @see <a href="https://www.electionguard.vote/spec/0.95.0/9_Verifier_construction/#guardian-public-key-validation">Guardian public key validation</a>
 */
public class GuardianPublicKeyVerifier {
  private final ElectionRecord electionRecord;

  GuardianPublicKeyVerifier(ElectionRecord electionRecord) {
    this.electionRecord = electionRecord;
  }

  /** verify all guardians" key generation info by examining challenge values and equations. */
  boolean verify_all_guardians() {
    boolean error = false;

    int count = 0;
    for (GuardianRecord gr : this.electionRecord.guardianRecords) {
      boolean res = this.verifyGuardian(gr);
      if (!res) {
        error = true;
        System.out.printf(" Guardian %d key generation verification failure. %n", count);
      }
      count++;
    }

    if (!error) {
      System.out.printf(" Guardians (%d) key generation verification success. %n",
              this.electionRecord.guardianRecords.size());
    }
    return !error;
  }

  boolean verifyGuardian(GuardianRecord guardian) {
    boolean error = false;

    int count = 0;
    for (SchnorrProof proof : guardian.coefficientProofs()) {
      boolean proofOk;
      if (proof.publicKey == null) {
        ElementModP publicKey = guardian.coefficientCommitments().get(count);
        proofOk = proof.isValidVer2(publicKey);
      } else {
        proofOk = proof.isValidVer1();
      }
      if (!proofOk) {
        error = true;
        System.out.printf("Guardian %s coefficient_proof %d: validation failed.%n", guardian.guardianId(), count);
      }
      count++;
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
    ElementModP left = Group.pow_p(this.electionRecord.generatorP(), response);
    ElementModP right = Group.mult_p(commitment, Group.pow_p(public_key, challenge));
    return left.equals(right);
  }

}
