package com.sunya.electionguard.verifier;

import com.sunya.electionguard.json.JsonConsumer;
import com.sunya.electionguard.publish.Consumer;
import net.jqwik.api.Example;

import java.io.IOException;

import static com.google.common.truth.Truth.assertThat;

public class TestBallotChainingVerifier {

  @Example
  public void testContestVoteLimitsValidatorProto() throws IOException {
    String topdir = TestParameterVerifier.topdirProto;
    Consumer consumer = new Consumer(topdir);
    BallotChainingVerifier validator = new BallotChainingVerifier(consumer.readElectionRecord());
    boolean sevOk = validator.verify_all_ballots();
    assertThat(sevOk).isTrue();
  }

  @Example
  public void testContestVoteLimitsValidatorJson() throws IOException {
    String topdir = TestParameterVerifier.topdirJson;
    JsonConsumer consumer = new JsonConsumer(topdir);
    BallotChainingVerifier validator = new BallotChainingVerifier(consumer.readElectionRecordJson());
    boolean sevOk = validator.verify_all_ballots();
    assertThat(sevOk).isTrue();
  }


}
