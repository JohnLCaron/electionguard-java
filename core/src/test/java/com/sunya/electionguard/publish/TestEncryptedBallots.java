package com.sunya.electionguard.publish;

import com.sunya.electionguard.Group;
import com.sunya.electionguard.SubmittedBallot;
import com.sunya.electionguard.verifier.ElectionRecord;
import com.sunya.electionguard.verifier.TestParameterVerifier;
import net.jqwik.api.Example;

import java.io.IOException;

import static com.google.common.truth.Truth.assertThat;

public class TestEncryptedBallots {
  private static final String kotlinPublish = "/home/snake/tmp/electionguard/kotlin/runBatchEncryption";

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
    Group.ElementModQ crypto_extended_base_hash = kotlin.context.commitmentHash;

    for (SubmittedBallot ballot : consumer.submittedAllBallotsProto()) {
      assertThat(ballot.is_valid_encryption(seed_hash, jointPublicKey, crypto_extended_base_hash)).isTrue();
    }
  }

  @Example
  public void testReadElectionRecordFromJava() throws IOException {
    Consumer consumer = new Consumer(TestParameterVerifier.topdirProto);
    System.out.printf("TestReadElectionRecordFromKotlin from %s%n", TestParameterVerifier.topdirProto);
    ElectionRecord kotlin = consumer.readElectionRecordProto();
    assertThat(kotlin.manifest).isNotNull();
    assertThat(kotlin.context).isNotNull();
    assertThat(kotlin.constants).isNotNull();
    assertThat(kotlin.guardianRecords).hasSize(3);

    Group.ElementModQ seed_hash = kotlin.context.manifestHash;
    Group.ElementModP jointPublicKey = kotlin.context.jointPublicKey;
    Group.ElementModQ crypto_extended_base_hash = kotlin.context.commitmentHash;

    for (SubmittedBallot ballot : consumer.submittedAllBallotsProto()) {
      assertThat(ballot.is_valid_encryption(seed_hash, jointPublicKey, crypto_extended_base_hash)).isTrue();
    }
  }

}
