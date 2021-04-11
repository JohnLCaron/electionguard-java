package com.sunya.electionguard.keyceremony;

import com.sunya.electionguard.Group;

import java.util.Optional;

public interface KeyCeremonyTrusteeIF {
  String id();

  int coordinate();

  Optional<KeyCeremony2.PublicKeySet> sendPublicKeys();

  boolean receivePublicKeys(KeyCeremony2.PublicKeySet keyset);

  Optional<KeyCeremony2.PartialKeyBackup> sendPartialKeyBackup(String guardianId);

  Optional<KeyCeremony2.PartialKeyVerification> verifyPartialKeyBackup(KeyCeremony2.PartialKeyBackup backup);

  Optional<KeyCeremony2.PartialKeyChallengeResponse> sendBackupChallenge(String guardianId);

  Optional<Group.ElementModP> sendJointPublicKey();
}
