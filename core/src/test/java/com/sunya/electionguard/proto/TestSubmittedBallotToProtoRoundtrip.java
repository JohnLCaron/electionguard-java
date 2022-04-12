package com.sunya.electionguard.proto;

import com.sunya.electionguard.ChaumPedersen;
import com.sunya.electionguard.CiphertextBallot;
import com.sunya.electionguard.SubmittedBallot;
import electionguard.protogen.CiphertextBallotProto;
import com.sunya.electionguard.publish.ConvertFromJson;
import com.sunya.electionguard.publish.Publisher;
import com.sunya.electionguard.verifier.TestParameterVerifier;
import net.jqwik.api.Example;
import net.jqwik.api.lifecycle.BeforeContainer;

import java.io.File;
import java.io.IOException;

import static com.google.common.truth.Truth.assertThat;

public class TestSubmittedBallotToProtoRoundtrip {
  private static Publisher publisher;

  @BeforeContainer
  public static void setup() throws IOException {
    publisher = new Publisher(TestParameterVerifier.topdirJson, Publisher.Mode.readonly, false);
  }

  @Example
  public void testCiphertextBallotsRoundtrip() throws IOException {
    assertThat(publisher.ballotFiles()).isNotEmpty();
    for (File file : publisher.ballotFiles()) {
      SubmittedBallot fromPython = ConvertFromJson.readSubmittedBallot(file.getAbsolutePath());
      assertThat(fromPython).isNotNull();
      CiphertextBallotProto.SubmittedBallot proto = SubmittedBallotToProto.translateToProto(fromPython);
      SubmittedBallot roundtrip = SubmittedBallotFromProto.translateFromProto(proto);
      int contestIdx = 0;
      for (CiphertextBallot.Contest contest : roundtrip.contests) {
        CiphertextBallot.Contest pcontest = fromPython.contests.get(contestIdx);
        int selectionIdx = 0;
        for (CiphertextBallot.Selection selection : contest.selections) {
          CiphertextBallot.Selection pselection = pcontest.selections.get(selectionIdx);
          assertThat(selection).isEqualTo(pselection);
          selectionIdx++;
        }
        contestIdx++;
      }
      assertThat(roundtrip.contests).isEqualTo(fromPython.contests);
      assertThat(roundtrip).isEqualTo(fromPython);
    }
    System.out.printf("testCiphertextBallotsRoundtrip %d%n", publisher.ballotFiles().length);
  }

}