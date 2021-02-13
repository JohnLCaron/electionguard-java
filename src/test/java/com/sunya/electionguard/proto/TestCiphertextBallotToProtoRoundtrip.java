package com.sunya.electionguard.proto;

import com.sunya.electionguard.CiphertextAcceptedBallot;
import com.sunya.electionguard.publish.ConvertFromJson;
import com.sunya.electionguard.publish.Publisher;
import net.jqwik.api.Example;
import net.jqwik.api.lifecycle.BeforeContainer;

import java.io.File;
import java.io.IOException;

import static com.google.common.truth.Truth.assertThat;

public class TestCiphertextBallotToProtoRoundtrip {
  private static Publisher publisher;

  @BeforeContainer
  public static void setup() throws IOException {
    publisher = new Publisher(TestElectionDescriptionToProtoRoundtrip.testElectionRecord, false, false);
  }

  @Example
  public void testBallotsRoundtrip() throws IOException {
    for (File file : publisher.ballotFiles()) {
      CiphertextAcceptedBallot fromPython = ConvertFromJson.readCiphertextBallot(file.getAbsolutePath());
      assertThat(fromPython).isNotNull();
      CiphertextBallotProto.CiphertextAcceptedBallot proto = CiphertextBallotToProto.translateToProto(fromPython);
      CiphertextAcceptedBallot roundtrip = CiphertextBallotFromProto.translateFromProto(proto);
      assertThat(roundtrip).isEqualTo(fromPython);
    }
  }

}
