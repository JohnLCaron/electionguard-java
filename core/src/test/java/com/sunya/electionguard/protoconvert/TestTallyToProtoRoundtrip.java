package com.sunya.electionguard.protoconvert;

import com.sunya.electionguard.ballot.EncryptedTally;
import com.sunya.electionguard.PlaintextTally;
import com.sunya.electionguard.json.ConvertFromJson;
import electionguard.protogen.EncryptedTallyProto;
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
    publisher = new JsonPublisher(TestParameterVerifier.topdirJsonExample, JsonPublisher.Mode.readonly);
  }

  @Example
  public void testCiphertextTallyRoundtrip() throws IOException {
    EncryptedTally fromPython = ConvertFromJson.readCiphertextTally(publisher.encryptedTallyPath().toString());
    assertThat(fromPython).isNotNull();
    EncryptedTallyProto.EncryptedTally proto = EncryptedTallyConvert.publishEncryptedTally(fromPython);
    EncryptedTally roundtrip = EncryptedTallyConvert.importEncryptedTally(proto);
    assertThat(roundtrip).isEqualTo(fromPython);
  }

  @Example
  public void testPlaintextTallyRoundtrip() throws IOException {
    PlaintextTally fromPython = ConvertFromJson.readPlaintextTally(publisher.tallyPath().toString());
    assertThat(fromPython).isNotNull();
    PlaintextTallyProto.PlaintextTally proto = PlaintextTallyConvert.publishPlaintextTally(fromPython);
    PlaintextTally roundtrip = PlaintextTallyConvert.importPlaintextTally(proto);
    assertThat(roundtrip).isEqualTo(fromPython);
  }
}
