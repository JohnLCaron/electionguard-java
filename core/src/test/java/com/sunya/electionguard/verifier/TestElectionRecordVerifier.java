package com.sunya.electionguard.verifier;

import com.sunya.electionguard.ElectionConstants;
import com.sunya.electionguard.Group;
import com.sunya.electionguard.json.JsonConsumer;
import com.sunya.electionguard.publish.Consumer;
import net.jqwik.api.Example;

import java.io.IOException;

import static com.google.common.truth.Truth.assertThat;

public class TestElectionRecordVerifier {

  @Example
  public void testElectionRecordVerifierProto() throws IOException {
    String topdir = TestParameterVerifier.topdirProto;
    Consumer consumer = new Consumer(topdir);
    System.out.printf(" VerifyElectionRecord read from %s%n", topdir);
    boolean ok = VerifyElectionRecord.verifyElectionRecord(consumer.readElectionRecord(), false);
    assertThat(ok).isTrue();
  }

  @Example
  public void testElectionRecordJsonFromJava() throws IOException {
    String topdir = TestParameterVerifier.topdirPublishEndToEnd;
    JsonConsumer consumer = new JsonConsumer(topdir);
    System.out.printf(" VerifyElectionRecord read from %s%n", topdir);
    boolean ok = VerifyElectionRecord.verifyElectionRecord(consumer.readElectionRecord(), false);
    assertThat(ok).isTrue();
  }

  @Example
  public void testElectionRecordJsonStandard() throws IOException {
    // older example record 2/2 guardians: 22 April, standard constants
    String topdir = TestParameterVerifier.topdirJsonExample;
    JsonConsumer consumer = new JsonConsumer(topdir);
    System.out.printf(" VerifyElectionRecord read from %s%n", topdir);
    boolean ok = VerifyElectionRecord.verifyElectionRecord(consumer.readElectionRecord(), false);
    assertThat(ok).isTrue();
  }

  @Example
  public void testElectionRecordJsonMay25() throws IOException {
    // latest example record 5/25/22, all guardians present
    try {
      String topdir = TestParameterVerifier.topdirJsonPython;
      testElectionRecord(JsonConsumer.fromElectionRecord(topdir));
    } finally {
      Group.reset();
    }
  }

  // these are out of date
  // @Example
  public void testElectionRecordVer095() throws IOException {
    String electionRecordDir = "/home/snake/dev/github/electionguard/data/0.95.0/sample/";
    testElectionRecord(JsonConsumer.fromElectionRecord(electionRecordDir + "hamilton-general/election_record/"));
    testElectionRecord(JsonConsumer.fromElectionRecord(electionRecordDir + "minimal/election_record/"));
    testElectionRecord(JsonConsumer.fromElectionRecord(electionRecordDir + "small/election_record/"));
  }

  private void testElectionRecord(JsonConsumer consumer) throws IOException {
    System.out.printf(" VerifyElectionRecord read from %s%n", consumer.location());
    boolean ok = VerifyElectionRecord.verifyElectionRecord(consumer.readElectionRecord(), false);
    assertThat(ok).isTrue();
  }

}
