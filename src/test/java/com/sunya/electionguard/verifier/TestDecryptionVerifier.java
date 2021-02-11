package com.sunya.electionguard.verifier;

import com.sunya.electionguard.publish.CloseableIterableAdapter;
import com.sunya.electionguard.publish.Consumer;
import net.jqwik.api.Example;

import java.io.IOException;

import static com.google.common.truth.Truth.assertThat;

public class TestDecryptionVerifier {

  @Example
  public void testDecryptionValidationProto() throws IOException {
    String topdir = TestParameterVerifier.topdirProto;
    Consumer consumer = new Consumer(topdir);
    DecryptionVerifier validator = new DecryptionVerifier(consumer.readElectionRecordProto());

    boolean sevOk1 = validator.verify_election_tally();
    assertThat(sevOk1).isTrue();

    boolean sevOk2 = validator.verify_spoiled_tallies(CloseableIterableAdapter.wrap(consumer.spoiledTallies()));
    assertThat(sevOk2).isTrue();
  }

  @Example
  public void testDecryptionValidationJson() throws IOException {
    String topdir = TestParameterVerifier.topdirJson;
    Consumer consumer = new Consumer(topdir);
    DecryptionVerifier validator = new DecryptionVerifier(consumer.readElectionRecordJson());

    boolean sevOk1 = validator.verify_election_tally();
    assertThat(sevOk1).isTrue();

    boolean sevOk2 = validator.verify_spoiled_tallies(CloseableIterableAdapter.wrap(consumer.spoiledTallies()));
    assertThat(sevOk2).isTrue();
  }
}