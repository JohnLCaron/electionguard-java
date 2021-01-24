package com.sunya.electionguard;

import com.google.common.collect.Iterables;
import net.jqwik.api.Example;

import java.util.List;
import java.util.Optional;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth8.assertThat;

import static com.sunya.electionguard.KeyCeremony.*;


public class TestKeyCeremonyMediator {
  private static final int NUMBER_OF_GUARDIANS = 2;
  private static final int QUORUM = 2;
  private static final CeremonyDetails CEREMONY_DETAILS = CeremonyDetails.create(NUMBER_OF_GUARDIANS, QUORUM);
  private static final String GUARDIAN_1_ID = "Guardian 1";
  private static final String GUARDIAN_2_ID = "Guardian 2";
  private static final String VERIFIER_ID = "Guardian 3";
  private static final Guardian GUARDIAN_1 = Guardian.createForTesting(GUARDIAN_1_ID, 1, NUMBER_OF_GUARDIANS, QUORUM, null);
  private static final Guardian GUARDIAN_2 = Guardian.createForTesting(GUARDIAN_2_ID, 2, NUMBER_OF_GUARDIANS, QUORUM, null);
  private static final Guardian VERIFIER =   Guardian.createForTesting(VERIFIER_ID, 3, NUMBER_OF_GUARDIANS, QUORUM, null);

  static Auxiliary.Decryptor identity_auxiliary_decrypt = (m, k) -> Optional.of(new String(m.getBytes()));
  static Auxiliary.Encryptor identity_auxiliary_encrypt = (m, k) -> Optional.of(new Auxiliary.ByteString(m.getBytes()));

  static {
    GUARDIAN_1.save_guardian_public_keys(GUARDIAN_2.share_public_keys());
    GUARDIAN_2.save_guardian_public_keys(GUARDIAN_1.share_public_keys());
    VERIFIER.save_guardian_public_keys(GUARDIAN_2.share_public_keys());
    GUARDIAN_1.generate_election_partial_key_backups(identity_auxiliary_encrypt);
    GUARDIAN_2.generate_election_partial_key_backups(identity_auxiliary_encrypt);
  }

  /* @Example
  public void test_reset() {
    KeyCeremonyMediator mediator = new KeyCeremonyMediator(CEREMONY_DETAILS);
    CeremonyDetails new_ceremony_details = CeremonyDetails.create(3, 3);

    // LOOK mutating
    mediator.reset(new_ceremony_details);
    assertThat(mediator.ceremony_details).isEqualTo(new_ceremony_details);
  } */

  @Example
  public void test_mediator_takes_attendance() {
    KeyCeremonyMediator mediator = new KeyCeremonyMediator(CEREMONY_DETAILS);

    mediator.confirm_presence_of_guardian(GUARDIAN_1.share_public_keys());
    assertThat(mediator.all_guardians_in_attendance()).isFalse();

    mediator.confirm_presence_of_guardian(GUARDIAN_2.share_public_keys());
    assertThat(mediator.all_guardians_in_attendance()).isTrue();

    Iterable<String> guardians = mediator.share_guardians_in_attendance();
    assertThat(guardians).isNotNull();
    assertThat(Iterables.size(guardians)).isEqualTo(NUMBER_OF_GUARDIANS);
  }

  @Example
  public void test_exchange_of_auxiliary_public_keys() {
    KeyCeremonyMediator mediator = new KeyCeremonyMediator(CEREMONY_DETAILS);

    mediator.receive_auxiliary_public_key(GUARDIAN_1.share_auxiliary_public_key());
    assertThat(mediator.all_auxiliary_public_keys_available()).isFalse();

    Iterable<Auxiliary.PublicKey> partial_list = mediator.share_auxiliary_public_keys();
    assertThat(partial_list).isNotNull();
    assertThat(Iterables.size(partial_list)).isEqualTo(1);

    mediator.receive_auxiliary_public_key(GUARDIAN_2.share_auxiliary_public_key());
    assertThat(mediator.all_auxiliary_public_keys_available()).isTrue();

    partial_list = mediator.share_auxiliary_public_keys();
    assertThat(partial_list).isNotNull();
    assertThat(Iterables.size(partial_list)).isEqualTo(2);
  }

  @Example
  public void test_exchange_of_election_public_keys() {
    KeyCeremonyMediator mediator = new KeyCeremonyMediator(CEREMONY_DETAILS);

    mediator.receive_election_public_key(GUARDIAN_1.share_election_public_key());
    assertThat(mediator.all_election_public_keys_available()).isFalse();

    Iterable<ElectionPublicKey>partial_list = mediator.share_election_public_keys();
    assertThat(partial_list).isNotNull();
    assertThat(Iterables.size(partial_list)).isEqualTo(1);

    mediator.receive_election_public_key(GUARDIAN_2.share_election_public_key());
    assertThat(mediator.all_election_public_keys_available()).isTrue();

    partial_list = mediator.share_election_public_keys();
    assertThat(partial_list).isNotNull();
    assertThat(Iterables.size(partial_list)).isEqualTo(2);
  }

  @Example
  public void test_exchange_of_election_partial_key_backup() {
    KeyCeremonyMediator mediator = new KeyCeremonyMediator(CEREMONY_DETAILS);
    mediator.confirm_presence_of_guardian(GUARDIAN_1.share_public_keys());
    mediator.confirm_presence_of_guardian(GUARDIAN_2.share_public_keys());
    Optional<ElectionPartialKeyBackup> backup_from_1_for_2 = GUARDIAN_1.share_election_partial_key_backup(GUARDIAN_2_ID);
    Optional<ElectionPartialKeyBackup> backup_from_2_for_1 = GUARDIAN_2.share_election_partial_key_backup(GUARDIAN_1_ID);

    mediator.receive_election_partial_key_backup(backup_from_1_for_2.orElseThrow());
    assertThat(mediator.all_election_partial_key_backups_available()).isFalse();

    mediator.receive_election_partial_key_backup(backup_from_2_for_1.orElseThrow());
    assertThat(mediator.all_election_partial_key_backups_available()).isTrue();

    List<ElectionPartialKeyBackup> guardian1_backups = mediator.share_election_partial_key_backups_to_guardian(GUARDIAN_1_ID);
    List<ElectionPartialKeyBackup> guardian2_backups = mediator.share_election_partial_key_backups_to_guardian(GUARDIAN_2_ID);

    assertThat(guardian1_backups).isNotNull();
    assertThat(guardian2_backups).isNotNull();
    assertThat(guardian1_backups.size()).isEqualTo(1);
    assertThat(guardian2_backups.size()).isEqualTo(1);
    for (ElectionPartialKeyBackup backup : guardian1_backups) {
      assertThat(backup.designated_id()).isEqualTo(GUARDIAN_1_ID);
    }
    for (ElectionPartialKeyBackup backup : guardian2_backups) {
      assertThat(backup.designated_id()).isEqualTo(GUARDIAN_2_ID);
    }
    assertThat(guardian1_backups.get(0)).isEqualTo(backup_from_2_for_1.get());
    assertThat(guardian2_backups.get(0)).isEqualTo(backup_from_1_for_2.get());
  }

  /** Test for the happy path of the verification process where each key is successfully verified and no bad actors. */
  @Example
  public void test_partial_key_backup_verification_success() {
     KeyCeremonyMediator mediator = new KeyCeremonyMediator(CEREMONY_DETAILS);

     mediator.confirm_presence_of_guardian(GUARDIAN_1.share_public_keys());
     mediator.confirm_presence_of_guardian(GUARDIAN_2.share_public_keys());
     mediator.receive_election_partial_key_backup(GUARDIAN_1.share_election_partial_key_backup(GUARDIAN_2_ID).orElseThrow());
     mediator.receive_election_partial_key_backup(GUARDIAN_2.share_election_partial_key_backup(GUARDIAN_1_ID).orElseThrow());

     GUARDIAN_1.save_election_partial_key_backup(mediator.share_election_partial_key_backups_to_guardian(GUARDIAN_1_ID).get(0));
     GUARDIAN_2.save_election_partial_key_backup(mediator.share_election_partial_key_backups_to_guardian(GUARDIAN_2_ID).get(0));
     Optional<ElectionPartialKeyVerification> verification1 = GUARDIAN_1.verify_election_partial_key_backup(GUARDIAN_2_ID, identity_auxiliary_decrypt);
     Optional<ElectionPartialKeyVerification> verification2 = GUARDIAN_2.verify_election_partial_key_backup(GUARDIAN_1_ID, identity_auxiliary_decrypt);

     mediator.receive_election_partial_key_verification(verification1.orElseThrow());
     assertThat(mediator.all_election_partial_key_verifications_received()).isFalse();
     assertThat(mediator.all_election_partial_key_backups_verified()).isFalse();
     assertThat(mediator.publish_joint_key()).isNotNull();

     mediator.receive_election_partial_key_verification(verification2.orElseThrow());
     Optional<Group.ElementModP> joint_key = mediator.publish_joint_key();

     assertThat(mediator.all_election_partial_key_verifications_received()).isTrue();
     assertThat(mediator.all_election_partial_key_backups_verified()).isTrue();
     assertThat(joint_key).isNotNull();
   }

  /**
   * In this case, the recipient guardian does not correctly verify the sent key backup.
   * This failed verificaton requires the sender create a challenge and a new verifier aka another guardian must verify this challenge.
   */
  @Example
  public void test_partial_key_backup_verification_failure() {
    KeyCeremonyMediator mediator = new KeyCeremonyMediator(CEREMONY_DETAILS);
    mediator.confirm_presence_of_guardian(GUARDIAN_1.share_public_keys());
    mediator.confirm_presence_of_guardian(GUARDIAN_2.share_public_keys());
    mediator.receive_election_partial_key_backup(GUARDIAN_1.share_election_partial_key_backup(GUARDIAN_2_ID).orElseThrow());
    mediator.receive_election_partial_key_backup(GUARDIAN_2.share_election_partial_key_backup(GUARDIAN_1_ID).orElseThrow());
    GUARDIAN_1.save_election_partial_key_backup(mediator.share_election_partial_key_backups_to_guardian(GUARDIAN_1_ID).get(0));
    GUARDIAN_2.save_election_partial_key_backup(mediator.share_election_partial_key_backups_to_guardian(GUARDIAN_2_ID).get(0));
    Optional<ElectionPartialKeyVerification> verification1O = GUARDIAN_1.verify_election_partial_key_backup(GUARDIAN_2_ID, identity_auxiliary_decrypt);
    Optional<ElectionPartialKeyVerification> verification2O = GUARDIAN_2.verify_election_partial_key_backup(GUARDIAN_1_ID, identity_auxiliary_decrypt);
    assertThat(verification1O).isPresent();
    assertThat(verification2O).isPresent();
    ElectionPartialKeyVerification verification1 = verification1O.get();
    ElectionPartialKeyVerification verification2 = verification2O.get();

    ElectionPartialKeyVerification failed_verification2 = ElectionPartialKeyVerification.create(
            verification2.owner_id(),
            verification2.designated_id(),
            verification2.verifier_id(),
            false);

    mediator.receive_election_partial_key_verification(verification1);
    mediator.receive_election_partial_key_verification(failed_verification2);
    List<GuardianPair> failed_pairs = mediator.share_failed_partial_key_verifications();
    List<GuardianPair> missing_challenges = mediator.share_missing_election_partial_key_challenges();

    assertThat(mediator.all_election_partial_key_verifications_received()).isTrue();
    assertThat(mediator.all_election_partial_key_backups_verified()).isFalse();
    assertThat(mediator.publish_joint_key()).isEmpty();
    assertThat(failed_pairs.size()).isEqualTo(1);
    assertThat(failed_pairs.get(0)).isEqualTo(GuardianPair.create(GUARDIAN_1_ID, GUARDIAN_2_ID));
    assertThat(missing_challenges.size()).isEqualTo(1);
    assertThat(missing_challenges.get(0)).isEqualTo(GuardianPair.create(GUARDIAN_1_ID, GUARDIAN_2_ID));

    Optional<ElectionPartialKeyChallenge> challenge = GUARDIAN_1.publish_election_backup_challenge(GUARDIAN_2_ID);
    mediator.receive_election_partial_key_challenge(challenge.orElseThrow());
    List<GuardianPair> no_missing_challenges = mediator.share_missing_election_partial_key_challenges();

    assertThat(mediator.all_election_partial_key_backups_verified()).isFalse();
    assertThat(no_missing_challenges.size()).isEqualTo(0);

    List<ElectionPartialKeyChallenge>  challenges = mediator.share_open_election_partial_key_challenges();
    ElectionPartialKeyVerification challenge_verification = VERIFIER.verify_election_partial_key_challenge(challenges.get(0));
    mediator.receive_election_partial_key_verification(challenge_verification);
    Optional<Group.ElementModP> joint_key = mediator.publish_joint_key();

    assertThat(challenges.size()).isEqualTo(1);
    assertThat(mediator.all_election_partial_key_backups_verified()).isTrue();
    assertThat(joint_key).isNotNull();
  }

}
