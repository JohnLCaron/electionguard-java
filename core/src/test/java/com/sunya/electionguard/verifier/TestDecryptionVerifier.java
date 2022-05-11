package com.sunya.electionguard.verifier;

import com.sunya.electionguard.json.JsonConsumer;
import com.sunya.electionguard.publish.Consumer;
import com.sunya.electionguard.publish.ElectionRecord;
import net.jqwik.api.Example;

import java.io.IOException;

import static com.google.common.truth.Truth.assertThat;

public class TestDecryptionVerifier {

  @Example
  public void testDecryptionValidationProto() throws IOException {
    String topdir = TestParameterVerifier.topdirProto;
    Consumer consumer = new Consumer(topdir);
    ElectionRecord electionrecord = consumer.readElectionRecord();
    DecryptionVerifier validator = new DecryptionVerifier(electionrecord, electionrecord.decryptedTally());

    boolean sevOk1 = validator.verify_election_tally();
    assertThat(sevOk1).isTrue();

    // LOOK
    // boolean sevOk2 = validator.verify_spoiled_tallies(CloseableIterableAdapter.wrap(consumer.spoiledTallies()));
    // assertThat(sevOk2).isTrue();
  }

  @Example
  public void testDecryptionValidationJson() throws IOException {
    String topdir = TestParameterVerifier.topdirJsonExample;
    JsonConsumer consumer = new JsonConsumer(topdir);
    ElectionRecord electionrecord = consumer.readElectionRecordJson();
    DecryptionVerifier validator = new DecryptionVerifier(electionrecord, electionrecord.decryptedTally());

    boolean sevOk1 = validator.verify_election_tally();
    assertThat(sevOk1).isTrue();

    // LOOK
    // boolean sevOk2 = validator.verify_spoiled_tallies(CloseableIterableAdapter.wrap(consumer.spoiledTallies()));
    // assertThat(sevOk2).isTrue();
  }
}