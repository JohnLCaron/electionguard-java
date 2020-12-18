package com.sunya.electionguard;

import org.junit.Test;

import static com.google.common.truth.Truth.assertThat;
import static com.sunya.electionguard.ChaumPedersen.*;
import static com.sunya.electionguard.Group.*;

public class TestChaumPedersen {

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

}
