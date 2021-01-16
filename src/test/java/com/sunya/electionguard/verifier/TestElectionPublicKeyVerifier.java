package com.sunya.electionguard.verifier;

import com.sunya.electionguard.publish.Consumer;
import net.jqwik.api.Example;
import net.jqwik.api.lifecycle.BeforeContainer;

import java.io.IOException;

import static com.google.common.truth.Truth.assertThat;

public class TestElectionPublicKeyVerifier {

  static ElectionParameters electionParameters;
  static Consumer consumer;
  static ElectionPublicKeyVerifier validator;

  @BeforeContainer
  public static void setUp() throws IOException {
    String topdir = TestParameterVerifier.topdir;

    // set up
    consumer = new Consumer(topdir);
    electionParameters = new ElectionParameters(consumer);
    validator = new ElectionPublicKeyVerifier(electionParameters);
  }

  @Example
  public void testVerifyPublicKeys() throws IOException {
    boolean sevOk = validator.verify_public_keys();
    assertThat(sevOk).isTrue();
  }

}
