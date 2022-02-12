package com.sunya.electionguard.proto;

import com.sunya.electionguard.PlaintextTally;
import com.sunya.electionguard.protogen.PlaintextTallyProto;
import com.sunya.electionguard.publish.Consumer;
import com.sunya.electionguard.verifier.TestParameterVerifier;
import net.jqwik.api.Example;
import net.jqwik.api.lifecycle.BeforeContainer;

import java.io.IOException;

import static com.google.common.truth.Truth.assertThat;

public class TestPlaintextBallotToProtoRoundtrip {
  private static Consumer consumer;

  @BeforeContainer
  public static void setup() throws IOException {
    consumer = new Consumer(TestParameterVerifier.topdirJson);
  }

  @Example
  public void testSpoiledBallotsRoundtrip() throws IOException {
    assertThat(consumer.spoiledBallots()).isNotEmpty();
    for (PlaintextTally tally : consumer.spoiledBallots()) {
      PlaintextTallyProto.PlaintextTally proto = PlaintextTallyToProto.translateToProto(tally);
      PlaintextTally roundtrip = PlaintextTallyFromProto.translateFromProto(proto);
      assertThat(roundtrip).isEqualTo(tally);
    }
  }
}
