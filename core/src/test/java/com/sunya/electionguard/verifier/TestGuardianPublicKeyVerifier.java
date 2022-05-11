package com.sunya.electionguard.verifier;

import com.sunya.electionguard.*;
import com.sunya.electionguard.json.JsonConsumer;
import com.sunya.electionguard.publish.Consumer;
import com.sunya.electionguard.publish.ElectionRecord;
import electionguard.ballot.Guardian;
import net.jqwik.api.Example;

import java.io.IOException;

import static com.google.common.truth.Truth.assertThat;

// Box 2
public class TestGuardianPublicKeyVerifier {

  @Example
  public void testGuardianPublicKeyValidationProto() throws IOException {
    String topdir = TestParameterVerifier.topdirProto;
    Consumer consumer = new Consumer(topdir);
    ElectionRecord electionRecord = consumer.readElectionRecord();
    GuardianPublicKeyVerifier kgv = new GuardianPublicKeyVerifier(electionRecord);
    assertThat(kgv.verify_all_guardians()).isTrue();

    for (Guardian coeff : electionRecord.guardians()) {
      boolean kgvOk = kgv.verifyGuardian(coeff);
      assertThat(kgvOk).isTrue();
    }

    for (Guardian coeff : electionRecord.guardians()) {
      verify_equations(electionRecord.generatorP(), coeff);
      verify_challenges(coeff);
    }
  }

  @Example
  public void testGuardianPublicKeyValidationJson() throws IOException {
    String topdir = TestParameterVerifier.topdirJsonExample;
    JsonConsumer consumer = new JsonConsumer(topdir);
    ElectionRecord electionRecord = consumer.readElectionRecordJson();
    GuardianPublicKeyVerifier kgv = new GuardianPublicKeyVerifier(electionRecord);
    assertThat(kgv.verify_all_guardians()).isTrue();

    for (Guardian coeff : electionRecord.guardians()) {
      boolean kgvOk = kgv.verifyGuardian(coeff);
      assertThat(kgvOk).isTrue();
    }

    for (Guardian coeff : electionRecord.guardians()) {
      verify_equations(electionRecord.generatorP(), coeff);
      verify_challenges(coeff);
    }
  }

  private void verify_equations(Group.ElementModP genP, Guardian coeff) {
    for (SchnorrProof proof : coeff.getCoefficientProofs()) {
      Group.ElementModP commitment = proof.commitment; // h
      Group.ElementModP public_key = proof.publicKey; // k
      Group.ElementModQ challenge = proof.challenge;   // c
      Group.ElementModQ response = proof.response;     // u

      // check equation
      verify_individual_key_computation(genP, response, commitment, public_key, challenge);
    }
  }

  private void verify_challenges(Guardian coeff) {
    for (SchnorrProof proof : coeff.getCoefficientProofs()) {
      Group.ElementModP commitment = proof.commitment; // h
      Group.ElementModP public_key = proof.publicKey; // k
      Group.ElementModQ challenge = proof.challenge;   // c

      // Changed validation spec 2.A. see issue #278
      Group.ElementModQ challenge_computed = Hash.hash_elems(public_key, commitment);
      assertThat(challenge_computed).isEqualTo(challenge);
    }
  }

  /** check the equation = generator ^ response mod p = (commitment * public key ^ challenge) mod p */
  void verify_individual_key_computation(Group.ElementModP genP, Group.ElementModQ response, Group.ElementModP commitment, Group.ElementModP public_key, Group.ElementModQ challenge) {
    Group.ElementModP left = Group.pow_p(genP, response);
    Group.ElementModP right = Group.mult_p(commitment, Group.pow_p(public_key, challenge));
    assertThat(left).isEqualTo(right);
  }
}
