package com.sunya.electionguard.verifier;

import com.sunya.electionguard.publish.Consumer;
import net.jqwik.api.Example;
import net.jqwik.api.lifecycle.BeforeContainer;

import java.io.IOException;

import static com.google.common.truth.Truth.assertThat;

public class TestPartialDecryptionVerifier {
  static Consumer consumer;
  static PartialDecryptionVerifier validator;

  @BeforeContainer
  public static void setUp() throws IOException {
    String topdir = TestParameterVerifier.topdir;

    consumer = new Consumer(topdir);
    validator = new PartialDecryptionVerifier(consumer.getElectionRecord());
  }

  @Example
  public void testSelectionEncryptionValidation() throws IOException {
    boolean sevOk = validator.verify_lagrange_coefficients();
    assertThat(sevOk).isTrue();
  }
}