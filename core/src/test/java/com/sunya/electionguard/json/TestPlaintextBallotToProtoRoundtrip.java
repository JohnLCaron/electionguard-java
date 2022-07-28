package com.sunya.electionguard.json;

import com.sunya.electionguard.PlaintextTally;
import com.sunya.electionguard.protoconvert.PlaintextTallyConvert;
import electionguard.protogen.PlaintextTallyProto;
import com.sunya.electionguard.verifier.TestParameterVerifier;
import net.jqwik.api.Example;
import net.jqwik.api.lifecycle.BeforeContainer;

import java.io.IOException;

import static com.google.common.truth.Truth.assertThat;

public class TestPlaintextBallotToProtoRoundtrip {
  private static JsonConsumer consumer;

  @BeforeContainer
  public static void setup() throws IOException {
    consumer = new JsonConsumer(TestParameterVerifier.topdirJsonExample);
  }

  @Example
  public void testSpoiledBallotsRoundtrip() {
    assertThat(consumer.spoiledBallots()).isNotEmpty();
    for (PlaintextTally tally : consumer.spoiledBallots()) {
      PlaintextTallyProto.PlaintextTally proto = PlaintextTallyConvert.publishPlaintextTally(tally);
      PlaintextTally roundtrip = PlaintextTallyConvert.importPlaintextTally(proto);
      assertThat(roundtrip).isEqualTo(tally);
    }
  }
}
