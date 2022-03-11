package com.sunya.electionguard.proto;

import com.sunya.electionguard.CiphertextTally;
import com.sunya.electionguard.PlaintextTally;
import electionguard.protogen.ElectionRecordProto;
import com.sunya.electionguard.verifier.ElectionRecord;
import com.sunya.electionguard.publish.Consumer;
import com.sunya.electionguard.publish.Publisher;
import com.sunya.electionguard.verifier.TestParameterVerifier;
import net.jqwik.api.Example;
import net.jqwik.api.lifecycle.BeforeContainer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

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
    assertThat(consumer.acceptedBallots()).isNotEmpty();
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
    assertThat(roundtrip.manifest).isEqualTo(consumer.election());
    assertThat(roundtrip.context).isEqualTo(consumer.context());
    assertThat(roundtrip.constants).isEqualTo(consumer.constants());
    assertThat(roundtrip.devices).isEqualTo(consumer.devices());
    CiphertextTally expected = consumer.ciphertextTally();
    assertThat(roundtrip.ciphertextTally).isEqualTo(expected);
    PlaintextTally expectedDecryptedTally = consumer.decryptedTally();
    comparePlaintextTally(roundtrip.decryptedTally, expectedDecryptedTally);
    assertThat(roundtrip.decryptedTally).isEqualTo(expectedDecryptedTally);
    assertThat(roundtrip.guardianRecords).isEqualTo(consumer.guardianRecords());
    assertThat(roundtrip.availableGuardians).isEqualTo(consumer.availableGuardians());
  }

  void comparePlaintextTally(PlaintextTally decryptedTally, PlaintextTally expected) {
    for (Map.Entry<String, PlaintextTally.Contest> entry : expected.contests.entrySet()) {
      PlaintextTally.Contest expectedContest = entry.getValue();
      PlaintextTally.Contest contest = decryptedTally.contests.get(entry.getKey());
      assertThat(contest).isNotNull();
      for (Map.Entry<String, PlaintextTally.Selection> entry2 : expectedContest.selections().entrySet()) {
        PlaintextTally.Selection expectedSelection = entry2.getValue();
        PlaintextTally.Selection selection = contest.selections().get(entry2.getKey());
        assertThat(selection).isEqualTo(expectedSelection);
      }
    }
  }

  @Example
  public void testElectionRecordPublishRoundtrip() throws IOException {
    Path tmp = Files.createTempDirectory("publish");
    tmp.toFile().deleteOnExit();
    String protoDir = tmp.toAbsolutePath().toString();
    Publisher publisher = new Publisher(protoDir, Publisher.Mode.createNew, false);
    publisher.writeDecryptionResultsProto(
            consumer.readElectionRecord(),
            consumer.ciphertextTally(),
            consumer.decryptedTally(),
            consumer.spoiledBallots(),
            consumer.availableGuardians());

    Consumer consumer2 = new Consumer(publisher);
    ElectionRecord roundtrip = consumer2.readElectionRecordProto();

    assertThat(roundtrip.manifest).isEqualTo(consumer.election());
    assertThat(roundtrip.context).isEqualTo(consumer.context());
    assertThat(roundtrip.constants).isEqualTo(consumer.constants());
    assertThat(roundtrip.devices).isEqualTo(consumer.devices());
    assertThat(roundtrip.ciphertextTally).isEqualTo(consumer.ciphertextTally());
    assertThat(roundtrip.decryptedTally).isEqualTo(consumer.decryptedTally());
    assertThat(roundtrip.guardianRecords).isEqualTo(consumer.guardianRecords());
    assertThat(roundtrip.availableGuardians).isEqualTo(consumer.availableGuardians());
  }
}
