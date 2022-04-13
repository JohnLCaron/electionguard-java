package com.sunya.electionguard.publish;

import com.sunya.electionguard.Group;
import com.sunya.electionguard.SubmittedBallot;
import com.sunya.electionguard.verifier.ElectionRecord;
import com.sunya.electionguard.verifier.TestParameterVerifier;
import net.jqwik.api.Example;

import java.io.IOException;

import static com.google.common.truth.Truth.assertThat;

public class TestEncryptedBallots {
  private static final String kotlinPublish = "/home/snake/dev/github/electionguard-kotlin-multiplatform/src/commonTest/data/workflow/runBatchEncryption";

  @Example
  public void testReadElectionRecordFromKotlin() throws IOException {
    Consumer consumer = new Consumer(kotlinPublish);
    System.out.printf("TestReadElectionRecordFromKotlin from %s%n", kotlinPublish);
    ElectionRecord kotlin = consumer.readElectionRecordProto();
    assertThat(kotlin.manifest).isNotNull();
    assertThat(kotlin.context).isNotNull();
    assertThat(kotlin.constants).isNotNull();
    assertThat(kotlin.guardianRecords).hasSize(3);

    Group.ElementModQ seed_hash = kotlin.context.manifestHash;
    Group.ElementModP jointPublicKey = kotlin.context.jointPublicKey;
    Group.ElementModQ cryptoExtendedBaseHash = kotlin.context.cryptoExtendedBaseHash;

    for (SubmittedBallot ballot : consumer.submittedAllBallotsProto()) {
      assertThat(ballot.is_valid_encryption(seed_hash, jointPublicKey, cryptoExtendedBaseHash)).isTrue();
    }
  }

  @Example
  public void testReadElectionRecordFromJava() throws IOException {
    Consumer consumer = new Consumer(TestParameterVerifier.topdirProto);
    System.out.printf("testReadElectionRecordFromJava from %s%n", TestParameterVerifier.topdirProto);
    ElectionRecord electionRecord = consumer.readElectionRecordProto();
    assertThat(electionRecord.manifest).isNotNull();
    assertThat(electionRecord.context).isNotNull();
    assertThat(electionRecord.constants).isNotNull();
    assertThat(electionRecord.guardianRecords).hasSize(3);

    Group.ElementModQ seed_hash = electionRecord.context.manifestHash;
    Group.ElementModP jointPublicKey = electionRecord.context.jointPublicKey;
    Group.ElementModQ cryptoExtendedBaseHash = electionRecord.context.cryptoExtendedBaseHash;

    for (SubmittedBallot ballot : consumer.submittedAllBallotsProto()) {
      assertThat(ballot.is_valid_encryption(seed_hash, jointPublicKey, cryptoExtendedBaseHash)).isTrue();
    }
  }

}
