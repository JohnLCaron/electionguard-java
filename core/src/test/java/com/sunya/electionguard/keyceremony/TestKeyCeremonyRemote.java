package com.sunya.electionguard.keyceremony;

import com.sunya.electionguard.Manifest;
import com.sunya.electionguard.input.ElectionInputBuilder;
import net.jqwik.api.Example;

import java.io.IOException;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.fail;

public class TestKeyCeremonyRemote {
  private static final String GUARDIAN1_ID = "Guardian 1";
  private static final int GUARDIAN1_X_COORDINATE = 11;
  private static final int NGUARDIANS = 4;
  private static final int QUORUM = 3;
  private static final String GUARDIAN2_ID = "Guardian 2";
  private static final int GUARDIAN2_X_COORDINATE = 99;
  private static final String GUARDIAN3_ID = "Guardian 3";
  private static final int GUARDIAN3_X_COORDINATE = 1;

  Manifest manifest;

  public TestKeyCeremonyRemote() {
    ElectionInputBuilder ebuilder = new ElectionInputBuilder("ballot_id");
    this.manifest = ebuilder.addContest("contest_id")
            .addSelection("selection_id", "candidate_1")
            .addSelection("selection_id2", "candidate_2")
            .done()
            .build();
  }

  private KeyCeremonyRemote makeRemote() throws IOException {
    // Manifest manifest, int nguardians, int quorum, Publisher publisher
    return new KeyCeremonyRemote(this.manifest, NGUARDIANS, QUORUM, "/home/snake/tmp/electionguard/publishKeyCeremonyRemote");
  }

  @Example
  public void testConstructor() throws IOException {
    KeyCeremonyRemote remote = makeRemote();

    assertThat(remote.manifest).isEqualTo(this.manifest);
    assertThat(remote.quorum).isEqualTo(QUORUM);
    assertThat(remote.nguardians).isEqualTo(NGUARDIANS);
    assertThat(remote.trusteeProxies).isEmpty();
    assertThat(remote.startedKeyCeremony).isFalse();
    assertThat(remote.ready()).isFalse();
  }

  @Example
  public void testRegisterTrustee() throws IOException {
    KeyCeremonyRemote remote = makeRemote();

    KeyCeremonyRemoteTrusteeProxy proxy = remote.registerTrustee("id1", "url1");
    assertThat(proxy.id()).isEqualTo("id1");
    assertThat(proxy.quorum()).isEqualTo(QUORUM);
    assertThat(proxy.xCoordinate()).isEqualTo(1);
  }

  @Example
  public void testRegisterTrustees() throws IOException {
    KeyCeremonyRemote remote = makeRemote();

    for (int i=0; i < NGUARDIANS; i++) {
      KeyCeremonyRemoteTrusteeProxy proxy = remote.registerTrustee("id" + i, "url" + i);
      assertThat(proxy.id()).isEqualTo("id" + i);
      assertThat(proxy.quorum()).isEqualTo(QUORUM);
      assertThat(proxy.xCoordinate()).isEqualTo(i + 1);
      assertThat(remote.trusteeProxies).hasSize(i + 1);
      assertThat(remote.startedKeyCeremony).isFalse();
      if (i < NGUARDIANS - 1 ) {
        assertThat(remote.ready()).isFalse();
      }
    }
    assertThat(remote.startedKeyCeremony).isFalse();
    assertThat(remote.ready()).isTrue();
  }
}
