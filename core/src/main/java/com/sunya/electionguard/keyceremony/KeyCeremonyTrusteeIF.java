package com.sunya.electionguard.keyceremony;

import com.sunya.electionguard.Group;

import java.util.Optional;

/** The interface to KeyCeremonyTrustee. */
public interface KeyCeremonyTrusteeIF {
  /** The Trustee's id. */
  String id();

  /** The Trustee's x coordinate, aka sequence_order. */
  int xCoordinate();

  /** Each guardian shares their public keys with all the other guardians. */
  Optional<KeyCeremony2.PublicKeySet> sendPublicKeys();

  /** Receive publicKeys from another guardian, and validates. Return error message or empty string on success. */
  String receivePublicKeys(KeyCeremony2.PublicKeySet keyset);

  /** Each guardian shares partial key backups for the other guardians. */
  Optional<KeyCeremony2.PartialKeyBackup> sendPartialKeyBackup(String otherGuardianId);

  /** Each guardian verifies their own partial key backups. */
  Optional<KeyCeremony2.PartialKeyVerification> verifyPartialKeyBackup(KeyCeremony2.PartialKeyBackup backup);

  /** For any partial backup verification failures, the challenged guardian responds to the challenge. */
  Optional<KeyCeremony2.PartialKeyChallengeResponse> sendBackupChallenge(String guardianId);

  /** Each guardian computes their joint election public key. */
  Optional<Group.ElementModP> sendJointPublicKey();
}
