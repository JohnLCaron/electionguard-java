package com.sunya.electionguard;

import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.IntRange;

import java.math.BigInteger;

import static com.google.common.truth.Truth.assertThat;
import static com.sunya.electionguard.ChaumPedersen.*;
import static com.sunya.electionguard.Group.*;

public class TestChaumPedersenProperties extends TestProperties {

  //// TestDisjunctiveChaumPedersen

  @Property
  public void test_djcp_proof_zero(
          @ForAll("elgamal_keypairs") ElGamal.KeyPair keypair,
          @ForAll("elements_mod_q_no_zero") ElementModQ nonce,
          @ForAll("elements_mod_q") ElementModQ seed) {

    ElGamal.Ciphertext message = ElGamal.elgamal_encrypt(0, nonce, keypair.public_key).orElseThrow();
    DisjunctiveChaumPedersenProof proof = make_disjunctive_chaum_pedersen_zero(
            message, nonce, keypair.public_key, ONE_MOD_Q, seed);
    DisjunctiveChaumPedersenProof proof_bad = make_disjunctive_chaum_pedersen_one(message, nonce, keypair.public_key, ONE_MOD_Q, seed);

    assertThat(proof.is_valid(message, keypair.public_key, ONE_MOD_Q)).isTrue();
    assertThat(proof_bad.is_valid(message, keypair.public_key, ONE_MOD_Q)).isFalse();
  }

  @Property
  public void test_djcp_proof_one(
          @ForAll("elgamal_keypairs") ElGamal.KeyPair keypair,
          @ForAll("elements_mod_q_no_zero") ElementModQ nonce,
          @ForAll("elements_mod_q") ElementModQ seed) {

    ElGamal.Ciphertext message = ElGamal.elgamal_encrypt(1, nonce, keypair.public_key).orElseThrow();
    DisjunctiveChaumPedersenProof proof = make_disjunctive_chaum_pedersen_one(
            message, nonce, keypair.public_key, ONE_MOD_Q, seed);
    DisjunctiveChaumPedersenProof proof_bad = make_disjunctive_chaum_pedersen_zero(message, nonce, keypair.public_key, ONE_MOD_Q, seed);

    assertThat(proof.is_valid(message, keypair.public_key, ONE_MOD_Q)).isTrue();
    assertThat(proof_bad.is_valid(message, keypair.public_key, ONE_MOD_Q)).isFalse();
  }

  @Property
  public void test_djcp_proof_broken(
          @ForAll("elgamal_keypairs") ElGamal.KeyPair keypair,
          @ForAll("elements_mod_q_no_zero") ElementModQ nonce,
          @ForAll("elements_mod_q") ElementModQ seed) {

    // verify two different ways to generate an invalid C-P proof.
    ElGamal.Ciphertext message = ElGamal.elgamal_encrypt(0, nonce, keypair.public_key).orElseThrow();
    ElGamal.Ciphertext message_bad = ElGamal.elgamal_encrypt(2, nonce, keypair.public_key).orElseThrow();

    DisjunctiveChaumPedersenProof proof = make_disjunctive_chaum_pedersen_zero(message, nonce, keypair.public_key, ONE_MOD_Q, seed);
    DisjunctiveChaumPedersenProof proof_bad = make_disjunctive_chaum_pedersen_zero(message_bad, nonce, keypair.public_key, ONE_MOD_Q, seed);

    assertThat(proof_bad.is_valid(message_bad, keypair.public_key, ONE_MOD_Q)).isFalse();
    assertThat(proof.is_valid(message_bad, keypair.public_key, ONE_MOD_Q)).isFalse();
  }

  //// TestChaumPedersen

  @Property(tries = 100)
  public void test_cp_proof(
          @ForAll("elgamal_keypairs") ElGamal.KeyPair keypair,
          @ForAll("elements_mod_q_no_zero") ElementModQ nonce,
          @ForAll("elements_mod_q") ElementModQ seed,
          @ForAll @IntRange(min = 0, max = 100) int constant,
          @ForAll @IntRange(min = 0, max = 100) int bad_constant
  ) {

    if (constant == bad_constant) {
      bad_constant = constant + 1;
    }
    ElementModP badP = Group.int_to_p_unchecked(BigInteger.valueOf(bad_constant));
    ElGamal.Ciphertext message = ElGamal.elgamal_encrypt(constant, nonce, keypair.public_key).orElseThrow();
    ElementModP decryption = message.partial_decrypt(keypair.secret_key);
    ChaumPedersenProof proof = make_chaum_pedersen(message, keypair.secret_key, decryption, seed, ONE_MOD_Q);
    ChaumPedersenProof bad_proof = make_chaum_pedersen(message, keypair.secret_key, badP, seed, ONE_MOD_Q);

    assertThat(proof.is_valid(message, keypair.public_key, decryption, ONE_MOD_Q)).isTrue();
    assertThat(bad_proof.is_valid(message, keypair.public_key, decryption, ONE_MOD_Q)).isFalse();
  }

  // TestConstantChaumPedersen

  @Property(tries = 100)
  public void test_ccp_proof(
          @ForAll("elgamal_keypairs") ElGamal.KeyPair keypair,
          @ForAll("elements_mod_q_no_zero") ElementModQ nonce,
          @ForAll("elements_mod_q") ElementModQ seed,
          @ForAll @IntRange(min = 0, max = 100) int constant,
          @ForAll @IntRange(min = 0, max = 100) int bad_constant) {

    if (constant == bad_constant) {
      bad_constant = constant + 1;
    }
    ElGamal.Ciphertext message = ElGamal.elgamal_encrypt(constant, nonce, keypair.public_key).orElseThrow();
    ElGamal.Ciphertext message_bad = ElGamal.elgamal_encrypt(bad_constant, nonce, keypair.public_key).orElseThrow();

    ConstantChaumPedersenProof proof = make_constant_chaum_pedersen(message, constant, nonce, keypair.public_key, seed, ONE_MOD_Q);
    assertThat(proof.is_valid(message, keypair.public_key, ONE_MOD_Q)).isTrue();

    ConstantChaumPedersenProof proof_bad1 = make_constant_chaum_pedersen(message_bad, constant, nonce, keypair.public_key, seed, ONE_MOD_Q);
    assertThat(proof_bad1.is_valid(message_bad, keypair.public_key, ONE_MOD_Q)).isFalse();

    ConstantChaumPedersenProof proof_bad2 = make_constant_chaum_pedersen(message, bad_constant, nonce, keypair.public_key, seed, ONE_MOD_Q);
    assertThat(proof_bad2.is_valid(message, keypair.public_key, ONE_MOD_Q)).isFalse();

    ConstantChaumPedersenProof proof_bad3 = new ConstantChaumPedersenProof(proof.pad, proof.data, proof.challenge, proof.response, -1);
    assertThat(proof_bad3.is_valid(message, keypair.public_key, ONE_MOD_Q)).isFalse();
  }

}
