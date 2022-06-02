package com.sunya.electionguard.workflow;

import com.sunya.electionguard.CompareHelper;
import com.sunya.electionguard.GuardianRecord;
import com.sunya.electionguard.PlaintextTally;
import com.sunya.electionguard.SubmittedBallot;
import com.sunya.electionguard.json.JsonConsumer;
import com.sunya.electionguard.publish.Consumer;
import com.sunya.electionguard.publish.ElectionRecord;
import electionguard.ballot.Guardian;
import net.jqwik.api.Example;

import java.io.IOException;
import java.util.Iterator;

import static com.google.common.truth.Truth.assertThat;

public class TestConvertJsonRecord {
  private static final String jsonInput = "src/test/data/python/sample_election_record";
  private static final String protoOutput = "/home/snake/tmp/electionguard/convertJsonRecord";

  @Example
  public void testConvertJsonRecord() throws IOException {
    RunConvertJsonRecord converter = new RunConvertJsonRecord(jsonInput, protoOutput, 0, 0);
    boolean ok = converter.convert();
    System.out.printf("*** Converted %s%n", ok ? "SUCCESS" : "FAILURE");

    JsonConsumer jsonConsumer = new JsonConsumer(jsonInput);
    ElectionRecord protoRecord = new Consumer(protoOutput).readElectionRecord();

    assertThat(protoRecord.constants()).isEqualTo(jsonConsumer.constants());
    assertThat(protoRecord.manifest()).isEqualTo(jsonConsumer.manifest());
    assertThat(protoRecord.ciphertextTally()).isEqualTo(jsonConsumer.ciphertextTally());
    assertThat(protoRecord.decryptedTally()).isEqualTo(jsonConsumer.decryptedTally());

    Iterator<SubmittedBallot> iter = jsonConsumer.acceptedBallots().iterator();
    for (SubmittedBallot ballot : protoRecord.submittedBallots()) {
      SubmittedBallot expected = iter.next();
      CompareHelper.compareCiphertextBallot(ballot, expected);
    }

    Iterator<PlaintextTally> iter2 = jsonConsumer.spoiledBallots().iterator();
    for (PlaintextTally tally : protoRecord.spoiledBallotTallies()) {
      PlaintextTally expected = iter2.next();
      CompareHelper.comparePlaintextTally(tally, expected);
    }

    Iterator<GuardianRecord> iter3 = jsonConsumer.guardianRecords().iterator();
    for (Guardian guardian : protoRecord.guardians()) {
      GuardianRecord expected = iter3.next();
      CompareHelper.compareGuardian(guardian, new Guardian(expected));
    }
    assertThat(protoRecord.availableGuardians()).isEqualTo(jsonConsumer.availableGuardians());

    System.out.printf("*** testConvertJsonRecord ok%n");
  }

}
