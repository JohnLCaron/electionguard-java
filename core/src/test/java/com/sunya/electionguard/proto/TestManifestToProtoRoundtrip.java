package com.sunya.electionguard.proto;

import com.sunya.electionguard.Manifest;
import electionguard.protogen.ManifestProto;
import com.sunya.electionguard.publish.ConvertFromJson;
import com.sunya.electionguard.publish.Publisher;
import com.sunya.electionguard.verifier.TestParameterVerifier;
import net.jqwik.api.Example;
import net.jqwik.api.lifecycle.BeforeContainer;

import java.io.IOException;

import static com.google.common.truth.Truth.assertThat;

public class TestManifestToProtoRoundtrip {
  private static Publisher publisher;

  @BeforeContainer
  public static void setup() throws IOException {
    publisher = new Publisher(TestParameterVerifier.topdirJson, Publisher.Mode.readonly, true);
  }

  @Example
  public void testElectionRoundtrip() throws IOException {
    Manifest fromPython = ConvertFromJson.readManifest(publisher.manifestPath().toString());
    assertThat(fromPython).isNotNull();
    ManifestProto.Manifest proto = ManifestToProto.translateToProto(fromPython);
    Manifest roundtrip = ManifestFromProto.translateFromProto(proto);

    assertThat(roundtrip.electionScopeId()).isEqualTo(fromPython.electionScopeId());
    assertThat(roundtrip.electionType()).isEqualTo(fromPython.electionType());
    assertThat(roundtrip.startDate()).isEqualTo(fromPython.startDate());
    assertThat(roundtrip.endDate()).isEqualTo(fromPython.endDate());

    assertThat(roundtrip.geopoliticalUnits()).containsExactlyElementsIn(fromPython.geopoliticalUnits());
    assertThat(roundtrip.parties()).containsExactlyElementsIn(fromPython.parties());
    assertThat(roundtrip.candidates()).containsExactlyElementsIn(fromPython.candidates());
    assertThat(roundtrip.contests()).containsExactlyElementsIn(fromPython.contests());
    assertThat(roundtrip.ballotStyles()).containsExactlyElementsIn(fromPython.ballotStyles());

    assertThat(roundtrip.name()).isEqualTo(fromPython.name());
    assertThat(roundtrip.contactInformation()).isEqualTo(fromPython.contactInformation());

    assertThat(roundtrip).isEqualTo(fromPython);
  }
}
