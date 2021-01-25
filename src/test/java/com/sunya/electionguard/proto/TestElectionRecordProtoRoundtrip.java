package com.sunya.electionguard.proto;

import com.sunya.electionguard.verifier.ElectionRecord;
import com.sunya.electionguard.publish.Consumer;
import com.sunya.electionguard.publish.Publisher;
import net.jqwik.api.Example;
import net.jqwik.api.lifecycle.BeforeContainer;

import java.io.IOException;

import static com.google.common.truth.Truth.assertThat;

public class TestElectionRecordProtoRoundtrip {
  private static Consumer consumer;

  @BeforeContainer
  public static void setUp() throws IOException {
    consumer = new Consumer(TestElectionDescriptionToProtoRoundtrip.testElectionRecord);
  }

  @Example
  public void testElectionRecordRoundtrip() throws IOException {
    ElectionRecordProto.ElectionRecord protoFromJson = ElectionRecordToProto.buildElectionRecord(
            consumer.election(),
            consumer.context(),
            consumer.constants(),
            consumer.devices(),
            consumer.ballots(),
            consumer.spoiled(),
            consumer.ciphertextTally(),
            consumer.decryptedTally(),
            consumer.guardianCoefficients());

    ElectionRecord roundtrip = ElectionRecordFromProto.translateFromProto(protoFromJson);
    assertThat(roundtrip.election).isEqualTo(consumer.election());
    assertThat(roundtrip.context).isEqualTo(consumer.context());
    assertThat(roundtrip.constants).isEqualTo(consumer.constants());
    assertThat(roundtrip.devices).isEqualTo(consumer.devices());
    assertThat(roundtrip.castBallots).isEqualTo(consumer.ballots());
    assertThat(roundtrip.spoiledBallots).isEqualTo(consumer.spoiled());
    assertThat(roundtrip.ciphertextTally).isEqualTo(consumer.ciphertextTally());
    assertThat(roundtrip.decryptedTally).isEqualTo(consumer.decryptedTally());
    assertThat(roundtrip.guardianCoefficients).isEqualTo(consumer.guardianCoefficients());
  }

  @Example
  public void testElectionRecordPublishRoundtrip() throws IOException {
    String dest = "/home/snake/tmp/TestElectionRecordProtoRoundtrip/";
    Publisher publisher = new Publisher(dest, true);
    publisher.writeElectionRecordProto(
            consumer.election(),
            consumer.context(),
            consumer.constants(),
            consumer.devices(),
            consumer.ballots(),
            consumer.spoiled(),
            consumer.ciphertextTally(),
            consumer.decryptedTally(),
            consumer.guardianCoefficients());

    ElectionRecord roundtrip = ElectionRecordFromProto.read(publisher.electionRecordProtoFile().toFile().getAbsolutePath());
    assertThat(roundtrip.election).isEqualTo(consumer.election());
    assertThat(roundtrip.context).isEqualTo(consumer.context());
    assertThat(roundtrip.constants).isEqualTo(consumer.constants());
    assertThat(roundtrip.devices).isEqualTo(consumer.devices());
    assertThat(roundtrip.castBallots).isEqualTo(consumer.ballots());
    assertThat(roundtrip.spoiledBallots).isEqualTo(consumer.spoiled());
    assertThat(roundtrip.ciphertextTally).isEqualTo(consumer.ciphertextTally());
    assertThat(roundtrip.decryptedTally).isEqualTo(consumer.decryptedTally());
    assertThat(roundtrip.guardianCoefficients).isEqualTo(consumer.guardianCoefficients());
  }
}