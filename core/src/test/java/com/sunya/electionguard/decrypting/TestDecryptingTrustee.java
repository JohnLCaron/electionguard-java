package com.sunya.electionguard.decrypting;

import com.sunya.electionguard.proto.TrusteeFromProto;
import net.jqwik.api.Example;

import java.io.IOException;

import static com.google.common.truth.Truth.assertThat;

public class TestDecryptingTrustee {
  private static final String GUARDIAN_ID = "remoteTrustee";
  private static final int NGUARDIANS = 3;

  DecryptingTrustee trustee1;

  public TestDecryptingTrustee() throws IOException {
    trustee1 = TrusteeFromProto.readTrustee(TestDecryptingMediator.TRUSTEE_DATA_DIR + "/remoteTrustee1.protobuf");
  }

  @Example
  public void testKeyCeremonyTrusteeGeneration() {
    assertThat(trustee1.id()).isEqualTo(GUARDIAN_ID + 1);
    assertThat(trustee1.xCoordinate()).isEqualTo(3); // TODO ARBITRARY
    assertThat(trustee1.electionPublicKey()).isNotNull();

    assertThat(trustee1.guardianCommittments().size()).isEqualTo(NGUARDIANS);
    assertThat(trustee1.otherGuardianPartialKeyBackups().size()).isEqualTo(NGUARDIANS-1);
  }
}
