package com.sunya.electionguard.verifier;

import com.sunya.electionguard.*;
import com.sunya.electionguard.publish.Consumer;
import net.jqwik.api.Example;
import net.jqwik.api.lifecycle.BeforeContainer;

import java.io.IOException;

import static com.google.common.truth.Truth.assertThat;

// Box 2
public class TestGuardianPublicKeyVerifier {
  static ElectionRecord electionRecord;
  static GuardianPublicKeyVerifier kgv;

  @BeforeContainer
  public static void setUp() throws IOException {
    String topdir = TestParameterVerifier.topdir;

    Consumer consumer = new Consumer(topdir);
    electionRecord = consumer.readElectionRecordJson();
    kgv = new GuardianPublicKeyVerifier(electionRecord);
  }

  @Example
  public void testVerifyAllGuardians() {
    assertThat(kgv.verify_all_guardians()).isTrue();
  }

  @Example
  public void testGuardianPublicKeyValidation() {
    for (KeyCeremony.CoefficientValidationSet coeff : electionRecord.guardianCoefficients) {
      boolean kgvOk = kgv.verify_one_guardian(coeff);
      assertThat(kgvOk).isTrue();
    }
  }

  @Example
  public void testVerifyOneGuardian() {
    for (KeyCeremony.CoefficientValidationSet coeff : electionRecord.guardianCoefficients) {
      verify_equations(coeff);
      verify_challenges(coeff);
    }
  }

  private void verify_equations(KeyCeremony.CoefficientValidationSet coeff) {
    for (SchnorrProof proof : coeff.coefficient_proofs()) {
      Group.ElementModP commitment = proof.commitment; // h
      Group.ElementModP public_key = proof.public_key; // k
      Group.ElementModQ challenge = proof.challenge;   // c
      Group.ElementModQ response = proof.response;     // u

      // check equation
      verify_individual_key_computation(response, commitment, public_key, challenge);
    }
  }

  private void verify_challenges(KeyCeremony.CoefficientValidationSet coeff) {
    for (SchnorrProof proof : coeff.coefficient_proofs()) {
      Group.ElementModP commitment = proof.commitment; // h
      Group.ElementModP public_key = proof.public_key; // k
      Group.ElementModQ challenge = proof.challenge;   // c

      // LOOK changed to follow validation spec 2.A. see issue #278
      Group.ElementModQ challenge_computed = Hash.hash_elems(electionRecord.base_hash(), public_key, commitment);
      assertThat(challenge_computed).isEqualTo(challenge);
    }
  }

  /** check the equation = generator ^ response mod p = (commitment * public key ^ challenge) mod p */
  void verify_individual_key_computation(Group.ElementModQ response, Group.ElementModP commitment, Group.ElementModP public_key, Group.ElementModQ challenge) {
    Group.ElementModP left = Group.pow_p(electionRecord.generatorP(), response);
    Group.ElementModP right = Group.mult_p(commitment, Group.pow_p(public_key, challenge));
    assertThat(left).isEqualTo(right);
  }
}
