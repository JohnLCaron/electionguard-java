package com.sunya.electionguard.proto;

import com.sunya.electionguard.Tally;
import com.sunya.electionguard.publish.ConvertFromJson;
import com.sunya.electionguard.publish.Publisher;
import net.jqwik.api.Example;
import net.jqwik.api.lifecycle.BeforeContainer;

import java.io.IOException;

import static com.google.common.truth.Truth.assertThat;

public class TestTallyToProtoRoundtrip {
  private static Publisher publisher;

  @BeforeContainer
  public static void setup() throws IOException {
    publisher = new Publisher(TestElectionDescriptionToProtoRoundtrip.testElectionRecord, false);
  }

  @Example
  public void testCiphertextTallyRoundtrip() throws IOException {
    Tally.PublishedCiphertextTally fromPython = ConvertFromJson.readCiphertextTally(publisher.encryptedTallyFile().toString());
    assertThat(fromPython).isNotNull();
    CiphertextProto.PublishedCiphertextTally proto = CiphertextTallyToProto.translateToProto(fromPython);
    Tally.PublishedCiphertextTally roundtrip = CiphertextTallyFromProto.translateFromProto(proto);
    assertThat(roundtrip).isEqualTo(fromPython);
  }

  @Example
  public void testPlaintextTallyRoundtrip() throws IOException {
    Tally.PlaintextTally fromPython = ConvertFromJson.readPlaintextTally(publisher.tallyFile().toString());
    assertThat(fromPython).isNotNull();
    PlaintextProto.PlaintextTally proto = PlaintextTallyToProto.translateToProto(fromPython);
    Tally.PlaintextTally roundtrip = PlaintextTallyFromProto.translateFromProto(proto);
    assertThat(roundtrip).isEqualTo(fromPython);
  }
}
