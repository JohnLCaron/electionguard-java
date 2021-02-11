package com.sunya.electionguard.verifier;

import com.sunya.electionguard.publish.Consumer;
import net.jqwik.api.Example;
import java.io.IOException;
import static com.google.common.truth.Truth.assertThat;

public class TestContestVoteLimitsVerifier {

  @Example
  public void testContestVoteLimitsValidatorProto() throws IOException {
    String topdir = TestParameterVerifier.topdirProto;
    Consumer consumer = new Consumer(topdir);
    ContestVoteLimitsVerifier validator = new ContestVoteLimitsVerifier(consumer.readElectionRecordProto());

    boolean sevOk = validator.verify_all_contests();
    assertThat(sevOk).isTrue();
  }

  @Example
  public void testContestVoteLimitsValidatorJson() throws IOException {
    String topdir = TestParameterVerifier.topdirJson;
    Consumer consumer = new Consumer(topdir);
    ContestVoteLimitsVerifier validator = new ContestVoteLimitsVerifier(consumer.readElectionRecordJson());

    boolean sevOk = validator.verify_all_contests();
    assertThat(sevOk).isTrue();
  }


}
