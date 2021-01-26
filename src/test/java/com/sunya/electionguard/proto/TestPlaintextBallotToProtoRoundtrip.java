package com.sunya.electionguard.proto;

import com.sunya.electionguard.Ballot;
import com.sunya.electionguard.publish.ConvertFromJson;
import com.sunya.electionguard.publish.Publisher;
import net.jqwik.api.Example;
import net.jqwik.api.lifecycle.BeforeContainer;

import java.io.File;
import java.io.IOException;

import static com.google.common.truth.Truth.assertThat;

public class TestPlaintextBallotToProtoRoundtrip {
  private static Publisher publisher;

  @BeforeContainer
  public static void setup() throws IOException {
    publisher = new Publisher(TestElectionDescriptionToProtoRoundtrip.testElectionRecord, false);
  }

  /* LOOK spoiled
  @Example
  public void testSpoiledBallotsRoundtrip() throws IOException {
    for (File file : publisher.spoiledFiles()) {
      Ballot.PlaintextBallot fromPython = ConvertFromJson.readPlaintextBallot(file.getAbsolutePath());
      assertThat(fromPython).isNotNull();
      PlaintextBallotProto.PlaintextBallot proto = PlaintextBallotToProto.translateToProto(fromPython);
      Ballot.PlaintextBallot roundtrip = PlaintextBallotFromProto.translateFromProto(proto);
      assertThat(roundtrip).isEqualTo(fromPython);
    }
  } */
}
