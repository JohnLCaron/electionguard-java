package com.sunya.electionguard.decrypting;

import com.sunya.electionguard.protoconvert.TrusteeFromProto;
import com.sunya.electionguard.publish.ElectionRecordPath;
import net.jqwik.api.Example;

import java.io.IOException;

import static com.google.common.truth.Truth.assertThat;

public class TestDecryptingTrustee {
  private static final String GUARDIAN_ID = "remoteTrustee";
  private static final int NGUARDIANS = 4;
  private static final ElectionRecordPath path = new ElectionRecordPath("whatever");

  DecryptingTrustee trustee1;

  public TestDecryptingTrustee() throws IOException {
    trustee1 = TrusteeFromProto.readTrustee(path.decryptingTrusteePath(TestDecryptingMediator.TRUSTEE_DATA_DIR, "remoteTrustee1"));
  }

  @Example
  public void testKeyCeremonyTrusteeGeneration() {
    assertThat(trustee1.id()).isEqualTo(GUARDIAN_ID + 1);
    assertThat(trustee1.electionPublicKey()).isNotNull();

    assertThat(trustee1.guardianCommittments().size()).isEqualTo(NGUARDIANS);
    assertThat(trustee1.otherGuardianPartialKeyBackups().size()).isEqualTo(NGUARDIANS-1);
  }
}
