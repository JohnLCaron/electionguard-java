package com.sunya.electionguard.standard;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import net.jqwik.api.Example;

import java.util.List;
import java.util.Optional;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth8.assertThat;
import static com.sunya.electionguard.standard.KeyCeremony.*;

public class TestKeyCeremonyMediator {
  private static final int NUMBER_OF_GUARDIANS = 2;
  private static final int QUORUM = 2;
  private static final CeremonyDetails CEREMONY_DETAILS = CeremonyDetails.create(NUMBER_OF_GUARDIANS, QUORUM);
  private static final String GUARDIAN_1_ID = "Guardian 1";
  private static final String GUARDIAN_2_ID = "Guardian 2";
  private static final String VERIFIER_ID = "Guardian 3";
  private static final Guardian GUARDIAN_1 = Guardian.createForTesting(GUARDIAN_1_ID, 1, NUMBER_OF_GUARDIANS, QUORUM,null);
  private static final Guardian GUARDIAN_2 = Guardian.createForTesting(GUARDIAN_2_ID, 2, NUMBER_OF_GUARDIANS, QUORUM, null);
  private static final Guardian VERIFIER =   Guardian.createForTesting(VERIFIER_ID, 3, NUMBER_OF_GUARDIANS, QUORUM, null);
  private static final List<Guardian> GUARDIANS = ImmutableList.of(GUARDIAN_1, GUARDIAN_2);

  static {
    GUARDIAN_1.save_guardian_key(GUARDIAN_2.share_key());
    GUARDIAN_2.save_guardian_key(GUARDIAN_1.share_key());
    VERIFIER.save_guardian_key(GUARDIAN_2.share_key());
    GUARDIAN_1.generate_election_partial_key_backups();
    GUARDIAN_2.generate_election_partial_key_backups();
  }

  // Round 1: Mediator takes attendance and guardians announce
  @Example
  public void test_take_attendance() {
    KeyCeremonyMediator mediator = new KeyCeremonyMediator("mediator_attendance", CEREMONY_DETAILS);

    mediator.announce(GUARDIAN_1.share_key());
    assertThat(mediator.all_guardians_announced()).isFalse();

    mediator.announce(GUARDIAN_2.share_key());
    assertThat(mediator.all_guardians_announced()).isTrue();

    Optional<List<ElectionPublicKey>> guardian_keys = mediator.share_announced(null);
    assertThat(guardian_keys).isPresent();
    assertThat(Iterables.size(guardian_keys.get())).isEqualTo(NUMBER_OF_GUARDIANS);
  }

  // Round 2: Exchange of election partial key backups
  @Example
  public void test_exchange_of_backups() {
    KeyCeremonyMediator mediator = new KeyCeremonyMediator("mediator_backups_exchange", CEREMONY_DETAILS);
    KeyCeremonyHelper.perform_round_1(GUARDIANS, mediator);

    // Round 2 - Guardians Only
    GUARDIAN_1.generate_election_partial_key_backups();
    GUARDIAN_2.generate_election_partial_key_backups();
    ElectionPartialKeyBackup backup_from_1_for_2 = GUARDIAN_1.share_election_partial_key_backup(GUARDIAN_2_ID).orElseThrow();
    ElectionPartialKeyBackup backup_from_2_for_1 = GUARDIAN_2.share_election_partial_key_backup(GUARDIAN_1_ID).orElseThrow();

    mediator.receive_backups(ImmutableList.of(backup_from_1_for_2));
    assertThat(mediator.all_backups_available()).isFalse();
    mediator.receive_backups(ImmutableList.of(backup_from_2_for_1));
    assertThat(mediator.all_backups_available()).isTrue();

    List<ElectionPartialKeyBackup> guardian1_backups = mediator.share_backups(GUARDIAN_1_ID).orElseThrow();
    List<ElectionPartialKeyBackup> guardian2_backups = mediator.share_backups(GUARDIAN_2_ID).orElseThrow();
    assertThat(guardian1_backups).hasSize(1);
    assertThat(guardian2_backups).hasSize(1);

    assertThat(guardian1_backups.get(0)).isEqualTo(backup_from_2_for_1);
    assertThat(guardian2_backups.get(0)).isEqualTo(backup_from_1_for_2);
  }

  // Test for the happy path of the verification process where each key is successfully verified and no bad actors.
  @Example
  public void test_partial_key_backup_verification_success() {
    KeyCeremonyMediator mediator = new KeyCeremonyMediator("mediator_verification", CEREMONY_DETAILS);
    KeyCeremonyHelper.perform_round_1(GUARDIANS, mediator);
    KeyCeremonyHelper.perform_round_2(GUARDIANS, mediator);

    // Round 3 - Guardians Only
    ElectionPartialKeyVerification verification1 =
            GUARDIAN_1.verify_election_partial_key_backup(GUARDIAN_2_ID).orElseThrow();
    ElectionPartialKeyVerification verification2 =
            GUARDIAN_2.verify_election_partial_key_backup(GUARDIAN_1_ID).orElseThrow();

    mediator.receive_backup_verifications(ImmutableList.of(verification1));
    assertThat(mediator.get_verification_state().all_sent()).isFalse();
    assertThat(mediator.all_backups_verified()).isFalse();
    assertThat(mediator.publish_joint_key()).isEmpty();

    mediator.receive_backup_verifications(ImmutableList.of(verification2));
    assertThat(mediator.get_verification_state().all_sent()).isTrue();
    assertThat(mediator.all_backups_verified()).isTrue();
    assertThat(mediator.publish_joint_key()).isPresent();
  }

  /**
   * In this case, the recipient guardian does not correctly verify the sent key backup.
   * This failed verificaton requires the sender create a challenge and a new verifier aka another guardian must verify this challenge.
   */
  @Example
  public void test_partial_key_backup_verification_failure() {
    KeyCeremonyMediator mediator = new KeyCeremonyMediator("mediator_verification", CEREMONY_DETAILS);
    KeyCeremonyHelper.perform_round_1(GUARDIANS, mediator);
    KeyCeremonyHelper.perform_round_2(GUARDIANS, mediator);

    // Round 3 - Guardians Only
    ElectionPartialKeyVerification verification1 =
            GUARDIAN_1.verify_election_partial_key_backup(GUARDIAN_2_ID).orElseThrow();

    ElectionPartialKeyVerification failed_verification2 = ElectionPartialKeyVerification.create(
            GUARDIAN_1_ID,
            GUARDIAN_2_ID,
            GUARDIAN_2_ID,
            false);

    mediator.receive_backup_verifications(ImmutableList.of(verification1, failed_verification2));
    KeyCeremonyMediator.BackupVerificationState state = mediator.get_verification_state();

    assertThat(state.all_sent()).isTrue();
    assertThat(state.all_verified()).isFalse();
    assertThat(mediator.publish_joint_key()).isEmpty();
    assertThat(state.failed_verifications()).hasSize(1);
    assertThat(state.failed_verifications().get(0))
            .isEqualTo(KeyCeremonyMediator.GuardianPair.create(GUARDIAN_1_ID, GUARDIAN_2_ID));

    ElectionPartialKeyChallenge challenge = GUARDIAN_1.publish_election_backup_challenge(GUARDIAN_2_ID).orElseThrow();
    mediator.verify_challenge(challenge);
    KeyCeremonyMediator.BackupVerificationState new_state = mediator.get_verification_state();
    boolean all_verified = mediator.all_backups_verified();
    Optional<ElectionJointKey> joint_key = mediator.publish_joint_key();

    assertThat(new_state.all_sent()).isTrue();
    assertThat(new_state.all_verified()).isTrue();
    assertThat(new_state.failed_verifications()).hasSize(0);
    assertThat(all_verified).isTrue();
    assertThat(joint_key).isPresent();
  }

}
