package com.sunya.electionguard.verifier;

import com.sunya.electionguard.publish.Consumer;
import net.jqwik.api.Example;
import net.jqwik.api.lifecycle.BeforeContainer;

import java.io.IOException;

import static com.google.common.truth.Truth.assertThat;

public class TestBallotAggregationVerifier {

  static ElectionParameters electionParameters;
  static Consumer consumer;
  static BallotAggregationVerifier validator;

  @BeforeContainer
  public static void setUp() throws IOException {
    String topdir = TestParameterVerifier.topdir;

    // set up
    consumer = new Consumer(topdir);
    electionParameters = new ElectionParameters(consumer);
    validator = new BallotAggregationVerifier(electionParameters, consumer);
  }

  @Example
  public void testVerifyBallotAggregation() throws IOException {
    boolean sevOk = validator.verify_ballot_aggregation();
    assertThat(sevOk).isTrue();
  }

  @Example
  public void testVerifyTallyDecryption() throws IOException {
    boolean sevOk = validator.verify_tally_decryption();
    assertThat(sevOk).isTrue();
  }
}
