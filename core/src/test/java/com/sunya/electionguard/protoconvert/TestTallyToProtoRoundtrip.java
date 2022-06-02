package com.sunya.electionguard.protoconvert;

import com.sunya.electionguard.CiphertextTally;
import com.sunya.electionguard.PlaintextTally;
import com.sunya.electionguard.json.ConvertFromJson;
import com.sunya.electionguard.json.PublisherOld;
import electionguard.protogen.CiphertextTallyProto;
import electionguard.protogen.PlaintextTallyProto;
import com.sunya.electionguard.json.JsonPublisher;
import com.sunya.electionguard.verifier.TestParameterVerifier;
import net.jqwik.api.Example;
import net.jqwik.api.lifecycle.BeforeContainer;

import java.io.IOException;

import static com.google.common.truth.Truth.assertThat;

public class TestTallyToProtoRoundtrip {
  private static JsonPublisher publisher;

  @BeforeContainer
  public static void setup() throws IOException {
    publisher = new JsonPublisher(TestParameterVerifier.topdirJsonExample, PublisherOld.Mode.readonly);
  }

  @Example
  public void testCiphertextTallyRoundtrip() throws IOException {
    CiphertextTally fromPython = ConvertFromJson.readCiphertextTally(publisher.encryptedTallyPath().toString());
    assertThat(fromPython).isNotNull();
    CiphertextTallyProto.CiphertextTally proto = CiphertextTallyToProto.publishCiphertextTally(fromPython);
    CiphertextTally roundtrip = CiphertextTallyFromProto.importCiphertextTally(proto);
    assertThat(roundtrip).isEqualTo(fromPython);
  }

  @Example
  public void testPlaintextTallyRoundtrip() throws IOException {
    PlaintextTally fromPython = ConvertFromJson.readPlaintextTally(publisher.tallyPath().toString());
    assertThat(fromPython).isNotNull();
    PlaintextTallyProto.PlaintextTally proto = PlaintextTallyToProto.publishPlaintextTally(fromPython);
    PlaintextTally roundtrip = PlaintextTallyFromProto.importPlaintextTally(proto);
    assertThat(roundtrip).isEqualTo(fromPython);
  }
}
