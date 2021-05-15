package com.sunya.electionguard.proto;

import com.sunya.electionguard.verifier.ElectionRecord;
import com.sunya.electionguard.publish.Consumer;
import com.sunya.electionguard.publish.Publisher;
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
    consumer = new Consumer(TestManifestToProtoRoundtrip.testElectionRecord);
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
            null);

    ElectionRecord roundtrip = ElectionRecordFromProto.translateFromProto(protoFromJson);
    assertThat(roundtrip.election).isEqualTo(consumer.election());
    assertThat(roundtrip.context).isEqualTo(consumer.context());
    assertThat(roundtrip.constants).isEqualTo(consumer.constants());
    assertThat(roundtrip.devices).isEqualTo(consumer.devices());
    assertThat(roundtrip.encryptedTally).isEqualTo(consumer.ciphertextTally());
    assertThat(roundtrip.decryptedTally).isEqualTo(consumer.decryptedTally());
    assertThat(roundtrip.guardianRecords).isEqualTo(consumer.guardianRecords());
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
            consumer.spoiledTallies(),
            null);

    Consumer consumer2 = new Consumer(publisher);
    ElectionRecord roundtrip = consumer2.readElectionRecordProto();

    assertThat(roundtrip.election).isEqualTo(consumer.election());
    assertThat(roundtrip.context).isEqualTo(consumer.context());
    assertThat(roundtrip.constants).isEqualTo(consumer.constants());
    assertThat(roundtrip.devices).isEqualTo(consumer.devices());
    assertThat(roundtrip.encryptedTally).isEqualTo(consumer.ciphertextTally());
    assertThat(roundtrip.decryptedTally).isEqualTo(consumer.decryptedTally());
    assertThat(roundtrip.guardianRecords).isEqualTo(consumer.guardianRecords());
  }
}
