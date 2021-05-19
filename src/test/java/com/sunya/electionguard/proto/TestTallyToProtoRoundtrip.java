package com.sunya.electionguard.proto;

import com.sunya.electionguard.CiphertextTally;
import com.sunya.electionguard.PlaintextTally;
import com.sunya.electionguard.protogen.CiphertextTallyProto;
import com.sunya.electionguard.protogen.PlaintextTallyProto;
import com.sunya.electionguard.publish.ConvertFromJson;
import com.sunya.electionguard.publish.Publisher;
import com.sunya.electionguard.verifier.TestParameterVerifier;
import net.jqwik.api.Example;
import net.jqwik.api.lifecycle.BeforeContainer;

import java.io.IOException;

import static com.google.common.truth.Truth.assertThat;

public class TestTallyToProtoRoundtrip {
  private static Publisher publisher;

  @BeforeContainer
  public static void setup() throws IOException {
    publisher = new Publisher(TestParameterVerifier.topdirJson, false, false);
  }

  @Example
  public void testCiphertextTallyRoundtrip() throws IOException {
    CiphertextTally fromPython = ConvertFromJson.readCiphertextTally(publisher.encryptedTallyPath().toString());
    assertThat(fromPython).isNotNull();
    CiphertextTallyProto.CiphertextTally proto = CiphertextTallyToProto.translateToProto(fromPython);
    CiphertextTally roundtrip = CiphertextTallyFromProto.translateFromProto(proto);
    assertThat(roundtrip).isEqualTo(fromPython);
  }

  @Example
  public void testPlaintextTallyRoundtrip() throws IOException {
    PlaintextTally fromPython = ConvertFromJson.readPlaintextTally(publisher.tallyPath().toString());
    assertThat(fromPython).isNotNull();
    PlaintextTallyProto.PlaintextTally proto = PlaintextTallyToProto.translateToProto(fromPython);
    PlaintextTally roundtrip = PlaintextTallyFromProto.translateFromProto(proto);
    assertThat(roundtrip).isEqualTo(fromPython);
  }
}
