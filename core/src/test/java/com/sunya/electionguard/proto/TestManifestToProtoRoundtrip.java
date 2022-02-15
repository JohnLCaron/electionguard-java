package com.sunya.electionguard.proto;

import com.sunya.electionguard.Manifest;
import com.sunya.electionguard.protogen.ManifestProto;
import com.sunya.electionguard.publish.ConvertFromJson;
import com.sunya.electionguard.publish.Publisher;
import com.sunya.electionguard.verifier.TestParameterVerifier;
import net.jqwik.api.Example;
import net.jqwik.api.lifecycle.BeforeContainer;

import java.io.IOException;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth8.assertThat;

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

    assertThat(roundtrip.election_scope_id()).isEqualTo(fromPython.election_scope_id());
    assertThat(roundtrip.type()).isEqualTo(fromPython.type());
    assertThat(roundtrip.start_date()).isEqualTo(fromPython.start_date());
    assertThat(roundtrip.end_date()).isEqualTo(fromPython.end_date());

    assertThat(roundtrip.geopolitical_units()).containsExactlyElementsIn(fromPython.geopolitical_units());
    assertThat(roundtrip.parties()).containsExactlyElementsIn(fromPython.parties());
    assertThat(roundtrip.candidates()).containsExactlyElementsIn(fromPython.candidates());
    assertThat(roundtrip.contests()).containsExactlyElementsIn(fromPython.contests());
    assertThat(roundtrip.ballot_styles()).containsExactlyElementsIn(fromPython.ballot_styles());

    assertThat(roundtrip.name()).isEqualTo(fromPython.name());
    assertThat(roundtrip.contact_information()).isEqualTo(fromPython.contact_information());

    assertThat(roundtrip).isEqualTo(fromPython);
  }
}
