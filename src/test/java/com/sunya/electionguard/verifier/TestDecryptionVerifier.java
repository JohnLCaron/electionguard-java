package com.sunya.electionguard.verifier;

import com.sunya.electionguard.publish.CloseableIterableAdapter;
import com.sunya.electionguard.publish.Consumer;
import net.jqwik.api.Example;
import net.jqwik.api.lifecycle.BeforeContainer;

import java.io.IOException;

import static com.google.common.truth.Truth.assertThat;

public class TestDecryptionVerifier {
  static Consumer consumer;
  static DecryptionVerifier validator;

  @BeforeContainer
  public static void setUp() throws IOException {
    String topdir = TestParameterVerifier.topdir;
    consumer = new Consumer(topdir);
    validator = new DecryptionVerifier(consumer.readElectionRecordJson());
  }

  @Example
  public void testSelectionEncryptionValidation() {
    boolean sevOk = validator.verify_election_tally();
    assertThat(sevOk).isTrue();
  }

  @Example
  public void testSelectionSpoiledBallots() throws IOException {
    boolean sevOk = validator.verify_spoiled_tallies(CloseableIterableAdapter.wrap(consumer.spoiledTallies()));
    assertThat(sevOk).isTrue();
  }
}