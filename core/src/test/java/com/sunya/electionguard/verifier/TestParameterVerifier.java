package com.sunya.electionguard.verifier;

import com.sunya.electionguard.Group;
import com.sunya.electionguard.json.JsonConsumer;
import com.sunya.electionguard.publish.Consumer;
import com.sunya.electionguard.publish.ElectionRecord;
import net.jqwik.api.Example;

import java.io.IOException;

import static com.google.common.truth.Truth.assertThat;

public class TestParameterVerifier {
  public static final String topdirProto = "src/test/data/workflow/decryptor/";
  // public static final String topdirEncryptor = "src/test/data/workflow/encryptor/";
  public static final String topdirEncryptor = "/home/snake/tmp/electionguard/remoteWorkflow/encryptor";
  public static final String topdirPublishEndToEnd = "src/test/data/publishEndToEnd";
  public static final String topdirJsonExample = "src/test/data/python/sample_election_record/";
  public static final String topdirJsonPython = "src/test/data/python/1.4.0/";

  @Example
  public void testElectionRecordJson() throws IOException {
    JsonConsumer consumer = new JsonConsumer(topdirJsonExample);
    ParameterVerifier blv = new ParameterVerifier(consumer.readElectionRecordJson());
    boolean blvOk = blv.verify_all_params();
    assertThat(blvOk).isTrue();
  }

  @Example
  public void testElectionRecordProto() throws IOException {
    Consumer consumer = new Consumer(topdirProto);
    ParameterVerifier blv = new ParameterVerifier(consumer.readElectionRecord());
    boolean blvOk = blv.verify_all_params();
    assertThat(blvOk).isTrue();
  }

  // Not working - must regenerate
  // @Example
  public void testElectionRecordPythonJson() throws IOException {
    try {
      JsonConsumer consumer = new JsonConsumer(topdirJsonPython);
      ElectionRecord electionRecord = consumer.readElectionRecordJson();
      // assertThat(electionRecord.constants.getPrimeOptionType()).isEqualTo(ElectionConstants.PrimeOption.LargeTest);
      // Group.setPrimes(electionRecord.constants);
      ParameterVerifier blv = new ParameterVerifier(electionRecord);
      boolean blvOk = blv.verify_all_params();
      assertThat(blvOk).isTrue();
    } finally {
      Group.reset();
    }
  }
}
