package com.sunya.electionguard.verifier;

import com.sunya.electionguard.publish.Consumer;
import net.jqwik.api.Example;
import net.jqwik.api.lifecycle.BeforeContainer;

import java.io.IOException;

import static com.google.common.truth.Truth.assertThat;

public class TestContestVoteLimitsVerifier {

  static Consumer consumer;
  static ContestVoteLimitsVerifier validator;

  @BeforeContainer
  public static void setUp() throws IOException {
    String topdir = TestParameterVerifier.topdir;

    consumer = new Consumer(topdir);
    validator = new ContestVoteLimitsVerifier(consumer.readElectionRecordJson());
  }

  @Example
  public void testContestVoteLimitsValidator() throws IOException {
    boolean sevOk = validator.verify_all_contests();
    assertThat(sevOk).isTrue();
  }


}
