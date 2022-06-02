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
  public void testElectionRecordJsonPreview1() throws IOException {
    // latest example record 3/5 guardians, test constants
    try {
      Group.setPrimes(ElectionConstants.get(ElectionConstants.PrimeOption.LargeTest));
      String electionRecordDir = "/home/snake/dev/github/electionguard/data/1.0.0-preview-1/sample/hamilton-general/election_record/";
      testElectionRecord(JsonConsumer.fromElectionRecord(electionRecordDir));
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
