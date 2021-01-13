package com.sunya.electionguard;

import com.google.common.flogger.FluentLogger;

import java.math.BigInteger;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static com.sunya.electionguard.Group.*;
import static com.sunya.electionguard.DecryptionShare.*;
import static com.sunya.electionguard.Tally.*;

/** Static methods for decryption. */
class DecryptWithShares {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  /**
   * Decrypt the specified `CiphertextTallySelection` with the collection of `ElementModP` decryption shares.
   * Each share is expected to be passed with the corresponding public key so that the encryption can be validated
   *
   * @param selection:               a `CiphertextTallySelection`
   * @param shares:                  the collection of shares to decrypt the selection
   * @param extended_base_hash:      the extended base hash code (𝑄') for the election
   * @param suppress_validity_check: do not validate the encryption prior to decrypting (default false)
   * @return a `PlaintextTallySelection` or `None` if there is an error
   */
  static Optional<Tally.PlaintextTallySelection> decrypt_selection_with_decryption_shares(
          Ballot.CiphertextSelection selection,
          // shares: Dict[ GUARDIAN_ID, Tuple[ELECTION_PUBLIC_KEY, CiphertextDecryptionSelection] ],
          Map<String, KeyAndSelection> shares,
          Group.ElementModQ extended_base_hash,
          boolean suppress_validity_check) {

    if (!suppress_validity_check) {
      // Verify that all of the shares are computed correctly
      for (KeyAndSelection tuple : shares.values()) {
        // verify we have a proof or recovered parts
        if (!tuple.decryption.is_valid(selection.ciphertext(), tuple.public_key, extended_base_hash)) {
          return Optional.empty();
        }
      }
    }

    // accumulate all of the shares calculated for the selection
    // all_shares_product_M = mult_p( *[decryption.share for (_, decryption) in shares.values()]);
    List<ElementModP> decryption_shares = shares.values().stream().map(t -> t.decryption.share()).collect(Collectors.toList());
    ElementModP all_shares_product_M = Group.mult_p(decryption_shares);

    // Calculate 𝑀 = 𝐵⁄(∏𝑀𝑖)mod 𝑝.
    Group.ElementModP decrypted_value = div_p(selection.ciphertext().data, all_shares_product_M);
    Integer dlogM = Dlog.discrete_log(decrypted_value);

    // [share for (guardian_id, (public_key, share))in shares.items()],
    List<CiphertextDecryptionSelection> selections = shares.values().stream().map(t -> t.decryption).collect(Collectors.toList());
    return Optional.of( Tally.PlaintextTallySelection.create(
            selection.object_id,
            dlogM,
            decrypted_value,
            selection.ciphertext(),
            selections
          ));
  }

  /**
   * Decrypt the specified tally within the context of the specified Decryption Shares
   *
   * @param tally:              the encrypted tally of contests
   * @param shares:             a collection of `TallyDecryptionShare` used to decrypt
   * @param extended_base_hash: the extended base hash code (𝑄') for the election
   * @return a collection of `PlaintextTallyContest` or `None` if there is an error
   */
  static Optional<Map<String, Tally.PlaintextTallyContest>> decrypt_tally_contests_with_decryption_shares(
          Map<String, CiphertextTallyContest> tally,
          Map<String, TallyDecryptionShare> shares,
          ElementModQ extended_base_hash) {

    HashMap<String, PlaintextTallyContest> contests = new HashMap<>();

    // iterate through the tally contests
    for (CiphertextTallyContest contest : tally.values()) {
      HashMap<String, PlaintextTallySelection> selections = new HashMap<>();

      for (CiphertextTallySelection selection : contest.tally_selections().values()) {
        Map<String, KeyAndSelection> tally_shares = get_tally_shares_for_selection(selection.object_id, shares);
        Optional<Tally.PlaintextTallySelection> plaintext_selectionO = decrypt_selection_with_decryption_shares(
                selection, tally_shares, extended_base_hash, false);
        if (plaintext_selectionO.isEmpty()) {
          logger.atWarning().log("could not decrypt tally for contest %s", contest.object_id);
          return Optional.empty();
        }
        Tally.PlaintextTallySelection plaintext_selection = plaintext_selectionO.get();
        selections.put(plaintext_selection.object_id(), plaintext_selection);
      }

      contests.put(contest.object_id, PlaintextTallyContest.create(contest.object_id, selections));
    }

    return Optional.of(contests);
  }

  /**
   * Try to decrypt the tally and the spoiled ballots using the provided decryption shares
   *
   * @param tally:   The CiphertextTally to decrypt
   * @param shares:  The guardian Decryption Shares for all guardians
   * @param context: the CiphertextElectionContext
   * @return A PlaintextTally or None if there is an error
   */
  static Optional<PlaintextTally> decrypt_tally(
          CiphertextTally tally,
          Map<String, TallyDecryptionShare> shares,
          Election.CiphertextElectionContext context
          ) {
    Optional<Map<String, Tally.PlaintextTallyContest>> contests = decrypt_tally_contests_with_decryption_shares(
            tally.cast(), shares, context.crypto_extended_base_hash
    );

    if (contests.isEmpty()) {
      return Optional.empty();
    }

    Optional<Map<String, Map<String, Tally.PlaintextTallyContest>>> spoiled_ballots = decrypt_spoiled_ballots(
            tally.spoiled_ballots(), shares, context.crypto_extended_base_hash
    );

    return spoiled_ballots.map(stringMapMap -> PlaintextTally.create(tally.object_id, contests.get(), stringMapMap));
  }

  /** Try to decrypt each of the spoiled ballots using the provided decryption shares */
  static Optional<Map<String, Map<String, Tally.PlaintextTallyContest>>> decrypt_spoiled_ballots(
          Map<String, Ballot.CiphertextAcceptedBallot> spoiled_ballots,
          Map<String, TallyDecryptionShare> shares,
          ElementModQ extended_base_hash) {

    HashMap<String, Map<String, Tally.PlaintextTallyContest>> plaintext_spoiled_ballots = new HashMap<>();
    for (Ballot.CiphertextAcceptedBallot spoiled_ballot : spoiled_ballots.values()) {
      HashMap<String, BallotDecryptionShare> ballot_shares = new HashMap<>();
      for (Map.Entry<String, TallyDecryptionShare> entry : shares.entrySet()) {
        TallyDecryptionShare share = entry.getValue();
        ballot_shares.put(entry.getKey(), share.spoiled_ballots().get(spoiled_ballot.object_id));
      }

      Optional<Map<String, Tally.PlaintextTallyContest>> decrypted_ballot = decrypt_ballot(spoiled_ballot, ballot_shares, extended_base_hash);
      if (decrypted_ballot.isPresent()) {
        plaintext_spoiled_ballots.put(spoiled_ballot.object_id, decrypted_ballot.get());
      } else {
        return Optional.empty();
      }
    }
    return Optional.of(plaintext_spoiled_ballots);
  }

  /** Try to decrypt a single ballot using the provided decryption shares. */
  static Optional<Map<String, PlaintextTallyContest>> decrypt_ballot(
          Ballot.CiphertextAcceptedBallot ballot,
          Map<String, BallotDecryptionShare> shares,
          ElementModQ extended_base_hash) {

    HashMap<String, PlaintextTallyContest> contests = new HashMap<>();

    for (Ballot.CiphertextBallotContest contest : ballot.contests) {
      HashMap<String, PlaintextTallySelection> selections = new HashMap<>();

      for (Ballot.CiphertextBallotSelection selection : contest.ballot_selections) {
        Map<String, KeyAndSelection> selection_shares = get_ballot_shares_for_selection(selection.object_id, shares);
        Optional<Tally.PlaintextTallySelection> plaintext_selectionO = decrypt_selection_with_decryption_shares(
                selection, selection_shares, extended_base_hash, false);

        // verify the plaintext values are received and add them to the collection
        if (plaintext_selectionO.isEmpty()) {
          logger.atWarning().log("could not decrypt ballot %s for contest %s selection %s",
              ballot.object_id, contest.object_id, selection.object_id);
          return Optional.empty();
        } else {
          Tally.PlaintextTallySelection plaintext_selection = plaintext_selectionO.get();
          selections.put(plaintext_selection.object_id(), plaintext_selection);
        }
      }
      contests.put(contest.object_id, PlaintextTallyContest.create(contest.object_id, selections));
    }
    return Optional.of(contests);
  }
}
