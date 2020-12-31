package com.sunya.electionguard;

import com.google.common.flogger.FluentLogger;

import javax.annotation.Nullable;
import java.util.*;

import static com.sunya.electionguard.KeyCeremony.*;

/**
 * Mutable KeyCeremonyMediator for assisting communication between guardians.
 */
public class KeyCeremonyMediator {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  CeremonyDetails ceremony_details;
  final Map<String, Auxiliary.PublicKey> _auxiliary_public_keys;
  final Map<String, ElectionPublicKey> _election_public_keys;
  final Map<GuardianPair, ElectionPartialKeyBackup> _election_partial_key_backups;
  final Map<GuardianPair, ElectionPartialKeyVerification> _election_partial_key_verifications;
  final Map<GuardianPair, ElectionPartialKeyChallenge> _election_partial_key_challenges;
  final List<Guardian> _guardians;

  KeyCeremonyMediator(CeremonyDetails ceremony_details) {
    this.ceremony_details = ceremony_details;
    this._auxiliary_public_keys = new HashMap<>();
    this._election_public_keys = new HashMap<>();
    this._election_partial_key_backups = new HashMap<>();
    this._election_partial_key_verifications = new HashMap<>();
    this._election_partial_key_challenges = new HashMap<>();
    this._guardians = new ArrayList<>();
  }

  /**
   * Announce the guardian as present and participating the Key Ceremony .
   */
  void announce(Guardian guardian) {
    this.confirm_presence_of_guardian(guardian.share_public_keys());
    this._guardians.add(guardian);

    // When all guardians have announced, share the public keys among them
    if (this.all_guardians_in_attendance()) {
      for (Guardian sender : this._guardians) {
        for (Guardian recipient : this._guardians) {
          if (!sender.object_id.equals(recipient.object_id)) {
            recipient.save_guardian_public_keys(sender.share_public_keys());
          }
        }
      }
    }
  }

  /**
   * Orchestrate the KLey Ceremony by sharing keys among the announced guardians.
   *
   * @param encryptor: Auxiliary encrypt function
   * @return a collection of guardians, or None if there is an error
   */
  Optional<List<Guardian>> orchestrate(@Nullable Auxiliary.Encryptor encryptor) {
    if (!this.all_guardians_in_attendance()) {
      return Optional.empty();
    }
    if (encryptor == null) {
      encryptor = Rsa::encrypt;
    }

    // Partial Key Backup Generation
    for (Guardian guardian : this._guardians) {
      guardian.generate_election_partial_key_backups(encryptor);
    }

    // Share Partial Key Backup
    for (Guardian sender : this._guardians) {
      for (Guardian recipient : this._guardians) {
        if (!sender.object_id.equals(recipient.object_id)) {
          Optional<ElectionPartialKeyBackup> backup = sender.share_election_partial_key_backup(recipient.object_id);

          if (backup.isPresent()) {
            this.receive_election_partial_key_backup(backup.get());
          } else {
            logger.atInfo().log("orchestrate failed sender {sender.object_id} could not share backup with recipient: {recipient.object_id}");
            return Optional.empty();
          }
        }
      }
    }

    // Save the backups
    if (this.all_election_partial_key_backups_available()) {
      for (Guardian recipient_guardian : this._guardians) {
        List<ElectionPartialKeyBackup> backups = this.share_election_partial_key_backups_to_guardian(recipient_guardian.object_id);
        for (ElectionPartialKeyBackup backup : backups) {
          recipient_guardian.save_election_partial_key_backup(backup);
        }
      }
    }
    return Optional.of(this._guardians);
  }

  /**
   * Verify that the guardians correctly shared keys
   *
   * @param decryptor: Auxiliary decrypt function
   * @return True if verification succeeds, else False
   */
  boolean verify(@Nullable Auxiliary.Decryptor decryptor) {
    if (decryptor == null) {
      decryptor = Rsa::decrypt;
    }

    for (Guardian recipient : this._guardians) {
      for (Guardian sender : this._guardians) {
        if (!sender.object_id.equals(recipient.object_id)) {
          Optional<ElectionPartialKeyVerification> verification = recipient.verify_election_partial_key_backup(sender.object_id, decryptor);

          if (verification.isPresent()) {
            this.receive_election_partial_key_verification(verification.get());
          } else {
            logger.atInfo().log("verify failed recipient %s could not verify backup from sender: %s",
                    recipient.object_id, sender.object_id);
            return false;
          }
        }
      }
    }

    return this.all_election_partial_key_verifications_received() &&
            this.all_election_partial_key_backups_verified();
  }

  /**
   * Reset mediator to initial state
   *
   * @param ceremony_details: Ceremony details of election
   */
  void reset(CeremonyDetails ceremony_details) {
    this.ceremony_details = ceremony_details;
    this._auxiliary_public_keys.clear();
    this._election_public_keys.clear();
    this._election_partial_key_backups.clear();
    this._election_partial_key_challenges.clear();
    this._election_partial_key_verifications.clear();
    this._guardians.clear();
  }

  /**
   * Confirm presence of guardian by passing their public key set
   *
   * @param public_key_set: Public key set
   */
  void confirm_presence_of_guardian(PublicKeySet public_key_set) {
    this.receive_auxiliary_public_key(
            new Auxiliary.PublicKey(
                    public_key_set.owner_id(),
                    public_key_set.sequence_order(),
                    public_key_set.auxiliary_public_key()));

    this.receive_election_public_key(
            ElectionPublicKey.create(
                    public_key_set.owner_id(),
                    public_key_set.election_public_key_proof(),
                    public_key_set.election_public_key()));
  }

  /**
   * Check the attendance of all the guardians expected
   *
   * @return True if all guardians in attendance
   */
  boolean all_guardians_in_attendance() {
    return this.all_auxiliary_public_keys_available() && this.all_election_public_keys_available();
  }

  /**
   * Share a list of all the guardians in attendance
   *
   * @return list of guardians ids
   */
  Iterable<String> share_guardians_in_attendance() {
    return this._election_public_keys.keySet();
  }

  /**
   * Receive auxiliary public key from guardian
   *
   * @param public_key: Auxiliary public key
   */
  void receive_auxiliary_public_key(Auxiliary.PublicKey public_key) {
    this._auxiliary_public_keys.put(public_key.owner_id, public_key);
  }

  /**
   * True if all auxiliary public key for all guardians available
   *
   * @return All auxiliary public backups for all guardians available
   */
  boolean all_auxiliary_public_keys_available() {
    return this._auxiliary_public_keys.size() == this.ceremony_details.number_of_guardians();
  }

  /**
   * Share all currently stored auxiliary public keys for all guardians.
   *
   * @return list of auxiliary public keys
   */
  Iterable<Auxiliary.PublicKey> share_auxiliary_public_keys() {
    return this._auxiliary_public_keys.values();
  }

  /**
   * Receive election public key from guardian.
   *
   * @param public_key election public key
   */
  void receive_election_public_key(ElectionPublicKey public_key) {
    this._election_public_keys.put(public_key.owner_id(), public_key);
  }

  /**
   * True if all election public keys for all guardians available
   *
   * @return All election public keys for all guardians available
   */
  boolean all_election_public_keys_available() {
    return (this._election_public_keys.size()) == this.ceremony_details.number_of_guardians();
  }

  /**
   * Share all currently stored election public keys for all guardians
   *
   * @return list of election public keys
   */
  Iterable<ElectionPublicKey> share_election_public_keys() {
    return this._election_public_keys.values();
  }

  /**
   * Receive election partial key backup from guardian
   *
   * @param backup: Election partial key backup
   * @return boolean indicating success or failure
   */
  boolean receive_election_partial_key_backup(ElectionPartialKeyBackup backup) {
    if (backup.owner_id().equals(backup.designated_id())) {
      return false;
    }
    this._election_partial_key_backups.put(GuardianPair.create(backup.owner_id(), backup.designated_id()), backup);
    return true;
  }

  /**
   * True if all election partial key backups for all guardians available
   *
   * @return All election partial key backups for all guardians available
   */
  boolean all_election_partial_key_backups_available() {
    int required_backups_per_guardian = this.ceremony_details.number_of_guardians() - 1;
    return this._election_partial_key_backups.size() ==
            required_backups_per_guardian * this.ceremony_details.number_of_guardians();
  }

  /**
   * Share all election partial key backups for designated guardian
   *
   * @param guardian_id Recipients guardian id
   * @return List of guardians designated backups
   */
  List<ElectionPartialKeyBackup> share_election_partial_key_backups_to_guardian(String guardian_id) {
    List<ElectionPartialKeyBackup> backups = new ArrayList<>();
    for (String current_guardian_id : this.share_guardians_in_attendance()) {
      if (!guardian_id.equals(current_guardian_id)) {
        ElectionPartialKeyBackup backup = this._election_partial_key_backups.get(
                GuardianPair.create(current_guardian_id, guardian_id));
        if (backup != null) {
          backups.add(backup);
        }
      }
    }
    return backups;
  }

  /**
   * Receive election partial key verification from guardian.
   */
  void receive_election_partial_key_verification(ElectionPartialKeyVerification verification) {
    if (!verification.owner_id().equals(verification.designated_id())) {
      this._election_partial_key_verifications.put(
              GuardianPair.create(verification.owner_id(), verification.designated_id()),
              verification);
    }
  }

  /**
   * True if all election partial key verifications received.
   */
  boolean all_election_partial_key_verifications_received() {
    int required_verifications_per_guardian = this.ceremony_details.number_of_guardians() - 1;
    return this._election_partial_key_verifications.size() ==
            required_verifications_per_guardian * this.ceremony_details.number_of_guardians();
  }

  /**
   * True if all election partial key backups verified .
   */
  boolean all_election_partial_key_backups_verified() {
    if (!this.all_election_partial_key_verifications_received()) {
      return false;
    }
    for (ElectionPartialKeyVerification verification : this._election_partial_key_verifications.values()) {
      if (!verification.verified()) {
        return false;
      }
    }
    return true;
  }

  /**
   * Share list of guardians with failed partial key backup verifications.
   */
  List<GuardianPair> share_failed_partial_key_verifications() {
    List<GuardianPair> failed_verifications = new ArrayList<>();
    for (Map.Entry<GuardianPair, ElectionPartialKeyVerification> pair : this._election_partial_key_verifications.entrySet()) {
      if (!pair.getValue().verified()) {
        failed_verifications.add(pair.getKey());
      }
    }
    return failed_verifications;
  }

  /**
   * Share list of guardians with missing election partial key challenges.
   */
  List<GuardianPair> share_missing_election_partial_key_challenges() {
    List<GuardianPair> failed_verifications = new ArrayList<>(this.share_failed_partial_key_verifications());
    for (GuardianPair pair : this._election_partial_key_challenges.keySet()) {
      failed_verifications.remove(pair);
    }
    return failed_verifications;
  }

  /**
   * Receive an election partial key challenge from a guardian with a failed verification.
   */
  void receive_election_partial_key_challenge(ElectionPartialKeyChallenge challenge) {
    this._election_partial_key_challenges.put(
            GuardianPair.create(challenge.owner_id(), challenge.designated_id()), challenge);
  }

  /**
   * Share all open election partial key challenges with guardians.
   */
  List<ElectionPartialKeyChallenge> share_open_election_partial_key_challenges() {
    return new ArrayList<>(this._election_partial_key_challenges.values());
  }

  /**
   * Publish joint election key from the public keys of all guardians.
   */
  Optional<Group.ElementModP> publish_joint_key() {
    if (!this.all_election_public_keys_available()) {
      return Optional.empty();
    }
    if (!this.all_election_partial_key_backups_verified()) {
      return Optional.empty();
    }
    return Optional.of(KeyCeremony.combine_election_public_keys(this._election_public_keys));
  }

}
