package com.sunya.electionguard.standard;

import com.sunya.electionguard.Group;
import com.sunya.electionguard.SchnorrProof;
import net.jqwik.api.Example;

import java.util.Optional;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth8.assertThat;

public class TestGuardian {
  private static final String SENDER_GUARDIAN_ID = "Test GuardianBuilder 1";
  private static final String RECIPIENT_GUARDIAN_ID = "Test GuardianBuilder 2";
  private static final String ALTERNATE_VERIFIER_GUARDIAN_ID = "Test GuardianBuilder 3";
  private static final int SENDER_SEQUENCE_ORDER = 1;
  private static final int RECIPIENT_SEQUENCE_ORDER = 2;
  private static final int ALTERNATE_VERIFIER_SEQUENCE_ORDER = 3;
  private static final int NUMBER_OF_GUARDIANS = 2;
  private static final int QUORUM = 2;

  @Example
  public void test_share_public_keys() {
    Guardian guardian = Guardian.createForTesting(SENDER_GUARDIAN_ID, SENDER_SEQUENCE_ORDER, NUMBER_OF_GUARDIANS, QUORUM,null);

    KeyCeremony.ElectionPublicKey public_keys = guardian.share_key();
    assertThat(public_keys).isNotNull();
    assertThat(public_keys.owner_id()).isEqualTo(SENDER_GUARDIAN_ID);
    assertThat(public_keys.sequence_order()).isEqualTo(SENDER_SEQUENCE_ORDER);
    assertThat(public_keys.sequence_order()).isEqualTo(SENDER_SEQUENCE_ORDER);
    assertThat(public_keys.sequence_order()).isEqualTo(SENDER_SEQUENCE_ORDER);
  }

  @Example
  public void test_save_guardian_public_keys() {
    Guardian guardian = Guardian.createForTesting(SENDER_GUARDIAN_ID, SENDER_SEQUENCE_ORDER, NUMBER_OF_GUARDIANS, QUORUM, null);
    Guardian other_guardian = Guardian.createForTesting(RECIPIENT_GUARDIAN_ID, RECIPIENT_SEQUENCE_ORDER, NUMBER_OF_GUARDIANS, QUORUM, null);

    KeyCeremony.ElectionPublicKey other_key = other_guardian.share_key();
    guardian.save_guardian_key(other_key);

    assertThat(guardian.all_guardian_keys_received()).isTrue();
  }

  @Example
  public void test_all_public_keys_received() {
    Guardian guardian = Guardian.createForTesting(SENDER_GUARDIAN_ID, SENDER_SEQUENCE_ORDER, NUMBER_OF_GUARDIANS, QUORUM, null);
    Guardian other_guardian = Guardian.createForTesting(RECIPIENT_GUARDIAN_ID, RECIPIENT_SEQUENCE_ORDER, NUMBER_OF_GUARDIANS, QUORUM, null);

    KeyCeremony.ElectionPublicKey other_key = other_guardian.share_key();

    assertThat(guardian.all_guardian_keys_received()).isFalse();
    guardian.save_guardian_key(other_key);

    assertThat(guardian.all_guardian_keys_received()).isTrue();
  }

  @Example
  public void test_share_election_public_key() {
    Guardian guardian = Guardian.createForTesting(SENDER_GUARDIAN_ID, SENDER_SEQUENCE_ORDER, NUMBER_OF_GUARDIANS, QUORUM, null);
    KeyCeremony.ElectionPublicKey public_key = guardian.share_key();

    assertThat(public_key).isNotNull();
    assertThat(public_key.key()).isNotNull();
    assertThat(public_key.owner_id()).isEqualTo(SENDER_GUARDIAN_ID);
    assertThat(public_key.sequence_order()).isEqualTo(SENDER_SEQUENCE_ORDER);
    assertThat(public_key.coefficient_commitments().isEmpty());
    assertThat(public_key.coefficient_proofs().isEmpty());
  }

  @Example
  public void test_save_election_public_key() {
    Guardian guardian = Guardian.createForTesting(SENDER_GUARDIAN_ID, SENDER_SEQUENCE_ORDER, NUMBER_OF_GUARDIANS, QUORUM, null);
    Guardian other_guardian = Guardian.createForTesting(RECIPIENT_GUARDIAN_ID, RECIPIENT_SEQUENCE_ORDER, NUMBER_OF_GUARDIANS, QUORUM, null);
    KeyCeremony.ElectionPublicKey public_key = other_guardian.share_key();

    guardian.save_guardian_key(public_key);

    assertThat(guardian.all_guardian_keys_received()).isTrue();
  }

  @Example
  public void test_all_election_public_keys_received() {
    Guardian guardian = Guardian.createForTesting(SENDER_GUARDIAN_ID, SENDER_SEQUENCE_ORDER, NUMBER_OF_GUARDIANS, QUORUM, null);
    Guardian other_guardian = Guardian.createForTesting(RECIPIENT_GUARDIAN_ID, RECIPIENT_SEQUENCE_ORDER, NUMBER_OF_GUARDIANS, QUORUM, null);
    KeyCeremony.ElectionPublicKey public_key = other_guardian.share_key();

    assertThat(guardian.all_guardian_keys_received()).isFalse();
    guardian.save_guardian_key(public_key);

    assertThat(guardian.all_guardian_keys_received()).isTrue();
  }

  @Example
  public void test_generate_election_partial_key_backups() {
    Guardian guardian = Guardian.createForTesting(SENDER_GUARDIAN_ID, SENDER_SEQUENCE_ORDER, NUMBER_OF_GUARDIANS, QUORUM, null);
    Guardian other_guardian = Guardian.createForTesting(RECIPIENT_GUARDIAN_ID, RECIPIENT_SEQUENCE_ORDER, NUMBER_OF_GUARDIANS, QUORUM, null);
    Optional<KeyCeremony.ElectionPartialKeyBackup> empty_key_backup = other_guardian.share_election_partial_key_backup(RECIPIENT_GUARDIAN_ID);

    assertThat(empty_key_backup).isEmpty();

    guardian.save_guardian_key(other_guardian.share_key());
    guardian.generate_election_partial_key_backups();
    Optional<KeyCeremony.ElectionPartialKeyBackup> key_backupO = guardian.share_election_partial_key_backup(RECIPIENT_GUARDIAN_ID);
    assertThat(key_backupO).isPresent();
    KeyCeremony.ElectionPartialKeyBackup key_backup = key_backupO.get();

    assertThat(key_backup.value()).isNotNull();
    assertThat(key_backup.owner_id()).isEqualTo(SENDER_GUARDIAN_ID);
    assertThat(key_backup.designated_id()).isEqualTo(RECIPIENT_GUARDIAN_ID);
  }

  @Example
  public void test_share_election_partial_key_backup() {
    Guardian guardian = Guardian.createForTesting(SENDER_GUARDIAN_ID, SENDER_SEQUENCE_ORDER, NUMBER_OF_GUARDIANS, QUORUM, null);
    Guardian other_guardian = Guardian.createForTesting(RECIPIENT_GUARDIAN_ID, RECIPIENT_SEQUENCE_ORDER, NUMBER_OF_GUARDIANS, QUORUM, null);

    guardian.save_guardian_key(other_guardian.share_key());
    guardian.generate_election_partial_key_backups();

    Optional<KeyCeremony.ElectionPartialKeyBackup> key_backupO = guardian.share_election_partial_key_backup(RECIPIENT_GUARDIAN_ID);
    assertThat(key_backupO).isPresent();
    KeyCeremony.ElectionPartialKeyBackup key_backup = key_backupO.get();

    assertThat(key_backup.value()).isNotNull();
    assertThat(key_backup.owner_id()).isEqualTo(SENDER_GUARDIAN_ID);
    assertThat(key_backup.designated_id()).isEqualTo(RECIPIENT_GUARDIAN_ID);
  }

  @Example
  public void test_save_election_partial_key_backup() {
    Guardian guardian = Guardian.createForTesting(SENDER_GUARDIAN_ID, SENDER_SEQUENCE_ORDER, NUMBER_OF_GUARDIANS, QUORUM, null);
    Guardian other_guardian = Guardian.createForTesting(RECIPIENT_GUARDIAN_ID, RECIPIENT_SEQUENCE_ORDER, NUMBER_OF_GUARDIANS, QUORUM, null);

    guardian.save_guardian_key(other_guardian.share_key());
    guardian.generate_election_partial_key_backups();

    Optional<KeyCeremony.ElectionPartialKeyBackup> key_backupO = guardian.share_election_partial_key_backup(RECIPIENT_GUARDIAN_ID);
    assertThat(key_backupO).isPresent();
    KeyCeremony.ElectionPartialKeyBackup key_backup = key_backupO.get();

    other_guardian.save_election_partial_key_backup(key_backup);
    assertThat(other_guardian.all_election_partial_key_backups_received()).isTrue();
  }

  @Example
  public void test_all_election_partial_key_backups_received() {
    Guardian guardian = Guardian.createForTesting(SENDER_GUARDIAN_ID, SENDER_SEQUENCE_ORDER, NUMBER_OF_GUARDIANS, QUORUM, null);
    Guardian other_guardian = Guardian.createForTesting(RECIPIENT_GUARDIAN_ID, RECIPIENT_SEQUENCE_ORDER, NUMBER_OF_GUARDIANS, QUORUM, null);

    guardian.save_guardian_key(other_guardian.share_key());
    guardian.generate_election_partial_key_backups();

    Optional<KeyCeremony.ElectionPartialKeyBackup> key_backupO = guardian.share_election_partial_key_backup(RECIPIENT_GUARDIAN_ID);
    assertThat(key_backupO).isPresent();
    KeyCeremony.ElectionPartialKeyBackup key_backup = key_backupO.get();

    assertThat(other_guardian.all_election_partial_key_backups_received()).isFalse();
    other_guardian.save_election_partial_key_backup(key_backup);
    assertThat(other_guardian.all_election_partial_key_backups_received()).isTrue();
  }

  @Example
  public void test_verify_election_partial_key_backup() {
    Guardian guardian = Guardian.createForTesting(SENDER_GUARDIAN_ID, SENDER_SEQUENCE_ORDER, NUMBER_OF_GUARDIANS, QUORUM, null);
    Guardian other_guardian = Guardian.createForTesting(RECIPIENT_GUARDIAN_ID, RECIPIENT_SEQUENCE_ORDER, NUMBER_OF_GUARDIANS, QUORUM, null);

    // Round 1
    guardian.save_guardian_key(other_guardian.share_key());
    other_guardian.save_guardian_key(guardian.share_key());

    // Round 2
    guardian.generate_election_partial_key_backups();
    KeyCeremony.ElectionPartialKeyBackup key_backup = guardian.share_election_partial_key_backup(RECIPIENT_GUARDIAN_ID).orElseThrow();
    other_guardian.save_election_partial_key_backup(key_backup);

    Optional<KeyCeremony.ElectionPartialKeyVerification> verificationO =
            other_guardian.verify_election_partial_key_backup(SENDER_GUARDIAN_ID);
    assertThat(verificationO).isPresent();
    KeyCeremony.ElectionPartialKeyVerification verification = verificationO.get();

    assertThat(verification.owner_id()).isEqualTo(SENDER_GUARDIAN_ID);
    assertThat(verification.designated_id()).isEqualTo(RECIPIENT_GUARDIAN_ID);
    assertThat(verification.verifier_id()).isEqualTo(RECIPIENT_GUARDIAN_ID);
    assertThat(verification.verified()).isTrue();
  }

  @Example
  public void test_verify_election_partial_key_challenge() {
    Guardian guardian = Guardian.createForTesting(SENDER_GUARDIAN_ID, SENDER_SEQUENCE_ORDER, NUMBER_OF_GUARDIANS, QUORUM, null);
    Guardian recipient_guardian = Guardian.createForTesting(RECIPIENT_GUARDIAN_ID, RECIPIENT_SEQUENCE_ORDER, NUMBER_OF_GUARDIANS, QUORUM, null);
    Guardian alternate_guardian = Guardian.createForTesting(ALTERNATE_VERIFIER_GUARDIAN_ID, ALTERNATE_VERIFIER_SEQUENCE_ORDER, NUMBER_OF_GUARDIANS, QUORUM, null);

    guardian.save_guardian_key(recipient_guardian.share_key());
    guardian.generate_election_partial_key_backups();
    Optional<KeyCeremony.ElectionPartialKeyChallenge> challenge =
            guardian.publish_election_backup_challenge(RECIPIENT_GUARDIAN_ID);

    assertThat(challenge).isPresent();
    KeyCeremony.ElectionPartialKeyVerification verification =
            alternate_guardian.verify_election_partial_key_challenge(challenge.get());

    assertThat(verification.owner_id()).isEqualTo(SENDER_GUARDIAN_ID);
    assertThat(verification.designated_id()).isEqualTo(RECIPIENT_GUARDIAN_ID);
    assertThat(verification.verifier_id()).isEqualTo(ALTERNATE_VERIFIER_GUARDIAN_ID);
    assertThat(verification.verified()).isTrue();
  }

  @Example
  public void test_publish_election_backup_challenge() {
    Guardian guardian = Guardian.createForTesting(SENDER_GUARDIAN_ID, SENDER_SEQUENCE_ORDER, NUMBER_OF_GUARDIANS, QUORUM, null);
    Guardian recipient_guardian = Guardian.createForTesting(RECIPIENT_GUARDIAN_ID, RECIPIENT_SEQUENCE_ORDER, NUMBER_OF_GUARDIANS, QUORUM, null);

    guardian.save_guardian_key(recipient_guardian.share_key());
    guardian.generate_election_partial_key_backups();

    Optional<KeyCeremony.ElectionPartialKeyChallenge> challengeO = guardian.publish_election_backup_challenge(RECIPIENT_GUARDIAN_ID);
    assertThat(challengeO).isPresent();
    KeyCeremony.ElectionPartialKeyChallenge challenge = challengeO.get();

    assertThat(challenge.value()).isNotNull();
    assertThat(challenge.owner_id()).isEqualTo(SENDER_GUARDIAN_ID);
    assertThat(challenge.designated_id()).isEqualTo(RECIPIENT_GUARDIAN_ID);
    assertThat(challenge.coefficient_commitments().size()).isEqualTo(QUORUM);
    assertThat(challenge.coefficient_proofs().size()).isEqualTo(QUORUM);
    for (SchnorrProof proof : challenge.coefficient_proofs()) {
      assertThat(proof.isValidVer1()).isTrue();
    }
  }

  @Example
  public void test_save_election_partial_key_verification() {
    Guardian guardian = Guardian.createForTesting(SENDER_GUARDIAN_ID, SENDER_SEQUENCE_ORDER, NUMBER_OF_GUARDIANS, QUORUM, null);
    Guardian other_guardian = Guardian.createForTesting(RECIPIENT_GUARDIAN_ID, RECIPIENT_SEQUENCE_ORDER, NUMBER_OF_GUARDIANS, QUORUM, null);

    guardian.save_guardian_key(other_guardian.share_key());
    other_guardian.save_guardian_key(guardian.share_key());
    guardian.generate_election_partial_key_backups();

    Optional<KeyCeremony.ElectionPartialKeyBackup> key_backup = guardian.share_election_partial_key_backup(RECIPIENT_GUARDIAN_ID);
    assertThat(key_backup).isPresent();
    other_guardian.save_election_partial_key_backup(key_backup.get());
    Optional<KeyCeremony.ElectionPartialKeyVerification> verification = other_guardian.verify_election_partial_key_backup(SENDER_GUARDIAN_ID);

    assertThat(verification).isPresent();
    guardian.save_election_partial_key_verification(verification.get());

    assertThat(guardian.all_election_partial_key_backups_verified()).isTrue();
  }

  @Example
  public void test_all_election_partial_key_backups_verified() {
    Guardian guardian = Guardian.createForTesting(SENDER_GUARDIAN_ID, SENDER_SEQUENCE_ORDER, NUMBER_OF_GUARDIANS, QUORUM, null);
    Guardian other_guardian = Guardian.createForTesting(RECIPIENT_GUARDIAN_ID, RECIPIENT_SEQUENCE_ORDER, NUMBER_OF_GUARDIANS, QUORUM, null);

    // Round 1
    guardian.save_guardian_key(other_guardian.share_key());
    other_guardian.save_guardian_key(guardian.share_key());

    // Round 2
    guardian.generate_election_partial_key_backups();
    Optional<KeyCeremony.ElectionPartialKeyBackup> key_backup = guardian.share_election_partial_key_backup(RECIPIENT_GUARDIAN_ID);
    assertThat(key_backup).isPresent();
    other_guardian.save_election_partial_key_backup(key_backup.get());
    Optional<KeyCeremony.ElectionPartialKeyVerification> verification = other_guardian.verify_election_partial_key_backup(SENDER_GUARDIAN_ID);
    assertThat(verification).isPresent();
    guardian.save_election_partial_key_verification(verification.get());

    boolean all_saved = guardian.all_election_partial_key_backups_verified();
    assertThat(all_saved).isTrue();
  }

  @Example
  public void test_publish_joint_key() {
    Guardian guardian = Guardian.createForTesting(SENDER_GUARDIAN_ID, SENDER_SEQUENCE_ORDER, NUMBER_OF_GUARDIANS, QUORUM, null);
    Guardian other_guardian = Guardian.createForTesting(RECIPIENT_GUARDIAN_ID, RECIPIENT_SEQUENCE_ORDER, NUMBER_OF_GUARDIANS, QUORUM, null);

    guardian.save_guardian_key(other_guardian.share_key());
    other_guardian.save_guardian_key(guardian.share_key());

    guardian.generate_election_partial_key_backups();

    Optional<KeyCeremony.ElectionPartialKeyBackup> key_backup = guardian.share_election_partial_key_backup(RECIPIENT_GUARDIAN_ID);
    assertThat(key_backup).isPresent();
    other_guardian.save_election_partial_key_backup(key_backup.get());
    Optional<KeyCeremony.ElectionPartialKeyVerification> verification = other_guardian.verify_election_partial_key_backup(SENDER_GUARDIAN_ID);
    assertThat(verification).isPresent();

    Optional<Group.ElementModP> joint_key = guardian.publish_joint_key();
    assertThat(joint_key).isEmpty();

    guardian.save_guardian_key(other_guardian.share_key());
    joint_key = guardian.publish_joint_key();
    assertThat(joint_key).isEmpty();

    guardian.save_election_partial_key_verification(verification.get());
    joint_key = guardian.publish_joint_key();
    assertThat(joint_key).isPresent();
    assertThat(joint_key).isNotEqualTo(guardian.share_key().key());
  }

}
