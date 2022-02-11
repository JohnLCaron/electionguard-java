package com.sunya.electionguard.standard;

import com.google.common.flogger.FluentLogger;
import com.sunya.electionguard.CiphertextBallot;
import com.sunya.electionguard.InternalManifest;
import com.sunya.electionguard.Manifest;
import com.sunya.electionguard.Nonces;
import com.sunya.electionguard.PlaintextBallot;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static com.sunya.electionguard.Group.*;
import static com.sunya.electionguard.InternalManifest.ContestWithPlaceholders;


/** Static methods for decryption when you know the secret keys. LOOK Unused. */
class DecryptWithSecrets {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  /**
   * Decrypt the specified `CiphertextBallotSelection` within the context of the specified selection.
   * <p>
   * @param selection: the selection to decrypt
   * @param description: the qualified selection description
   * @param public_key: the public key for the election (K)
   * @param secret_key: the known secret key used to generate the public key for this election
   * @param crypto_extended_base_hash: the extended base hash code (𝑄') for the election
   * @param suppress_validity_check: do not validate the encryption prior to decrypting (useful for tests)
   */
  static Optional<PlaintextBallot.Selection> decrypt_selection_with_secret(
          CiphertextBallot.Selection selection,
          Manifest.SelectionDescription description,
          ElementModP public_key,
          ElementModQ secret_key,
          ElementModQ crypto_extended_base_hash,
          boolean suppress_validity_check // default false
    ) {

    if (!suppress_validity_check &&
            !selection.is_valid_encryption(description.crypto_hash(), public_key, crypto_extended_base_hash)) {
      logger.atWarning().log("selection: %s failed secret validity check", selection.object_id());
      return Optional.empty();
    }

    Integer plaintext_vote = selection.ciphertext().decrypt(secret_key);

    // TODO: ISSUE #47: handle decryption of the extra data field if needed

    return Optional.of(new PlaintextBallot.Selection(
            selection.object_id(),
            selection.sequence_order(),
            plaintext_vote,
            selection.is_placeholder_selection,
            null));
  }

  /**
   * Decrypt the specified `CiphertextBallotSelection` within the context of the specified selection.
   * <p>
   * @param selection: the contest selection to decrypt
   * @param description: the qualified selection description that may be a placeholder selection
   * @param public_key: the public key for the election (K)
   * @param crypto_extended_base_hash: the extended base hash code (𝑄') for the election
   * @param nonce_seed: the optional nonce that was seeded to the encryption function.
   *                    if no value is provided, the nonce field from the selection is used
   * @param suppress_validity_check: do not validate the encryption prior to decrypting (useful for tests)
   */
  static Optional<PlaintextBallot.Selection> decrypt_selection_with_nonce(
          CiphertextBallot.Selection selection,
          Manifest.SelectionDescription description,
          ElementModP public_key,
          ElementModQ crypto_extended_base_hash,
          Optional<ElementModQ> nonce_seed,
          boolean suppress_validity_check // default false
    ) {

    if (!suppress_validity_check &&
            !selection.is_valid_encryption(description.crypto_hash(), public_key, crypto_extended_base_hash)) {
      logger.atWarning().log("selection: %s failed nonce validity check", selection.object_id());
      return Optional.empty();
    }

    Optional<ElementModQ> nonce;
    if (nonce_seed.isEmpty()) {
      nonce = selection.nonce;
    } else {
      Nonces nonce_sequence = new Nonces(description.crypto_hash(), nonce_seed.get());
      nonce = Optional.of(nonce_sequence.get(description.sequence_order));
      logger.atFine().log("decrypt_selection_with_nonce %n  %s%n  %s%n  %d%n%s%n",
              description.crypto_hash(), nonce_seed, description.sequence_order, nonce);
    }

    if (nonce.isEmpty()) {
      logger.atWarning().log("missing nonce value.  decrypt could not derive a nonce value for selection %s}",
              selection.object_id());
      return Optional.empty();
    }

    if (selection.nonce.isPresent() && !nonce.get().equals(selection.nonce.get())) {
      logger.atWarning().log("decrypt could not verify a nonce value for selection %s}",
              selection.object_id());
      return Optional.empty();
    }

    Integer plaintext_vote = selection.ciphertext().decrypt_known_nonce(public_key, nonce.get());

    // TODO: ISSUE #35: encrypt/decrypt: handle decryption of the extradata field if needed

    return Optional.of(new PlaintextBallot.Selection(
            selection.object_id(),
            selection.sequence_order(),
            plaintext_vote,
            selection.is_placeholder_selection,
            null));
  }

  /**
   * Decrypt the specified `CiphertextBallotContest` within the context of the specified contest.
   * <p>
   * @param contest: the contest to decrypt
   * @param description: the qualified contest description that includes placeholder selections
   * @param public_key: the public key for the election (K)
   * @param secret_key: the known secret key used to generate the public key for this election
   * @param crypto_extended_base_hash: the extended base hash code (𝑄') for the election
   * @param suppress_validity_check: do not validate the encryption prior to decrypting (useful for tests)
   * @param remove_placeholders: filter out placeholder ciphertext selections after decryption
   */
  static Optional<PlaintextBallot.Contest> decrypt_contest_with_secret(
          CiphertextBallot.Contest contest,
          ContestWithPlaceholders description,
          ElementModP public_key,
          ElementModQ secret_key,
          ElementModQ crypto_extended_base_hash,
          boolean suppress_validity_check, // default false
          boolean remove_placeholders // default true
    ) {

    if (!suppress_validity_check &&
            !contest.is_valid_encryption(description.crypto_hash(), public_key, crypto_extended_base_hash)) {
      logger.atWarning().log("contest: %s failed secret validity check", contest.object_id);
      return Optional.empty();
    }
    List<PlaintextBallot.Selection> plaintext_selections = new ArrayList<>();
    for (CiphertextBallot.Selection selection : contest.ballot_selections) {
      Manifest.SelectionDescription selection_description =
              description.getSelectionById(selection.object_id()).orElseThrow(IllegalStateException::new);
      Optional<PlaintextBallot.Selection> plaintext_selection = decrypt_selection_with_secret(
              selection,
              selection_description,
              public_key,
              secret_key,
              crypto_extended_base_hash,
              suppress_validity_check);
      if (plaintext_selection.isPresent()) {
        if (!remove_placeholders || !plaintext_selection.get().is_placeholder_selection) {
          plaintext_selections.add(plaintext_selection.get());
        }
      } else {
        logger.atWarning().log(
                "decryption with secret failed for contest: %s selection: %s",
                contest.object_id, selection.object_id());
        return Optional.empty();
      }
    }

    return Optional.of(new PlaintextBallot.Contest(
            contest.object_id(),
            contest.sequence_order(),
            plaintext_selections));
  }

  /**
   * Decrypt the specified `CiphertextBallotContest` within the context of the specified contest.
   * <p>
   * @param contest: the contest to decrypt
   * @param description: the qualified contest description that includes placeholder selections
   * @param public_key: the public key for the election (K)
   * @param crypto_extended_base_hash: the extended base hash code (𝑄') for the election
   * @param nonce_seed: the optional nonce that was seeded to the encryption function
   * if no value is provided, the nonce field from the contest is used
   * @param suppress_validity_check: do not validate the encryption prior to decrypting (useful for tests)
   * @param remove_placeholders: filter out placeholder ciphertext selections after decryption
   */
  static Optional<PlaintextBallot.Contest> decrypt_contest_with_nonce(
          CiphertextBallot.Contest contest,
          ContestWithPlaceholders description,
          ElementModP public_key,
          ElementModQ crypto_extended_base_hash,
          Optional<ElementModQ> nonce_seed,
          boolean suppress_validity_check, // default false,
          boolean remove_placeholders // default True,
    ) {

    if (!suppress_validity_check && !contest.is_valid_encryption(
            description.crypto_hash(), public_key, crypto_extended_base_hash)) {
      logger.atWarning().log("contest: %s failed nonce validity check", contest.object_id);
      return Optional.empty();
    }

    if (nonce_seed.isEmpty()) {
      nonce_seed = contest.nonce;
      logger.atFine().log("decrypt_contest_with_nonce empty %n  %s%n", nonce_seed);

    } else {
      Nonces nonce_sequence = new Nonces(description.crypto_hash(), nonce_seed.get());
      ElementModQ result = nonce_sequence.get(description.sequence_order);
      logger.atFine().log("decrypt_contest_with_nonce %n  %s%n  %s%n  %d%n%s%n",
              description.crypto_hash(), nonce_seed, description.sequence_order, result);
      nonce_seed = Optional.of(result);
    }

    if (nonce_seed.isEmpty()) {
      logger.atWarning().log("missing nonce_seed value.  decrypt could not derive a nonce value for contest %s}",
              contest.object_id);
      return Optional.empty();
    }

    if (contest.nonce.isPresent() && !nonce_seed.get().equals(contest.nonce.get())) {
      logger.atWarning().log("decrypt could not verify a nonce value for contest %s}",
              contest.object_id);
      return Optional.empty();
    }

    List<PlaintextBallot.Selection> plaintext_selections = new ArrayList<>();
    for (CiphertextBallot.Selection selection : contest.ballot_selections) {
      Manifest.SelectionDescription selection_description =
              description.getSelectionById(selection.object_id()).orElseThrow(IllegalStateException::new);
      Optional<PlaintextBallot.Selection> plaintext_selection = decrypt_selection_with_nonce(
              selection,
              selection_description,
              public_key,
              crypto_extended_base_hash,
              nonce_seed,
              suppress_validity_check);

      if (plaintext_selection.isPresent()) {
        if (!remove_placeholders || !plaintext_selection.get().is_placeholder_selection) {
          plaintext_selections.add(plaintext_selection.get());
        }
      } else {
        logger.atWarning().log("decryption with nonce failed for contest: %s selection: %s",
                contest.object_id, selection.object_id());
        return Optional.empty();
      }
    }

    return Optional.of(new PlaintextBallot.Contest(
            contest.object_id(),
            contest.sequence_order(),
            plaintext_selections));
  }

  /**
   * Decrypt the specified `CiphertextBallot` within the context of the specified election.
   * <p>
   * @param ballot: the ballot to decrypt
   * @param metadata: the qualified election description that includes placeholder selections
   * @param crypto_extended_base_hash: the extended base hash code (𝑄') for the election
   * @param public_key: the public key for the election (K)
   * @param secret_key: the known secret key used to generate the public key for this election
   * @param suppress_validity_check: do not validate the encryption prior to decrypting (useful for tests)
   * @param remove_placeholders: filter out placeholder ciphertext selections after decryption
   */
  static Optional<PlaintextBallot> decrypt_ballot_with_secret(
          CiphertextBallot ballot,
          InternalManifest metadata,
          ElementModQ crypto_extended_base_hash,
          ElementModP public_key,
          ElementModQ secret_key,
          boolean suppress_validity_check, // default False,
          boolean remove_placeholders // default  True,
    ) {

    if (!suppress_validity_check && !ballot.is_valid_encryption(
            metadata.manifest.crypto_hash, public_key, crypto_extended_base_hash)) {
      logger.atWarning().log("ballot: %s failed secret validity check", ballot.object_id());
      return Optional.empty();
    }

    List<PlaintextBallot.Contest> plaintext_contests = new ArrayList<>();

    for (CiphertextBallot.Contest contest : ballot.contests) {
      ContestWithPlaceholders description =
              metadata.getContestById(contest.object_id).orElseThrow(IllegalStateException::new);
      Optional<PlaintextBallot.Contest> plaintext_contest = decrypt_contest_with_secret(
              contest,
              description,
              public_key,
              secret_key,
              crypto_extended_base_hash,
              suppress_validity_check,
              remove_placeholders);

      if (plaintext_contest.isPresent()) {
        plaintext_contests.add(plaintext_contest.get());
      } else {
        logger.atWarning().log("decryption with nonce failed for ballot: %s selection: %s",
                ballot.object_id(), contest.object_id);
        return Optional.empty();
      }
    }

    return Optional.of(new PlaintextBallot(ballot.object_id(), ballot.style_id, plaintext_contests));
  }

  /**
   * Decrypt the specified `CiphertextBallot` within the context of the specified election.
   * <p>
   * @param ballot: the ballot to decrypt
   * @param metadata: the qualified election metadata that includes placeholder selections
   * @param crypto_extended_base_hash: the extended base hash code (𝑄') for the election
   * @param public_key: the public key for the election (K)
   * @param nonce: the optional master ballot nonce that was either seeded to, or generated by the encryption function
   * @param suppress_validity_check: do not validate the encryption prior to decrypting (useful for tests)
   * @param remove_placeholders: filter out placeholder ciphertext selections after decryption
   */
  static Optional<PlaintextBallot> decrypt_ballot_with_nonce(
          CiphertextBallot ballot,
          InternalManifest metadata,
          ElementModQ crypto_extended_base_hash,
          ElementModP public_key,
          Optional<ElementModQ> nonce,
          boolean suppress_validity_check, // default False,
          boolean remove_placeholders // default True,
    ) {

    if (!suppress_validity_check && !ballot.is_valid_encryption(
            metadata.manifest.crypto_hash, public_key, crypto_extended_base_hash)) {
      logger.atWarning().log("ballot: %s failed nonce validity check", ballot.object_id());
      return Optional.empty();
    }

    // Use the hashed representation included in the ballot or override with the provided values
    Optional<ElementModQ> nonce_seed = nonce.isEmpty() ? ballot.hashed_ballot_nonce() :
            Optional.of(CiphertextBallot.nonce_seed(metadata.manifest.crypto_hash, ballot.object_id(), nonce.get()));

    if (nonce_seed.isEmpty()) {
      logger.atWarning().log(
              "missing nonce_seed value. decrypt could not derive a nonce value for ballot %s", ballot.object_id());
      return Optional.empty();
    }

    List<PlaintextBallot.Contest> plaintext_contests = new ArrayList<>();

    for (CiphertextBallot.Contest contest : ballot.contests) {
      ContestWithPlaceholders description =
              metadata.getContestById(contest.object_id).orElseThrow(IllegalStateException::new);
      Optional<PlaintextBallot.Contest> plaintext_contest = decrypt_contest_with_nonce(
              contest,
              description,
              public_key,
              crypto_extended_base_hash,
              nonce_seed,
              suppress_validity_check,
              remove_placeholders);

      if (plaintext_contest.isPresent()) {
        plaintext_contests.add(plaintext_contest.get());
      } else {
        logger.atWarning().log(
                "decryption with nonce failed for ballot: %s selection: %s", ballot.object_id(), contest.object_id);
        return Optional.empty();
      }
    }
    return Optional.of(new PlaintextBallot(ballot.object_id(), ballot.style_id, plaintext_contests));
  }

}
