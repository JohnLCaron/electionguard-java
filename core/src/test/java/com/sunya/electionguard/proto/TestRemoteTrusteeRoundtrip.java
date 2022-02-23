package com.sunya.electionguard.proto;

import com.sunya.electionguard.Group;
import com.sunya.electionguard.decrypting.DecryptingTrustee;
import com.sunya.electionguard.keyceremony.KeyCeremonyTrustee;
import electionguard.protogen.TrusteeProto;
import com.sunya.electionguard.publish.PrivateData;
import net.jqwik.api.Example;
import net.jqwik.api.lifecycle.BeforeProperty;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Formatter;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.google.common.truth.Truth.assertThat;

public class TestRemoteTrusteeRoundtrip {
  private static PrivateData publisher;

  private static final String ID = "remoteTrustee42";
  private static final int xCoordinate =  42;
  private static final int quorum = 24;

  @BeforeProperty
  public void setUp() throws IOException {
    Path tempPath = Files.createTempDirectory("testRemoteTrusteeRoundtrip.temp");
    File tempDir = tempPath.toFile();
    tempDir.deleteOnExit();
    String outputDir = tempDir.getAbsolutePath();
    System.out.printf("outputDir %s%n", outputDir);
    publisher = new PrivateData(outputDir, false, true);
    Formatter errors = new Formatter();
    if (!publisher.validateOutputDir(errors)) {
      System.out.printf("*** Publisher validateOutputDir FAILED on %s%n%s", outputDir, errors);
      throw new FileNotFoundException(errors.toString());
    }
  }

  @Example
  public void testRemoteTrusteeRoundtrip() throws IOException {
    // Yikes thats a lot of work to get a DecryptingTrustee to serialize
    KeyCeremonyTrustee org = new KeyCeremonyTrustee(ID, xCoordinate, quorum, null);

    KeyCeremonyTrustee trustee1 = new KeyCeremonyTrustee("pks1", 1, quorum, null);
    KeyCeremonyTrustee trustee2 = new KeyCeremonyTrustee("pks2", 2, quorum, null);

    assertThat(org.receivePublicKeys(trustee1.sharePublicKeys())).isEmpty();
    assertThat(org.receivePublicKeys(trustee2.sharePublicKeys())).isEmpty();
    assertThat(org.allGuardianPublicKeys).hasSize(3);

    assertThat(trustee1.receivePublicKeys(org.sharePublicKeys())).isEmpty();
    assertThat(trustee2.receivePublicKeys(org.sharePublicKeys())).isEmpty();

    assertThat(org.sendPartialKeyBackup("pks1")).isNotNull();
    assertThat(org.sendPartialKeyBackup("pks2")).isNotNull();

    assertThat(org.verifyPartialKeyBackup(trustee1.sendPartialKeyBackup(ID))).isNotNull();
    assertThat(org.verifyPartialKeyBackup(trustee2.sendPartialKeyBackup(ID))).isNotNull();
    assertThat(org.otherGuardianPartialKeyBackups).hasSize(2);

    TrusteeProto.DecryptingTrustee trusteeProto = TrusteeToProto.convertTrustee(org);
    publisher.overwriteTrusteeProto(trusteeProto);

    DecryptingTrustee roundtrip = publisher.readDecryptingTrustee(trusteeProto.getGuardianId());
    assertThat(roundtrip.id()).isEqualTo(org.id);
    assertThat(roundtrip.xCoordinate()).isEqualTo(org.xCoordinate);
    assertThat(roundtrip.election_keypair()).isEqualTo(org.secrets().election_key_pair);
    assertThat(roundtrip.otherGuardianPartialKeyBackups()).isEqualTo(org.otherGuardianPartialKeyBackups);

    Map<String, List<Group.ElementModP>> expected = org.allGuardianPublicKeys.entrySet().stream()
            .collect(Collectors.toMap(Map.Entry::getKey,
                    e -> e.getValue().coefficientCommitments()));
    assertThat(roundtrip.guardianCommittments()).isEqualTo(expected);
  }
}
