package com.sunya.electionguard.verifier;

import com.sunya.electionguard.publish.Consumer;
import net.jqwik.api.Example;
import java.io.IOException;

import static com.google.common.truth.Truth.assertThat;

public class TestPartialDecryptionVerifier {

  @Example
  public void testSelectionEncryptionValidationProto() throws IOException {
    String topdir = TestParameterVerifier.topdirProto;
    Consumer consumer = new Consumer(topdir);
    PartialDecryptionVerifier validator = new PartialDecryptionVerifier(consumer.readElectionRecordProto());

    boolean sevOk = validator.verify_lagrange_coefficients();
    assertThat(sevOk).isTrue();
  }

  @Example
  public void testSelectionEncryptionValidationJson() throws IOException {
    String topdir = TestParameterVerifier.topdirJson;
    Consumer consumer = new Consumer(topdir);
    PartialDecryptionVerifier validator = new PartialDecryptionVerifier(consumer.readElectionRecordJson());

    boolean sevOk = validator.verify_lagrange_coefficients();
    assertThat(sevOk).isTrue();
  }
}