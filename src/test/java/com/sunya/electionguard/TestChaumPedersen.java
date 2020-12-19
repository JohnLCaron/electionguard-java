package com.sunya.electionguard;

import org.junit.Test;

import java.util.Optional;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth8.assertThat;
import static com.sunya.electionguard.ChaumPedersen.*;
import static com.sunya.electionguard.Group.*;
import static org.junit.Assert.fail;

public class TestChaumPedersen {

  //// TestDisjunctiveChaumPedersen

  @Test
  public void test_djcp_proofs_simple() {
          // doesn't get any simpler than this
    ElGamal.KeyPair  keypair = ElGamal.elgamal_keypair_from_secret(TWO_MOD_Q).get();
    ElementModQ nonce = ONE_MOD_Q;
    ElementModQ seed = TWO_MOD_Q;
    ElGamal.Ciphertext message0 = ElGamal.elgamal_encrypt(0, nonce, keypair.public_key).get();

    DisjunctiveChaumPedersenProof proof0 = make_disjunctive_chaum_pedersen(message0, nonce, keypair.public_key, ONE_MOD_Q, seed, 0);
    DisjunctiveChaumPedersenProof proof0bad = make_disjunctive_chaum_pedersen(message0, nonce, keypair.public_key, ONE_MOD_Q, seed, 1);
    
    assertThat(proof0.is_valid(message0, keypair.public_key, ONE_MOD_Q)).isTrue();
    assertThat(proof0bad.is_valid(message0, keypair.public_key, ONE_MOD_Q)).isFalse();

    ElGamal.Ciphertext message1 = ElGamal.elgamal_encrypt(1, nonce, keypair.public_key).get();
    DisjunctiveChaumPedersenProof proof1 = make_disjunctive_chaum_pedersen(message1, nonce, keypair.public_key, ONE_MOD_Q, seed, 1);
    DisjunctiveChaumPedersenProof proof1bad = make_disjunctive_chaum_pedersen(message1, nonce, keypair.public_key, ONE_MOD_Q, seed, 0);

    assertThat(proof1.is_valid(message1, keypair.public_key, ONE_MOD_Q)).isTrue();
    assertThat(proof1bad.is_valid(message1, keypair.public_key, ONE_MOD_Q)).isFalse();
  }

  @Test
  public void test_djcp_proof_invalid_inputs() {
    // this is here to push up our coverage
    Optional<ElGamal.KeyPair> keypair = ElGamal.elgamal_keypair_from_secret(TWO_MOD_Q);
    ElementModQ nonce = ONE_MOD_Q;
    ElementModQ seed = TWO_MOD_Q;
    Optional<ElGamal.Ciphertext> message0 = ElGamal.elgamal_encrypt(0, nonce, keypair.get().public_key);
    assertThat(message0).isPresent();

    try {
      make_disjunctive_chaum_pedersen(message0.get(), nonce, keypair.get().public_key, seed, seed, 3);
      fail();
    } catch (Exception e) {
      // correct
    }
  }

  @Test
  public void test_djcp_proof_zero() {
    ElGamal.KeyPair keypair = TestUtils.elgamal_keypairs();
    ElementModQ nonce = TestUtils.elements_mod_q_no_zero();
    ElementModQ seed = TestUtils.elements_mod_q();

    ElGamal.Ciphertext message = ElGamal.elgamal_encrypt(0, nonce, keypair.public_key).get();
    DisjunctiveChaumPedersenProof proof = make_disjunctive_chaum_pedersen_zero(
            message, nonce, keypair.public_key, ONE_MOD_Q, seed);
    DisjunctiveChaumPedersenProof proof_bad = make_disjunctive_chaum_pedersen_one(message, nonce, keypair.public_key, ONE_MOD_Q, seed);

    assertThat(proof.is_valid(message, keypair.public_key, ONE_MOD_Q)).isTrue();
    assertThat(proof_bad.is_valid(message, keypair.public_key, ONE_MOD_Q)).isFalse();
  }

  @Test
  public void test_djcp_proof_one() {
    ElGamal.KeyPair keypair = TestUtils.elgamal_keypairs();
    ElementModQ nonce = TestUtils.elements_mod_q_no_zero();
    ElementModQ seed = TestUtils.elements_mod_q();

    ElGamal.Ciphertext message = ElGamal.elgamal_encrypt(1, nonce, keypair.public_key).get();

    DisjunctiveChaumPedersenProof proof = make_disjunctive_chaum_pedersen_one(
            message, nonce, keypair.public_key, ONE_MOD_Q, seed);
    DisjunctiveChaumPedersenProof proof_bad = make_disjunctive_chaum_pedersen_zero(message, nonce, keypair.public_key, ONE_MOD_Q, seed);

    assertThat(proof.is_valid(message, keypair.public_key, ONE_MOD_Q)).isTrue();
    assertThat(proof_bad.is_valid(message, keypair.public_key, ONE_MOD_Q)).isFalse();
  }

  @Test
  public void test_djcp_proof_broken() {
    ElGamal.KeyPair keypair = TestUtils.elgamal_keypairs();
    ElementModQ nonce = TestUtils.elements_mod_q_no_zero();
    ElementModQ seed = TestUtils.elements_mod_q();

    // verify two different ways to generate an invalid C-P proof.
    ElGamal.Ciphertext message = ElGamal.elgamal_encrypt(0, nonce, keypair.public_key).get();
    ElGamal.Ciphertext message_bad = ElGamal.elgamal_encrypt(2, nonce, keypair.public_key).get();

    DisjunctiveChaumPedersenProof proof = make_disjunctive_chaum_pedersen_zero(message, nonce, keypair.public_key, ONE_MOD_Q, seed);
    DisjunctiveChaumPedersenProof proof_bad = make_disjunctive_chaum_pedersen_zero(message_bad, nonce, keypair.public_key, ONE_MOD_Q, seed);

    assertThat(proof_bad.is_valid(message_bad, keypair.public_key, ONE_MOD_Q)).isFalse();
    assertThat(proof.is_valid(message_bad, keypair.public_key, ONE_MOD_Q)).isFalse();
  }

  //// TestChaumPedersen

  @Test
  public void test_cp_proofs_simple() {
    ElGamal.KeyPair  keypair = ElGamal.elgamal_keypair_from_secret(TWO_MOD_Q).get();
    ElementModQ nonce = ONE_MOD_Q;
    ElementModQ seed = TWO_MOD_Q;
    ElGamal.Ciphertext message = ElGamal.elgamal_encrypt(0, nonce, keypair.public_key).get();
    ElementModP decryption = message.partial_decrypt(keypair.secret_key);

    ChaumPedersenProof proof = make_chaum_pedersen(message, keypair.secret_key, decryption, seed, ONE_MOD_Q);
    ChaumPedersenProof bad_proof = make_chaum_pedersen(message, keypair.secret_key, TWO_MOD_P, seed, ONE_MOD_Q);

    assertThat(proof.is_valid(message, keypair.public_key, decryption, ONE_MOD_Q)).isTrue();
    assertThat(bad_proof.is_valid(message, keypair.public_key, decryption, ONE_MOD_Q)).isFalse();
  }

  @Test
  public void test_cp_proof() {
    ElGamal.KeyPair keypair = TestUtils.elgamal_keypairs();
    ElementModQ nonce = TestUtils.elements_mod_q_no_zero();
    ElementModQ seed = TestUtils.elements_mod_q();

    for (int constant = 0; constant < 100; constant+=11) {
      for (int bad_constant = 0; bad_constant < 100; bad_constant+=13) {
        if (constant == bad_constant) {
          bad_constant = constant + 1;
        }
        ElGamal.Ciphertext message = ElGamal.elgamal_encrypt(constant, nonce, keypair.public_key).get();
        ElementModP decryption = message.partial_decrypt(keypair.secret_key);
        ChaumPedersenProof proof = make_chaum_pedersen(message, keypair.secret_key, decryption, seed, ONE_MOD_Q);
        ChaumPedersenProof bad_proof = make_chaum_pedersen(message, keypair.secret_key, TWO_MOD_P, seed, ONE_MOD_Q);

        assertThat(proof.is_valid(message, keypair.public_key, decryption, ONE_MOD_Q)).isTrue();
        assertThat(bad_proof.is_valid(message, keypair.public_key, decryption, ONE_MOD_Q)).isFalse();
      }
    }
  }

  // TestConstantChaumPedersen

  @Test
  public void test_ccp_proofs_simple_encryption_of_zero() {
    ElGamal.KeyPair keypair = ElGamal.elgamal_keypair_from_secret(TWO_MOD_Q).get();
    ElementModQ nonce = ONE_MOD_Q;
    ElementModQ seed = TWO_MOD_Q;

    ElGamal.Ciphertext message = ElGamal.elgamal_encrypt(0, nonce, keypair.public_key).get();

    ConstantChaumPedersenProof proof = make_constant_chaum_pedersen(message, 0, nonce, keypair.public_key, seed, ONE_MOD_Q);
    ConstantChaumPedersenProof bad_proof = make_constant_chaum_pedersen(message, 1, nonce, keypair.public_key, seed, ONE_MOD_Q);

    assertThat(proof.is_valid(message, keypair.public_key, ONE_MOD_Q)).isTrue();
    assertThat(bad_proof.is_valid(message, keypair.public_key, ONE_MOD_Q)).isFalse();
  }

  @Test
  public void test_ccp_proofs_simple_encryption_of_one() {
    ElGamal.KeyPair keypair = ElGamal.elgamal_keypair_from_secret(TWO_MOD_Q).get();
    ElementModQ nonce = ONE_MOD_Q;
    ElementModQ seed = TWO_MOD_Q;

    ElGamal.Ciphertext message = ElGamal.elgamal_encrypt(1, nonce, keypair.public_key).get();

    ConstantChaumPedersenProof proof = make_constant_chaum_pedersen(message, 1, nonce, keypair.public_key, seed, ONE_MOD_Q);
    ConstantChaumPedersenProof bad_proof = make_constant_chaum_pedersen(message, 0, nonce, keypair.public_key, seed, ONE_MOD_Q);

    assertThat(proof.is_valid(message, keypair.public_key, ONE_MOD_Q)).isTrue();
    assertThat(bad_proof.is_valid(message, keypair.public_key, ONE_MOD_Q)).isFalse();
  }

  @Test
  public void test_ccp_proof() {
    ElGamal.KeyPair keypair = TestUtils.elgamal_keypairs();
    ElementModQ nonce = TestUtils.elements_mod_q_no_zero();
    ElementModQ seed = TestUtils.elements_mod_q();

    for (int constant = 0; constant < 100; constant+=11) {
      for (int bad_constant = 0; bad_constant < 100; bad_constant+=13) {
        if (constant == bad_constant) {
          bad_constant = constant + 1;
        }
        ElGamal.Ciphertext message = ElGamal.elgamal_encrypt(constant, nonce, keypair.public_key).get();
        ElGamal.Ciphertext message_bad = ElGamal.elgamal_encrypt(bad_constant, nonce, keypair.public_key).get();

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
  }


}
