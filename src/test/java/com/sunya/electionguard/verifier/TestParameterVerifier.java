package com.sunya.electionguard.verifier;

import com.sunya.electionguard.publish.Consumer;
import net.jqwik.api.Example;

import java.io.IOException;

import static com.google.common.truth.Truth.assertThat;

public class TestParameterVerifier {
  public static final String topdirProto = "src/test/data/electionRecordProto/";
  public static final String topdirJson = "src/test/data/publishEndToEnd/";


  @Example
  public void testElectionRecordJson() throws IOException {
    Consumer consumer = new Consumer(topdirJson);
    ParameterVerifier blv = new ParameterVerifier(consumer.readElectionRecordJson());
    boolean blvOk = blv.verify_all_params();
    assertThat(blvOk).isTrue();
  }

  @Example
  public void testElectionRecordProto() throws IOException {
    Consumer consumer = new Consumer(topdirProto);
    ParameterVerifier blv = new ParameterVerifier(consumer.readElectionRecordProto());
    boolean blvOk = blv.verify_all_params();
    assertThat(blvOk).isTrue();
  }
}
