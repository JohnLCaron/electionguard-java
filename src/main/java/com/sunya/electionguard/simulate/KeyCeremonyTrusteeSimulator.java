package com.sunya.electionguard.simulate;

import com.sunya.electionguard.Group;
import com.sunya.electionguard.keyceremony.KeyCeremony2;
import com.sunya.electionguard.keyceremony.KeyCeremonyTrustee;
import com.sunya.electionguard.keyceremony.KeyCeremonyTrusteeIF;

import java.util.Optional;

public class KeyCeremonyTrusteeSimulator implements KeyCeremonyTrusteeIF {
  public final KeyCeremonyTrustee delegate;

  public KeyCeremonyTrusteeSimulator(KeyCeremonyTrustee delegate) {
    this.delegate = delegate;
  }

  @Override
  public String id() {
    return this.delegate.id;
  }

  @Override
  public int coordinate() {
    return this.delegate.xCoordinate;
  }

  @Override
  public Optional<KeyCeremony2.PublicKeySet> sendPublicKeys() {
    return Optional.of(this.delegate.sharePublicKeys());
  }

  @Override
  public boolean receivePublicKeys(KeyCeremony2.PublicKeySet publicKeys) {
    return this.delegate.receivePublicKeys(publicKeys);
  }

  @Override
  public Optional<KeyCeremony2.PartialKeyBackup> sendPartialKeyBackup(String otherId) {
    return Optional.of(this.delegate.sendPartialKeyBackup(otherId));
  }

  @Override
  public Optional<KeyCeremony2.PartialKeyVerification> verifyPartialKeyBackup(KeyCeremony2.PartialKeyBackup backup) {
    return Optional.of(this.delegate.verifyPartialKeyBackup(backup));
  }

  @Override
  public Optional<KeyCeremony2.PartialKeyChallengeResponse> sendBackupChallenge(String guardian_id) {
    return Optional.of(this.delegate.sendBackupChallenge(guardian_id));
  }

  @Override
  public Optional<Group.ElementModP> sendJointPublicKey() {
    return Optional.of(this.delegate.publishJointKey());
  }
}
