package com.sunya.electionguard.guardian;

import com.sunya.electionguard.Group;

import javax.annotation.Nullable;

public interface KeyCeremonyTrusteeIF {
  String id();

  int coordinate();

  int quorum(); // LOOK needed?

  @Nullable
  KeyCeremony2.PublicKeySet sendPublicKeys();

  boolean receivePublicKeys(KeyCeremony2.PublicKeySet keyset);

  @Nullable
  KeyCeremony2.PartialKeyBackup sendPartialKeyBackup(String guardianId);

  @Nullable
  KeyCeremony2.PartialKeyVerification verifyPartialKeyBackup(KeyCeremony2.PartialKeyBackup backup);

  @Nullable
  KeyCeremony2.PartialKeyChallengeResponse sendBackupChallenge(String guardianId);

  @Nullable
  Group.ElementModP sendJointPublicKey();
}
