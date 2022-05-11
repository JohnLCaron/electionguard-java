package com.sunya.electionguard.workflow;

import com.sunya.electionguard.CiphertextBallot;
import com.sunya.electionguard.ElGamal;
import com.sunya.electionguard.ElectionCryptoContext;
import com.sunya.electionguard.Encrypt;
import com.sunya.electionguard.Group;
import com.sunya.electionguard.InternalManifest;
import com.sunya.electionguard.PlaintextBallot;
import com.sunya.electionguard.json.JsonConsumer;
import com.sunya.electionguard.publish.ElectionRecord;
import net.jqwik.api.Example;

import java.io.IOException;
import java.util.Optional;

import static com.google.common.truth.Truth.assertThat;
import static com.sunya.electionguard.ElGamal.elgamal_keypair_from_secret;

public class TestEncrypt {
  String input = "/home/snake/dev/github/electionguard-kotlin-multiplatform/src/commonTest/data/testPython";

  @Example
  public void testEncryption() throws IOException {
    JsonConsumer consumer = new JsonConsumer(input);
    ElectionRecord electionRecord = consumer.readElectionRecord();
    PlaintextBallot ballot = FakeBallotProvider.makeBallot(electionRecord.manifest(), "congress-district-7-arlington", 3, 0);

    InternalManifest metadata = new InternalManifest(electionRecord.manifest());
    ElGamal.KeyPair keypair = elgamal_keypair_from_secret(Group.TWO_MOD_Q).orElseThrow();

    ElectionCryptoContext context = new ElectionCryptoContext(1,1, keypair.public_key(), electionRecord.manifest().cryptoHash(),
            Group.TWO_MOD_Q, Group.TWO_MOD_Q, Group.TWO_MOD_Q, null);
    System.out.printf("electionRecord.manifest.cryptoHash() %s%n", electionRecord.manifest().cryptoHash());

    //   public static Optional<CiphertextBallot> encrypt_ballot(
    //          PlaintextBallot ballot,
    //          InternalManifest internal_manifest,
    //          ElectionContext context,
    //          ElementModQ encryption_seed,
    //          Optional<ElementModQ> nonce,
    //          boolean should_verify_proofs)
    CiphertextBallot result = Encrypt.encrypt_ballot(ballot, metadata, context,
            Group.TWO_MOD_Q, Optional.of(Group.TWO_MOD_Q), false).orElseThrow();

    System.out.printf("result = %s nonce %s%n", result.crypto_hash, result.hashed_ballot_nonce());
    Group.ElementModQ expected = Group.hex_to_q_unchecked("7AABEA51E6BA904C27A1C22F7C13FA685B28FF7A9CDEE7B33207C87E2BD6EDFE");

    boolean first = true;
    for (CiphertextBallot.Contest contest : result.contests) {
      System.out.printf(" contest %s = %s nonce %s%n", contest.contestId, contest.crypto_hash, contest.nonce);
      for (CiphertextBallot.Selection selection : contest.selections) {
        System.out.printf("  selection %s = %s nonce %s%n", selection.selectionId, selection.crypto_hash, selection.nonce);
        if (first) System.out.printf("%n*****first %s%n", selection);
        first = false;
      }
    }
    // assertThat(result.crypto_hash).isEqualTo(expected);
  }
}
