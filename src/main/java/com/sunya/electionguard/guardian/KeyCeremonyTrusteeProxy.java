package com.sunya.electionguard.guardian;

import com.sunya.electionguard.Group;

import javax.annotation.Nullable;

public class KeyCeremonyTrusteeProxy {
  private final KeyCeremonyTrustee delegate;

  public KeyCeremonyTrusteeProxy(KeyCeremonyTrustee delegate) {
    this.delegate = delegate;
  }

  String id() {
    return this.delegate.id;
  }

  KeyCeremony2.PublicKeySet sendPublicKeys() {
    return this.delegate.sharePublicKeys();
  }

  boolean receivePublicKeys(KeyCeremony2.PublicKeySet publicKeys) {
    return this.delegate.receivePublicKeys(publicKeys);
  }

  @Nullable
  KeyCeremony2.PartialKeyBackup sendPartialKeyBackup(String otherId) {
    return this.delegate.sendPartialKeyBackup(otherId);
  }
  KeyCeremony2.PartialKeyVerification verifyPartialKeyBackup(KeyCeremony2.PartialKeyBackup backup) {
    return this.delegate.verifyPartialKeyBackup(backup);
  }

  KeyCeremony2.PartialKeyChallengeResponse sendBackupChallenge(String guardian_id) {
    return this.delegate.sendBackupChallenge(guardian_id);
  }

  Group.ElementModP sendJointPublicKey() {
    return this.delegate.publishJointKey();
  }
}
