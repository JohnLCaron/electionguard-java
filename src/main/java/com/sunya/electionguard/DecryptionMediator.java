package com.sunya.electionguard;

import com.google.common.collect.ImmutableList;
import com.google.common.flogger.FluentLogger;

import javax.annotation.Nullable;
import java.math.BigInteger;
import java.util.*;
import java.util.stream.Collectors;

import static com.sunya.electionguard.DecryptionShare.*;
import static com.sunya.electionguard.Election.*;
import static com.sunya.electionguard.Tally.*;

/**
 * The Decryption Mediator composes partial decryptions from each Guardian
 * to form a decrypted representation of an election tally.
 */
public class DecryptionMediator {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final CiphertextElectionContext _encryption;
  private final CiphertextTally _ciphertext_tally;
  private final InternalElectionDescription _metadata;

  private final Map<String, Guardian> _available_guardians = new HashMap<>();
  private final Map<String, KeyCeremony.ElectionPublicKey> _missing_guardians = new HashMap<>();

  private final Optional<PlaintextTally> _plaintext_tally = Optional.empty();

  /** Since spoiled ballots are decrypted, they are just a special case of a tally. */
  private final Map<String, Optional<PlaintextTally>> _plaintext_spoiled_ballots = new HashMap<>();

  /** A collection of Decryption Shares for each Available Guardian. */
  private final Map<String, TallyDecryptionShare> _decryption_shares = new HashMap<>();

  /** A collection of lagrange coefficients `w_{i,j}` computed by available guardians for each missing guardian. */
  private final Map<String, Map<String, Group.ElementModQ>> _lagrange_coefficients = new HashMap<>();

  /** A collection of Compensated Decryption Shares for each Available Guardian. */
  private final Map<String, Map<String, CompensatedTallyDecryptionShare>> _compensated_decryption_shares = new HashMap<>();

  public DecryptionMediator(InternalElectionDescription metadata, CiphertextElectionContext context, CiphertextTally ciphertext_tally) {
    this._metadata = metadata;
    this._encryption = context;
    this._ciphertext_tally = ciphertext_tally;
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
    if (_available_guardians.containsKey(guardian.object_id)) {
      logger.atInfo().log("guardian %s already announced", guardian.object_id, Optional.empty());
      return Optional.ofNullable(this._decryption_shares.get(guardian.object_id));
    }

    // Compute the Decryption Share for the guardian
    Optional<TallyDecryptionShare> share =
            Decryption.compute_decryption_share(guardian, this._ciphertext_tally, this._encryption);
    if (share.isEmpty()) {
      logger.atInfo().log("announce could not generate decryption share for %s", guardian.object_id);
      return Optional.empty();
    }

    // Submit the share
    if (this._submit_decryption_share(share.get())) {
      this._available_guardians.put(guardian.object_id, guardian);
    } else {
      logger.atInfo().log("announce could not submit decryption share for %s", guardian.object_id);
      return Optional.empty();
    }

    // This guardian removes itself from the missing list since it generated a valid share
    this._missing_guardians.remove(guardian.object_id);

    // Check this guardian's collection of public keys
    // for other guardians that have not announced
    Map<String, KeyCeremony.ElectionPublicKey> missing_guardians =
            guardian.guardian_election_public_keys().entrySet().stream()
                    .filter(e -> !this._available_guardians.containsKey(e.getKey()))
                    .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

    // Check that the public keys match for any missing guardians already reported
    // note this check naively assumes that the first guardian to announce is telling the truth
    // but for this implementation it is simply a sanity check on the input data.
    // a consuming application should implement better validation of the guardian state
    // before announcing a guardian is available for decryption.
    for (Map.Entry<String, KeyCeremony.ElectionPublicKey> entry : missing_guardians.entrySet()) {
      String guardian_id = entry.getKey();
      KeyCeremony.ElectionPublicKey public_key = entry.getValue();
      if (this._missing_guardians.containsKey(guardian_id)) {
        if (!this._missing_guardians.get(guardian_id).equals(public_key)) {
          logger.atInfo().log("announce guardian: %s expected public key mismatch for missing %s", guardian.object_id, guardian_id);
          return Optional.empty();
        }
      } else {
        this._missing_guardians.put(guardian_id, missing_guardians.get(guardian_id));
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
  Optional<List<CompensatedTallyDecryptionShare>> compensate(
          String missing_guardian_id, @Nullable Auxiliary.Decryptor decryptor) {

    // Only allow a guardian to be compensated for once
    if (this._compensated_decryption_shares.containsKey(missing_guardian_id)) {
      logger.atInfo().log("guardian {missing_guardian_id} already compensated", missing_guardian_id);
      return Optional.of(ImmutableList.copyOf(
              this._compensated_decryption_shares.get(missing_guardian_id).values()));
    }

    List<CompensatedTallyDecryptionShare> compensated_decryptions = new ArrayList<>();
    Map<String, Group.ElementModQ> lagrange_coefficients = new HashMap<>();

    // Loop through each of the available guardians
    // and calculate a partial for the missing one
    for (Guardian available_guardian : this._available_guardians.values()) {

      // Compute lagrange coefficients for each of the available guardians
      //  *[guardian.sequence_order for guardian in this._available_guardians.values()
      //     if guardian.object_id != available_guardian.object_id]
      List<BigInteger> seq_orders = this._available_guardians.values().stream().filter(g -> !g.object_id.equals(available_guardian.object_id))
              .map(g -> BigInteger.valueOf(g.sequence_order())).collect(Collectors.toList());
      lagrange_coefficients.put(
              available_guardian.object_id,
              ElectionPolynomial.compute_lagrange_coefficient(BigInteger.valueOf(available_guardian.sequence_order()), seq_orders));

      // Compute the decryption shares
      Optional<CompensatedTallyDecryptionShare> share = Decryption.compute_compensated_decryption_share(
              available_guardian,
              missing_guardian_id,
              this._ciphertext_tally,
              this._encryption,
              decryptor);
      if (share.isEmpty()) {
        logger.atInfo().log("compensation failed for missing: %s", missing_guardian_id);
        break;
      } else {
        compensated_decryptions.add(share.get());
      }
    }

    // Verify generated the correct number of partials
    if (compensated_decryptions.size() != this._available_guardians.size()) {
      logger.atInfo().log("compensate mismatch partial decryptions for missing guardian  %s", missing_guardian_id);
      return Optional.empty();
    } else {
      this._lagrange_coefficients.put(missing_guardian_id, lagrange_coefficients);
      this._submit_compensated_decryption_shares(compensated_decryptions);
      return Optional.of(compensated_decryptions);
    }
  }

  /**
   * Get the plaintext tally for the election by composing each Guardian's
   * decrypted representation of each selection into a decrypted representation.
   * <p>
   * @param recompute: Specify if the function should recompute the result, even if one already exists. default false
   * @return a `PlaintextTally` or `None`
   */
  Optional<PlaintextTally> get_plaintext_tally(boolean recompute, @Nullable Auxiliary.Decryptor decryptor) {
    if (decryptor == null) {
      decryptor = Rsa::decrypt;
    }

    if (this._plaintext_tally.isPresent() && !recompute) {
      return this._plaintext_tally;
    }

    // Make sure a Quorum of Guardians have announced
    if (this._available_guardians.size() < this._encryption.quorum) {
      logger.atInfo().log("cannot get plaintext tally with less than quorum available guardians");
      return Optional.empty();
    }

    // If all Guardians are present decrypt the tally
    if (this._available_guardians.size() == this._encryption.number_of_guardians) {
      return DecryptWithShares.decrypt_tally(this._ciphertext_tally, this._decryption_shares, this._encryption);
    }

    // If missing guardians compensate for the missing guardians
    for (String missing : this._missing_guardians.keySet()) {
      Optional<List<CompensatedTallyDecryptionShare>> compensated_decryptions = this.compensate(missing, decryptor);
      if (compensated_decryptions.isEmpty()) {
        logger.atInfo().log("get plaintext tally failed compensating for %s", missing);
        return Optional.empty();
      }
    }

    // Reconstruct the missing partial decryptions from the compensation shares
    Optional<Map<String, TallyDecryptionShare>> missing_decryption_shares =
            Decryption.reconstruct_missing_tally_decryption_shares(
                    this._ciphertext_tally,
                    this._missing_guardians,
                    this._compensated_decryption_shares,
                    this._lagrange_coefficients);
    if (missing_decryption_shares.isEmpty() ||
            missing_decryption_shares.get().size() != this._missing_guardians.size()) {
      logger.atInfo().log("get plaintext tally failed with missing decryption shares");
      return Optional.empty();
    }

    Map<String, TallyDecryptionShare> merged_decryption_shares = new HashMap<>();

    for (Map.Entry<String, TallyDecryptionShare> entry : this._decryption_shares.entrySet()) {
      merged_decryption_shares.put(entry.getKey(), entry.getValue());
    }

    for (Map.Entry<String, TallyDecryptionShare> entry : missing_decryption_shares.get().entrySet()) {
      merged_decryption_shares.put(entry.getKey(), entry.getValue());
    }

    if (merged_decryption_shares.size() != this._encryption.number_of_guardians) {
      logger.atInfo().log("get plaintext tally failed with share length mismatch");
      return Optional.empty();
    }

    return DecryptWithShares.decrypt_tally(this._ciphertext_tally, merged_decryption_shares, this._encryption);
  }

  /** Submit the decryption share to be used in the decryption. */
  boolean _submit_decryption_share(TallyDecryptionShare share) {
    if (this._decryption_shares.containsKey(share.guardian_id())) {
      logger.atInfo().log("cannot submit for guardian %s that already decrypted", share.guardian_id());
      return false;
    }
    this._decryption_shares.put(share.guardian_id(), share);
    return true;
  }

  /** Submit compensated decryption shares to be used in the decryption. */
  boolean _submit_compensated_decryption_shares(List<CompensatedTallyDecryptionShare> shares) {
    List<Boolean> ok = shares.stream().map(this::_submit_compensated_decryption_share).
            collect(Collectors.toList());
    return ok.stream().allMatch(b -> b);
  }

  /** Submit compensated decryption share to be used in the decryption. */
  boolean _submit_compensated_decryption_share(CompensatedTallyDecryptionShare share) {
    Map<String, CompensatedTallyDecryptionShare> shareMap =
            this._compensated_decryption_shares.get(share.missing_guardian_id());
    if (this._compensated_decryption_shares.containsKey(share.missing_guardian_id()) &&
            (shareMap != null) && shareMap.containsKey(share.guardian_id())) {
      logger.atInfo().log("cannot submit compensated share for guardian %s on behalf of %s that already compensated",
        share.guardian_id(), share.missing_guardian_id());
      return false;
    }

    if (!this._compensated_decryption_shares.containsKey(share.missing_guardian_id())) {
      this._compensated_decryption_shares.put(share.missing_guardian_id(), new HashMap<>());
    }
    Map<String, CompensatedTallyDecryptionShare> y = this._compensated_decryption_shares.get(share.missing_guardian_id());
    y.put(share.guardian_id(), share);

    return true;
  }
}
