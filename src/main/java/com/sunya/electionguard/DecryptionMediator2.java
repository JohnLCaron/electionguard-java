package com.sunya.electionguard;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.flogger.FluentLogger;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Orchestrates the decryption of encrypted Tallies and Ballots.
 * Mutable.
 * LOOK where are the decrypted spoiled ballots?
 */
public class DecryptionMediator2 {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final CiphertextElectionContext context;
  private final CiphertextTally ciphertext_tally;
  private final Iterable<CiphertextAcceptedBallot> ciphertext_ballots; // spoiled ballots

  // Map(AVAILABLE_GUARDIAN_ID, DecryptionShare)
  private final Map<String, DecryptionShare> tally_shares = new HashMap<>();

  // Map<AVAILABLE_GUARDIAN_ID, Map<BALLOT_ID, DecryptionShare>>
  private final Map<String, Map<String, DecryptionShare>> ballot_shares = new HashMap<>();

  // Map(AVAILABLE_GUARDIAN_ID, Guardian)
  private final Map<String, Guardian> available_guardians = new HashMap<>();
  // Map(MISSING_GUARDIAN_ID, ElectionPublicKey)
  private final Map<String, KeyCeremony.ElectionPublicKey> missing_guardians = new HashMap<>();
  // Map(AVAILABLE_GUARDIAN_ID, ElementModQ)
  private Map<String, Group.ElementModQ> lagrange_coefficients;
  private List<GuardianState> guardianStates;

  public DecryptionMediator2(CiphertextElectionContext context,
                             CiphertextTally encryptedTally,
                             Iterable<CiphertextAcceptedBallot> spoiled_ballots) {
    this.context = context;
    this.ciphertext_tally = encryptedTally;
    this.ciphertext_ballots = spoiled_ballots;
  }

  /**
   * Announce that a Guardian is present and participating in the decryption.
   * A Decryption Share will be generated for the Guardian.
   * <p>
   * @param guardian: The guardian who will participate in the decryption.
   * @return true on "success".
   */
  public boolean announce(Guardian guardian) {
    // Only allow a guardian to announce once
    if (available_guardians.containsKey(guardian.object_id)) {
      logger.atInfo().log("guardian %s already announced", guardian.object_id);
      return false;
    }

    // Compute the Decryption Share for the guardian.
    Optional<DecryptionShare> tally_share =
            Decryptions2.compute_decryption_share(guardian, this.ciphertext_tally, this.context);
    if (tally_share.isEmpty()) {
      logger.atWarning().log("announce could not generate decryption share for %s", guardian.object_id);
      return false;
    }
    this.tally_shares.put(guardian.object_id, tally_share.get());

    // Compute the spoiled ballot decryption shares
    Optional<Map<String, DecryptionShare>> ballot_shares =
            Decryptions2.compute_decryption_share_for_ballots(guardian, this.ciphertext_ballots, context);
    if (ballot_shares.isEmpty()) {
      logger.atWarning().log("announce could not generate spoiled ballot decryption share for %s", guardian.object_id);
      return false;
    }
    this.ballot_shares.put(guardian.object_id, ballot_shares.get());

    // Mark guardian in attendance and check their keys
    this.mark_available(guardian);
    return this.validate_missing_guardian_keys(guardian);
  }

  /** This guardian removes itself from the missing list since it generated a valid share. */
  private void mark_available(Guardian guardian) {
    this.available_guardians.put(guardian.object_id, guardian);
    this.missing_guardians.remove(guardian.object_id);
  }

  /** Check the guardian's collections of keys and ensure the public keys match for the missing guardians. */
  private boolean validate_missing_guardian_keys(Guardian guardian) {

    // Check this guardian's collection of public keys for other guardians that have not announced
    Map<String, KeyCeremony.ElectionPublicKey> missing_guardians =
            guardian.otherGuardianElectionKeys().entrySet().stream()
                    .filter(e -> !this.available_guardians.containsKey(e.getKey()))
                    .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

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
                  guardian.object_id, guardian_id);
          return false;
        }
      } else {
        this.missing_guardians.put(guardian_id, public_key);
      }
    }
    return true;
  }

  // Decrypt the tally.
  // Get the plaintext tally for the election by composing each Guardian's
  // decrypted representation of each selection into a decrypted representation
  public Optional<PlaintextTally> get_plaintext_tally(Auxiliary.Decryptor decrypt) {
    // Make sure a Quorum of Guardians have announced
    if (this.available_guardians.size() < this.context.quorum) {
      logger.atWarning().log("cannot get plaintext tally with less than quorum available guardians");
      return Optional.empty();
    }
    compute_lagrange_coefficients();

    // If all Guardians are present decrypt the tally
    if (this.available_guardians.size() == this.context.number_of_guardians) {
      return DecryptWithShares2.decrypt_tally(
              this.ciphertext_tally,
              this.tally_shares,
              this.context,
              this.lagrange_coefficients,
              this.guardianStates);
    }

    // If guardians are missing, compensate
    this.compute_missing_shares_for_tally(decrypt);
    if (this.tally_shares.size() != this.context.number_of_guardians) {
      logger.atWarning().log("get plaintext tally failed with share length mismatch");
      return Optional.empty();
    }

    // The tally_shares have been augmented with missing guardians. Can now decrypt.
    return DecryptWithShares2.decrypt_tally(
            this.ciphertext_tally,
            this.tally_shares,
            this.context,
            this.lagrange_coefficients,
            this.guardianStates);
  }

  private void compute_missing_shares_for_tally(Auxiliary.Decryptor decrypt) {
    Map<String, DecryptionShare> missing_tally_shares = new HashMap<>(); // Map(MISSING_GUARDIAN_ID, DecryptionShare)

    for (Map.Entry<String, KeyCeremony.ElectionPublicKey> entry : this.missing_guardians.entrySet()) {
      String missing_guardian_id = entry.getKey();
      KeyCeremony.ElectionPublicKey public_key = entry.getValue();
      if (this.tally_shares.containsKey(missing_guardian_id)) {
        continue;
      }

      Optional<Map<String, DecryptionShare.CompensatedDecryptionShare>> compensated_shares =
              this.get_compensated_shares_for_tally(missing_guardian_id, decrypt);
      if (compensated_shares.isEmpty()) {
        logger.atWarning().log("compute_missing_shares_for_tally failed compensating for %s", missing_guardian_id);
        return;
      }

      DecryptionShare missing_decryption_share = Decryptions2.reconstruct_decryption_share(
              missing_guardian_id,
              public_key,
              this.ciphertext_tally,
              compensated_shares.get(),
              this.lagrange_coefficients);
      missing_tally_shares.put(missing_guardian_id, missing_decryption_share);
    }

    if (missing_tally_shares.isEmpty()) {
      logger.atWarning().log("get plaintext tally failed with computing missing decryption shares");
      return;
    }

    // Add the newly calculated shares
    for (Map.Entry<String, DecryptionShare> entry : missing_tally_shares.entrySet()) {
      this.tally_shares.put(entry.getKey(), entry.getValue());
    }
  }

  /**
   * Compensate for a missing guardian by reconstructing the share using the available guardians.
   * <p>
   * @param missing_guardian_id: the guardian that failed to `announce`.
   * @return a collection of `CompensatedTallyDecryptionShare` generated from all available guardians
   * These are also stored in this.compensated_decryption_shares.
   */
  @VisibleForTesting
  Optional<Map<String, DecryptionShare.CompensatedDecryptionShare>> get_compensated_shares_for_tally(
          String missing_guardian_id, @Nullable Auxiliary.Decryptor decryptor) {

    Map<String, DecryptionShare.CompensatedDecryptionShare> compensated_decryptions = new HashMap<>();
    // Loop through each of the available guardians and calculate decryption shares for the missing one
    for (Guardian available_guardian : this.available_guardians.values()) {
      Optional<DecryptionShare.CompensatedDecryptionShare> tally_share = Decryptions2.compute_compensated_decryption_share(
              available_guardian,
              missing_guardian_id,
              this.ciphertext_tally,
              this.context,
              decryptor);
      if (tally_share.isEmpty()) {
        logger.atWarning().log("compensation failed for missing: %s", missing_guardian_id);
        break;
      } else {
        compensated_decryptions.put(available_guardian.object_id, tally_share.get());
      }
    }

    // Verify that we generated the correct number of partials
    if (compensated_decryptions.size() != this.available_guardians.size()) {
      logger.atWarning().log("compensate mismatch partial decryptions for missing guardian %s", missing_guardian_id);
      return Optional.empty();
    } else {
      return Optional.of(compensated_decryptions);
    }
  }

  /** A tuple of a decrypted spoiled ballot and its tally. */
  public static class SpoiledBallotAndTally {
    public final PlaintextTally tally;
    public final PlaintextBallot ballot;

    SpoiledBallotAndTally(PlaintextTally tally, PlaintextBallot ballot) {
      this.tally = tally;
      this.ballot = ballot;
    }
  }

  /** You must call get_plaintext_tally() first. */
  public Optional<List<SpoiledBallotAndTally>> decrypt_spoiled_ballots() {
    // static Optional<List<DecryptionMediator2.SpoiledBallotAndTally>> decrypt_spoiled_ballots
    return DecryptWithShares2.decrypt_spoiled_ballots(this.ciphertext_ballots, this.ballot_shares, this.context);
  }

  /**
   * Get the plaintext spoiled ballots for the election by composing each Guardian's
   * decrypted representation of each selection into a decrypted representation.
   * @return Map(BALLOT_ID, PlaintextTally)
   */
  public Optional<Map<String, PlaintextTally>> get_plaintext_ballots(
          Auxiliary.Decryptor decryptor) {

    // Make sure a Quorum of Guardians have announced
    if (this.available_guardians.size() < this.context.quorum){
      logger.atWarning().log("cannot decrypt with less than quorum available guardians");
      return Optional.empty();
    }

    // If all Guardians are present, decrypt the ballots using standard shares
    if (this.available_guardians.size() == this.context.number_of_guardians) {
      return DecryptWithShares2.decrypt_ballots(
              this.ciphertext_ballots,
              this.ballot_shares,
              this.context);
    }

    // If guardians are missing, for each ballot compute compensated ballot_shares
    for (CiphertextAcceptedBallot ballot : this.ciphertext_ballots) { // LOOK running through ballots twice
      this.compute_missing_shares_for_ballot(ballot, decryptor);
      if (this.count_ballot_shares(ballot.object_id) != this.context.number_of_guardians) {
        logger.atWarning().log("get plaintext ballot failed with share length mismatch");
        return Optional.empty();
      }
    }

    // Optional<Map<String, PlaintextTally>> decrypt_ballots(
    //          Iterable<CiphertextAcceptedBallot> ballots,
    //          Map<String, Map<String, DecryptionShare>> shares,
    //          CiphertextElectionContext context)
    return DecryptWithShares2.decrypt_ballots(
            this.ciphertext_ballots, // LOOK running through ballots twice
            this.ballot_shares, // MAP(AVAILABLE_GUARDIAN_ID, Map(BALLOT_ID, DecryptionShare))
            this.context);
  }

  private int count_ballot_shares(String ballot_id) {
    int count = 0;
    for (Map<String, DecryptionShare> shares : this.ballot_shares.values()) {
      if (shares.containsKey(ballot_id)) {
        count += 1;
      }
    }
    return count;
  }

  private void compute_missing_shares_for_ballot(CiphertextAcceptedBallot ballot, Auxiliary.Decryptor decrypt) {
    for (Map.Entry<String, KeyCeremony.ElectionPublicKey> entry : this.missing_guardians.entrySet()) {
      String missing_guardian_id = entry.getKey();
      KeyCeremony.ElectionPublicKey public_key = entry.getValue();

      Optional<Map<String, DecryptionShare.CompensatedDecryptionShare>> compensated_shares =
              this.get_compensated_shares_for_ballot(ballot, missing_guardian_id, decrypt);
      if (compensated_shares.isEmpty()) {
        logger.atWarning().log("get plaintext ballot failed compensating for %s", missing_guardian_id);
        return;
      }

      DecryptionShare missing_decryption_share = Decryptions2.reconstruct_decryption_share_for_ballot(
              missing_guardian_id,
              public_key,
              ballot,
              compensated_shares.get(),
              this.lagrange_coefficients);

      /* LOOK what does this mean?
      if (missing_decryption_share.isEmpty()) {
        logger.atWarning().log("get plaintext ballot failed with computing missing decryption shares");
        return;
      } */

      this.ballot_shares.get(missing_guardian_id).put(ballot.object_id, missing_decryption_share);
    }
  }

  private Optional<Map<String, DecryptionShare.CompensatedDecryptionShare>> get_compensated_shares_for_ballot(
          CiphertextAcceptedBallot ballot, String missing_guardian_id, @Nullable Auxiliary.Decryptor decryptor) {

    Map<String, DecryptionShare.CompensatedDecryptionShare> compensated_decryptions = new HashMap<>();

    // Loop through each of the available guardians and calculate decryption shares for the missing one
    for (Guardian available_guardian : this.available_guardians.values()) {
      Optional<DecryptionShare.CompensatedDecryptionShare> ballot_share = Decryptions2.compute_compensated_decryption_share_for_ballot(
              available_guardian,
              missing_guardian_id,
              ballot,
              this.context,
              decryptor);
      if (ballot_share.isEmpty()) {
        logger.atWarning().log("compensation failed for missing: %s", missing_guardian_id);
        break;
      } else {
        compensated_decryptions.put(available_guardian.object_id, ballot_share.get());
      }
    }

    // Verify that we generated the correct number of partials
    if (compensated_decryptions.size() != this.available_guardians.size()) {
      logger.atWarning().log("compensate mismatch partial decryptions for missing guardian %s", missing_guardian_id);
      return Optional.empty();
    } else {
      return Optional.of(compensated_decryptions);
    }
  }

  private void compute_lagrange_coefficients() {
    if (this.lagrange_coefficients != null) {
      return;
    }
    // Compute lagrange coefficients for each of the available guardians
    this.lagrange_coefficients = new HashMap<>();
    for (Guardian available_guardian : this.available_guardians.values()) {
      List<Integer> seq_orders = this.available_guardians.values().stream()
              .filter(g -> !g.object_id.equals(available_guardian.object_id))
              .map(g -> g.sequence_order()).collect(Collectors.toList());
      this.lagrange_coefficients.put(
              available_guardian.object_id,
              ElectionPolynomial.compute_lagrange_coefficient(available_guardian.sequence_order(), seq_orders));
    }

    // Compute GuardianState's for all of the guardians
    this.guardianStates = new ArrayList<>();
    this.available_guardians.values().forEach(g -> this.guardianStates.add(GuardianState.create(
            g.object_id, g.sequence_order(), false)));
    this.missing_guardians.values().forEach(k -> this.guardianStates.add(GuardianState.create(
            k.owner_id(), k.sequence_order(), true)));
  }
}
