package com.sunya.electionguard;

import net.jqwik.api.Example;

import java.util.Optional;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth8.assertThat;
import static com.sunya.electionguard.ChaumPedersen.*;
import static com.sunya.electionguard.Group.*;
import static org.junit.Assert.assertThrows;

public class TestChaumPedersen {

  //// TestDisjunctiveChaumPedersen

  @Example
  public void test_djcp_proofs_simple() {
          // doesn't get any simpler than this
    ElGamal.KeyPair  keypair = ElGamal.elgamal_keypair_from_secret(TWO_MOD_Q).orElseThrow();
    ElementModQ nonce = ONE_MOD_Q;
    ElementModQ seed = TWO_MOD_Q;
    ElGamal.Ciphertext message0 = ElGamal.elgamal_encrypt_ver1(0, nonce, keypair.public_key()).orElseThrow();

    DisjunctiveChaumPedersenProof proof0 = make_disjunctive_chaum_pedersen(message0, nonce, keypair.public_key(), ONE_MOD_Q, seed, 0);
    DisjunctiveChaumPedersenProof proof0bad = make_disjunctive_chaum_pedersen(message0, nonce, keypair.public_key(), ONE_MOD_Q, seed, 1);
    
    assertThat(proof0.is_valid(message0, keypair.public_key(), ONE_MOD_Q)).isTrue();
    assertThrows(IllegalStateException.class, () -> proof0bad.is_valid(message0, keypair.public_key(), ONE_MOD_Q));

    ElGamal.Ciphertext message1 = ElGamal.elgamal_encrypt_ver1(1, nonce, keypair.public_key()).orElseThrow();
    DisjunctiveChaumPedersenProof proof1 = make_disjunctive_chaum_pedersen(message1, nonce, keypair.public_key(), ONE_MOD_Q, seed, 1);
    DisjunctiveChaumPedersenProof proof1bad = make_disjunctive_chaum_pedersen(message1, nonce, keypair.public_key(), ONE_MOD_Q, seed, 0);

    assertThat(proof1.is_valid(message1, keypair.public_key(), ONE_MOD_Q)).isTrue();
    assertThrows(IllegalStateException.class, () -> proof1bad.is_valid(message1, keypair.public_key(), ONE_MOD_Q));
  }

  @Example
  public void test_djcp_proof_invalid_inputs() {
    // this is here to push up our coverage
    Optional<ElGamal.KeyPair> keypair = ElGamal.elgamal_keypair_from_secret(TWO_MOD_Q);
    ElementModQ nonce = ONE_MOD_Q;
    ElementModQ seed = TWO_MOD_Q;
    Optional<ElGamal.Ciphertext> message0 = ElGamal.elgamal_encrypt_ver1(0, nonce, keypair.orElseThrow().public_key());
    assertThat(message0).isPresent();

    assertThrows(IllegalArgumentException.class, () -> make_disjunctive_chaum_pedersen(message0.get(), nonce, keypair.get().public_key(), seed, seed, 3));
  }

  //// TestChaumPedersen

  @Example
  public void test_cp_proofs_simple() {
    ElGamal.KeyPair keypair = ElGamal.elgamal_keypair_from_secret(Group.rand_q()).orElseThrow();
    ElementModQ extended_base_hash = Group.rand_q();
    ElementModQ seed = Group.rand_q();
    ElGamal.Ciphertext text = ElGamal.elgamal_encrypt_ver1(42, Group.rand_q(), keypair.public_key()).orElseThrow();
    ElementModP partial_decryption = text.partial_decrypt(keypair.secret_key());

    ChaumPedersenProof proof = make_chaum_pedersen(text, keypair.secret_key(), partial_decryption, seed, extended_base_hash);
    ChaumPedersenProof bad_proof = make_chaum_pedersen(text, keypair.secret_key(), TWO_MOD_P, seed, extended_base_hash);

    assertThat(proof.is_valid1(text, keypair.public_key(), partial_decryption, extended_base_hash)).isTrue();
    assertThat(bad_proof.is_valid1(text, keypair.public_key(), partial_decryption, extended_base_hash)).isFalse();
  }

  // TestConstantChaumPedersen

  @Example
  public void test_ccp_proofs_simple_encryption_of_zero() {
    ElGamal.KeyPair keypair = ElGamal.elgamal_keypair_from_secret(TWO_MOD_Q).orElseThrow();
    ElementModQ nonce = ONE_MOD_Q;
    ElementModQ seed = TWO_MOD_Q;

    ElGamal.Ciphertext message = ElGamal.elgamal_encrypt_ver1(0, nonce, keypair.public_key()).orElseThrow();

    ConstantChaumPedersenProof proof = make_constant_chaum_pedersen(message, 0, nonce, keypair.public_key(), seed, ONE_MOD_Q);
    ConstantChaumPedersenProof bad_proof = make_constant_chaum_pedersen(message, 1, nonce, keypair.public_key(), seed, ONE_MOD_Q);

    assertThat(proof.is_valid(message, keypair.public_key(), ONE_MOD_Q)).isTrue();
    assertThrows(IllegalStateException.class, () -> bad_proof.is_valid(message, keypair.public_key(), ONE_MOD_Q));
  }

  @Example
  public void test_ccp_proofs_simple_encryption_of_one() {
    ElGamal.KeyPair keypair = ElGamal.elgamal_keypair_from_secret(TWO_MOD_Q).orElseThrow();
    ElementModQ nonce = ONE_MOD_Q;
    ElementModQ seed = TWO_MOD_Q;

    ElGamal.Ciphertext message = ElGamal.elgamal_encrypt_ver1(1, nonce, keypair.public_key()).orElseThrow();

    ConstantChaumPedersenProof proof = make_constant_chaum_pedersen(message, 1, nonce, keypair.public_key(), seed, ONE_MOD_Q);
    ConstantChaumPedersenProof bad_proof = make_constant_chaum_pedersen(message, 0, nonce, keypair.public_key(), seed, ONE_MOD_Q);

    assertThat(proof.is_valid(message, keypair.public_key(), ONE_MOD_Q)).isTrue();
    assertThrows(IllegalStateException.class, () -> bad_proof.is_valid(message, keypair.public_key(), ONE_MOD_Q));
  }

}
