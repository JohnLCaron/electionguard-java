package com.sunya.electionguard;

import com.google.common.flogger.FluentLogger;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static com.sunya.electionguard.Group.*;

public class DecryptWithSecrets {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  /**
   * Decrypt the specified `CiphertextBallotSelection` within the context of the specified selection.
   * <p>
   * :param selection: the selection to decrypt
   * :param description: the qualified selection metadata
   * :param public_key: the public key for the election (K)
   * :param secret_key: the known secret key used to generate the public key for this election
   * :param crypto_extended_base_hash: the extended base hash code (𝑄') for the election
   * :param suppress_validity_check: do not validate the encryption prior to decrypting (useful for tests)
   */
  static Optional<Ballot.PlaintextBallotSelection> decrypt_selection_with_secret(
          Ballot.CiphertextBallotSelection selection,
          Election.SelectionDescription description,
          ElementModP public_key,
          ElementModQ secret_key,
          ElementModQ crypto_extended_base_hash,
          boolean suppress_validity_check // default false
  ) {

    if (!suppress_validity_check &&
            !selection.is_valid_encryption(description.crypto_hash(), public_key, crypto_extended_base_hash)) {
      return Optional.empty();
    }

    BigInteger plaintext_vote = selection.ciphertext.decrypt(secret_key);

    // TODO: ISSUE #47: handle decryption of the extradata field if needed

    return Optional.of(new Ballot.PlaintextBallotSelection(
            selection.object_id,
            Utils.isTrue(plaintext_vote),
            selection.is_placeholder_selection,
            Optional.empty()));
  }

  /**
   * Decrypt the specified `CiphertextBallotSelection` within the context of the specified selection.
   * <p>
   * :param selection: the contest selection to decrypt
   * :param description: the qualified selection metadata that may be a placeholder selection
   * :param public_key: the public key for the election (K)
   * :param crypto_extended_base_hash: the extended base hash code (𝑄') for the election
   * :param nonce_seed: the optional nonce that was seeded to the encryption function.
   * if no value is provided, the nonce field from the selection is used
   * :param suppress_validity_check: do not validate the encryption prior to decrypting (useful for tests)
   *
   * @return
   */
  static Optional<Ballot.PlaintextBallotSelection> decrypt_selection_with_nonce(
          Ballot.CiphertextBallotSelection selection,
          Election.SelectionDescription description,
          ElementModP public_key,
          ElementModQ crypto_extended_base_hash,
          Optional<ElementModQ> nonce_seed,
          boolean suppress_validity_check // default false
  ) {

    if (!suppress_validity_check &&
            !selection.is_valid_encryption(description.crypto_hash(), public_key, crypto_extended_base_hash)) {
      return Optional.empty();
    }

    Optional<ElementModQ> nonce;
    if (nonce_seed.isEmpty()) {
      nonce = selection.nonce;
    } else {
      Nonces nonce_sequence = new Nonces(description.crypto_hash(), nonce_seed);
      nonce = Optional.of(nonce_sequence.get(description.sequence_order));
    }

    if (nonce.isEmpty()) {
      logger.atWarning().log("missing nonce value.  decrypt could not derive a nonce value for selection %s}",
              selection.object_id);
      return Optional.empty();
    }

    if (selection.nonce.isPresent() && !nonce.equals(selection.nonce)) {
      logger.atWarning().log("decrypt could not verify a nonce value for selection %s}",
              selection.object_id);
      return Optional.empty();
    }

    BigInteger plaintext_vote = selection.ciphertext.decrypt_known_nonce(public_key, nonce.get());

    // TODO: ISSUE #35: encrypt/decrypt: handle decryption of the extradata field if needed

    return Optional.of(new Ballot.PlaintextBallotSelection(
            selection.object_id,
            Utils.isTrue(plaintext_vote),
            selection.is_placeholder_selection,
            Optional.empty()));
  }

  /**
   * Decrypt the specified `CiphertextBallotContest` within the context of the specified contest.
   * <p>
   * :param contest: the contest to decrypt
   * :param description: the qualified contest metadata that includes placeholder selections
   * :param public_key: the public key for the election (K)
   * :param secret_key: the known secret key used to generate the public key for this election
   * :param crypto_extended_base_hash: the extended base hash code (𝑄') for the election
   * :param suppress_validity_check: do not validate the encryption prior to decrypting (useful for tests)
   * :param remove_placeholders: filter out placeholder ciphertext selections after decryption
   */
  static Optional<Ballot.PlaintextBallotContest> decrypt_contest_with_secret(
          Ballot.CiphertextBallotContest contest,
          Election.ContestDescriptionWithPlaceholders description,
          ElementModP public_key,
          ElementModQ secret_key,
          ElementModQ crypto_extended_base_hash,
          boolean suppress_validity_check, // default false
          boolean remove_placeholders // default true
  ) {

    if (!suppress_validity_check &&
            !contest.is_valid_encryption(description.crypto_hash(), public_key, crypto_extended_base_hash)) {
      return Optional.empty();
    }
    List<Ballot.PlaintextBallotSelection> plaintext_selections = new ArrayList<>();
    for (Ballot.CiphertextBallotSelection selection : contest.ballot_selections) {
      Optional<Election.SelectionDescription> selection_description = description.selection_for(selection.object_id);
      Optional<Ballot.PlaintextBallotSelection> plaintext_selection = decrypt_selection_with_secret(
              selection,
              selection_description.get(),
              public_key,
              secret_key,
              crypto_extended_base_hash,
              suppress_validity_check);
      if (plaintext_selection.isPresent()) {
        if (!remove_placeholders || !plaintext_selection.get().is_placeholder_selection) {
          plaintext_selections.add(plaintext_selection.get());
        } else {
          logger.atWarning().log(
                  "decryption with secret failed for contest: %s selection: %s",
                  contest.object_id, selection.object_id);
          return Optional.empty();
        }
      }
    }

    return Optional.of(new Ballot.PlaintextBallotContest(contest.object_id, plaintext_selections));
  }

}
