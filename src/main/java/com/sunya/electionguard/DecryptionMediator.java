package com.sunya.electionguard;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.common.flogger.FluentLogger;

import javax.annotation.Nullable;
import java.math.BigInteger;
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

  private final InternalElectionDescription metadata;
  private final CiphertextElectionContext context;
  private final CiphertextTallyBuilder encryptedTally;
  private final Optional<PlaintextTally> decryptedTally = Optional.empty();

  private final Map<String, Guardian> available_guardians = new HashMap<>(); // Map(AVAILABLE_GUARDIAN_ID, Guardian)
  private final Map<String, KeyCeremony.ElectionPublicKey> missing_guardians = new HashMap<>(); // Map(MISSING_GUARDIAN_ID, Guardian)

  /** A collection of Decryption Shares for each Available Guardian. */
  private final Map<String, TallyDecryptionShare> decryption_shares = new HashMap<>(); // Map(AVAILABLE_GUARDIAN_ID, TallyDecryptionShare)

  /**
   * A collection of lagrange coefficients w_ij computed by available guardians for each missing guardian.
   * Map(MISSING_GUARDIAN_ID, Map(AVAILABLE_GUARDIAN_ID, ElementModQ))
   * So _lagrange_coefficients(MISSING_GUARDINA) are the w_l of section 10.
   */
  private final Map<String, Map<String, Group.ElementModQ>> lagrange_coefficients = new HashMap<>();

  /**
   * A collection of Compensated Decryption Shares for each Available Guardian.
   * Map( MISSING_GUARDIAN_ID, Map(AVAILABLE_GUARDIAN_ID, CompensatedTallyDecryptionShare))
   */
  private final Map<String, Map<String, CompensatedTallyDecryptionShare>> compensated_decryption_shares = new HashMap<>();

  public DecryptionMediator(InternalElectionDescription metadata, CiphertextElectionContext context, CiphertextTallyBuilder encryptedTally) {
    this.metadata = metadata;
    this.context = context;
    this.encryptedTally = encryptedTally;
  }

  /**
   * Announce that a Guardian is present and participating in the decryption.
   * A Decryption Share will be generated for the Guardian.
   * <p>
   * @param guardian: The guardian who will participate in the decryption.
   * @return a `TallyDecryptionShare` for this `Guardian` or `None` if there is an error.
   */
  Optional<TallyDecryptionShare> announce(Guardian guardian) {
    // Only allow a guardian to announce once
    if (available_guardians.containsKey(guardian.object_id)) {
      logger.atInfo().log("guardian %s already announced", guardian.object_id);
      return Optional.ofNullable(this.decryption_shares.get(guardian.object_id));
    }

    // Compute the Decryption Share for the guardian
    Optional<TallyDecryptionShare> share =
            Decryptions.compute_decryption_share(guardian, this.encryptedTally, this.context);
    if (share.isEmpty()) {
      logger.atInfo().log("announce could not generate decryption share for %s", guardian.object_id);
      return Optional.empty();
    }

    // Submit the share
    if (this.submit_decryption_share(share.get())) {
      this.available_guardians.put(guardian.object_id, guardian);
    } else {
      logger.atInfo().log("announce could not submit decryption share for %s", guardian.object_id);
      return Optional.empty();
    }

    // This guardian removes itself from the missing list since it generated a valid share
    this.missing_guardians.remove(guardian.object_id);

    // Check this guardian's collection of public keys
    // for other guardians that have not announced
    Map<String, KeyCeremony.ElectionPublicKey> missing_guardians =
            guardian.otherGuardianElectionKeys().entrySet().stream()
                    .filter(e -> !this.available_guardians.containsKey(e.getKey()))
                    .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

    // Check that the public keys match for any missing guardians already reported
    // note this check naively assumes that the first guardian to announce is telling the truth
    // but for this implementation it is simply a sanity check on the input data.
    // a consuming application should implement better validation of the guardian state
    // before announcing a guardian is available for decryption.
    for (Map.Entry<String, KeyCeremony.ElectionPublicKey> entry : missing_guardians.entrySet()) {
      String guardian_id = entry.getKey();
      KeyCeremony.ElectionPublicKey public_key = entry.getValue();
      if (this.missing_guardians.containsKey(guardian_id)) {
        if (!this.missing_guardians.get(guardian_id).equals(public_key)) {
          logger.atInfo().log("announce guardian: %s expected public key mismatch for missing %s", guardian.object_id, guardian_id);
          return Optional.empty();
        }
      } else {
        this.missing_guardians.put(guardian_id, missing_guardians.get(guardian_id));
        // why not this._missing_guardians.put(guardian_id, public_key);
      }
    }

    return share;
  }

  /**
   * Compensate for a missing guardian by reconstructing the share using the available guardians.
   * <p>
   * @param missing_guardian_id: the guardian that failed to `announce`.
   * @return a collection of `CompensatedTallyDecryptionShare` generated from all available guardians
   * or `None if there is an error
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
    Map<String, Group.ElementModQ> lagrange_coefficients = new HashMap<>();

    // Loop through each of the available guardians
    // and calculate a partial for the missing one
    for (Guardian available_guardian : this.available_guardians.values()) {
      // Compute lagrange coefficients for each of the available guardians
      //  *[guardian.sequence_order for guardian in this._available_guardians.values()
      //     if guardian.object_id != available_guardian.object_id]
      List<BigInteger> seq_orders = this.available_guardians.values().stream().filter(g -> !g.object_id.equals(available_guardian.object_id))
              .map(g -> BigInteger.valueOf(g.sequence_order())).collect(Collectors.toList());
      lagrange_coefficients.put(
              available_guardian.object_id,
              ElectionPolynomial.compute_lagrange_coefficient(BigInteger.valueOf(available_guardian.sequence_order()), seq_orders));

      // Compute the decryption shares
      Optional<CompensatedTallyDecryptionShare> share = Decryptions.compute_compensated_decryption_share(
              available_guardian,
              missing_guardian_id,
              this.encryptedTally,
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
      this.lagrange_coefficients.put(missing_guardian_id, lagrange_coefficients);
      this.submit_compensated_decryption_shares(compensated_decryptions);
      return Optional.of(compensated_decryptions);
    }
  }

  /**
   * Decrypt the ciphertext Tally.
   * Get the plaintext tally for the election by composing each Guardian's
   * decrypted representation of each selection into a decrypted representation.
   * <p>
   * @param recompute: Specify if the function should recompute the result, even if one already exists. default false
   * @return a `PlaintextTally` or `None`
   */
  Optional<PlaintextTally> getDecryptedTally(boolean recompute, @Nullable Auxiliary.Decryptor decryptor) {
    if (decryptor == null) {
      decryptor = Rsa::decrypt;
    }

    if (this.decryptedTally.isPresent() && !recompute) {
      return this.decryptedTally;
    }

    // Make sure a Quorum of Guardians have announced
    if (this.available_guardians.size() < this.context.quorum) {
      logger.atInfo().log("cannot get plaintext tally with less than quorum available guardians");
      return Optional.empty();
    }

    // If all Guardians are present decrypt the tally
    if (this.available_guardians.size() == this.context.number_of_guardians) {
      return DecryptWithShares.decrypt_tally(this.encryptedTally, this.decryption_shares, this.context);
    }

    // If missing guardians compensate for the missing guardians
    for (String missing : this.missing_guardians.keySet()) {
      Optional<List<CompensatedTallyDecryptionShare>> compensated_decryptions = this.compensate(missing, decryptor);
      if (compensated_decryptions.isEmpty()) {
        logger.atInfo().log("get plaintext tally failed compensating for %s", missing);
        return Optional.empty();
      }
    }

    // Reconstruct the missing partial decryptions from the compensation shares
    Optional<Map<String, TallyDecryptionShare>> missing_decryption_shares =
            Decryptions.reconstruct_missing_tally_decryption_shares(
                    this.encryptedTally,
                    this.missing_guardians,
                    this.compensated_decryption_shares,
                    this.lagrange_coefficients);
    if (missing_decryption_shares.isEmpty() ||
            missing_decryption_shares.get().size() != this.missing_guardians.size()) {
      logger.atInfo().log("get plaintext tally failed with missing decryption shares");
      return Optional.empty();
    }

    Map<String, TallyDecryptionShare> merged_decryption_shares = new HashMap<>();

    for (Map.Entry<String, TallyDecryptionShare> entry : this.decryption_shares.entrySet()) {
      merged_decryption_shares.put(entry.getKey(), entry.getValue());
    }

    for (Map.Entry<String, TallyDecryptionShare> entry : missing_decryption_shares.get().entrySet()) {
      merged_decryption_shares.put(entry.getKey(), entry.getValue());
    }

    if (merged_decryption_shares.size() != this.context.number_of_guardians) {
      logger.atInfo().log("get plaintext tally failed with share length mismatch");
      return Optional.empty();
    }

    return DecryptWithShares.decrypt_tally(this.encryptedTally, merged_decryption_shares, this.context);
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
}
