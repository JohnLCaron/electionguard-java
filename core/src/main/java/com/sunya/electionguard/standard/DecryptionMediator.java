package com.sunya.electionguard.standard;

import com.google.common.flogger.FluentLogger;
import com.sunya.electionguard.AvailableGuardian;
import com.sunya.electionguard.ElectionContext;
import com.sunya.electionguard.CiphertextTally;
import com.sunya.electionguard.DecryptWithShares;
import com.sunya.electionguard.DecryptionShare;
import com.sunya.electionguard.Group;
import com.sunya.electionguard.PlaintextTally;
import com.sunya.electionguard.SubmittedBallot;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static com.sunya.electionguard.DecryptionShare.CompensatedDecryptionShare;
import static com.sunya.electionguard.standard.KeyCeremonyMediator.GuardianPair;

/**
 * The Decryption Mediator composes partial decryptions from each Guardian
 * to form a decrypted representation of an election tally.
 * Used in RunStandardWorkflow.
 */
class DecryptionMediator {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final String id;
  private final ElectionContext context;

  // Map(GUARDIAN_ID, Guardian)
  private final Map<String, KeyCeremony.ElectionPublicKey> available_guardians = new HashMap<>();
  // Map(GUARDIAN_ID, ElectionPublicKey)
  private final Map<String, KeyCeremony.ElectionPublicKey> missing_guardians = new HashMap<>();

  // Map(GUARDIAN_ID, DecryptionShare)
  private final Map<String, DecryptionShare> tally_shares = new HashMap<>();

  // Map<BALLOT_ID, Map<GUARDIAN_ID, DecryptionShare>>
  private final Map<String, Map<String, DecryptionShare>> ballot_shares = new HashMap<>();

  private final Map<GuardianPair, CompensatedDecryptionShare> compensated_tally_shares = new HashMap<>();

  // Map<BALLOT_ID, Map<GuardianPair, DecryptionShare>>
  private final Map<String, Map<GuardianPair, CompensatedDecryptionShare>> compensated_ballot_shares = new HashMap<>();

  public DecryptionMediator(String id, ElectionContext context) {
    this.id = id;
    this.context = context;
  }

  /**
   * Announce that a Guardian is present and participating in the decryption.
   *
   * @param guardian_key:  The election public key of the guardian who will participate in the decryption.
   * @param tally_share:   Guardian's decryption share of the tally
   * @param ballot_shares: Guardian's decryption shares of the ballots
   */
  public void announce(KeyCeremony.ElectionPublicKey guardian_key, DecryptionShare tally_share,
                          Map<String, Optional<DecryptionShare>> ballot_shares) {
    String guardian_id = guardian_key.owner_id();

    // Only allow a guardian to announce once
    if (available_guardians.containsKey(guardian_id)) {
      logger.atInfo().log("guardian %s already announced", guardian_id);
      return;
    }

    this.save_tally_share(guardian_id, tally_share);

    if (!ballot_shares.isEmpty()) {
      this.save_ballot_shares(guardian_id, ballot_shares);
    }

    // Mark guardian in attendance and check their keys
    this.mark_available(guardian_key);
  }

  /**
   * Announce that a Guardian is missing and not participating in the decryption.
   * @param missing_guardian_key: The election public key of the missing guardian
   */
  public void announce_missing(KeyCeremony.ElectionPublicKey missing_guardian_key) {
    String missing_guardian_id = missing_guardian_key.owner_id();

    // If guardian is available, can't be marked missing
    if (this.available_guardians.containsKey(missing_guardian_id)) {
      logger.atWarning().log("guardian %s already announced", missing_guardian_id);
      return;
    }
    this.mark_missing(missing_guardian_key);
  }

  /** This guardian removes itself from the missing list since it generated a valid share. */
  private void mark_available(KeyCeremony.ElectionPublicKey guardian_key) {
    String guardian_id = guardian_key.owner_id();
    this.available_guardians.put(guardian_id, guardian_key);
    this.missing_guardians.remove(guardian_id);
  }

  private void mark_missing(KeyCeremony.ElectionPublicKey guardian_key) {
    this.missing_guardians.put(guardian_key.owner_id(), guardian_key);
  }

  /** Check the guardian's collections of keys and ensure the public keys match for the missing guardians. */
  private boolean validate_missing_guardians(List<KeyCeremony.ElectionPublicKey> guardian_keys) {

    // Check this guardian's collection of public keys for other guardians that have not announced
    Map<String, KeyCeremony.ElectionPublicKey> missing_guardians =
            guardian_keys.stream()
                    .filter(g -> !this.available_guardians.containsKey(g.owner_id()))
                    .collect(Collectors.toMap(g -> g.owner_id(), g -> g));

    //  Check that the public keys match for any missing guardians already reported.
    //  Note this check naively assumes that the first guardian to announce is telling the truth.
    //  But for this implementation it is simply a sanity check on the input data.
    //  LOOK A consuming application should implement better validation of the guardian state
    //   before announcing a guardian is available for decryption.
    for (Map.Entry<String, KeyCeremony.ElectionPublicKey> entry : missing_guardians.entrySet()) {
      String guardian_id = entry.getKey();
      KeyCeremony.ElectionPublicKey public_key = entry.getValue();
      if (this.missing_guardians.containsKey(guardian_id)) {
        if (!this.missing_guardians.get(guardian_id).equals(public_key)) {
          logger.atWarning().log("announce guardian: %s expected public key mismatch for missing %s",
                  guardian_id, guardian_id); // LOOK
          return false;
        }
      } else {
        this.missing_guardians.put(guardian_id, public_key);
      }
    }
    return true;
  }

  /** Determine if the announcement phase is complete */
   boolean announcement_complete() {
     // If a quorum not announced, not ready
     if (this.available_guardians.size() < this.context.quorum) {
       logger.atWarning().log("cannot decrypt with less than quorum available guardians");
       return false;
     }
     // If guardians missing or available not accounted for, not ready
     if (this.available_guardians.size() + this.missing_guardians.size() != this.context.numberOfGuardians) {
       logger.atWarning().log("cannot decrypt without accounting for all guardians missing or present");
       return false;
     }
     return true;
   }

  boolean ready_to_decrypt(Map<String, DecryptionShare> shares) {
    // If all guardian shares are represented including if necessary
    // the missing guardians reconstructed shares, the decryption can be made
    return shares.size() == this.context.numberOfGuardians;
  }

   /**  Get all available guardian keys. */
  public List<KeyCeremony.ElectionPublicKey> get_available_guardians() {
    return new ArrayList<>(this.available_guardians.values());
  }

  /**  Get all missing guardian keys. */
  public List<KeyCeremony.ElectionPublicKey> get_missing_guardians() {
    return new ArrayList<>(this.missing_guardians.values());
  }

  public void receive_tally_compensation_share(CompensatedDecryptionShare tally_compensation_share) {
    this.compensated_tally_shares.put(
            new GuardianPair(
                    tally_compensation_share.guardian_id,
                    tally_compensation_share.missing_guardian_id),
            tally_compensation_share);
  }

  public void receive_ballot_compensation_shares(Map<String, CompensatedDecryptionShare> ballot_compensation_shares) {

    for (Map.Entry<String, CompensatedDecryptionShare> entry : ballot_compensation_shares.entrySet()) {
      String ballot_id = entry.getKey();
      CompensatedDecryptionShare share = entry.getValue();
      // Dict[ BALLOT_ID, Dict[GuardianPair, CompensatedDecryptionShare]
      Map<GuardianPair, CompensatedDecryptionShare> shares =
              this.compensated_ballot_shares.computeIfAbsent(ballot_id, k -> new HashMap<>());
      shares.put(
              new GuardianPair(
                      share.guardian_id,
                      share.missing_guardian_id),
              share);
    }
  }

  public List<AvailableGuardian> availableGuardians() {
    Map<String, Group.ElementModQ> lagrange_coefficients = Decryptions.compute_lagrange_coefficients_for_guardians(
            new ArrayList<>(available_guardians.values()));

    List<AvailableGuardian> result = new ArrayList<>();
    available_guardians.values().forEach(g -> result.add( new AvailableGuardian(
            g.owner_id(), g.sequence_order(), lagrange_coefficients.get(g.owner_id()))));
    return result;
  }

  void reconstruct_shares_for_tally(CiphertextTally ciphertext_tally) {
    Map<String, Group.ElementModQ> lagrange_coefficients = Decryptions.compute_lagrange_coefficients_for_guardians(
            new ArrayList<>(available_guardians.values()));

    for (Map.Entry<String, KeyCeremony.ElectionPublicKey> entry : this.missing_guardians.entrySet()) {
      String missing_guardian_id = entry.getKey();
      KeyCeremony.ElectionPublicKey missing_guardian_key = entry.getValue();

      // Share already reconstructed
      if (this.tally_shares.containsKey(missing_guardian_id)) {
        continue;
      }

      Map<String, CompensatedDecryptionShare> compensated_shares = filter_by_missing_guardian(missing_guardian_id, this.compensated_tally_shares);

      DecryptionShare reconstructed_share = Decryptions.reconstruct_decryption_share(
              missing_guardian_key,
              ciphertext_tally,
              compensated_shares,
              lagrange_coefficients);

      // Add reconstructed share into tally shares
      this.tally_shares.put(missing_guardian_id, reconstructed_share);
    }
  }

  void reconstruct_shares_for_ballots(List<SubmittedBallot> ciphertext_ballots) {
    Map<String, Group.ElementModQ> lagrange_coefficients = Decryptions.compute_lagrange_coefficients_for_guardians(
            new ArrayList<>(available_guardians.values()));

    for (SubmittedBallot ciphertext_ballot : ciphertext_ballots) {
      String ballot_id = ciphertext_ballot.object_id();
      Map<String, DecryptionShare> ballot_shares = this.ballot_shares.computeIfAbsent(ballot_id, m -> new HashMap<>());

      for (Map.Entry<String, KeyCeremony.ElectionPublicKey> entry : this.missing_guardians.entrySet()) {
        String missing_guardian_id = entry.getKey();
        KeyCeremony.ElectionPublicKey missing_guardian_key = entry.getValue();

        // Share already reconstructed
        if (this.ballot_shares.containsKey(missing_guardian_id)) {
          continue;
        }

        Map<String, CompensatedDecryptionShare> compensated_shares =
                filter_by_missing_guardian(missing_guardian_id,
                        this.compensated_ballot_shares.computeIfAbsent(ballot_id, m -> new HashMap<>()));

        DecryptionShare reconstructed_share = Decryptions.reconstruct_decryption_share_for_ballot(
                missing_guardian_key,
                ciphertext_ballot,
                compensated_shares,
                lagrange_coefficients);

        // Add reconstructed share for this guardian
        ballot_shares.put(missing_guardian_id, reconstructed_share);
      }

      // Add all shares for this ballot
      this.ballot_shares.put(ballot_id, ballot_shares);
    }
  }

  /**
   * Get the plaintext tally for the election by composing each Guardian's
   * decrypted representation of each selection into a decrypted representation.
   */
  public Optional<PlaintextTally> get_plaintext_tally(CiphertextTally ciphertext_tally) {
    // Make sure a Quorum of Guardians have announced
    if (!this.announcement_complete() || !this.ready_to_decrypt(this.tally_shares)) {
      logger.atWarning().log("cannot get plaintext tally with less than quorum available guardians");
      return Optional.empty();
    }

    // The tally_shares have been augmented with missing guardians. Can now decrypt.
    return DecryptWithShares.decrypt_tally(
            ciphertext_tally,
            this.tally_shares,
            this.context);
  }


  /**
   * Get the plaintext spoiled ballots for the election by composing each Guardian's
   * decrypted representation of each selection into a decrypted representation.
   * @return Map(BALLOT_ID, PlaintextTally)
   */
  public Optional<Map<String, PlaintextTally>> get_plaintext_ballots(
          Iterable<SubmittedBallot> ciphertext_ballots) {

    if (!this.announcement_complete()) {
      logger.atWarning().log("cannot get plaintext ballots with less than quorum available guardians");
      return Optional.empty();
    }

    Map<String, PlaintextTally> ballots = new HashMap<>();
    for (SubmittedBallot ciphertext_ballot : ciphertext_ballots) {
      Map<String, DecryptionShare> ballot_shares = this.ballot_shares.get(ciphertext_ballot.object_id());
      if (ballot_shares == null || !this.ready_to_decrypt(ballot_shares)) {
        // Skip ballot if not ready to decrypt LOOK silent failure
        continue;
      }

      Optional<PlaintextTally> tallyO = DecryptWithShares.decrypt_ballot(
              ciphertext_ballot,
              ballot_shares,
              this.context.cryptoExtendedBaseHash);

      // LOOK silent failure
      tallyO.ifPresent(t -> ballots.put(t.tallyId, t));
    }

    return Optional.of(ballots);
  }

  void save_tally_share(String guardian_id, DecryptionShare guardians_tally_share) {
    this.tally_shares.put(guardian_id, guardians_tally_share);
  }

  // Dict[BALLOT_ID, DecryptionShare]
  void save_ballot_shares(String guardian_id, Map<String, Optional<DecryptionShare>> guardians_ballot_shares) {

    for (Map.Entry<String, Optional<DecryptionShare>> entry : guardians_ballot_shares.entrySet()) {
      String ballot_id = entry.getKey();
      Optional<DecryptionShare> guardian_ballot_share = entry.getValue();

      Map<String, DecryptionShare> shares = this.ballot_shares.computeIfAbsent(ballot_id, k -> new HashMap<>());
      guardian_ballot_share.ifPresent(share -> shares.put(guardian_id, share));
    }
  }

  // Filter a guardian pair and compensated share dictionary by missing guardian
  Map<String, CompensatedDecryptionShare> filter_by_missing_guardian(String missing_guardian_id,
                                                                     Map<GuardianPair, CompensatedDecryptionShare> shares) {

    return shares.entrySet().stream().filter(e -> e.getKey().designated_id().equals(missing_guardian_id)).collect(
            Collectors.toMap(e -> e.getKey().owner_id(), e -> e.getValue()));
  }
}
