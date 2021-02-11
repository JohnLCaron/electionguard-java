package com.sunya.electionguard.verifier;

import com.sunya.electionguard.publish.Consumer;
import net.jqwik.api.Example;
import net.jqwik.api.lifecycle.BeforeContainer;

import java.io.IOException;

import static com.google.common.truth.Truth.assertThat;

public class TestElectionPublicKeyVerifier {

  static Consumer consumer;
  static ElectionPublicKeyVerifier validator;

  @BeforeContainer
  public static void setUp() throws IOException {
    String topdir = TestParameterVerifier.topdir;

    // set up
    consumer = new Consumer(topdir);
    validator = new ElectionPublicKeyVerifier(consumer.readElectionRecordProto());
  }

  @Example
  public void testVerifyPublicKeys() {
    boolean sevOk = validator.verify_public_keys();
    assertThat(sevOk).isTrue();
  }

}
