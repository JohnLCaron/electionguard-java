package com.sunya.electionguard.standard;

import com.sunya.electionguard.Auxiliary;

import java.util.ArrayList;
import java.util.List;

/** Helper to assist in the key ceremony for testing. */
public class KeyCeremonyHelper {

  /** Perform full key ceremony so joint election key is ready for publish. */
  public static void perform_full_ceremony(List<Guardian> guardians, KeyCeremonyMediator mediator) {
    perform_round_1(guardians, mediator);
    perform_round_2(guardians, mediator);
    perform_round_3(guardians, mediator);
  }

  /** Perform Round 1 including announcing guardians and sharing public keys. */
  public static void perform_round_1(List<Guardian> guardians, KeyCeremonyMediator mediator) {

    for (Guardian guardian : guardians) {
      mediator.announce(guardian.share_public_keys());
    }

    for (Guardian guardian : guardians) {
      List<KeyCeremony.PublicKeySet> other_guardian_keys = mediator.share_announced(guardian.object_id).orElseThrow();
      for (KeyCeremony.PublicKeySet guardian_public_keys : other_guardian_keys) {
        guardian.save_guardian_public_keys(guardian_public_keys);
      }
    }
  }

  /** Perform Round 2 including generating backups and sharing backups. */
  public static void perform_round_2(List<Guardian> guardians, KeyCeremonyMediator mediator) {

    for (Guardian guardian : guardians) {
      guardian.generate_election_partial_key_backups(Auxiliary.identity_auxiliary_encrypt);
      mediator.receive_backups(guardian.share_election_partial_key_backups());
    }

    for (Guardian guardian : guardians) {
      List<KeyCeremony.ElectionPartialKeyBackup> backups = mediator.share_backups(guardian.object_id).orElseThrow();
      for (KeyCeremony.ElectionPartialKeyBackup backup : backups) {
        guardian.save_election_partial_key_backup(backup);
      }
    }
  }

  /** Perform Round 3 including verifying backups. */
  public static void perform_round_3(List<Guardian> guardians, KeyCeremonyMediator mediator) {

    for (Guardian guardian : guardians) {
      for (Guardian other_guardian : guardians) {
        List<KeyCeremony.ElectionPartialKeyVerification> verifications = new ArrayList<>();
        if (!guardian.object_id.equals(other_guardian.object_id)) {
          verifications.add(
                  guardian.verify_election_partial_key_backup(
                          other_guardian.object_id, Auxiliary.identity_auxiliary_decrypt).orElseThrow());
          mediator.receive_backup_verifications(verifications);
        }
      }
    }
  }


}
