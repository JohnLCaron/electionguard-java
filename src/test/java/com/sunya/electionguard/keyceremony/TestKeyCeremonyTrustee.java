package com.sunya.electionguard.keyceremony;

import com.sunya.electionguard.ElGamal;
import com.sunya.electionguard.ElectionPolynomial;
import com.sunya.electionguard.Group;
import com.sunya.electionguard.SchnorrProof;
import net.jqwik.api.Example;

import static com.google.common.truth.Truth.assertThat;

public class TestKeyCeremonyTrustee {
  private static final String GUARDIAN_ID = "Guardian 1";
  private static final int GUARDIAN_X_COORDINATE = 11;
  private static final int QUORUM = 3;
  private static final String GUARDIAN2_ID = "Guardian 2";
  private static final int GUARDIAN2_X_COORDINATE = 2;

  @Example
  public void testKeyCeremonyTrusteeGeneration() {
    KeyCeremonyTrustee trustee = new KeyCeremonyTrustee(GUARDIAN_ID, GUARDIAN_X_COORDINATE, QUORUM, null);

    assertThat(trustee.id).isEqualTo(GUARDIAN_ID);
    assertThat(trustee.xCoordinate).isEqualTo(GUARDIAN_X_COORDINATE);
    assertThat(trustee.allGuardianPublicKeys).hasSize(1);
    assertThat(trustee.otherGuardianPartialKeyBackups).isEmpty();

    java.security.KeyPair rsa_key_pair = trustee.secrets().rsa_keypair;
    assertThat(rsa_key_pair).isNotNull();
    assertThat(rsa_key_pair.getPrivate()).isNotNull();
    assertThat(rsa_key_pair.getPublic()).isNotNull();

    ElGamal.KeyPair election_key_pair = trustee.secrets().election_key_pair;
    assertThat(election_key_pair).isNotNull();
    assertThat(election_key_pair.public_key).isNotNull();
    assertThat(election_key_pair.secret_key).isNotNull();

    ElectionPolynomial polynomial = trustee.secrets().polynomial;
    assertThat(polynomial).isNotNull();
    assertThat(polynomial.coefficients.size()).isEqualTo(QUORUM);
    assertThat(polynomial.coefficient_commitments.size()).isEqualTo(QUORUM);
    assertThat(polynomial.coefficient_proofs.size()).isEqualTo(QUORUM);
    for (SchnorrProof proof : polynomial.coefficient_proofs) {
      assertThat(proof.is_valid()).isTrue();
    }

    KeyCeremony2.PublicKeySet public_keys = trustee.sharePublicKeys();
    assertThat(public_keys).isEqualTo(trustee.allGuardianPublicKeys.get(GUARDIAN_ID));

    assertThat(public_keys.ownerId()).isEqualTo(GUARDIAN_ID);
    assertThat(public_keys.guardianXCoordinate()).isEqualTo(GUARDIAN_X_COORDINATE);
    assertThat(public_keys.auxiliaryPublicKey()).isEqualTo(rsa_key_pair.getPublic());
    int count = 0;
    for (Group.ElementModP key : public_keys.coefficientCommitments()) {
      assertThat(key).isEqualTo(polynomial.coefficient_commitments.get(count++));
    }
    count = 0;
    for (SchnorrProof proof : public_keys.coefficientProofs()) {
      assertThat(proof).isEqualTo(polynomial.coefficient_proofs.get(count++));
    }
  }

  @Example
  public void testReceivePublicKeys() {
    KeyCeremonyTrustee trustee = new KeyCeremonyTrustee(GUARDIAN_ID, GUARDIAN_X_COORDINATE, QUORUM, null);
    KeyCeremonyTrustee trustee2 = new KeyCeremonyTrustee(GUARDIAN2_ID, GUARDIAN2_X_COORDINATE, QUORUM, null);

    assertThat(trustee.receivePublicKeys(trustee2.sharePublicKeys())).isTrue();
    assertThat(trustee.allGuardianPublicKeys).hasSize(2);
    assertThat(trustee2.sharePublicKeys()).isEqualTo(trustee.allGuardianPublicKeys.get(GUARDIAN2_ID));
  }

  @Example
  public void testPartialKeyBackup() {
    KeyCeremonyTrustee trustee1 = new KeyCeremonyTrustee(GUARDIAN_ID, GUARDIAN_X_COORDINATE, QUORUM, null);
    KeyCeremonyTrustee trustee2 = new KeyCeremonyTrustee(GUARDIAN2_ID, GUARDIAN2_X_COORDINATE, QUORUM, null);
    assertThat(trustee1.receivePublicKeys(trustee2.sharePublicKeys())).isTrue();
    assertThat(trustee2.receivePublicKeys(trustee1.sharePublicKeys())).isTrue();

    KeyCeremony2.PartialKeyBackup backup = trustee1.sendPartialKeyBackup(GUARDIAN2_ID);
    assertThat(backup).isNotNull();
    assertThat(backup.error()).isEmpty();

    KeyCeremony2.PartialKeyVerification verify2 = trustee2.verifyPartialKeyBackup(backup);
    assertThat(verify2.generatingGuardianId()).isEqualTo(GUARDIAN_ID);
    assertThat(verify2.designatedGuardianId()).isEqualTo(GUARDIAN2_ID);
    assertThat(verify2.error()).isEmpty();
  }

  @Example
  public void testPartialKeyBackupSameGuardian() {
    KeyCeremonyTrustee trustee1 = new KeyCeremonyTrustee(GUARDIAN_ID, GUARDIAN_X_COORDINATE, QUORUM, null);
    KeyCeremonyTrustee trustee2 = new KeyCeremonyTrustee(GUARDIAN2_ID, GUARDIAN2_X_COORDINATE, QUORUM, null);
    assertThat(trustee1.receivePublicKeys(trustee2.sharePublicKeys())).isTrue();
    assertThat(trustee2.receivePublicKeys(trustee1.sharePublicKeys())).isTrue();

    KeyCeremony2.PartialKeyBackup backup = trustee1.sendPartialKeyBackup(GUARDIAN_ID);
    assertThat(backup).isNotNull();
    assertThat(backup.error()).contains("sendPartialKeyBackup cannot ask for its own backup 'Guardian 1'");
  }

  @Example
  public void testPartialKeyBackupUnKnownGuardian() {
    KeyCeremonyTrustee trustee1 = new KeyCeremonyTrustee(GUARDIAN_ID, GUARDIAN_X_COORDINATE, QUORUM, null);
    KeyCeremonyTrustee trustee2 = new KeyCeremonyTrustee(GUARDIAN2_ID, GUARDIAN2_X_COORDINATE, QUORUM, null);
    assertThat(trustee1.receivePublicKeys(trustee2.sharePublicKeys())).isTrue();
    assertThat(trustee2.receivePublicKeys(trustee1.sharePublicKeys())).isTrue();

    KeyCeremony2.PartialKeyBackup backup = trustee1.sendPartialKeyBackup("Unknown");
    assertThat(backup).isNotNull();
    assertThat(backup.error()).contains("Trustee 'Guardian 1', does not have public key for 'Unknown'");
  }

  @Example
  public void testPartialKeyWrongBackup() {
    KeyCeremonyTrustee trustee1 = new KeyCeremonyTrustee(GUARDIAN_ID, GUARDIAN_X_COORDINATE, QUORUM, null);
    KeyCeremonyTrustee trustee2 = new KeyCeremonyTrustee(GUARDIAN2_ID, GUARDIAN2_X_COORDINATE, QUORUM, null);
    assertThat(trustee1.receivePublicKeys(trustee2.sharePublicKeys())).isTrue();
    assertThat(trustee2.receivePublicKeys(trustee1.sharePublicKeys())).isTrue();

    KeyCeremony2.PartialKeyBackup backup = trustee1.sendPartialKeyBackup(GUARDIAN2_ID);
    assertThat(backup).isNotNull();
    assertThat(backup.error()).isEmpty();

    KeyCeremony2.PartialKeyVerification verify2 = trustee1.verifyPartialKeyBackup(backup);
    assertThat(verify2.generatingGuardianId()).isEqualTo(GUARDIAN_ID);
    assertThat(verify2.designatedGuardianId()).isEqualTo(GUARDIAN2_ID);
    assertThat(verify2.error()).contains("Sent backup to wrong trustee 'Guardian 1', should be trustee 'Guardian 2'");
  }

  @Example
  public void testPartialKeyFailure() {
    KeyCeremonyTrustee trustee1 = new KeyCeremonyTrustee(GUARDIAN_ID, GUARDIAN_X_COORDINATE, QUORUM, null);
    KeyCeremonyTrustee trustee2 = new KeyCeremonyTrustee(GUARDIAN2_ID, GUARDIAN2_X_COORDINATE, QUORUM, null);
    assertThat(trustee1.receivePublicKeys(trustee2.sharePublicKeys())).isTrue();
    // trustee1 does not share its backup with trustee2

    KeyCeremony2.PartialKeyBackup backup = trustee1.sendPartialKeyBackup(GUARDIAN2_ID);
    assertThat(backup).isNotNull();

    KeyCeremony2.PartialKeyVerification verify2 = trustee2.verifyPartialKeyBackup(backup);
    assertThat(verify2.generatingGuardianId()).isEqualTo(GUARDIAN_ID);
    assertThat(verify2.designatedGuardianId()).isEqualTo(GUARDIAN2_ID);
    assertThat(verify2.error()).contains("'Guardian 2' trustee does not have public key for 'Guardian 1' trustee");
  }

  @Example
  public void testBackupChallenge() {
    KeyCeremonyTrustee trustee1 = new KeyCeremonyTrustee(GUARDIAN_ID, GUARDIAN_X_COORDINATE, QUORUM, null);
    KeyCeremonyTrustee trustee2 = new KeyCeremonyTrustee(GUARDIAN2_ID, GUARDIAN2_X_COORDINATE, QUORUM, null);
    assertThat(trustee1.receivePublicKeys(trustee2.sharePublicKeys())).isTrue();
    KeyCeremony2.PublicKeySet challengedGuardianKeys = trustee1.sharePublicKeys();
    assertThat(trustee2.receivePublicKeys(challengedGuardianKeys)).isTrue();

    KeyCeremony2.PartialKeyBackup backup = trustee1.sendPartialKeyBackup(GUARDIAN2_ID);
    assertThat(backup).isNotNull();
    assertThat(backup.error()).isEmpty();

    KeyCeremony2.PartialKeyChallengeResponse challengeResponse = trustee1.sendBackupChallenge(GUARDIAN2_ID);
    assertThat(challengeResponse.error()).isEmpty();

    KeyCeremony2.PartialKeyVerification challengeVerify = KeyCeremony2.verifyElectionPartialKeyChallenge(challengeResponse, challengedGuardianKeys.coefficientCommitments());
    assertThat(challengeVerify.error()).isEmpty();
  }

  @Example
  public void testUnknownBackupChallenge() {
    KeyCeremonyTrustee trustee1 = new KeyCeremonyTrustee(GUARDIAN_ID, GUARDIAN_X_COORDINATE, QUORUM, null);
    KeyCeremonyTrustee trustee2 = new KeyCeremonyTrustee(GUARDIAN2_ID, GUARDIAN2_X_COORDINATE, QUORUM, null);
    assertThat(trustee1.receivePublicKeys(trustee2.sharePublicKeys())).isTrue();
    KeyCeremony2.PublicKeySet challengedGuardianKeys = trustee1.sharePublicKeys();
    assertThat(trustee2.receivePublicKeys(challengedGuardianKeys)).isTrue();

    KeyCeremony2.PartialKeyChallengeResponse challengeResponse = trustee1.sendBackupChallenge("Unknown");
    assertThat(challengeResponse.error()).contains("Trustee 'Guardian 1' does not have backup for 'Unknown' trustee");
  }

  @Example
  public void testPublishJointKey() {
    KeyCeremonyTrustee trustee1 = new KeyCeremonyTrustee(GUARDIAN_ID, GUARDIAN_X_COORDINATE, QUORUM, null);
    KeyCeremonyTrustee trustee2 = new KeyCeremonyTrustee(GUARDIAN2_ID, GUARDIAN2_X_COORDINATE, QUORUM, null);
    assertThat(trustee1.receivePublicKeys(trustee2.sharePublicKeys())).isTrue();
    KeyCeremony2.PublicKeySet challengedGuardianKeys = trustee1.sharePublicKeys();
    assertThat(trustee2.receivePublicKeys(challengedGuardianKeys)).isTrue();

    assertThat(trustee1.publishJointKey()).isEqualTo(trustee2.publishJointKey());
  }
}
