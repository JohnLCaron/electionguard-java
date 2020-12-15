package sunya.electionguard;

import java.util.*;

import static sunya.electionguard.KeyCeremony.*;

/**
 * Mutable KeyCeremonyMediator for assisting communication between guardians.
 */
public class KeyCeremonyMediator {
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
   * :return: True if all guardians in attendance
   */
  boolean all_guardians_in_attendance() {
    return this.all_auxiliary_public_keys_available() && this.all_election_public_keys_available();
  }

  /**
   * Share a list of all the guardians in attendance
   * :return: list of guardians ids
   */
  Iterable<String> share_guardians_in_attendance() {
    return this._election_public_keys.keySet();
  }

  /**
   * Receive auxiliary public key from guardian
   * :param public_key: Auxiliary public key
   */
  void receive_auxiliary_public_key(Auxiliary.PublicKey public_key) {
    this._auxiliary_public_keys.put(public_key.owner_id, public_key);
  }

  /*
          True if all auxiliary public key for all guardians available
        :return: All auxiliary public backups for all guardians available
   */
  boolean all_auxiliary_public_keys_available() {
    return this._auxiliary_public_keys.size() == this.ceremony_details.number_of_guardians();
  }

  /**
   * Share all currently stored auxiliary public keys for all guardians
   * :return: list of auxiliary public keys
   *
   * @return
   */
  Iterable<Auxiliary.PublicKey> share_auxiliary_public_keys() {
    return this._auxiliary_public_keys.values();
  }

  /**
   * Receive election public key from guardian
   * :param public_key: election public key
   */
  void receive_election_public_key(ElectionPublicKey public_key) {
    this._election_public_keys.put(public_key.owner_id(), public_key);
  }

  /**
   * True if all election public keys for all guardians available
   * :return: All election public keys for all guardians available
   */
  boolean all_election_public_keys_available() {
    return (this._election_public_keys.size()) == this.ceremony_details.number_of_guardians();
  }

  /**
   * Share all currently stored election public keys for all guardians
   * :return: list of election public keys
   */
  Iterable<ElectionPublicKey> share_election_public_keys() {
    return this._election_public_keys.values();
  }

  /**
   * Receive election partial key backup from guardian
   * :param backup: Election partial key backup
   * :return: boolean indicating success or failure
   *
   * @return
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
   * :return: All election partial key backups for all guardians available
   */
  boolean all_election_partial_key_backups_available() {
    int required_backups_per_guardian = this.ceremony_details.number_of_guardians() - 1;
    return this._election_partial_key_backups.size() ==
            required_backups_per_guardian * this.ceremony_details.number_of_guardians();
  }

  /*
          Share all election partial key backups for designated guardian
        :param guardian_id: Recipients guardian id
        :return: List of guardians designated backups
   */
  List<ElectionPartialKeyBackup> share_election_partial_key_backups_to_guardian(String guardian_id) {
    List<ElectionPartialKeyBackup> backups = new ArrayList();
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
   * Receive election partial key verification from guardian
   * :param verification: Election partial key verification
   *
   * @return
   */
  void receive_election_partial_key_verification(ElectionPartialKeyVerification verification) {
    if (!verification.owner_id().equals(verification.designated_id())) {
      this._election_partial_key_verifications.put(
              GuardianPair.create(verification.owner_id(), verification.designated_id()),
              verification);
    }
  }

  /**
   * True if all election partial key verifications recieved.
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

  /**         Receive an election partial key challenge from a guardian with a failed verification. */
   void receive_election_partial_key_challenge(ElectionPartialKeyChallenge challenge) {
     this._election_partial_key_challenges.put(
             GuardianPair.create(challenge.owner_id(), challenge.designated_id()), challenge);
   }

   /** Share all open election partial key challenges with guardians. */
  List<ElectionPartialKeyChallenge> share_open_election_partial_key_challenges() {
    return new ArrayList<>(this._election_partial_key_challenges.values());
  }

}
