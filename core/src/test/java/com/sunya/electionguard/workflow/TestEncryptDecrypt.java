package com.sunya.electionguard.workflow;

import com.sunya.electionguard.CiphertextBallot;
import com.sunya.electionguard.Dlog;
import com.sunya.electionguard.ElGamal;
import com.sunya.electionguard.ElectionCryptoContext;
import com.sunya.electionguard.Encrypt;
import com.sunya.electionguard.Group;
import com.sunya.electionguard.InternalManifest;
import com.sunya.electionguard.PlaintextBallot;
import com.sunya.electionguard.publish.Consumer;
import com.sunya.electionguard.publish.ElectionRecord;
import com.sunya.electionguard.verifier.TestParameterVerifier;
import net.jqwik.api.Example;

import java.io.IOException;
import java.math.BigInteger;
import java.util.List;
import java.util.Optional;

import static com.google.common.truth.Truth.assertThat;
import static com.sunya.electionguard.ElGamal.elgamal_add;
import static com.sunya.electionguard.ElGamal.elgamal_keypair_from_secret;

public class TestEncryptDecrypt {
  String input = TestParameterVerifier.topdirEncryptor;

  @Example
  public void testEncryption() throws IOException {
    Consumer consumer = new Consumer(input);
    ElectionRecord electionRecord = consumer.readElectionRecord();
    PlaintextBallot inputBallot = consumer.iterateInputBallots(input + "/election_private_data",
                    b -> b.object_id().equals("ballot-id-641736708"))
            .iterator().next();
    assertThat(inputBallot).isNotNull();

    ElGamal.KeyPair keypair = elgamal_keypair_from_secret(Group.TWO_MOD_Q).orElseThrow();

    // int numberOfGuardians, int quorum, Group.ElementModP jointPublicKey,
    //                               Group.ElementModQ manifestHash, Group.ElementModQ cryptoBaseHash,
    //                               Group.ElementModQ cryptoExtendedBaseHash, Group.ElementModQ commitmentHash,
    //                               @Nullable Map<String, String> extended_data)
    ElectionCryptoContext context = new ElectionCryptoContext(1, 1, keypair.public_key(), electionRecord.manifest().cryptoHash(),
            Group.TWO_MOD_Q, Group.TWO_MOD_Q, Group.TWO_MOD_Q, null);
    System.out.printf("electionRecord.manifest.cryptoHash() %s%n", electionRecord.manifest().cryptoHash());

    //   public static Optional<CiphertextBallot> encrypt_ballot(
    //          PlaintextBallot ballot,
    //          InternalManifest internal_manifest,
    //          ElectionContext context,
    //          ElementModQ encryption_seed,
    //          Optional<ElementModQ> nonce,
    //          boolean should_verify_proofs)
    InternalManifest manifest = new InternalManifest(electionRecord.manifest());
    CiphertextBallot result = Encrypt.encrypt_ballot(inputBallot, manifest, context,
            Group.TWO_MOD_Q, Optional.of(Group.TWO_MOD_Q), false).orElseThrow();

    System.out.printf("result = %s nonce %s%n", result.crypto_hash, result.hashed_ballot_nonce());
    // not really a point to this
    Group.ElementModQ expected = Group.hex_to_q_unchecked("AD939754E9242C9FB516A7C21940274F7810E454BCDC66B1CCE3FB1F0A38F9C9");

    boolean first = true;
    for (CiphertextBallot.Contest contest : result.contests) {
      System.out.printf(" contest %s = %s nonce %s%n", contest.contestId, contest.crypto_hash, contest.nonce);
      for (CiphertextBallot.Selection selection : contest.selections) {
        System.out.printf("  selection %s = %s nonce %s%n", selection.selectionId, selection.crypto_hash, selection.nonce);
        if (first) System.out.printf("%n*****first %s%n", selection);
        first = false;
      }
    }
    assertThat(result.crypto_hash).isEqualTo(expected);
  }

  @Example
  public void singleTrusteeSingleSelection() {
    Group.ElementModQ secret = Group.rand_q();
    ElGamal.KeyPair keypair = elgamal_keypair_from_secret(secret).orElseThrow();
    Group.ElementModQ nonce = Group.rand_q();

    // public static Optional<Ciphertext> elgamal_encrypt_ver1(int message, ElementModQ nonce, ElementModP public_key) {
    //    Group.ElementModP pad = g_pow_p(nonce);
    //    Group.ElementModP gpowp_m = g_pow_p(int_to_q_unchecked(BigInteger.valueOf(message)));
    //    Group.ElementModP pubkey_pow_n = pow_p(public_key, nonce);
    //    Group.ElementModP data = mult_p(gpowp_m, pubkey_pow_n);
    int vote = 42;
    ElGamal.Ciphertext evote =
            ElGamal.elgamal_encrypt_ver1(vote, nonce, keypair.public_key()).orElseThrow();

    assertThat(Group.g_pow_p(nonce)).isEqualTo(evote.pad());

    Group.ElementModP gpowp_m = Group.g_pow_p(Group.int_to_q_unchecked(BigInteger.valueOf(vote)));
    Group.ElementModP pubkey_pow_n = Group.pow_p(keypair.public_key(), nonce);
    Group.ElementModP expectedData = Group.mult_p(gpowp_m, pubkey_pow_n);
    assertThat(expectedData).isEqualTo(evote.data());

    //decrypt
    Group.ElementModP partial_decryption = evote.partial_decrypt(keypair.secret_key());

    // Calculate 𝑀 = 𝐵⁄(∏𝑀𝑖) mod 𝑝.
    Group.ElementModP decrypted_value = Group.div_p(evote.data(), partial_decryption);
    Integer dlogM = Dlog.discrete_log(decrypted_value);
    assertThat(dlogM).isEqualTo(vote);
  }

  @Example
  public void multipleTrusteesMultipleSelections() {
    List<ElGamal.KeyPair> trustees = List.of(
            elgamal_keypair_from_secret(Group.rand_q()).orElseThrow(),
            elgamal_keypair_from_secret(Group.rand_q()).orElseThrow(),
            elgamal_keypair_from_secret(Group.rand_q()).orElseThrow());
    Group.ElementModP pubkey = Group.mult_p(trustees.stream().map(t -> t.public_key()).toList());

    // public static Optional<Ciphertext> elgamal_encrypt_ver1(int message, ElementModQ nonce, ElementModP public_key) {
    //    Group.ElementModP pad = g_pow_p(nonce);
    //    Group.ElementModP gpowp_m = g_pow_p(int_to_q_unchecked(BigInteger.valueOf(message)));
    //    Group.ElementModP pubkey_pow_n = pow_p(public_key, nonce);
    //    Group.ElementModP data = mult_p(gpowp_m, pubkey_pow_n);
    int vote = 1;
    ElGamal.Ciphertext evote1 = ElGamal.elgamal_encrypt_ver1(vote, Group.rand_q(), pubkey).orElseThrow();
    ElGamal.Ciphertext evote2 = ElGamal.elgamal_encrypt_ver1(vote, Group.rand_q(), pubkey).orElseThrow();
    ElGamal.Ciphertext evote3 = ElGamal.elgamal_encrypt_ver1(vote, Group.rand_q(), pubkey).orElseThrow();

    // encrypt
    ElGamal.Ciphertext eAccum = elgamal_add(evote1, evote2, evote3);

    //decrypt
    List<Group.ElementModP> shares = trustees.stream().map(it -> Group.pow_p(eAccum.pad(), it.secret_key())).toList();
    Group.ElementModP allSharesProductM = Group.mult_p(shares);

    // Calculate 𝑀 = 𝐵⁄(∏𝑀𝑖) mod 𝑝.
    Group.ElementModP decrypted_value = Group.div_p(eAccum.data(), allSharesProductM);
    Integer dlogM = Dlog.discrete_log(decrypted_value);
    assertThat(dlogM).isEqualTo(3 * vote);
  }

}
