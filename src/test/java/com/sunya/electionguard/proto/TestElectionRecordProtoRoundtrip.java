package com.sunya.electionguard.proto;

import com.sunya.electionguard.protogen.ElectionRecordProto;
import com.sunya.electionguard.verifier.ElectionRecord;
import com.sunya.electionguard.publish.Consumer;
import com.sunya.electionguard.publish.Publisher;
import com.sunya.electionguard.verifier.TestParameterVerifier;
import net.jqwik.api.Example;
import net.jqwik.api.lifecycle.BeforeContainer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static com.google.common.truth.Truth.assertThat;

public class TestElectionRecordProtoRoundtrip {
  private static Consumer consumer;

  @BeforeContainer
  public static void setUp() throws IOException {
    consumer = new Consumer(TestParameterVerifier.topdirJson);
  }

  @Example
  public void testConsumer() throws IOException {
    assertThat(consumer.election()).isNotNull();
    assertThat(consumer.context()).isNotNull();
    assertThat(consumer.constants()).isNotNull();
    assertThat(consumer.ciphertextTally()).isNotNull();
    assertThat(consumer.decryptedTally()).isNotNull();
    assertThat(consumer.devices()).hasSize(1);
    assertThat(consumer.guardianRecords()).hasSize(7);
    assertThat(consumer.availableGuardians()).hasSize(5);

    assertThat(consumer.acceptedBallots()).isNotNull();
  }

  @Example
  public void testElectionRecordRoundtrip() throws IOException {
    ElectionRecordProto.ElectionRecord protoFromJson = ElectionRecordToProto.buildElectionRecord(
            consumer.election(),
            consumer.context(),
            consumer.constants(),
            consumer.guardianRecords(),
            consumer.devices(),
            consumer.ciphertextTally(),
            consumer.decryptedTally(),
            consumer.availableGuardians());

    ElectionRecord roundtrip = ElectionRecordFromProto.translateFromProto(protoFromJson);
    assertThat(roundtrip.election).isEqualTo(consumer.election());
    assertThat(roundtrip.context).isEqualTo(consumer.context());
    assertThat(roundtrip.constants).isEqualTo(consumer.constants());
    assertThat(roundtrip.devices).isEqualTo(consumer.devices());
    assertThat(roundtrip.encryptedTally).isEqualTo(consumer.ciphertextTally());
    assertThat(roundtrip.decryptedTally).isEqualTo(consumer.decryptedTally());
    assertThat(roundtrip.guardianRecords).isEqualTo(consumer.guardianRecords());
    assertThat(roundtrip.availableGuardians).isEqualTo(consumer.availableGuardians());
  }

  @Example
  public void testElectionRecordPublishRoundtrip() throws IOException {
    Path tmp = Files.createTempDirectory("publish");
    tmp.toFile().deleteOnExit();
    String protoDir = tmp.toAbsolutePath().toString();
    Publisher publisher = new Publisher(protoDir, true, false);
    publisher.writeDecryptionResultsProto(
            consumer.readElectionRecord(),
            consumer.ciphertextTally(),
            consumer.decryptedTally(),
            consumer.spoiledBallots(),
            consumer.availableGuardians());

    Consumer consumer2 = new Consumer(publisher);
    ElectionRecord roundtrip = consumer2.readElectionRecordProto();

    assertThat(roundtrip.election).isEqualTo(consumer.election());
    assertThat(roundtrip.context).isEqualTo(consumer.context());
    assertThat(roundtrip.constants).isEqualTo(consumer.constants());
    assertThat(roundtrip.devices).isEqualTo(consumer.devices());
    assertThat(roundtrip.encryptedTally).isEqualTo(consumer.ciphertextTally());
    assertThat(roundtrip.decryptedTally).isEqualTo(consumer.decryptedTally());
    assertThat(roundtrip.guardianRecords).isEqualTo(consumer.guardianRecords());
    assertThat(roundtrip.availableGuardians).isEqualTo(consumer.availableGuardians());
  }
}
