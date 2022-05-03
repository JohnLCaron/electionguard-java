package com.sunya.electionguard.verifier;

import com.sunya.electionguard.json.JsonConsumer;
import com.sunya.electionguard.publish.Consumer;
import com.sunya.electionguard.publish.ElectionRecord;
import net.jqwik.api.Example;
import java.io.IOException;

import static com.google.common.truth.Truth.assertThat;

public class TestPartialDecryptionVerifier {

  @Example
  public void testSelectionEncryptionValidationProto() throws IOException {
    String topdir = TestParameterVerifier.topdirProto;
    System.out.printf("testSelectionEncryptionValidationProto %s%n", topdir);
    Consumer consumer = new Consumer(topdir);
    ElectionRecord electionrecord = consumer.readElectionRecord();
    PartialDecryptionVerifier validator = new PartialDecryptionVerifier(electionrecord, electionrecord.decryptedTally());

    boolean sevOk = validator.verify_lagrange_coefficients();
    assertThat(sevOk).isTrue();
  }

  @Example
  public void testSelectionEncryptionValidationJson() throws IOException {
    String topdir = TestParameterVerifier.topdirJson;
    System.out.printf("testSelectionEncryptionValidationJson %s%n", topdir);
    JsonConsumer consumer = new JsonConsumer(topdir);
    ElectionRecord electionrecord = consumer.readElectionRecordJson();
    PartialDecryptionVerifier validator = new PartialDecryptionVerifier(electionrecord, electionrecord.decryptedTally());

    boolean sevOk = validator.verify_lagrange_coefficients();
    assertThat(sevOk).isTrue();
  }
}