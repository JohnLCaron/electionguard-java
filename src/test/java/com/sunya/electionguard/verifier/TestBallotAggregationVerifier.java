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
    // String topdir = "src/test/data/python-modified";
    String topdir = "/home/snake/tmp/testEndToEnd";

    // set up
    consumer = new Consumer(topdir);
    electionParameters = new ElectionParameters(consumer);
    System.out.println("set up finished. ");

    validator = new BallotAggregationVerifier(electionParameters, consumer);
  }

  @Example
  public void testVerifyBallotAggregation() throws IOException {
    boolean sevOk = validator.verify_ballot_aggregation();
    assertThat(sevOk).isTrue();
  }

  @Example
  public void testMatchTotalsAcrossBallots() throws IOException {
    boolean sevOk = validator.match_total_across_ballots();
    assertThat(sevOk).isTrue();
  }

  @Example
  public void testVerifyTallyDecryption() throws IOException {
    boolean sevOk = validator.verify_tally_decryption();
    assertThat(sevOk).isTrue();
  }
}
