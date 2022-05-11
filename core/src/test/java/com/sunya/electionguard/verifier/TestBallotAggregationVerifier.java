package com.sunya.electionguard.verifier;

import com.sunya.electionguard.json.JsonConsumer;
import com.sunya.electionguard.publish.Consumer;
import com.sunya.electionguard.publish.ElectionRecord;
import net.jqwik.api.Example;
import java.io.IOException;
import static com.google.common.truth.Truth.assertThat;

public class TestBallotAggregationVerifier {

  @Example
  public void testVerifyBallotAggregationProto() throws IOException {
    Consumer consumer = new Consumer(TestParameterVerifier.topdirProto);
    ElectionRecord electionRecord = consumer.readElectionRecord();
    var validator = new BallotAggregationVerifier(electionRecord.submittedBallots(),
            electionRecord.decryptedTally());
    boolean sevOk1 = validator.verify_ballot_aggregation();
    assertThat(sevOk1).isTrue();
  }

  @Example
  public void testVerifyBallotAggregationJson() throws IOException {
    JsonConsumer consumer = new JsonConsumer(TestParameterVerifier.topdirJsonExample);
    ElectionRecord electionRecord = consumer.readElectionRecordJson();
    var validator = new BallotAggregationVerifier(electionRecord.submittedBallots(),
            electionRecord.decryptedTally());

    boolean sevOk1 = validator.verify_ballot_aggregation();
    assertThat(sevOk1).isTrue();
  }
}
