package com.sunya.electionguard.decrypting;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.flogger.FluentLogger;
import com.sunya.electionguard.AvailableGuardian;
import com.sunya.electionguard.ElectionContext;
import com.sunya.electionguard.CiphertextTally;
import com.sunya.electionguard.DecryptWithShares;
import com.sunya.electionguard.DecryptionShare;
import com.sunya.electionguard.ElectionPolynomial;
import com.sunya.electionguard.Group;
import com.sunya.electionguard.PlaintextTally;
import com.sunya.electionguard.SubmittedBallot;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Orchestrates the decryption of encrypted Tallies and Ballots with remote Guardians. Mutable.
 * Replaces DecryptionMediator in the main library.
 */
public class DecryptingMediator {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final ElectionContext context;
  private final CiphertextTally ciphertext_tally;
  private final Iterable<SubmittedBallot> ciphertext_ballots; // spoiled ballots

  // Map(MISSING_GUARDIAN_ID, Group.ElementModQ) The guardian's ElGamal.KeyPair public key.
  private final Map<String, Group.ElementModP> guardianPublicKeys;

  //// computed

  private final Set<String> missingGuardians;

  // Map(AVAILABLE_GUARDIAN_ID, DecryptingTrustee.Proxy)
  private final Map<String, DecryptingTrusteeIF> available_guardians = new HashMap<>();

  // Map(AVAILABLE_GUARDIAN_ID, DecryptionShare)
  private final Map<String, DecryptionShare> tally_shares = new HashMap<>();

  // Map<ALL_GUARDIAN_ID, Map<BALLOT_ID, DecryptionShare>>
  private final Map<String, Map<String, DecryptionShare>> ballot_shares = new HashMap<>();

  // Map(AVAILABLE_GUARDIAN_ID, ElementModQ)
  private Map<String, Group.ElementModQ> lagrange_coefficients;
  private List<AvailableGuardian> guardianStates;

  public DecryptingMediator(ElectionContext context,
                            CiphertextTally encryptedTally,
                            Iterable<SubmittedBallot> spoiled_ballots,
                            Map<String, Group.ElementModP> guardianPublicKeys) {
    Preconditions.checkNotNull(context);
    Preconditions.checkNotNull(encryptedTally);
    Preconditions.checkNotNull(spoiled_ballots);
    Preconditions.checkNotNull(guardianPublicKeys);
    Preconditions.checkArgument(!guardianPublicKeys.isEmpty());

    this.context = context;
    this.ciphertext_tally = encryptedTally;
    this.ciphertext_ballots = spoiled_ballots;
    this.guardianPublicKeys = guardianPublicKeys;

    this.missingGuardians = new HashSet<>(guardianPublicKeys.keySet());
  }

  @Nullable
  public List<AvailableGuardian> getAvailableGuardians() {
    return this.guardianStates;
  }

  /**
   * Announce that a Guardian is present and participating in the decryption.
   * A Decryption Share will be generated for the Guardian.
   * <p>
   * @param guardian: The guardian who will participate in the decryption.
   * @return true on "success".
   */
  public boolean announce(DecryptingTrusteeIF guardian) {
    // Only allow a guardian to announce once
    if (available_guardians.containsKey(guardian.id())) {
      logger.atInfo().log("guardian %s already announced", guardian.id());
      return false;
    }

    // LOOK Compute the Decryption Share for the guardian, right now. Should be a separate step??
    DecryptionShare tally_share =
            RemoteDecryptions.computeDecryptionShareForTally(guardian, this.ciphertext_tally, this.context);
    this.tally_shares.put(guardian.id(), tally_share);

    // LOOK Compute the spoiled ballot decryption shares. Should be a separate step??
    Optional<Map<String, DecryptionShare>> ballot_shares =
            RemoteDecryptions.computeDecryptionShareForBallots(guardian, this.ciphertext_ballots, context);
    if (ballot_shares.isEmpty()) {
      logger.atWarning().log("announce could not generate spoiled ballot decryption share for %s", guardian.id());
      return false;
    }
    this.ballot_shares.put(guardian.id(), ballot_shares.get());

    // Mark guardian in attendance and check their keys
    this.available_guardians.put(guardian.id(), guardian);
    this.missingGuardians.remove(guardian.id());

    return true; // this.validate_missing_guardian_keys(guardian);
  }

  /* Check the guardian's collections of keys and ensure the public keys match for the missing guardians.
  private boolean validate_missing_guardian_keys(DecryptingTrustee.Proxy guardian) {

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
                  guardian.id(), guardian_id);
          return false;
        }
      } else {
        this.missing_guardians.put(guardian_id, public_key);
      }
    }
    return true;
  } */

  // Decrypt the tally.
  // Get the plaintext tally for the election by composing each Guardian's
  // decrypted representation of each selection into a decrypted representation
  public Optional<PlaintextTally> get_plaintext_tally() {
    // Make sure a Quorum of Guardians have announced
    if (this.available_guardians.size() < this.context.quorum) {
      logger.atWarning().log("cannot get plaintext tally with less than quorum available guardians");
      return Optional.empty();
    }
    compute_lagrange_coefficients();

    // If all Guardians are present decrypt the tally
    if (this.available_guardians.size() == this.context.numberOfGuardians) {
      return DecryptWithShares.decrypt_tally(
              this.ciphertext_tally,
              this.tally_shares,
              this.context);
    }

    // If guardians are missing, compensate
    this.compute_missing_shares_for_tally();
    if (this.tally_shares.size() != this.context.numberOfGuardians) {
      logger.atWarning().log("get plaintext tally failed with share length mismatch");
      return Optional.empty();
    }

    // The tally_shares have been augmented with missing guardians. Can now decrypt.
    return DecryptWithShares.decrypt_tally(
            this.ciphertext_tally,
            this.tally_shares,
            this.context);
  }

  private void compute_missing_shares_for_tally() {
    Map<String, DecryptionShare> missing_tally_shares = new HashMap<>(); // Map(MISSING_GUARDIAN_ID, DecryptionShare)

    for (String missing_guardian_id : this.missingGuardians) {
      Group.ElementModP missing_public_key = this.guardianPublicKeys.get(missing_guardian_id);
      if (missing_public_key == null) {
        logger.atWarning().log("compute_missing_shares_for_tally has no public key for missing_guardian %s", missing_guardian_id);
        return;
      }
      if (this.tally_shares.containsKey(missing_guardian_id)) {
        continue;
      }

      Optional<Map<String, DecryptionShare.CompensatedDecryptionShare>> compensated_shares =
              this.get_compensated_shares_for_tally(missing_guardian_id);
      if (compensated_shares.isEmpty()) {
        logger.atWarning().log("compute_missing_shares_for_tally failed compensating for %s", missing_guardian_id);
        return;
      }

      DecryptionShare missing_decryption_share = RemoteDecryptions.reconstruct_decryption_share_for_tally(
              missing_guardian_id,
              missing_public_key,
              this.ciphertext_tally,
              compensated_shares.get(),
              this.lagrange_coefficients);
      missing_tally_shares.put(missing_guardian_id, missing_decryption_share);
    }

    if (missing_tally_shares.isEmpty()) {
      logger.atWarning().log("get plaintext tally failed: missing_tally_shares is empty");
      return;
    }

    // Add the newly calculated shares
    this.tally_shares.putAll(missing_tally_shares);
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
          String missing_guardian_id) {

    Map<String, DecryptionShare.CompensatedDecryptionShare> compensated_decryptions = new HashMap<>();
    // Loop through each of the available guardians and calculate decryption shares for the missing guardian
    for (DecryptingTrusteeIF available_guardian : this.available_guardians.values()) {
      DecryptionShare.CompensatedDecryptionShare tally_share = RemoteDecryptions.computeCompensatedDecryptionShareForTally(
              available_guardian,
              missing_guardian_id,
              this.ciphertext_tally,
              this.context);
        compensated_decryptions.put(available_guardian.id(), tally_share);
    }

    // Verify that we generated the correct number of partials
    if (compensated_decryptions.size() != this.available_guardians.size()) {
      logger.atWarning().log("compensate mismatch partial decryptions for missing guardian %s", missing_guardian_id);
      return Optional.empty();
    } else {
      return Optional.of(compensated_decryptions);
    }
  }

  /** You must call get_plaintext_tally() first. */
  public Optional<List<PlaintextTally>> decrypt_spoiled_ballots() {
    if (!compute_ballot_shares()){
      return Optional.empty();
    }

    // LOOK must augment this.ballot_shares with missing guardians
    return Optional.of(DecryptWithShares.decrypt_spoiled_ballots(this.ciphertext_ballots, this.ballot_shares, this.context));
  }

  /**
   * Get the plaintext spoiled ballots for the election by composing each Guardian's
   * decrypted representation of each selection into a decrypted representation.
   * @return Map(BALLOT_ID, PlaintextTally)
   */
  public Optional<Map<String, PlaintextTally>> get_plaintext_ballots() {
    if (!compute_ballot_shares()){
      return Optional.empty();
    }

    return DecryptWithShares.decrypt_ballots(
            this.ciphertext_ballots, // LOOK running through ballots twice
            this.ballot_shares, // MAP(AVAILABLE_GUARDIAN_ID, Map(BALLOT_ID, DecryptionShare))
            this.context);
  }

  private boolean compute_ballot_shares() {
    // Make sure a Quorum of Guardians have announced
    if (this.available_guardians.size() < this.context.quorum) {
      logger.atWarning().log("cannot decrypt with less than quorum available guardians");
      return false;
    }

    // If guardians are missing, for each ballot compute compensated ballot_shares, add to this.ballot_shares
    if (this.available_guardians.size() < this.context.numberOfGuardians) {
      for (SubmittedBallot ballot : this.ciphertext_ballots) { // LOOK running through ballots
        if (this.count_ballot_shares(ballot.object_id()) < this.context.numberOfGuardians) {
          this.compute_missing_shares_for_ballot(ballot);
        }
        if (this.count_ballot_shares(ballot.object_id()) != this.context.numberOfGuardians) {
          logger.atWarning().log("decrypt_spoiled_ballots failed with share length mismatch");
          return false;
        }
      }
    }
    return true;
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

  private void compute_missing_shares_for_ballot(SubmittedBallot ballot) {
    for (String missing_guardian_id : this.missingGuardians) {
      Group.ElementModP missing_public_key = this.guardianPublicKeys.get(missing_guardian_id);
      if (missing_public_key == null) {
        logger.atWarning().log("compute_missing_shares_for_tally has no public key for missing_guardian %s", missing_guardian_id);
        return;
      }

      Optional<Map<String, DecryptionShare.CompensatedDecryptionShare>> compensated_shares =
              this.get_compensated_shares_for_ballot(ballot, missing_guardian_id);
      if (compensated_shares.isEmpty()) {
        logger.atWarning().log("get plaintext ballot failed compensating for %s", missing_guardian_id);
        return;
      }

      DecryptionShare missing_decryption_share = RemoteDecryptions.reconstruct_decryption_share_for_ballot(
              missing_guardian_id,
              missing_public_key,
              ballot,
              compensated_shares.get(),
              this.lagrange_coefficients);

      // LOOK ballot_shares now include missing_ballots
      Map<String, DecryptionShare> guardian_shares = this.ballot_shares.computeIfAbsent(missing_guardian_id, k -> new HashMap<>());
      guardian_shares.put(ballot.object_id(), missing_decryption_share);

      this.ballot_shares.get(missing_guardian_id).put(ballot.object_id(), missing_decryption_share);
    }
  }

  private Optional<Map<String, DecryptionShare.CompensatedDecryptionShare>> get_compensated_shares_for_ballot(
          SubmittedBallot ballot, String missing_guardian_id) {

    Map<String, DecryptionShare.CompensatedDecryptionShare> compensated_decryptions = new HashMap<>();

    // Loop through each of the available guardians and calculate decryption shares for the missing one
    for (DecryptingTrusteeIF available_guardian : this.available_guardians.values()) {
      DecryptionShare.CompensatedDecryptionShare ballot_share = RemoteDecryptions.computeCompensatedDecryptionShareForBallot(
              available_guardian,
              missing_guardian_id,
              ballot,
              this.context);
        compensated_decryptions.put(available_guardian.id(), ballot_share);
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
    this.guardianStates = new ArrayList<>();
    this.lagrange_coefficients = new HashMap<>();
    for (DecryptingTrusteeIF guardian : this.available_guardians.values()) {
      List<Integer> seq_orders = this.available_guardians.values().stream()
              .filter(g -> !g.id().equals(guardian.id()))
              .map(g -> g.xCoordinate())
              .toList();
      Group.ElementModQ coeff = ElectionPolynomial.compute_lagrange_coefficient(guardian.xCoordinate(), seq_orders);
      this.lagrange_coefficients.put(guardian.id(), coeff);
      this.guardianStates.add(new AvailableGuardian(guardian.id(), guardian.xCoordinate(), coeff));
    }
  }

}
