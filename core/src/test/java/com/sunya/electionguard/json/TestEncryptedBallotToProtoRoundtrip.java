package com.sunya.electionguard.json;

import com.sunya.electionguard.CiphertextBallot;
import com.sunya.electionguard.ballot.EncryptedBallot;
import com.sunya.electionguard.protoconvert.EncryptedBallotConvert;
import com.sunya.electionguard.verifier.TestParameterVerifier;
import electionguard.protogen.EncryptedBallotProto;
import net.jqwik.api.Example;

import java.io.File;
import java.io.IOException;

import static com.google.common.truth.Truth.assertThat;

public class TestEncryptedBallotToProtoRoundtrip {

  @Example
  public void testCiphertextBallotsJsonExample() throws IOException {
    JsonPublisher publisher = new JsonPublisher(TestParameterVerifier.topdirJsonExample, JsonPublisher.Mode.readonly);
    assertThat(publisher.ballotFiles()).isNotEmpty();
    for (File file : publisher.ballotFiles()) {
      EncryptedBallot fromPython = ConvertFromJson.readSubmittedBallot(file.getAbsolutePath());
      assertThat(fromPython).isNotNull();
      EncryptedBallotProto.EncryptedBallot proto = EncryptedBallotConvert.publishEncryptedBallot(fromPython);
      EncryptedBallot roundtrip = EncryptedBallotConvert.importEncryptedBallot(proto);
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
    System.out.printf("testCiphertextBallotsJsonExample %d%n", publisher.ballotFiles().length);
  }

  @Example
  public void testCiphertextBallotPublishEndToEnd() throws IOException {
    JsonPublisher publisher = new JsonPublisher(TestParameterVerifier.topdirPublishEndToEnd, JsonPublisher.Mode.readonly);
    assertThat(publisher.ballotFiles()).isNotEmpty();
    for (File file : publisher.ballotFiles()) {
      EncryptedBallot fromPython = ConvertFromJson.readSubmittedBallot(file.getAbsolutePath());
      assertThat(fromPython).isNotNull();
      EncryptedBallotProto.EncryptedBallot proto = EncryptedBallotConvert.publishEncryptedBallot(fromPython);
      EncryptedBallot roundtrip = EncryptedBallotConvert.importEncryptedBallot(proto);
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
    System.out.printf("testCiphertextBallotPublishEndToEnd %d%n", publisher.ballotFiles().length);
  }

  @Example
  public void testPublishEndToEndProblem() throws IOException {
    JsonPublisher publisher = new JsonPublisher(TestParameterVerifier.topdirPublishEndToEnd, JsonPublisher.Mode.readonly);
    assertThat(publisher.ballotFiles()).isNotEmpty();
    String filename = TestParameterVerifier.topdirPublishEndToEnd +
            "/submitted_ballots/submitted_ballot_5a150c74-a2cb-47f6-b575-165ba8a4ce53.json";
    EncryptedBallot fromJson = ConvertFromJson.readSubmittedBallot(filename);
    assertThat(fromJson).isNotNull();
    for (CiphertextBallot.Contest contest : fromJson.contests) {
      for (CiphertextBallot.Selection selection : contest.selections) {
        if (selection.selectionId.equals("benjamin-franklin-selection")) {
          System.out.printf("selection %s%n", selection);
        }
      }
    }
  }

}
