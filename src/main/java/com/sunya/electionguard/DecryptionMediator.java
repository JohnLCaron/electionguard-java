package com.sunya.electionguard;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.common.flogger.FluentLogger;

import javax.annotation.Nullable;
import java.util.*;
import java.util.stream.Collectors;

import static com.sunya.electionguard.DecryptionShare.*;
import static com.sunya.electionguard.Election.*;

/**
 * Create a plaintext tally by composing each guardian's partial decryptions or compensated decryptions.
 * Mutable.
 */
public class DecryptionMediator {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final CiphertextElectionContext context;
  private final PublishedCiphertextTally encryptedTally;
  private final Iterable<Ballot.CiphertextAcceptedBallot> spoiled_ballots;


  // Map(AVAILABLE_GUARDIAN_ID, Guardian)
  private final Map<String, Guardian> available_guardians = new HashMap<>();
  // Map(MISSING_GUARDIAN_ID, ElectionPublicKey)
  private final Map<String, KeyCeremony.ElectionPublicKey> missing_guardians = new HashMap<>();

  // Map(AVAILABLE_GUARDIAN_ID, TallyDecryptionShare)
  private final Map<String, TallyDecryptionShare> decryption_shares = new HashMap<>();

  // Missing guardians - compensated shares
  // Map( MISSING_GUARDIAN_ID, Map(AVAILABLE_GUARDIAN_ID, CompensatedTallyDecryptionShare))
  private final Map<String, Map<String, CompensatedTallyDecryptionShare>> compensated_decryption_shares = new HashMap<>();

  // intermediate results, created in decrypt_tally(), used in decrypt_spoiled_ballots()
  private Optional<PlaintextTally> decryptedTally = Optional.empty();
  // Map(AVAILABLE_GUARDIAN_ID, ElementModQ)
  private Map<String, Group.ElementModQ> lagrange_coefficients;
  // Map(ALL?_GUARDIAN_ID, TallyDecryptionShare)
  private Map<String, TallyDecryptionShare> merged_decryption_shares;

  public DecryptionMediator(CiphertextElectionContext context,
                            PublishedCiphertextTally encryptedTally,
                            Iterable<Ballot.CiphertextAcceptedBallot> spoiled_ballots) {
    this.context = context;
    this.encryptedTally = encryptedTally;
    this.spoiled_ballots = spoiled_ballots;
  }

  public Iterable<Ballot.CiphertextAcceptedBallot> spoiled_ballots() {
    return spoiled_ballots;
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

    // Compute the Decryption Share for the guardian. Parallizable over each of the tally's contests.
    Optional<TallyDecryptionShare> share =
            Decryptions.compute_decryption_share(guardian, this.encryptedTally, this.context, this.spoiled_ballots);
    if (share.isEmpty()) {
      logger.atInfo().log("announce could not generate decryption share for %s", guardian.object_id);
      return false;
    }

    // Submit the share
    if (this.submit_decryption_share(share.get())) {
      this.available_guardians.put(guardian.object_id, guardian);
    } else {
      logger.atInfo().log("announce could not submit decryption share for %s", guardian.object_id);
      return false;
    }

    // This guardian removes itself from the missing list since it generated a valid share
    this.missing_guardians.remove(guardian.object_id);

    // Check this guardian's collection of public keys for other guardians that have not announced
    Map<String, KeyCeremony.ElectionPublicKey> missing_guardians =
            guardian.otherGuardianElectionKeys().entrySet().stream()
                    .filter(e -> !this.available_guardians.containsKey(e.getKey()))
                    .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

    // TODO Check that the public keys match for any missing guardians already reported
    //  note this check naively assumes that the first guardian to announce is telling the truth
    //  but for this implementation it is simply a sanity check on the input data.
    //  a consuming application should implement better validation of the guardian state
    //  before announcing a guardian is available for decryption.
    for (Map.Entry<String, KeyCeremony.ElectionPublicKey> entry : missing_guardians.entrySet()) {
      String guardian_id = entry.getKey();
      KeyCeremony.ElectionPublicKey public_key = entry.getValue();
      if (this.missing_guardians.containsKey(guardian_id)) {
        if (!this.missing_guardians.get(guardian_id).equals(public_key)) {
          logger.atInfo().log("announce guardian: %s expected public key mismatch for missing %s", guardian.object_id, guardian_id);
          return false;
        }
      } else {
        this.missing_guardians.put(guardian_id, missing_guardians.get(guardian_id));
        // why not this._missing_guardians.put(guardian_id, public_key);
      }
    }

    return true;
  }

  /** Submit the decryption share to be used in the decryption. */
  @VisibleForTesting
  boolean submit_decryption_share(TallyDecryptionShare share) {
    if (this.decryption_shares.containsKey(share.guardian_id())) {
      logger.atInfo().log("cannot submit for guardian %s that already decrypted", share.guardian_id());
      return false;
    }
    this.decryption_shares.put(share.guardian_id(), share);
    return true;
  }

  /**
   * Decrypt the ciphertext Tally. Python method: get_plaintext_tally().
   * @param recompute: Specify if the function should recompute the result, even if one already exists. default false
   * @return a `PlaintextTally` or `None`
   */
  public Optional<PlaintextTally> decrypt_tally(boolean recompute, @Nullable Auxiliary.Decryptor decryptor) {
    if (decryptor == null) {
      decryptor = Rsa::decrypt;
    }

    if (this.decryptedTally.isPresent() && !recompute) {
      return this.decryptedTally;
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
    List<PlaintextTally.GuardianState> guardianStates = new ArrayList<>();
    this.available_guardians.values().forEach(g -> guardianStates.add(PlaintextTally.GuardianState.create(
            g.object_id, g.sequence_order(), false)));
    this.missing_guardians.values().forEach(k -> guardianStates.add(PlaintextTally.GuardianState.create(
            k.owner_id(), k.sequence_order(), true)));

    // Make sure a Quorum of Guardians have announced
    if (this.available_guardians.size() < this.context.quorum) {
      logger.atInfo().log(
              String.format("decrypt_tally fails with less than quorum (%d) available guardians (%d)",
                      this.context.quorum, this.available_guardians.size()));
      return Optional.empty();
    }

    // If all Guardians are present, decrypt the tally using standard shares
    if (this.available_guardians.size() == this.context.number_of_guardians) {
      // LOOK technically we dont need lagrange_coefficients and guardianStates when all Guardians are present.
      return DecryptWithShares.decrypt_tally(this.encryptedTally, this.decryption_shares, this.context,
              this.lagrange_coefficients, guardianStates);
    }

    // If missing guardians, compensate for them, store CompensatedTallyDecryptionShare into compensated_decryption_shares
    for (String missing : this.missing_guardians.keySet()) {
      Optional<List<CompensatedTallyDecryptionShare>> compensated_decryptions = this.compensate(missing, decryptor);
      if (compensated_decryptions.isEmpty()) {
        logger.atInfo().log("decrypt_tally failed compensating for %s", missing);
        return Optional.empty();
      }
    }

    // Reconstruct the missing partial decryptions from the compensation shares
    // Map(MISSING_GUARDIAN_ID, TallyDecryptionShare)
    Optional<Map<String, TallyDecryptionShare>> missing_decryption_shares =
            Decryptions.reconstruct_missing_tally_decryption_shares(
                    this.encryptedTally,
                    this.missing_guardians,
                    this.compensated_decryption_shares,
                    this.lagrange_coefficients,
                    this.spoiled_ballots);
    if (missing_decryption_shares.isEmpty() ||
            missing_decryption_shares.get().size() != this.missing_guardians.size()) {
      logger.atInfo().log("decrypt_tally failed with missing decryption shares");
      return Optional.empty();
    }

    // Create merged decryption shares
    this.merged_decryption_shares = new HashMap<>();
    for (Map.Entry<String, TallyDecryptionShare> entry : this.decryption_shares.entrySet()) {
      this.merged_decryption_shares.put(entry.getKey(), entry.getValue());
    }
    for (Map.Entry<String, TallyDecryptionShare> entry : missing_decryption_shares.get().entrySet()) {
      this.merged_decryption_shares.put(entry.getKey(), entry.getValue());
    }
    if (this.merged_decryption_shares.size() != this.context.number_of_guardians) {
      logger.atInfo().log("decrypt_tally failed with share length mismatch");
      return Optional.empty();
    }

    // If all Guardians are not present, decrypt the tally using compensated shares
    this.decryptedTally = DecryptWithShares.decrypt_tally(this.encryptedTally, merged_decryption_shares, this.context,
            this.lagrange_coefficients, guardianStates);
    return this.decryptedTally;
  }

  /**
   * Compensate for a missing guardian by reconstructing the share using the available guardians.
   * Parallizable over each of the tally's contests.
   * <p>
   * @param missing_guardian_id: the guardian that failed to `announce`.
   * @return a collection of `CompensatedTallyDecryptionShare` generated from all available guardians
   * These are also stored in this.compensated_decryption_shares.
   */
  @VisibleForTesting
  Optional<List<CompensatedTallyDecryptionShare>> compensate(
          String missing_guardian_id, @Nullable Auxiliary.Decryptor decryptor) {

    // Only allow a guardian to be compensated for once
    if (this.compensated_decryption_shares.containsKey(missing_guardian_id)) {
      logger.atInfo().log("guardian %s already compensated", missing_guardian_id);
      return Optional.of(ImmutableList.copyOf(
              this.compensated_decryption_shares.get(missing_guardian_id).values()));
    }

    List<CompensatedTallyDecryptionShare> compensated_decryptions = new ArrayList<>();
    // Loop through each of the available guardians and calculate decryption shares for the missing one
    for (Guardian available_guardian : this.available_guardians.values()) {
      Optional<CompensatedTallyDecryptionShare> share = Decryptions.compute_compensated_decryption_share(
              available_guardian,
              missing_guardian_id,
              this.encryptedTally,
              this.spoiled_ballots,
              this.context,
              decryptor);
      if (share.isEmpty()) {
        logger.atInfo().log("compensation failed for missing: %s", missing_guardian_id);
        break;
      } else {
        compensated_decryptions.add(share.get());
      }
    }

    // Verify that we generated the correct number of partials
    if (compensated_decryptions.size() != this.available_guardians.size()) {
      logger.atInfo().log("compensate mismatch partial decryptions for missing guardian %s", missing_guardian_id);
      return Optional.empty();
    } else {
      this.submit_compensated_decryption_shares(compensated_decryptions);
      return Optional.of(compensated_decryptions);
    }
  }

  /** Submit compensated decryption shares to be used in the decryption. */
  private boolean submit_compensated_decryption_shares(List<CompensatedTallyDecryptionShare> shares) {
    List<Boolean> ok = shares.stream().map(this::submit_compensated_decryption_share).
            collect(Collectors.toList());
    return ok.stream().allMatch(b -> b);
  }

  /** Submit compensated decryption share to be used in the decryption. */
  private boolean submit_compensated_decryption_share(CompensatedTallyDecryptionShare share) {
    Map<String, CompensatedTallyDecryptionShare> shareMap =
            this.compensated_decryption_shares.get(share.missing_guardian_id());
    if (this.compensated_decryption_shares.containsKey(share.missing_guardian_id()) &&
            (shareMap != null) && shareMap.containsKey(share.guardian_id())) {
      logger.atInfo().log("cannot submit compensated share for guardian %s on behalf of %s that already compensated",
              share.guardian_id(), share.missing_guardian_id());
      return false;
    }

    if (!this.compensated_decryption_shares.containsKey(share.missing_guardian_id())) {
      this.compensated_decryption_shares.put(share.missing_guardian_id(), new HashMap<>());
    }
    Map<String, CompensatedTallyDecryptionShare> y = this.compensated_decryption_shares.get(share.missing_guardian_id());
    y.put(share.guardian_id(), share);

    return true;
  }

  /** You must call decrypt_tally() first */
  public Optional<List<DecryptWithShares.SpoiledTallyAndBallot>> decrypt_spoiled_ballots() {

    if (this.available_guardians.size() == this.context.number_of_guardians) {
      // If all Guardians are present, decrypt the ballot using standard shares
      return DecryptWithShares.decrypt_spoiled_ballots(this.spoiled_ballots, this.available_guardians, this.decryption_shares, this.context);

    } else {
      // If all Guardians are not present, decrypt the ballot using compensated shares
      return DecryptWithShares.decrypt_spoiled_ballots(this.spoiled_ballots, this.available_guardians, this.merged_decryption_shares, this.context);
    }
  }

  /** One for each ballot. You must call decrypt_tally() first
  public Optional<Iterable<DecryptWithShares.SpoiledTallyAndBallot>> decrypt_spoiled_ballots2(
          Iterable<Ballot.CiphertextAcceptedBallot> spoiled_ballots) {

    // If all Guardians are present, decrypt the ballots using standard shares
    if (this.available_guardians.size() == this.context.number_of_guardians) {
      return DecryptWithShares.decrypt_spoiled_ballots(
              spoiled_ballots,
              this.available_guardians, // Map(AVAILABLE_GUARDIAN_ID, Guardian)
              this.decryption_shares, // Map(AVAILABLE_GUARDIAN_ID, TallyDecryptionShare)
              this.context);
    }

    // If missing guardians, compensate for them
    for (String missing : this.missing_guardians.keySet()) {
      Optional<List<CompensatedTallyDecryptionShare>> compensated_decryptions = this.compensate(missing, decryptor);
      if (compensated_decryptions.isEmpty()) {
        logger.atInfo().log("decrypt_tally failed compensating for %s", missing);
        return Optional.empty();
      }
    }

    // Reconstruct the missing partial decryptions from the compensation shares
    // Map(MISSING_GUARDIAN_ID, TallyDecryptionShare)
    Optional<Map<String, TallyDecryptionShare>> missing_decryption_shares =
            Decryptions.reconstruct_missing_tally_decryption_shares_ballots(
                    spoiled_ballots,
                    this.encryptedTally,
                    this.missing_guardians,
                    this.compensated_decryption_shares,
                    this.lagrange_coefficients);
    if (missing_decryption_shares.isEmpty() ||
            missing_decryption_shares.get().size() != this.missing_guardians.size()) {
      logger.atInfo().log("decrypt_tally failed with missing decryption shares");
      return Optional.empty();
    }

    // Create merged decryption shares
    // Map(ALL?_GUARDIAN_ID, TallyDecryptionShare)
    Map<String, TallyDecryptionShare> merged_decryption_shares = new HashMap<>();
    for (Map.Entry<String, TallyDecryptionShare> entry : this.decryption_shares.entrySet()) {
      merged_decryption_shares.put(entry.getKey(), entry.getValue());
    }
    for (Map.Entry<String, TallyDecryptionShare> entry : missing_decryption_shares.get().entrySet()) {
      merged_decryption_shares.put(entry.getKey(), entry.getValue());
    }
    if (merged_decryption_shares.size() != this.context.number_of_guardians) {
      logger.atInfo().log("decrypt_spoiled_ballots failed to have enough shares for decryption");
      return Optional.empty();
    }

    return DecryptWithShares.decrypt_spoiled_ballots(
            spoiled_ballots,
            this.available_guardians, // Map(AVAILABLE_GUARDIAN_ID, Guardian)
            merged_decryption_shares, // Map(ALL?_GUARDIAN_ID, TallyDecryptionShare)
            this.context);
  } */
}
