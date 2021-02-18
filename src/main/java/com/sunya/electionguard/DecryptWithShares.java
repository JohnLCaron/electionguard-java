package com.sunya.electionguard;

import com.google.common.flogger.FluentLogger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static com.sunya.electionguard.DecryptionShare.TallyDecryptionShare;
import static com.sunya.electionguard.DecryptionShare.KeyAndSelection;
import static com.sunya.electionguard.Group.*;

/** Static methods for decryption with shares. */
class DecryptWithShares {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  /**
   * Decrypt the CiphertextTally into a PlaintextTally.
   *
   * @param tally:   The CiphertextTally to decrypt
   * @param shares:  The guardian Decryption Shares for all guardians
   * @param context: the CiphertextElectionContext
   * @param lagrange_coefficients: pass to PlaintextTally
   * @param guardianStates: pass to PlaintextTally
   * @return A PlaintextTally or None if there is an error
   */
  static Optional<PlaintextTally> decrypt_tally(
          CiphertextTally tally,
          Map<String, TallyDecryptionShare> shares, // Map(AVAILABLE_GUARDIAN_ID, TallyDecryptionShare)
          CiphertextElectionContext context,
          Map<String, Group.ElementModQ> lagrange_coefficients,
          List<GuardianState> guardianStates) {

    Optional<Map<String, PlaintextTally.Contest>> contests = decrypt_tally_contests_with_decryption_shares(
            tally.contests, shares, context.crypto_extended_base_hash);

    if (contests.isEmpty()) {
      return Optional.empty();
    }

    return Optional.of(
            new PlaintextTally(tally.object_id, contests.get(), lagrange_coefficients, guardianStates));
  }

  /**
   * Decrypt the CiphertextTally.Contest into a PlaintextTally.Contest.
   *
   * @param tally:              the encrypted tally of contests
   * @param shares:             a collection of `TallyDecryptionShare` used to decrypt
   * @param extended_base_hash: the extended base hash code (𝑄') for the election
   * @return Map(CONTEST_ID, PlaintextTally.Contest)
   */
  static Optional<Map<String, PlaintextTally.Contest>> decrypt_tally_contests_with_decryption_shares(
          Map<String, CiphertextTally.Contest> tally, // Map(CONTEST_ID, CiphertextTallyContest)
          Map<String, DecryptionShare.TallyDecryptionShare> shares, // Map(AVAILABLE_GUARDIAN_ID, TallyDecryptionShare)
          ElementModQ extended_base_hash) {

    HashMap<String, PlaintextTally.Contest> contests = new HashMap<>();

    // iterate through the tally contests
    for (CiphertextTally.Contest contest : tally.values()) {
      HashMap<String, PlaintextTally.Selection> selections = new HashMap<>();

      for (CiphertextTally.Selection selection : contest.tally_selections.values()) {
        // Map(AVAILABLE_GUARDIAN_ID, KeyAndSelection)
        Map<String, KeyAndSelection> tally_shares = DecryptionShare.get_tally_shares_for_selection(selection.object_id, shares);
        Optional<PlaintextTally.Selection> plaintext_selectionO = decrypt_selection_with_decryption_shares(
                selection, tally_shares, extended_base_hash, false);
        if (plaintext_selectionO.isEmpty()) {
          logger.atWarning().log("could not decrypt tally for contest %s", contest.object_id);
          return Optional.empty();
        }
        PlaintextTally.Selection plaintext_selection = plaintext_selectionO.get();
        selections.put(plaintext_selection.object_id(), plaintext_selection);
      }

      contests.put(contest.object_id, PlaintextTally.Contest.create(contest.object_id, selections));
    }

    return Optional.of(contests);
  }

  /**
   * Decrypt the CiphertextSelection into a PlaintextTallySelection.
   *
   * @param selection:               a CiphertextSelection to decrypt
   * @param shares:                  the collection of shares used to decrypt the selection
   * @param extended_base_hash:      the extended base hash code (𝑄') for the election
   * @param suppress_validity_check: do not validate the encryption prior to decrypting (default false)
   * @return the decrypted selection
   */
  static Optional<PlaintextTally.Selection> decrypt_selection_with_decryption_shares(
          CiphertextSelection selection,
          Map<String, KeyAndSelection> shares, // Map(AVAILABLE_GUARDIAN_ID, KeyAndSelection)
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

    // LOOK can this be moved up?
    // accumulate all of the shares calculated for the selection
    // all_shares_product_M = mult_p( *[decryption.share for (_, decryption) in shares.values()]);
    List<ElementModP> decryption_shares = shares.values().stream().map(t -> t.decryption.share()).collect(Collectors.toList());
    ElementModP all_shares_product_M = Group.mult_p(decryption_shares);

    // Calculate 𝑀 = 𝐵⁄(∏𝑀𝑖) mod 𝑝.
    Group.ElementModP decrypted_value = div_p(selection.ciphertext().data, all_shares_product_M);
    Integer dlogM = Dlog.discrete_log(decrypted_value);

    // [share for (guardian_id, (public_key, share))in shares.items()],
    List<DecryptionShare.CiphertextDecryptionSelection> selections = shares.values().stream().map(t -> t.decryption).collect(Collectors.toList());
    return Optional.of( PlaintextTally.Selection.create(
            selection.object_id,
            dlogM,
            decrypted_value,
            selection.ciphertext(),
            selections
    ));
  }


  ///////////////////////////////////////////////////////////////////////////////////////////////////////////
  // spoiled ballots -> plaintext ballot and tally

  /** Decrypt a collection of ciphertext spoiled ballots into decrypted plaintext tallies and ballots. */
  static Optional<List<DecryptionMediator.SpoiledBallotAndTally>> decrypt_spoiled_ballots(
          Iterable<CiphertextAcceptedBallot> spoiled_ballots,
          Map<String, TallyDecryptionShare> shares, // Map(AVAILABLE_GUARDIAN_ID, TallyDecryptionShare)
          CiphertextElectionContext context) {

    List<DecryptionMediator.SpoiledBallotAndTally> result = new ArrayList<>();
    for (CiphertextAcceptedBallot spoiled_ballot : spoiled_ballots) {
      HashMap<String, TallyDecryptionShare> ballot_shares = new HashMap<>();
      for (Map.Entry<String, TallyDecryptionShare> entry : shares.entrySet()) {
        TallyDecryptionShare share = entry.getValue();
        ballot_shares.put(entry.getKey(), share.spoiled_ballots().get(spoiled_ballot.object_id));
      }

      // tally decryptor
      Optional<Map<String, PlaintextTally.Contest>> decrypted_contests =
              decrypt_tally(spoiled_ballot, ballot_shares, context.crypto_extended_base_hash);
      if (decrypted_contests.isEmpty()) {
        return Optional.empty();
      }
      PlaintextTally tally = new PlaintextTally(spoiled_ballot.object_id, decrypted_contests.get(), null, null);

      // ballot decryptor
      Optional<PlaintextBallot> decrypted_ballot = decrypt_ballot(spoiled_ballot, ballot_shares, context.crypto_extended_base_hash);
      if (decrypted_ballot.isEmpty()) {
         return Optional.empty();
      }
      result.add(new DecryptionMediator.SpoiledBallotAndTally(tally, decrypted_ballot.get()));
    }

    return Optional.of(result);
  }

  /** Decrypt one ciphertext spoiled ballot into a decrypted tally. */
  static Optional<Map<String, PlaintextTally.Contest>> decrypt_tally(
          CiphertextAcceptedBallot ballot,
          Map<String, TallyDecryptionShare> shares,
          ElementModQ extended_base_hash) {

    HashMap<String, PlaintextTally.Contest> contests = new HashMap<>();

    for (CiphertextBallot.Contest contest : ballot.contests) {
      HashMap<String, PlaintextTally.Selection> selections = new HashMap<>();

      for (CiphertextBallot.Selection selection : contest.ballot_selections) {
        if (selection.is_placeholder_selection) {
          continue;
        }
        Map<String, KeyAndSelection> selection_shares = DecryptionShare.get_ballot_shares_for_selection(selection.object_id, shares);
        Optional<PlaintextTally.Selection> plaintext_selectionO = decrypt_selection_with_decryption_shares(
                selection, selection_shares, extended_base_hash, false);

        // verify the plaintext values are received and add them to the collection
        if (plaintext_selectionO.isEmpty()) {
          logger.atWarning().log("could not decrypt ballot %s for contest %s selection %s",
                  ballot.object_id, contest.object_id, selection.object_id);
          return Optional.empty();
        } else {
          PlaintextTally.Selection plaintext_selection = plaintext_selectionO.get();
          selections.put(plaintext_selection.object_id(), plaintext_selection);
        }
      }
      contests.put(contest.object_id, PlaintextTally.Contest.create(contest.object_id, selections));
    }
    return Optional.of(contests);
  }

  /** Decrypt a ciphertext ballot into a decrypted plaintext ballot. */
  private static Optional<PlaintextBallot> decrypt_ballot(
          CiphertextAcceptedBallot ballot,
          Map<String, TallyDecryptionShare> shares,
          ElementModQ extended_base_hash) {

    List<PlaintextBallot.Contest> contests = new ArrayList<>();
    for (CiphertextBallot.Contest contest : ballot.contests) {
      List<PlaintextBallot.Selection> ballot_selections = new ArrayList<>();

      for (CiphertextBallot.Selection selection : contest.ballot_selections) {
        if (selection.is_placeholder_selection) {
          continue;
        }
        // LOOK is this duplicated in decrypt_spoiled_tallies?
        Map<String, KeyAndSelection> selection_shares = DecryptionShare.get_ballot_shares_for_selection(selection.object_id, shares);
        Optional<PlaintextTally.Selection> plaintext_selectionO = decrypt_selection_with_decryption_shares(
                selection, selection_shares, extended_base_hash, false);

        // verify the plaintext values are received and add them to the collection
        if (plaintext_selectionO.isEmpty()) {
          logger.atWarning().log("could not decrypt ballot %s for contest %s selection %s",
                  ballot.object_id, contest.object_id, selection.object_id);
          return Optional.empty();
        } else {
          PlaintextTally.Selection plaintext_selection = plaintext_selectionO.get();
          PlaintextBallot.Selection decryptedBallot = new PlaintextBallot.Selection(
                  plaintext_selection.object_id(),
                  plaintext_selection.tally(),
                  false, null);
          ballot_selections.add(decryptedBallot);
        }
      }
      contests.add(new PlaintextBallot.Contest(contest.object_id, ballot_selections));
    }

    // String object_id, String ballot_style, List<PlaintextBallotContest> contests
    return Optional.of(new PlaintextBallot(ballot.object_id, ballot.ballot_style, contests));
  }
}
