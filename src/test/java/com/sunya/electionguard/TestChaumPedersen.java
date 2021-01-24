package com.sunya.electionguard;

import net.jqwik.api.Example;

import java.util.Optional;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth8.assertThat;
import static com.sunya.electionguard.ChaumPedersen.*;
import static com.sunya.electionguard.Group.*;
import static org.junit.Assert.fail;

public class TestChaumPedersen {

  //// TestDisjunctiveChaumPedersen

  @Example
  public void test_djcp_proofs_simple() {
          // doesn't get any simpler than this
    ElGamal.KeyPair  keypair = ElGamal.elgamal_keypair_from_secret(TWO_MOD_Q).orElseThrow();
    ElementModQ nonce = ONE_MOD_Q;
    ElementModQ seed = TWO_MOD_Q;
    ElGamal.Ciphertext message0 = ElGamal.elgamal_encrypt(0, nonce, keypair.public_key).orElseThrow();

    DisjunctiveChaumPedersenProof proof0 = make_disjunctive_chaum_pedersen(message0, nonce, keypair.public_key, ONE_MOD_Q, seed, 0);
    DisjunctiveChaumPedersenProof proof0bad = make_disjunctive_chaum_pedersen(message0, nonce, keypair.public_key, ONE_MOD_Q, seed, 1);
    
    assertThat(proof0.is_valid(message0, keypair.public_key, ONE_MOD_Q)).isTrue();
    assertThat(proof0bad.is_valid(message0, keypair.public_key, ONE_MOD_Q)).isFalse();

    ElGamal.Ciphertext message1 = ElGamal.elgamal_encrypt(1, nonce, keypair.public_key).orElseThrow();
    DisjunctiveChaumPedersenProof proof1 = make_disjunctive_chaum_pedersen(message1, nonce, keypair.public_key, ONE_MOD_Q, seed, 1);
    DisjunctiveChaumPedersenProof proof1bad = make_disjunctive_chaum_pedersen(message1, nonce, keypair.public_key, ONE_MOD_Q, seed, 0);

    assertThat(proof1.is_valid(message1, keypair.public_key, ONE_MOD_Q)).isTrue();
    assertThat(proof1bad.is_valid(message1, keypair.public_key, ONE_MOD_Q)).isFalse();
  }

  @Example
  public void test_djcp_proof_invalid_inputs() {
    // this is here to push up our coverage
    Optional<ElGamal.KeyPair> keypair = ElGamal.elgamal_keypair_from_secret(TWO_MOD_Q);
    ElementModQ nonce = ONE_MOD_Q;
    ElementModQ seed = TWO_MOD_Q;
    Optional<ElGamal.Ciphertext> message0 = ElGamal.elgamal_encrypt(0, nonce, keypair.orElseThrow().public_key);
    assertThat(message0).isPresent();

    try {
      make_disjunctive_chaum_pedersen(message0.get(), nonce, keypair.get().public_key, seed, seed, 3);
      fail();
    } catch (Exception e) {
      // correct
    }
  }

  //// TestChaumPedersen

  @Example
  public void test_cp_proofs_simple() {
    ElGamal.KeyPair  keypair = ElGamal.elgamal_keypair_from_secret(TWO_MOD_Q).orElseThrow();
    ElementModQ seed = TWO_MOD_Q;
    ElGamal.Ciphertext message = ElGamal.elgamal_encrypt(0, ONE_MOD_Q, keypair.public_key).orElseThrow();
    ElementModP decryption = message.partial_decrypt(keypair.secret_key);

    ChaumPedersenProof proof = make_chaum_pedersen(message, keypair.secret_key, decryption, seed, ONE_MOD_Q);
    ChaumPedersenProof bad_proof = make_chaum_pedersen(message, keypair.secret_key, TWO_MOD_P, seed, ONE_MOD_Q);

    assertThat(proof.is_valid(message, keypair.public_key, decryption, ONE_MOD_Q)).isTrue();
    assertThat(bad_proof.is_valid(message, keypair.public_key, decryption, ONE_MOD_Q)).isFalse();
  }

  // TestConstantChaumPedersen

  @Example
  public void test_ccp_proofs_simple_encryption_of_zero() {
    ElGamal.KeyPair keypair = ElGamal.elgamal_keypair_from_secret(TWO_MOD_Q).orElseThrow();
    ElementModQ nonce = ONE_MOD_Q;
    ElementModQ seed = TWO_MOD_Q;

    ElGamal.Ciphertext message = ElGamal.elgamal_encrypt(0, nonce, keypair.public_key).orElseThrow();

    ConstantChaumPedersenProof proof = make_constant_chaum_pedersen(message, 0, nonce, keypair.public_key, seed, ONE_MOD_Q);
    ConstantChaumPedersenProof bad_proof = make_constant_chaum_pedersen(message, 1, nonce, keypair.public_key, seed, ONE_MOD_Q);

    assertThat(proof.is_valid(message, keypair.public_key, ONE_MOD_Q)).isTrue();
    assertThat(bad_proof.is_valid(message, keypair.public_key, ONE_MOD_Q)).isFalse();
  }

  @Example
  public void test_ccp_proofs_simple_encryption_of_one() {
    ElGamal.KeyPair keypair = ElGamal.elgamal_keypair_from_secret(TWO_MOD_Q).orElseThrow();
    ElementModQ nonce = ONE_MOD_Q;
    ElementModQ seed = TWO_MOD_Q;

    ElGamal.Ciphertext message = ElGamal.elgamal_encrypt(1, nonce, keypair.public_key).orElseThrow();

    ConstantChaumPedersenProof proof = make_constant_chaum_pedersen(message, 1, nonce, keypair.public_key, seed, ONE_MOD_Q);
    ConstantChaumPedersenProof bad_proof = make_constant_chaum_pedersen(message, 0, nonce, keypair.public_key, seed, ONE_MOD_Q);

    assertThat(proof.is_valid(message, keypair.public_key, ONE_MOD_Q)).isTrue();
    assertThat(bad_proof.is_valid(message, keypair.public_key, ONE_MOD_Q)).isFalse();
  }

}
