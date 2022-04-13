package com.sunya.electionguard.core;

import com.sunya.electionguard.ChaumPedersen;
import com.sunya.electionguard.ElGamal;
import com.sunya.electionguard.Group;
import com.sunya.electionguard.TestUtils;
import com.sunya.electionguard.core.ChaumPedersenKt.ConstantChaumPedersenProofKnownNonce;
import net.jqwik.api.Example;

import static com.google.common.truth.Truth.assertThat;
import static com.sunya.electionguard.ChaumPedersen.make_constant_chaum_pedersen;
import static com.sunya.electionguard.Group.TWO_MOD_Q;

public class TestChaumPedersenKt {

  @Example
  public void testConstantChaumPedersenProofKnownNonce() {
    ElGamal.KeyPair keypair = TestUtils.elgamal_keypairs();

    int constant = 42;
    Group.ElementModQ nonce = TestUtils.elements_mod_q_no_zero();
    Group.ElementModQ seed = TestUtils.elements_mod_q();
    Group.ElementModQ hashHeader = TestUtils.elements_mod_q_no_zero();
    ElGamalKt.Ciphertext message = ElGamalKt.encryptVer2(constant, keypair.public_key(), nonce);

    ConstantChaumPedersenProofKnownNonce proof = ChaumPedersenKt.constantChaumPedersenProofKnownNonceOf(
            message,
            constant,
            nonce,
            keypair.public_key(),
            seed,
            hashHeader);

    System.out.printf(" ======================================================= %n");
    assertThat(proof.isValid(
            message,
            keypair.public_key(),
            hashHeader,
            constant)).isTrue();
  }

  @Example
  public void oneKnown() {
      int constant = 42;
      Group.ElementModP publicKey = Group.ONE_MOD_P;
      Group.ElementModQ nonce = Group.TWO_MOD_Q;
      Group.ElementModQ seed = Group.TWO_MOD_Q;
      Group.ElementModQ hashHeader = Group.ONE_MOD_Q;
      ElGamalKt.Ciphertext message = ElGamalKt.encryptVer2(constant, publicKey, nonce);

      ConstantChaumPedersenProofKnownNonce proof = ChaumPedersenKt.constantChaumPedersenProofKnownNonceOf(
              message,
              constant,
              nonce,
              publicKey,
              seed,
              hashHeader
              );

    assertThat(
              proof.isValid(
                      message,
                      publicKey,
                      hashHeader,
                      constant
              )).isTrue();
  }

  @Example
  public void testVer1() {
    int constant = 42;
    ElGamal.KeyPair keypair = ElGamal.elgamal_keypair_from_secret(TWO_MOD_Q).orElseThrow();
    Group.ElementModQ nonce = TWO_MOD_Q;
    Group.ElementModQ seed = TWO_MOD_Q;
    Group.ElementModQ hashHeader = Group.ONE_MOD_Q;

    ElGamal.Ciphertext message = ElGamal.elgamal_encrypt_ver1(constant, nonce, keypair.public_key()).orElseThrow();
    ElGamalKt.Ciphertext messageKt = ElGamalKt.encryptVer2(constant, keypair.public_key(), nonce);

    ElGamal.Ciphertext messageVer1 = new ElGamal.Ciphertext(messageKt.pad(), messageKt.data());
    assertThat(message).isNotEqualTo(messageVer1);

    ChaumPedersen.ConstantChaumPedersenProof proof =
            make_constant_chaum_pedersen(message, constant, nonce, keypair.public_key(), seed, hashHeader);

    assertThat(proof.is_valid(message, keypair.public_key(), hashHeader)).isTrue();
  }

  @Example
  public void testFromVer1() {
    int constant = 42;
    ElGamalKt.KeyPair keypair = ElGamalKt.elGamalKeyPairFromRandom();
    Group.ElementModP publicKey = keypair.public_key();
    Group.ElementModQ nonce = Group.TWO_MOD_Q;
    Group.ElementModQ seed = Group.TWO_MOD_Q;
    Group.ElementModQ hashHeader = Group.ONE_MOD_Q;
    ElGamal.Ciphertext messageVer1 = ElGamal.elgamal_encrypt_ver1(constant, nonce, keypair.public_key()).orElseThrow();

    ChaumPedersen.ConstantChaumPedersenProof proofver1 =
            ChaumPedersen.make_constant_chaum_pedersen(
                    messageVer1,
                    constant,
                    nonce,
                    publicKey,
                    seed,
                    hashHeader);

    System.out.printf(" ======================================================= %n");
    ChaumPedersen.ConstantChaumPedersenProof proofver2 =
            new ChaumPedersen.ConstantChaumPedersenProof(proofver1.challenge, proofver1.response, constant);

    // hash fails to agree
    assertThat(
            proofver2.is_valid(
                    messageVer1,
                    publicKey,
                    hashHeader
            )).isFalse();
  }

  @Example
  public void testFromVer2() {
    int constant = 42;
    ElGamalKt.KeyPair keypair = ElGamalKt.elGamalKeyPairFromSecret(Group.TWO_MOD_Q);
    Group.ElementModP publicKey = keypair.public_key();
    Group.ElementModQ nonce = Group.TWO_MOD_Q;
    Group.ElementModQ seed = Group.TWO_MOD_Q;
    Group.ElementModQ hashHeader = Group.ONE_MOD_Q;
    ElGamalKt.Ciphertext message = ElGamalKt.encryptVer2(constant, publicKey, nonce);
    ElGamal.Ciphertext messageVer2 = new ElGamal.Ciphertext(message.pad(), message.data());

    ConstantChaumPedersenProofKnownNonce proof2 = ChaumPedersenKt.constantChaumPedersenProofKnownNonceOf(
            message,
            constant,
            nonce,
            publicKey,
            seed,
            hashHeader
    );

    System.out.printf(" ======================================================= %n");
    ChaumPedersen.ConstantChaumPedersenProof proof1 =
            new ChaumPedersen.ConstantChaumPedersenProof(proof2.proof().c(), proof2.proof().r(), constant);

    // success
    assertThat(
            proof1.is_valid(
                    messageVer2,
                    publicKey,
                    hashHeader
            )).isTrue();
  }
}
