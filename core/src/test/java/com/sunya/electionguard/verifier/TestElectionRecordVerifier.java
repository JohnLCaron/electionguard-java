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

}
