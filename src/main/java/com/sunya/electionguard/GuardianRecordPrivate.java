package com.sunya.electionguard;

import com.google.auto.value.AutoValue;

import java.util.Collection;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Unpublishable private record containing information per Guardian
 */
@AutoValue
public abstract class GuardianRecordPrivate {

  /**
   * Private election Key pair of this guardian.
   */
  public abstract KeyCeremony.ElectionKeyPair election_keys();

  /**
   * Private auxiliary key pair of this guardian.
   */
  public abstract Auxiliary.KeyPair auxiliary_keys();

  /**
   * This guardian's partial key backups that will be shared to other guardians.
   */
  public abstract Map<String, KeyCeremony.ElectionPartialKeyBackup> backups_to_share();

  /**
   * Received auxiliary public keys that are shared with this guardian.
   */
  public abstract Map<String, Auxiliary.PublicKey> guardian_auxiliary_public_keys();

  /**
   * Received election public keys that are shared with this guardian.
   */
  public abstract Map<String, KeyCeremony.ElectionPublicKey> guardian_election_public_keys();

  /**
   * Received partial key backups that are shared with this guardian.
   */
  public abstract Map<String, KeyCeremony.ElectionPartialKeyBackup> guardian_election_partial_key_backups();

  /**
   * Verifications of other guardian's backups.
   */
  public abstract Map<String, KeyCeremony.ElectionPartialKeyVerification> guardian_election_partial_key_verifications();

  public static GuardianRecordPrivate create(
          KeyCeremony.ElectionKeyPair election_keys,
          Auxiliary.KeyPair auxiliary_keys,
          Map<String, KeyCeremony.ElectionPartialKeyBackup> backups_to_share,
          Map<String, Auxiliary.PublicKey> guardian_auxiliary_public_keys,
          Map<String, KeyCeremony.ElectionPublicKey> guardian_election_public_keys,
          Map<String, KeyCeremony.ElectionPartialKeyBackup> guardian_election_partial_key_backups,
          Map<String, KeyCeremony.ElectionPartialKeyVerification> guardian_election_partial_key_verifications) {

    return new AutoValue_GuardianRecordPrivate(election_keys, auxiliary_keys, backups_to_share, guardian_auxiliary_public_keys,
            guardian_election_public_keys, guardian_election_partial_key_backups, guardian_election_partial_key_verifications);
  }

  public static GuardianRecordPrivate create(
          KeyCeremony.ElectionKeyPair election_keys,
          Auxiliary.KeyPair auxiliary_keys,
          Collection<KeyCeremony.ElectionPartialKeyBackup> backups_to_share,
          Collection<Auxiliary.PublicKey> guardian_auxiliary_public_keys,
          Collection<KeyCeremony.ElectionPublicKey> guardian_election_public_keys,
          Collection<KeyCeremony.ElectionPartialKeyBackup> guardian_election_partial_key_backups,
          Collection<KeyCeremony.ElectionPartialKeyVerification> guardian_election_partial_key_verifications) {

    return new AutoValue_GuardianRecordPrivate(
            election_keys,
            auxiliary_keys,
            backups_to_share.stream().collect(Collectors.toMap(o -> o.owner_id(), o -> o)),
            guardian_auxiliary_public_keys.stream().collect(Collectors.toMap(o -> o.owner_id, o -> o)),
            guardian_election_public_keys.stream().collect(Collectors.toMap(o -> o.owner_id(), o -> o)),
            guardian_election_partial_key_backups.stream().collect(Collectors.toMap(o -> o.owner_id(), o -> o)),
            guardian_election_partial_key_verifications.stream().collect(Collectors.toMap(o -> o.owner_id(), o -> o)));
  }

  public String guardian_id() {
    return election_keys().owner_id();
  }

}
