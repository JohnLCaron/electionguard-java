package com.sunya.electionguard.guardian;

import com.sunya.electionguard.Group;

import javax.annotation.Nullable;

public class KeyCeremonyTrusteeSimulator implements KeyCeremonyTrusteeIF {
  final KeyCeremonyTrustee delegate;

  public KeyCeremonyTrusteeSimulator(KeyCeremonyTrustee delegate) {
    this.delegate = delegate;
  }

  public String id() {
    return this.delegate.id;
  }

  @Override
  public int coordinate() {
    return delegate.xCoordinate;
  }

  @Override
  public int quorum() {
    return delegate.quorum;
  }

  @Override
  public KeyCeremony2.PublicKeySet sendPublicKeys() {
    return this.delegate.sharePublicKeys();
  }

  @Override
  public boolean receivePublicKeys(KeyCeremony2.PublicKeySet publicKeys) {
    return this.delegate.receivePublicKeys(publicKeys);
  }

  @Nullable
  public KeyCeremony2.PartialKeyBackup sendPartialKeyBackup(String otherId) {
    return this.delegate.sendPartialKeyBackup(otherId);
  }

  @Override
  public KeyCeremony2.PartialKeyVerification verifyPartialKeyBackup(KeyCeremony2.PartialKeyBackup backup) {
    return this.delegate.verifyPartialKeyBackup(backup);
  }

  @Override
  public KeyCeremony2.PartialKeyChallengeResponse sendBackupChallenge(String guardian_id) {
    return this.delegate.sendBackupChallenge(guardian_id);
  }

  @Override
  public Group.ElementModP sendJointPublicKey() {
    return this.delegate.publishJointKey();
  }
}
