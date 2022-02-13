package com.sunya.electionguard.verifier;

import com.sunya.electionguard.publish.Consumer;
import net.jqwik.api.Example;
import java.io.IOException;
import static com.google.common.truth.Truth.assertThat;

public class TestBallotAggregationVerifier {

  @Example
  public void testVerifyBallotAggregationProto() throws IOException {
    Consumer consumer = new Consumer(TestParameterVerifier.topdirProto);
    ElectionRecord electionRecord = consumer.readElectionRecordProto();
    BallotAggregationVerifier validator = new BallotAggregationVerifier(electionRecord.acceptedBallots, electionRecord.decryptedTally);
    boolean sevOk1 = validator.verify_ballot_aggregation();
    assertThat(sevOk1).isTrue();
  }

  @Example
  public void testVerifyBallotAggregationJson() throws IOException {
    Consumer consumer = new Consumer(TestParameterVerifier.topdirJson);
    ElectionRecord electionRecord = consumer.readElectionRecordJson();
    BallotAggregationVerifier validator = new BallotAggregationVerifier(electionRecord.acceptedBallots, electionRecord.decryptedTally);

    boolean sevOk1 = validator.verify_ballot_aggregation();
    assertThat(sevOk1).isTrue();
  }
}
