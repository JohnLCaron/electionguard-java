package com.sunya.electionguard.verifier;

import com.sunya.electionguard.publish.Consumer;
import net.jqwik.api.Example;
import net.jqwik.api.lifecycle.BeforeContainer;

import java.io.IOException;

import static com.google.common.truth.Truth.assertThat;

public class TestBallotChainingVerifier {

  static Consumer consumer;
  static BallotChainingVerifier validator;

  @BeforeContainer
  public static void setUp() throws IOException {
    String topdir = TestParameterVerifier.topdir;

    // set up
    consumer = new Consumer(topdir);
    validator = new BallotChainingVerifier(consumer.getElectionRecord());
  }

  @Example
  public void testContestVoteLimitsValidator() throws IOException {
    boolean sevOk = validator.verify_all_ballots();
    assertThat(sevOk).isTrue();
  }


}
