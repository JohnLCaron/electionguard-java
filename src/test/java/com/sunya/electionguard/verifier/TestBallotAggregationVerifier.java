package com.sunya.electionguard.verifier;

import com.sunya.electionguard.publish.Consumer;
import net.jqwik.api.Example;
import net.jqwik.api.lifecycle.BeforeContainer;

import java.io.IOException;

import static com.google.common.truth.Truth.assertThat;

public class TestBallotAggregationVerifier {
  static BallotAggregationVerifier validator;

  @BeforeContainer
  public static void setUp() throws IOException {
    String topdir = TestParameterVerifier.topdir;

    // set up
    Consumer consumer = new Consumer(topdir);
    validator = new BallotAggregationVerifier(consumer.readElectionRecordProto());
  }

  @Example
  public void testVerifyBallotAggregation() {
    boolean sevOk = validator.verify_ballot_aggregation();
    assertThat(sevOk).isTrue();
  }

  @Example
  public void testVerifyTallyDecryption() {
    boolean sevOk = validator.verify_tally_decryption();
    assertThat(sevOk).isTrue();
  }
}
