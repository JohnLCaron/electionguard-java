package com.sunya.electionguard.proto;

import com.sunya.electionguard.Ballot;
import com.sunya.electionguard.Election;
import com.sunya.electionguard.publish.ConvertFromJson;
import com.sunya.electionguard.publish.Publisher;
import net.jqwik.api.Example;
import net.jqwik.api.lifecycle.BeforeContainer;

import java.io.File;
import java.io.IOException;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth8.assertThat;

public class TestBallotToProtoRoundtrip {
  private static final String pythonModified = "src/test/data/python-modified/";
  private static Publisher publisher;

  @BeforeContainer
  public static void setup() throws IOException {
    publisher = new Publisher(pythonModified, false);
  }

  @Example
  public void testBallotsRoundtrip() throws IOException {
    for (File file : publisher.ballotFiles()) {
      Ballot.CiphertextAcceptedBallot fromPython = ConvertFromJson.readBallot(file.getAbsolutePath());
      assertThat(fromPython).isNotNull();
      BallotProto.CiphertextAcceptedBallot proto = BallotToProto.translate(fromPython);
      Ballot.CiphertextAcceptedBallot roundtrip = BallotFromProto.translate(proto);
      assertThat(roundtrip).isEqualTo(fromPython);
    }
  }

  @Example
  public void testSpoiledBallotsRoundtrip() throws IOException {
    for (File file : publisher.spoiledFiles()) {
      Ballot.CiphertextAcceptedBallot fromPython = ConvertFromJson.readBallot(file.getAbsolutePath());
      assertThat(fromPython).isNotNull();
      BallotProto.CiphertextAcceptedBallot proto = BallotToProto.translate(fromPython);
      Ballot.CiphertextAcceptedBallot roundtrip = BallotFromProto.translate(proto);
      assertThat(roundtrip).isEqualTo(fromPython);
    }
  }
}
