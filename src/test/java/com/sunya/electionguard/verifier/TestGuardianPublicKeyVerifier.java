package com.sunya.electionguard.verifier;

import com.sunya.electionguard.*;
import com.sunya.electionguard.publish.Consumer;
import net.jqwik.api.Example;
import net.jqwik.api.lifecycle.BeforeContainer;

import java.io.IOException;
import java.math.BigInteger;

import static com.google.common.truth.Truth.assertThat;

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

      Group.ElementModQ challenge_computed = compute_guardian_challenge_threshold_separated(public_key, commitment);
      assertThat(challenge_computed).isEqualTo(challenge);
    }
  }

  /** computes challenge c_i = H(base hash, public key, commitment) % q. */
  private Group.ElementModQ compute_guardian_challenge_threshold_separated(Group.ElementModP public_key, Group.ElementModP commitment) {
    Group.ElementModQ hash = Hash.hash_elems(electionParameters.base_hash(), public_key, commitment);
    BigInteger hashp = hash.getBigInt().mod(Group.P);

    // LOOK LOOK election-verifier-python has
    //  return mod_p(hash_elems(self.base_hash, public_key, commitment))
    //  which i translate as
    //  Group.ElementModQ hash = Hash.hash_elems(electionParameters.base_hash(), public_key, commitment);
    //  return grp.mod_q(hash.getBigInt())
    //  which fails to validate
    //
    //  but this works, see SchnoorProof.make_schnorr_proof
    //  Note that hash does BigInteger biggy = new BigInteger(bytes).mod(Group.Q_MINUS_ONE);
    Group.ElementModQ hash2 = Hash.hash_elems(public_key, commitment);
    // Note that EG.Verifier.Construction has ci=HQ,Ki,0,Ki,1,Ki,2,…,Ki,k-1,hi,0,hi,1,hi,2,…hi,k-1 mod q.
    return hash2;
  }

  /** check the equation = generator ^ response mod p = (commitment * public key ^ challenge) mod p */
  void verify_individual_key_computation(Group.ElementModQ response, Group.ElementModP commitment, Group.ElementModP public_key, Group.ElementModQ challenge) {
    BigInteger left = grp.pow_p(electionParameters.generator(), response.getBigInt());
    BigInteger right = grp.mult_p(commitment.getBigInt(), grp.pow_p(public_key.getBigInt(), challenge.getBigInt()));
    assertThat(left).isEqualTo(right);
  }
}
