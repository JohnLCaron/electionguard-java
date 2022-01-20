package com.sunya.electionguard.proto;

import com.sunya.electionguard.CiphertextBallot;
import com.sunya.electionguard.SubmittedBallot;
import com.sunya.electionguard.protogen.CiphertextBallotProto;
import com.sunya.electionguard.publish.ConvertFromJson;
import com.sunya.electionguard.publish.Publisher;
import com.sunya.electionguard.verifier.TestParameterVerifier;
import net.jqwik.api.Example;
import net.jqwik.api.lifecycle.BeforeContainer;

import java.io.File;
import java.io.IOException;

import static com.google.common.truth.Truth.assertThat;

public class TestCiphertextBallotToProtoRoundtrip {
  private static Publisher publisher;

  @BeforeContainer
  public static void setup() throws IOException {
    publisher = new Publisher(TestParameterVerifier.topdirJson, Publisher.Mode.readonly, false);
  }

  @Example
  public void testCiphertextBallotsRoundtrip() throws IOException {
    for (File file : publisher.ballotFiles()) {
      SubmittedBallot fromPython = ConvertFromJson.readSubmittedBallot(file.getAbsolutePath());
      assertThat(fromPython).isNotNull();
      CiphertextBallotProto.SubmittedBallot proto = CiphertextBallotToProto.translateToProto(fromPython);
      SubmittedBallot roundtrip = CiphertextBallotFromProto.translateFromProto(proto);
      int contestIdx = 0;
      for (CiphertextBallot.Contest contest : roundtrip.contests) {
        CiphertextBallot.Contest pcontest = fromPython.contests.get(contestIdx);
        int selectionIdx = 0;
        for (CiphertextBallot.Selection selection : contest.ballot_selections) {
          CiphertextBallot.Selection pselection = pcontest.ballot_selections.get(selectionIdx);
          assertThat(selection).isEqualTo(pselection);
          selectionIdx++;
        }
        contestIdx++;
      }
      assertThat(roundtrip.contests).isEqualTo(fromPython.contests);
      assertThat(roundtrip).isEqualTo(fromPython);
    }
  }

}
