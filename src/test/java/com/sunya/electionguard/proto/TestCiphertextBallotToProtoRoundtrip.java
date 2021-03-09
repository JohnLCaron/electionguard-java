package com.sunya.electionguard.proto;

import com.sunya.electionguard.SubmittedBallot;
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
    publisher = new Publisher(TestManifestToProtoRoundtrip.testElectionRecord, false, false);
  }

  @Example
  public void testCiphertextBallotsRoundtrip() throws IOException {
    for (File file : publisher.ballotFiles()) {
      SubmittedBallot fromPython = ConvertFromJson.readSubmittedBallot(file.getAbsolutePath());
      assertThat(fromPython).isNotNull();
      CiphertextBallotProto.SubmittedBallot proto = CiphertextBallotToProto.translateToProto(fromPython);
      SubmittedBallot roundtrip = CiphertextBallotFromProto.translateFromProto(proto);
      assertThat(roundtrip).isEqualTo(fromPython);
    }
  }

}
