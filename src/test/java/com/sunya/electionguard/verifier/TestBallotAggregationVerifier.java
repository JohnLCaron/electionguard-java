package com.sunya.electionguard.verifier;

import com.sunya.electionguard.publish.Consumer;
import net.jqwik.api.Example;
import java.io.IOException;
import static com.google.common.truth.Truth.assertThat;

public class TestBallotAggregationVerifier {

  @Example
  public void testVerifyBallotAggregationProto() throws IOException {
    String topdir = TestParameterVerifier.topdirProto;
    Consumer consumer = new Consumer(topdir);
    BallotAggregationVerifier validator = new BallotAggregationVerifier(consumer.readElectionRecordProto());

    boolean sevOk1 = validator.verify_ballot_aggregation();
    assertThat(sevOk1).isTrue();

    boolean sevOk2 = validator.verify_tally_decryption();
    assertThat(sevOk2).isTrue();
  }

  @Example
  public void testVerifyBallotAggregationJson() throws IOException {
    String topdir = TestParameterVerifier.topdirJson;
    Consumer consumer = new Consumer(topdir);
    BallotAggregationVerifier validator = new BallotAggregationVerifier(consumer.readElectionRecordJson());

    boolean sevOk1 = validator.verify_ballot_aggregation();
    assertThat(sevOk1).isTrue();

    boolean sevOk2 = validator.verify_tally_decryption();
    assertThat(sevOk2).isTrue();
  }
}
