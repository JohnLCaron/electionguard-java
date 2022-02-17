package com.sunya.electionguard.publish;

import com.sunya.electionguard.verifier.ElectionRecord;
import com.sunya.electionguard.verifier.TestParameterVerifier;
import net.jqwik.api.Example;

import java.io.IOException;

import static com.google.common.truth.Truth.assertThat;

public class TestConsumer {

  @Example
  public void testConsumerJson() throws IOException {
    Consumer consumer = new Consumer(TestParameterVerifier.topdirJson);
    ElectionRecord record = consumer.readElectionRecord();
    assertThat(record.election).isNotNull();
    assertThat(record.context).isNotNull();
    assertThat(record.constants).isNotNull();
    assertThat(record.encryptedTally).isNotNull();
    assertThat(record.decryptedTally).isNotNull();
    assertThat(record.devices).hasSize(1);
    assertThat(record.guardianRecords).hasSize(7);
    assertThat(record.availableGuardians).hasSize(5);

    assertThat(record.acceptedBallots).hasSize(6);
    assertThat(record.spoiledBallots).hasSize(3);
  }

  @Example
  public void testConsumerJsonPython() throws IOException {
    Consumer consumer = new Consumer(TestParameterVerifier.topdirJsonPython);
    ElectionRecord record = consumer.readElectionRecord();
    assertThat(record.election).isNotNull();
    assertThat(record.context).isNotNull();
    assertThat(record.constants).isNotNull();
    assertThat(record.encryptedTally).isNotNull();
    assertThat(record.decryptedTally).isNotNull();
    assertThat(record.devices).hasSize(1);
    assertThat(record.guardianRecords).hasSize(5);

    assertThat(record.acceptedBallots).hasSize(6);
    assertThat(record.spoiledBallots).hasSize(3);
  }

  @Example
  public void testConsumerProto() throws IOException {
    Consumer consumer = new Consumer(TestParameterVerifier.topdirProto);
    ElectionRecord record = consumer.readElectionRecord();
    assertThat(record.election).isNotNull();
    assertThat(record.context).isNotNull();
    assertThat(record.constants).isNotNull();
    assertThat(record.encryptedTally).isNotNull();
    assertThat(record.decryptedTally).isNotNull();
    assertThat(record.devices).hasSize(1);
    assertThat(record.guardianRecords).hasSize(3);
    assertThat(record.availableGuardians).hasSize(2);

    assertThat(record.acceptedBallots).hasSize(11);
    assertThat(record.spoiledBallots).hasSize(3);
  }

}
