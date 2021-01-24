package com.sunya.electionguard.verifier;

import com.sunya.electionguard.*;
import com.sunya.electionguard.publish.Consumer;
import net.jqwik.api.Example;
import net.jqwik.api.lifecycle.BeforeContainer;

import java.io.IOException;
import java.math.BigInteger;

import static com.google.common.truth.Truth.assertThat;

// Box 2
public class TestGuardianPublicKeyVerifier {
  static ElectionParameters electionParameters;
  static GuardianPublicKeyVerifier kgv;
  static Grp grp;

  @BeforeContainer
  public static void setUp() throws IOException {
    String topdir = TestParameterVerifier.topdir;

    Consumer consumer = new Consumer(topdir);
    electionParameters = new ElectionParameters(consumer);
    kgv = new GuardianPublicKeyVerifier(electionParameters);
    grp = new Grp(electionParameters.large_prime(), electionParameters.small_prime());
  }

  @Example
  public void testVerifyAllGuardians() {
    assertThat(kgv.verify_all_guardians()).isTrue();
  }

  @Example
  public void testGuardianPublicKeyValidation() {
    for (KeyCeremony.CoefficientValidationSet coeff : electionParameters.coefficients()) {
      boolean kgvOk = kgv.verify_one_guardian(coeff);
      assertThat(kgvOk).isTrue();
    }
  }

  @Example
  public void testVerifyOneGuardian() {
    for (KeyCeremony.CoefficientValidationSet coeff : electionParameters.coefficients()) {
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

      // LOOK withdraw correct hash until python code to catches up
      //   Group.ElementModQ challenge_computed = Hash.hash_elems(electionParameters.base_hash(), public_key, commitment);
      Group.ElementModQ challenge_computed = Hash.hash_elems(public_key, commitment);
      assertThat(challenge_computed).isEqualTo(challenge);
    }
  }

  /** check the equation = generator ^ response mod p = (commitment * public key ^ challenge) mod p */
  void verify_individual_key_computation(Group.ElementModQ response, Group.ElementModP commitment, Group.ElementModP public_key, Group.ElementModQ challenge) {
    BigInteger left = grp.pow_p(electionParameters.generator(), response.getBigInt());
    BigInteger right = grp.mult_p(commitment.getBigInt(), grp.pow_p(public_key.getBigInt(), challenge.getBigInt()));
    assertThat(left).isEqualTo(right);
  }
}
