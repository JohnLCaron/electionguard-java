package com.sunya.electionguard.publish;

import com.sunya.electionguard.Group;
import com.sunya.electionguard.SubmittedBallot;
import com.sunya.electionguard.core.UInt256;
import com.sunya.electionguard.verifier.TestParameterVerifier;
import electionguard.ballot.DecryptionResult;
import electionguard.ballot.TallyResult;
import net.jqwik.api.Example;

import java.io.IOException;

import static com.google.common.truth.Truth.assertThat;

public class TestEncryptedBallots {
  private static final String kotlinPublish = "/home/snake/dev/github/electionguard-kotlin-multiplatform/src/commonTest/data/runWorkflow";

  @Example
  public void testReadElectionRecordFromKotlin() throws IOException {
    Consumer consumer = new Consumer(kotlinPublish);
    System.out.printf("TestReadElectionRecordFromKotlin from %s%n", kotlinPublish);
    DecryptionResult kotlin = consumer.readDecryptionResult();
    assertThat(kotlin.getTallyResult()).isNotNull();
    assertThat(kotlin.getDecryptedTally()).isNotNull();
    assertThat(kotlin.getAvailableGuardians()).hasSize(3);

    TallyResult tallyResult = kotlin.getTallyResult();
    UInt256 manifestHash = tallyResult.manifestHash();
    Group.ElementModP jointPublicKey = tallyResult.jointPublicKey();
    Group.ElementModQ cryptoExtendedBaseHash = tallyResult.cryptoExtendedBaseHash();

    for (SubmittedBallot ballot : consumer.iterateSubmittedBallots()) {
      assertThat(ballot.is_valid_encryption(manifestHash.toModQ(), jointPublicKey, cryptoExtendedBaseHash)).isTrue();
    }
  }

  @Example
  public void testReadElectionRecordFromJava() throws IOException {
    Consumer consumer = new Consumer(TestParameterVerifier.topdirProto);
    System.out.printf("TestReadElectionRecordFromKotlin from %s%n", TestParameterVerifier.topdirProto);
    DecryptionResult kotlin = consumer.readDecryptionResult();
    assertThat(kotlin.getTallyResult()).isNotNull();
    assertThat(kotlin.getDecryptedTally()).isNotNull();
    assertThat(kotlin.getAvailableGuardians()).hasSize(3);

    TallyResult tallyResult = kotlin.getTallyResult();
    UInt256 manifestHash = tallyResult.manifestHash();
    Group.ElementModP jointPublicKey = tallyResult.jointPublicKey();
    Group.ElementModQ cryptoExtendedBaseHash = tallyResult.cryptoExtendedBaseHash();

    for (SubmittedBallot ballot : consumer.iterateSubmittedBallots()) {
      assertThat(ballot.is_valid_encryption(manifestHash.toModQ(), jointPublicKey, cryptoExtendedBaseHash)).isTrue();
    }
  }

}
