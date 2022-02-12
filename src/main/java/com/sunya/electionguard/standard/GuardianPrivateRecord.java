package com.sunya.electionguard.standard;

import java.util.Map;

/**
 * Unpublishable private record containing information per Guardian
 */
public record GuardianPrivateRecord(
        KeyCeremony.ElectionKeyPair election_keys,
        Map<String, KeyCeremony.ElectionPartialKeyBackup> backups_to_share,
        Map<String, KeyCeremony.ElectionPublicKey> guardian_election_public_keys,
        Map<String, KeyCeremony.ElectionPartialKeyBackup> guardian_election_partial_key_backups,
        Map<String, KeyCeremony.ElectionPartialKeyVerification> guardian_election_partial_key_verifications) {

  public GuardianPrivateRecord {
    backups_to_share = Map.copyOf(backups_to_share);
    guardian_election_public_keys = Map.copyOf(guardian_election_public_keys);
    guardian_election_partial_key_backups = Map.copyOf(guardian_election_partial_key_backups);
    guardian_election_partial_key_verifications = Map.copyOf(guardian_election_partial_key_verifications);
  }

  public String guardian_id() {
    return election_keys().owner_id();
  }

}
