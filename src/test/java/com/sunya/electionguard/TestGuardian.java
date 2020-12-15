package com.sunya.electionguard;

import org.junit.Test;

import java.util.Optional;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth8.assertThat;

public class TestGuardian {

  private static final String SENDER_GUARDIAN_ID = "Test Guardian 1";
  private static final String RECIPIENT_GUARDIAN_ID = "Test Guardian 2";
  private static final String ALTERNATE_VERIFIER_GUARDIAN_ID = "Test Guardian 3";
  private static final int SENDER_SEQUENCE_ORDER = 1;
  private static final int RECIPIENT_SEQUENCE_ORDER = 2;
  private static final int ALTERNATE_VERIFIER_SEQUENCE_ORDER = 3;
  private static final int NUMBER_OF_GUARDIANS = 2;
  private static final int QUORUM = 2;

  static Auxiliary.Decryptor identity_auxiliary_decryptor = (m, k) -> Optional.of(new String(m.getBytes()));
  static Auxiliary.Encryptor identity_auxiliary_encryptor = (m, k) -> Optional.of(new Auxiliary.ByteString(m.getBytes()));

  @Test
  public void test_reset() {
    Guardian guardian = new Guardian(SENDER_GUARDIAN_ID, SENDER_SEQUENCE_ORDER, NUMBER_OF_GUARDIANS, QUORUM, null);
    int expected_number_of_guardians = 10;
    int expected_quorum = 4;

    guardian.reset(expected_number_of_guardians, expected_quorum);

    assertThat(expected_number_of_guardians).isEqualTo(guardian.ceremony_details.number_of_guardians());
    assertThat(expected_quorum).isEqualTo(guardian.ceremony_details.quorum());
  }

  @Test
  public void test_set_ceremony_details() {
    Guardian guardian = new Guardian(SENDER_GUARDIAN_ID, SENDER_SEQUENCE_ORDER, NUMBER_OF_GUARDIANS, QUORUM, null);
    int expected_number_of_guardians = 10;
    int expected_quorum = 4;

    guardian.set_ceremony_details(expected_number_of_guardians, expected_quorum);

    assertThat(expected_number_of_guardians).isEqualTo(guardian.ceremony_details.number_of_guardians());
    assertThat(expected_quorum).isEqualTo(guardian.ceremony_details.quorum());
  }

  @Test
  public void test_share_public_keys() {
    Guardian guardian = new Guardian(SENDER_GUARDIAN_ID, SENDER_SEQUENCE_ORDER, NUMBER_OF_GUARDIANS, QUORUM, null);

    KeyCeremony.PublicKeySet public_keys = guardian.share_public_keys();

    assertThat(public_keys).isNotNull();
    assertThat(public_keys.auxiliary_public_key()).isNotNull();
    assertThat(public_keys.election_public_key()).isNotNull();
    assertThat(public_keys.owner_id()).isEqualTo(SENDER_GUARDIAN_ID);
    assertThat(public_keys.sequence_order()).isEqualTo(SENDER_SEQUENCE_ORDER);
    assertThat(public_keys.election_public_key_proof().is_valid()).isTrue();
  }

  @Test
  public void test_save_guardian_public_keys() {
    Guardian guardian = new Guardian(SENDER_GUARDIAN_ID, SENDER_SEQUENCE_ORDER, NUMBER_OF_GUARDIANS, QUORUM, null);
    Guardian other_guardian = new Guardian(RECIPIENT_GUARDIAN_ID, RECIPIENT_SEQUENCE_ORDER, NUMBER_OF_GUARDIANS, QUORUM, null);

    KeyCeremony.PublicKeySet public_keys = other_guardian.share_public_keys();
    guardian.save_guardian_public_keys(public_keys);

    assertThat(guardian.all_auxiliary_public_keys_received()).isTrue();
    assertThat(guardian.all_public_keys_received()).isTrue();
  }

  @Test
  public void test_all_public_keys_received() {
    Guardian guardian = new Guardian(SENDER_GUARDIAN_ID, SENDER_SEQUENCE_ORDER, NUMBER_OF_GUARDIANS, QUORUM, null);
    Guardian other_guardian = new Guardian(RECIPIENT_GUARDIAN_ID, RECIPIENT_SEQUENCE_ORDER, NUMBER_OF_GUARDIANS, QUORUM, null);

    KeyCeremony.PublicKeySet public_keys = other_guardian.share_public_keys();

    assertThat(guardian.all_public_keys_received()).isFalse();
    guardian.save_guardian_public_keys(public_keys);

    assertThat(guardian.all_public_keys_received()).isTrue();
  }

  @Test
  public void test_generate_auxiliary_key_pair() {
    Guardian guardian = new Guardian(SENDER_GUARDIAN_ID, SENDER_SEQUENCE_ORDER, NUMBER_OF_GUARDIANS, QUORUM, null);
    Auxiliary.PublicKey first_public_key = guardian.share_auxiliary_public_key();

    guardian.generate_auxiliary_key_pair();
    Auxiliary.PublicKey second_public_key = guardian.share_auxiliary_public_key();

    assertThat(second_public_key).isNotNull();
    assertThat(second_public_key.key).isNotNull();
    assertThat(first_public_key.key).isNotEqualTo(second_public_key.key);
  }

  @Test
  public void test_share_auxiliary_public_key() {
    Guardian guardian = new Guardian(SENDER_GUARDIAN_ID, SENDER_SEQUENCE_ORDER, NUMBER_OF_GUARDIANS, QUORUM, null);

    Auxiliary.PublicKey public_key = guardian.share_auxiliary_public_key();

    assertThat(public_key).isNotNull();
    assertThat(public_key.key).isNotNull();
    assertThat(public_key.owner_id).isEqualTo(SENDER_GUARDIAN_ID);
    assertThat(public_key.sequence_order).isEqualTo(SENDER_SEQUENCE_ORDER);
  }

  @Test
  public void test_save_auxiliary_public_key() {
    Guardian guardian = new Guardian(SENDER_GUARDIAN_ID, SENDER_SEQUENCE_ORDER, NUMBER_OF_GUARDIANS, QUORUM, null);
    Guardian other_guardian = new Guardian(RECIPIENT_GUARDIAN_ID, RECIPIENT_SEQUENCE_ORDER, NUMBER_OF_GUARDIANS, QUORUM, null);
    Auxiliary.PublicKey public_key = other_guardian.share_auxiliary_public_key();

    guardian.save_auxiliary_public_key(public_key);

    assertThat(guardian.all_auxiliary_public_keys_received()).isTrue();
  }

  @Test
  public void test_all_auxiliary_public_keys_received() {
    Guardian guardian = new Guardian(SENDER_GUARDIAN_ID, SENDER_SEQUENCE_ORDER, NUMBER_OF_GUARDIANS, QUORUM, null);
    Guardian other_guardian = new Guardian(RECIPIENT_GUARDIAN_ID, RECIPIENT_SEQUENCE_ORDER, NUMBER_OF_GUARDIANS, QUORUM, null);
    Auxiliary.PublicKey public_key = other_guardian.share_auxiliary_public_key();

    assertThat(guardian.all_auxiliary_public_keys_received()).isFalse();
    guardian.save_auxiliary_public_key(public_key);

    assertThat(guardian.all_auxiliary_public_keys_received()).isTrue();
  }

  @Test
  public void test_generate_election_key_pair() {
    Guardian guardian = new Guardian(SENDER_GUARDIAN_ID, SENDER_SEQUENCE_ORDER, NUMBER_OF_GUARDIANS, QUORUM, null);
    KeyCeremony.ElectionPublicKey first_public_key = guardian.share_election_public_key();

    guardian.generate_election_key_pair(null);
    KeyCeremony.ElectionPublicKey second_public_key = guardian.share_election_public_key();

    assertThat(second_public_key).isNotNull();
    assertThat(second_public_key.key()).isNotNull();
    assertThat(first_public_key.key()).isNotEqualTo(second_public_key.key());
  }

  @Test
  public void test_share_election_public_key() {
    Guardian guardian = new Guardian(SENDER_GUARDIAN_ID, SENDER_SEQUENCE_ORDER, NUMBER_OF_GUARDIANS, QUORUM, null);
    KeyCeremony.ElectionPublicKey public_key = guardian.share_election_public_key();

    assertThat(public_key).isNotNull();
    assertThat(public_key.key()).isNotNull();
    assertThat(public_key.owner_id()).isEqualTo(SENDER_GUARDIAN_ID);
    assertThat(public_key.proof().is_valid()).isTrue();
  }

  @Test
  public void test_save_election_public_key() {
    Guardian guardian = new Guardian(SENDER_GUARDIAN_ID, SENDER_SEQUENCE_ORDER, NUMBER_OF_GUARDIANS, QUORUM, null);
    Guardian other_guardian = new Guardian(RECIPIENT_GUARDIAN_ID, RECIPIENT_SEQUENCE_ORDER, NUMBER_OF_GUARDIANS, QUORUM, null);
    KeyCeremony.ElectionPublicKey public_key = other_guardian.share_election_public_key();

    guardian.save_election_public_key(public_key);

    assertThat(guardian.all_election_public_keys_received()).isTrue();
  }

  @Test
  public void test_all_election_public_keys_received() {
    Guardian guardian = new Guardian(SENDER_GUARDIAN_ID, SENDER_SEQUENCE_ORDER, NUMBER_OF_GUARDIANS, QUORUM, null);
    Guardian other_guardian = new Guardian(RECIPIENT_GUARDIAN_ID, RECIPIENT_SEQUENCE_ORDER, NUMBER_OF_GUARDIANS, QUORUM, null);
    KeyCeremony.ElectionPublicKey public_key = other_guardian.share_election_public_key();

    assertThat(guardian.all_election_public_keys_received()).isFalse();
    guardian.save_election_public_key(public_key);

    assertThat(guardian.all_election_public_keys_received()).isTrue();
  }

  @Test
  public void test_generate_election_partial_key_backups() {
    Guardian guardian = new Guardian(SENDER_GUARDIAN_ID, SENDER_SEQUENCE_ORDER, NUMBER_OF_GUARDIANS, QUORUM, null);
    Guardian other_guardian = new Guardian(RECIPIENT_GUARDIAN_ID, RECIPIENT_SEQUENCE_ORDER, NUMBER_OF_GUARDIANS, QUORUM, null);
    Optional<KeyCeremony.ElectionPartialKeyBackup> empty_key_backup = other_guardian.share_election_partial_key_backup(RECIPIENT_GUARDIAN_ID);

    assertThat(empty_key_backup).isEmpty();

    guardian.save_auxiliary_public_key(other_guardian.share_auxiliary_public_key());
    guardian.generate_election_partial_key_backups(identity_auxiliary_encryptor);
    Optional<KeyCeremony.ElectionPartialKeyBackup> key_backupO = guardian.share_election_partial_key_backup(RECIPIENT_GUARDIAN_ID);
    assertThat(key_backupO).isNotNull();
    KeyCeremony.ElectionPartialKeyBackup key_backup = key_backupO.get();

    assertThat(key_backup.encrypted_value()).isNotNull();
    assertThat(key_backup.owner_id()).isEqualTo(SENDER_GUARDIAN_ID);
    assertThat(key_backup.designated_id()).isEqualTo(RECIPIENT_GUARDIAN_ID);
    assertThat(key_backup.coefficient_commitments().size()).isEqualTo(QUORUM);
    assertThat(key_backup.coefficient_proofs().size()).isEqualTo(QUORUM);
    for (SchnorrProof proof : key_backup.coefficient_proofs()) {
      assertThat(proof.is_valid()).isTrue();
    }
  }

  @Test
  public void test_share_election_partial_key_backup() {
    Guardian guardian = new Guardian(SENDER_GUARDIAN_ID, SENDER_SEQUENCE_ORDER, NUMBER_OF_GUARDIANS, QUORUM, null);
    Guardian other_guardian = new Guardian(RECIPIENT_GUARDIAN_ID, RECIPIENT_SEQUENCE_ORDER, NUMBER_OF_GUARDIANS, QUORUM, null);

    guardian.save_auxiliary_public_key(other_guardian.share_auxiliary_public_key());
    guardian.generate_election_partial_key_backups(identity_auxiliary_encryptor);

    Optional<KeyCeremony.ElectionPartialKeyBackup> key_backupO = guardian.share_election_partial_key_backup(RECIPIENT_GUARDIAN_ID);
    assertThat(key_backupO).isNotNull();
    KeyCeremony.ElectionPartialKeyBackup key_backup = key_backupO.get();

    assertThat(key_backup.encrypted_value()).isNotNull();
    assertThat(key_backup.owner_id()).isEqualTo(SENDER_GUARDIAN_ID);
    assertThat(key_backup.designated_id()).isEqualTo(RECIPIENT_GUARDIAN_ID);
    assertThat(key_backup.coefficient_commitments().size()).isEqualTo(QUORUM);
    assertThat(key_backup.coefficient_proofs().size()).isEqualTo(QUORUM);
    for (SchnorrProof proof : key_backup.coefficient_proofs()) {
      assertThat(proof.is_valid()).isTrue();
    }
  }

  @Test
  public void test_save_election_partial_key_backup() {
    Guardian guardian = new Guardian(SENDER_GUARDIAN_ID, SENDER_SEQUENCE_ORDER, NUMBER_OF_GUARDIANS, QUORUM, null);
    Guardian other_guardian = new Guardian(RECIPIENT_GUARDIAN_ID, RECIPIENT_SEQUENCE_ORDER, NUMBER_OF_GUARDIANS, QUORUM, null);

    guardian.save_auxiliary_public_key(other_guardian.share_auxiliary_public_key());
    guardian.generate_election_partial_key_backups(identity_auxiliary_encryptor);

    Optional<KeyCeremony.ElectionPartialKeyBackup> key_backupO = guardian.share_election_partial_key_backup(RECIPIENT_GUARDIAN_ID);
    assertThat(key_backupO).isNotNull();
    KeyCeremony.ElectionPartialKeyBackup key_backup = key_backupO.get();

    other_guardian.save_election_partial_key_backup(key_backup);
    assertThat(other_guardian.all_election_partial_key_backups_received()).isTrue();
  }

  @Test
  public void test_all_election_partial_key_backups_received() {
    Guardian guardian = new Guardian(SENDER_GUARDIAN_ID, SENDER_SEQUENCE_ORDER, NUMBER_OF_GUARDIANS, QUORUM, null);
    Guardian other_guardian = new Guardian(RECIPIENT_GUARDIAN_ID, RECIPIENT_SEQUENCE_ORDER, NUMBER_OF_GUARDIANS, QUORUM, null);

    guardian.save_auxiliary_public_key(other_guardian.share_auxiliary_public_key());
    guardian.generate_election_partial_key_backups(identity_auxiliary_encryptor);

    Optional<KeyCeremony.ElectionPartialKeyBackup> key_backupO = guardian.share_election_partial_key_backup(RECIPIENT_GUARDIAN_ID);
    assertThat(key_backupO).isNotNull();
    KeyCeremony.ElectionPartialKeyBackup key_backup = key_backupO.get();

    assertThat(other_guardian.all_election_partial_key_backups_received()).isFalse();
    other_guardian.save_election_partial_key_backup(key_backup);
    assertThat(other_guardian.all_election_partial_key_backups_received()).isTrue();
  }

  @Test
  public void test_verify_election_partial_key_backup() {
    Guardian guardian = new Guardian(SENDER_GUARDIAN_ID, SENDER_SEQUENCE_ORDER, NUMBER_OF_GUARDIANS, QUORUM, null);
    Guardian other_guardian = new Guardian(RECIPIENT_GUARDIAN_ID, RECIPIENT_SEQUENCE_ORDER, NUMBER_OF_GUARDIANS, QUORUM, null);

    guardian.save_auxiliary_public_key(other_guardian.share_auxiliary_public_key());
    guardian.generate_election_partial_key_backups(identity_auxiliary_encryptor);

    Optional<KeyCeremony.ElectionPartialKeyBackup> key_backupO = guardian.share_election_partial_key_backup(RECIPIENT_GUARDIAN_ID);
    assertThat(key_backupO).isNotNull();
    KeyCeremony.ElectionPartialKeyBackup key_backup = key_backupO.get();

    other_guardian.save_election_partial_key_backup(key_backup);

    Optional<KeyCeremony.ElectionPartialKeyVerification> verificationO =
            other_guardian.verify_election_partial_key_backup(SENDER_GUARDIAN_ID, identity_auxiliary_decryptor);
    assertThat(verificationO).isPresent();
    KeyCeremony.ElectionPartialKeyVerification verification = verificationO.get();

    assertThat(verification.owner_id()).isEqualTo(SENDER_GUARDIAN_ID);
    assertThat(verification.designated_id()).isEqualTo(RECIPIENT_GUARDIAN_ID);
    assertThat(verification.verifier_id()).isEqualTo(RECIPIENT_GUARDIAN_ID);
    assertThat(verification.verified()).isTrue();
  }

  @Test
  public void test_verify_election_partial_key_challenge() {
    Guardian guardian = new Guardian(SENDER_GUARDIAN_ID, SENDER_SEQUENCE_ORDER, NUMBER_OF_GUARDIANS, QUORUM, null);
    Guardian recipient_guardian = new Guardian(RECIPIENT_GUARDIAN_ID, RECIPIENT_SEQUENCE_ORDER, NUMBER_OF_GUARDIANS, QUORUM, null);
    Guardian alternate_guardian = new Guardian(ALTERNATE_VERIFIER_GUARDIAN_ID, ALTERNATE_VERIFIER_SEQUENCE_ORDER, NUMBER_OF_GUARDIANS, QUORUM, null);

    guardian.save_auxiliary_public_key(recipient_guardian.share_auxiliary_public_key());
    guardian.generate_election_partial_key_backups(identity_auxiliary_encryptor);
    Optional<KeyCeremony.ElectionPartialKeyChallenge> challenge =
            guardian.publish_election_backup_challenge(RECIPIENT_GUARDIAN_ID);

    KeyCeremony.ElectionPartialKeyVerification verification =
            alternate_guardian.verify_election_partial_key_challenge(challenge.get());

    assertThat(verification.owner_id()).isEqualTo(SENDER_GUARDIAN_ID);
    assertThat(verification.designated_id()).isEqualTo(RECIPIENT_GUARDIAN_ID);
    assertThat(verification.verifier_id()).isEqualTo(ALTERNATE_VERIFIER_GUARDIAN_ID);
    assertThat(verification.verified()).isTrue();
  }

  @Test
  public void test_publish_election_backup_challenge() {
    Guardian guardian = new Guardian(SENDER_GUARDIAN_ID, SENDER_SEQUENCE_ORDER, NUMBER_OF_GUARDIANS, QUORUM, null);
    Guardian recipient_guardian = new Guardian(RECIPIENT_GUARDIAN_ID, RECIPIENT_SEQUENCE_ORDER, NUMBER_OF_GUARDIANS, QUORUM, null);

    guardian.save_auxiliary_public_key(recipient_guardian.share_auxiliary_public_key());
    guardian.generate_election_partial_key_backups(identity_auxiliary_encryptor);

    Optional<KeyCeremony.ElectionPartialKeyChallenge> challengeO = guardian.publish_election_backup_challenge(RECIPIENT_GUARDIAN_ID);
    assertThat(challengeO).isPresent();
    KeyCeremony.ElectionPartialKeyChallenge challenge = challengeO.get();

    assertThat(challenge.value()).isNotNull();
    assertThat(challenge.owner_id()).isEqualTo(SENDER_GUARDIAN_ID);
    assertThat(challenge.designated_id()).isEqualTo(RECIPIENT_GUARDIAN_ID);
    assertThat(challenge.coefficient_commitments().size()).isEqualTo(QUORUM);
    assertThat(challenge.coefficient_proofs().size()).isEqualTo(QUORUM);
    for (SchnorrProof proof : challenge.coefficient_proofs()) {
      assertThat(proof.is_valid()).isTrue();
    }
  }

  @Test
  public void test_save_election_partial_key_verification() {
    Guardian guardian = new Guardian(SENDER_GUARDIAN_ID, SENDER_SEQUENCE_ORDER, NUMBER_OF_GUARDIANS, QUORUM, null);
    Guardian other_guardian = new Guardian(RECIPIENT_GUARDIAN_ID, RECIPIENT_SEQUENCE_ORDER, NUMBER_OF_GUARDIANS, QUORUM, null);

    guardian.save_auxiliary_public_key(other_guardian.share_auxiliary_public_key());
    guardian.generate_election_partial_key_backups(identity_auxiliary_encryptor);

    Optional<KeyCeremony.ElectionPartialKeyBackup> key_backup = guardian.share_election_partial_key_backup(RECIPIENT_GUARDIAN_ID);
    assertThat(key_backup).isPresent();
    other_guardian.save_election_partial_key_backup(key_backup.get());
    Optional<KeyCeremony.ElectionPartialKeyVerification> verification = other_guardian.verify_election_partial_key_backup(SENDER_GUARDIAN_ID, identity_auxiliary_decryptor);

    assertThat(verification).isPresent();
    guardian.save_election_partial_key_verification(verification.get());

    assertThat(guardian.all_election_partial_key_backups_verified()).isTrue();
  }

  @Test
  public void test_all_election_partial_key_backups_verified() {
    Guardian guardian = new Guardian(SENDER_GUARDIAN_ID, SENDER_SEQUENCE_ORDER, NUMBER_OF_GUARDIANS, QUORUM, null);
    Guardian other_guardian = new Guardian(RECIPIENT_GUARDIAN_ID, RECIPIENT_SEQUENCE_ORDER, NUMBER_OF_GUARDIANS, QUORUM, null);

    guardian.save_auxiliary_public_key(other_guardian.share_auxiliary_public_key());
    guardian.generate_election_partial_key_backups(identity_auxiliary_encryptor);

    Optional<KeyCeremony.ElectionPartialKeyBackup> key_backup = guardian.share_election_partial_key_backup(RECIPIENT_GUARDIAN_ID);
    assertThat(key_backup).isPresent();
    other_guardian.save_election_partial_key_backup(key_backup.get());
    Optional<KeyCeremony.ElectionPartialKeyVerification> verification = other_guardian.verify_election_partial_key_backup(SENDER_GUARDIAN_ID, identity_auxiliary_decryptor);
    assertThat(verification).isPresent();
    guardian.save_election_partial_key_verification(verification.get());

    boolean all_saved = guardian.all_election_partial_key_backups_verified();
    assertThat(all_saved).isTrue();
  }

  @Test
  public void test_publish_joint_key() {
    Guardian guardian = new Guardian(SENDER_GUARDIAN_ID, SENDER_SEQUENCE_ORDER, NUMBER_OF_GUARDIANS, QUORUM, null);
    Guardian other_guardian = new Guardian(RECIPIENT_GUARDIAN_ID, RECIPIENT_SEQUENCE_ORDER, NUMBER_OF_GUARDIANS, QUORUM, null);

    guardian.save_auxiliary_public_key(other_guardian.share_auxiliary_public_key());
    guardian.generate_election_partial_key_backups(identity_auxiliary_encryptor);

    Optional<KeyCeremony.ElectionPartialKeyBackup> key_backup = guardian.share_election_partial_key_backup(RECIPIENT_GUARDIAN_ID);
    assertThat(key_backup).isPresent();
    other_guardian.save_election_partial_key_backup(key_backup.get());
    Optional<KeyCeremony.ElectionPartialKeyVerification> verification = other_guardian.verify_election_partial_key_backup(SENDER_GUARDIAN_ID, identity_auxiliary_decryptor);
    assertThat(verification).isPresent();

    Optional<Group.ElementModP> joint_key = guardian.publish_joint_key();
    assertThat(joint_key).isEmpty();

    guardian.save_election_public_key(other_guardian.share_election_public_key());
    joint_key = guardian.publish_joint_key();
    assertThat(joint_key).isEmpty();

    guardian.save_election_partial_key_verification(verification.get());
    joint_key = guardian.publish_joint_key();
    assertThat(joint_key).isPresent();
    assertThat(joint_key).isNotEqualTo(guardian.share_election_public_key().key());
  }

}
