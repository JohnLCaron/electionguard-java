package com.sunya.electionguard.json;

import com.sunya.electionguard.PlaintextTally;
import com.sunya.electionguard.protoconvert.PlaintextTallyFromProto;
import com.sunya.electionguard.protoconvert.PlaintextTallyToProto;
import electionguard.protogen.PlaintextTallyProto;
import com.sunya.electionguard.publish.Consumer;
import com.sunya.electionguard.verifier.TestParameterVerifier;
import net.jqwik.api.Example;
import net.jqwik.api.lifecycle.BeforeContainer;

import java.io.IOException;

import static com.google.common.truth.Truth.assertThat;

public class TestPlaintextBallotToProtoRoundtrip {
  private static JsonConsumer consumer;

  @BeforeContainer
  public static void setup() throws IOException {
    consumer = new JsonConsumer(TestParameterVerifier.topdirJson);
  }

  @Example
  public void testSpoiledBallotsRoundtrip() throws IOException {
    assertThat(consumer.spoiledBallots()).isNotEmpty();
    for (PlaintextTally tally : consumer.spoiledBallots()) {
      PlaintextTallyProto.PlaintextTally proto = PlaintextTallyToProto.publishPlaintextTally(tally);
      PlaintextTally roundtrip = PlaintextTallyFromProto.importPlaintextTally(proto);
      assertThat(roundtrip).isEqualTo(tally);
    }
  }
}
