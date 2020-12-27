package com.sunya.electionguard;

import net.jqwik.api.ForAll;
import net.jqwik.api.Property;

import static com.google.common.truth.Truth.assertThat;
import static com.sunya.electionguard.Group.*;
import static com.sunya.electionguard.SchnorrProof.*;

public class TestSchnorrProperties extends TestProperties {

  @Property
  public void test_schnorr_proofs_simple() {
    //doesn 't get any simpler than this
    ElGamal.KeyPair keypair = ElGamal.elgamal_keypair_from_secret(TWO_MOD_Q).get();
    Group.ElementModQ nonce = ONE_MOD_Q;
    SchnorrProof proof = make_schnorr_proof(keypair, nonce);
    assertThat(proof.is_valid()).isTrue();
  }

  @Property
  public void test_schnorr_proofs_valid(
          @ForAll("elgamal_keypairs") ElGamal.KeyPair keypair,
          @ForAll("elements_mod_q") Group.ElementModQ nonce) {

    SchnorrProof proof = make_schnorr_proof(keypair, nonce);
    assertThat(proof.is_valid()).isTrue();
  }

  //Now, we introduce errors in the proofs and make sure that they fail to verify
  @Property
  public void test_schnorr_proofs_invalid_u(
          @ForAll("elgamal_keypairs") ElGamal.KeyPair keypair,
          @ForAll("elements_mod_q") Group.ElementModQ nonce,
          @ForAll("elements_mod_q") Group.ElementModQ other) {

    SchnorrProof proof = make_schnorr_proof(keypair, nonce);
    // assume(other != proof.response);
    SchnorrProof proof2 = new SchnorrProof(proof.public_key, proof.commitment, proof.challenge, other);
    assertThat(proof2.is_valid()).isFalse();
  }

  @Property
  public void test_schnorr_proofs_invalid_h(
          @ForAll("elgamal_keypairs") ElGamal.KeyPair keypair,
          @ForAll("elements_mod_q") Group.ElementModQ nonce,
          @ForAll("elements_mod_p_no_zero") Group.ElementModP other) {

    SchnorrProof proof = make_schnorr_proof(keypair, nonce);
    // assume(other != proof.commitment);
    SchnorrProof proof_bad = new SchnorrProof(proof.public_key, other, proof.challenge, proof.response);
    assertThat(proof_bad.is_valid()).isFalse();
  }

  @Property
  public void test_schnorr_proofs_invalid_public_key(
          @ForAll("elgamal_keypairs") ElGamal.KeyPair keypair,
          @ForAll("elements_mod_q") Group.ElementModQ nonce,
          @ForAll("elements_mod_p_no_zero") Group.ElementModP other) {

    SchnorrProof proof = make_schnorr_proof(keypair, nonce);
    // assume(other != proof.public_key);
    SchnorrProof proof2 = new SchnorrProof(other, proof.commitment, proof.challenge, proof.response);
    assertThat(proof2.is_valid()).isFalse();
  }

  @Property
  public void test_schnorr_proofs_bounds_checking(
          @ForAll("elgamal_keypairs") ElGamal.KeyPair keypair,
          @ForAll("elements_mod_q") Group.ElementModQ nonce) {
    SchnorrProof proof = make_schnorr_proof(keypair, nonce);
    SchnorrProof proof2 = new SchnorrProof(ZERO_MOD_P, proof.commitment, proof.challenge, proof.response);
    SchnorrProof proof3 = new SchnorrProof(int_to_p_unchecked(P), proof.commitment, proof.challenge, proof.response);
    assertThat(proof2.is_valid()).isFalse();
    assertThat(proof3.is_valid()).isFalse();
  }

}