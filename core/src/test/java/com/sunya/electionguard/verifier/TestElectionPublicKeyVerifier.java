package com.sunya.electionguard.verifier;

import com.sunya.electionguard.json.JsonConsumer;
import com.sunya.electionguard.publish.Consumer;
import net.jqwik.api.Example;
import java.io.IOException;

import static com.google.common.truth.Truth.assertThat;

public class TestElectionPublicKeyVerifier {

  @Example
  public void testVerifyPublicKeysProto() throws IOException {
    String topdir = TestParameterVerifier.topdirProto;
    Consumer consumer = new Consumer(topdir);
    ElectionPublicKeyVerifier validator = new ElectionPublicKeyVerifier(consumer.readElectionRecord());

    boolean sevOk = validator.verify_public_keys();
    assertThat(sevOk).isTrue();
  }

  @Example
  public void testVerifyPublicKeysJson() throws IOException {
    String topdir = TestParameterVerifier.topdirJsonExample;
    JsonConsumer consumer = new JsonConsumer(topdir);
    ElectionPublicKeyVerifier validator = new ElectionPublicKeyVerifier(consumer.readElectionRecordJson());

    boolean sevOk = validator.verify_public_keys();
    assertThat(sevOk).isTrue();
  }

}
