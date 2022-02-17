package com.sunya.electionguard.verifier;

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
  public void testElectionRecordVerifierJson() throws IOException {
    String topdir = TestParameterVerifier.topdirJson;
    Consumer consumer = new Consumer(topdir);
    System.out.printf(" VerifyElectionRecord read from %s%n", topdir);
    boolean ok = VerifyElectionRecord.verifyElectionRecord(consumer.readElectionRecord(), false);
    assertThat(ok).isTrue();
  }

  // @Example
  public void testElectionRecordSampleFull() throws IOException {
    String electionRecordDir = "/home/snake/dev/github/electionguard/data/0.95.0/sample/full/artifacts/";
    testElectionRecord(Consumer.fromElectionRecord(electionRecordDir, true));
  }

  // @Example
  public void testElectionRecordSampleHamilton() throws IOException {
    String electionRecordDir = "/home/snake/dev/github/electionguard/data/0.95.0/sample/hamilton-general/artifacts/";
    testElectionRecord(Consumer.fromElectionRecord(electionRecordDir, true));
  }

  // @Example
  public void testElectionRecordSampleMinimal() throws IOException {
    String electionRecordDir = "/home/snake/dev/github/electionguard/data/0.95.0/sample/minimal/artifacts/";
    testElectionRecord(Consumer.fromElectionRecord(electionRecordDir, true));
  }

  // @Example
  public void testElectionRecordSampleSmall() throws IOException {
    String electionRecordDir = "/home/snake/dev/github/electionguard/data/0.95.0/sample/small/artifacts/";
    testElectionRecord(Consumer.fromElectionRecord(electionRecordDir, true));
  }

  private void testElectionRecord(Consumer consumer) throws IOException {
    System.out.printf(" VerifyElectionRecord read from %s%n", consumer.location());
    boolean ok = VerifyElectionRecord.verifyElectionRecord(consumer.readElectionRecord(), false);
    assertThat(ok).isTrue();
  }

}
