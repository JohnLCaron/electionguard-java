package com.sunya.electionguard;

import com.google.common.collect.ImmutableMap;
import com.google.common.flogger.FluentLogger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static com.sunya.electionguard.DecryptionShare.BallotDecryptionShare;
import static com.sunya.electionguard.DecryptionShare.KeyAndSelection;
import static com.sunya.electionguard.DecryptionShare.TallyDecryptionShare;
import static com.sunya.electionguard.Group.*;

/** Static methods for decryption. */
class DecryptWithShares {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  /**
   * Decrypt the ciphertextTally and the spoiled ballots, into a PlaintextTally.
   *
   * @param tally:   The CiphertextTally to decrypt
   * @param shares:  The guardian Decryption Shares for all guardians
   * @param context: the CiphertextElectionContext
   * @param lagrange_coefficients: pass to PlaintextTally
   * @param guardianStates: pass to PlaintextTally
   * @return A PlaintextTally or None if there is an error
   */
  static Optional<PlaintextTally> decrypt_tally(
          CiphertextTallyBuilder tally,
          Map<String, TallyDecryptionShare> shares, // Map(AVAILABLE_GUARDIAN_ID, TallyDecryptionShare)
          Election.CiphertextElectionContext context,
          Map<String, Group.ElementModQ> lagrange_coefficients,
          List<PlaintextTally.GuardianState> guardianStates) {

    Optional<Map<String, PlaintextTally.PlaintextTallyContest>> contests = decrypt_tally_contests_with_decryption_shares(
            tally.cast, shares, context.crypto_extended_base_hash);

    if (contests.isEmpty()) {
      return Optional.empty();
    }

    // Map(BALLOT_ID, Map(AVAILABLE_GUARDIAN_ID, PlaintextTallyContest))
    Optional<Map<String, Map<String, PlaintextTally.PlaintextTallyContest>>> decrypted_ballotsO = decrypt_spoiled_ballots(
            tally.spoiled_ballots, shares, context.crypto_extended_base_hash);

    if (decrypted_ballotsO.isEmpty()) {
      return Optional.empty();
    }

    return Optional.of(
            new PlaintextTally(tally.object_id, contests.get(), decrypted_ballotsO.get(), lagrange_coefficients, guardianStates));
  }

  /**
   * Decrypt the ciphertextTallyContest into a PlaintextTallyContest.
   *
   * @param tally:              the encrypted tally of contests
   * @param shares:             a collection of `TallyDecryptionShare` used to decrypt
   * @param extended_base_hash: the extended base hash code (𝑄') for the election
   * @return Map(CONTEST_ID, PlaintextTallyContest)
   */
  static Optional<Map<String, PlaintextTally.PlaintextTallyContest>> decrypt_tally_contests_with_decryption_shares(
          Map<String, CiphertextTallyBuilder.CiphertextTallyContestBuilder> tally, // Map(CONTEST_ID, CiphertextTallyContest)
          Map<String, DecryptionShare.TallyDecryptionShare> shares, // Map(AVAILABLE_GUARDIAN_ID, TallyDecryptionShare)
          ElementModQ extended_base_hash) {

    HashMap<String, PlaintextTally.PlaintextTallyContest> contests = new HashMap<>();

    // iterate through the tally contests
    for (CiphertextTallyBuilder.CiphertextTallyContestBuilder contest : tally.values()) {
      HashMap<String, PlaintextTally.PlaintextTallySelection> selections = new HashMap<>();

      for (CiphertextTallyBuilder.CiphertextTallySelectionBuilder selection : contest.tally_selections.values()) {
        // Map(AVAILABLE_GUARDIAN_ID, KeyAndSelection)
        Map<String, KeyAndSelection> tally_shares = DecryptionShare.get_tally_shares_for_selection(selection.object_id, shares);
        Optional<PlaintextTally.PlaintextTallySelection> plaintext_selectionO = decrypt_selection_with_decryption_shares(
                selection, tally_shares, extended_base_hash, false);
        if (plaintext_selectionO.isEmpty()) {
          logger.atWarning().log("could not decrypt tally for contest %s", contest.object_id);
          return Optional.empty();
        }
        PlaintextTally.PlaintextTallySelection plaintext_selection = plaintext_selectionO.get();
        selections.put(plaintext_selection.object_id(), plaintext_selection);
      }

      contests.put(contest.object_id, PlaintextTally.PlaintextTallyContest.create(contest.object_id, selections));
    }

    return Optional.of(contests);
  }

  /**
   * Decrypt the CiphertextSelection into a PlaintextTallySelection.
   * Each share is expected to be passed with the corresponding public key so that the encryption can be validated
   *
   * @param selection:               a CiphertextSelection to decrypt
   * @param shares:                  the collection of shares used to decrypt the selection
   * @param extended_base_hash:      the extended base hash code (𝑄') for the election
   * @param suppress_validity_check: do not validate the encryption prior to decrypting (default false)
   * @return the decrypted selection
   */
  static Optional<PlaintextTally.PlaintextTallySelection> decrypt_selection_with_decryption_shares(
          Ballot.CiphertextSelection selection,
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

    // accumulate all of the shares calculated for the selection
    // all_shares_product_M = mult_p( *[decryption.share for (_, decryption) in shares.values()]);
    List<ElementModP> decryption_shares = shares.values().stream().map(t -> t.decryption.share()).collect(Collectors.toList());
    ElementModP all_shares_product_M = Group.mult_p(decryption_shares);

    // Calculate 𝑀 = 𝐵⁄(∏𝑀𝑖) mod 𝑝.
    Group.ElementModP decrypted_value = div_p(selection.ciphertext().data, all_shares_product_M);
    Integer dlogM = Dlog.discrete_log(decrypted_value);

    // [share for (guardian_id, (public_key, share))in shares.items()],
    List<DecryptionShare.CiphertextDecryptionSelection> selections = shares.values().stream().map(t -> t.decryption).collect(Collectors.toList());
    return Optional.of( PlaintextTally.PlaintextTallySelection.create(
            selection.object_id,
            dlogM,
            decrypted_value,
            selection.ciphertext(),
            selections
    ));
  }

  /**
   * Try to decrypt each of the spoiled ballots using the provided decryption shares.
   * @return Map(BALLOT_ID, Map(AVAILABLE_GUARDIAN_ID, PlaintextTallyContest))
   */
  static Optional<Map<String, Map<String, PlaintextTally.PlaintextTallyContest>>> decrypt_spoiled_ballots(
          Map<String, Ballot.CiphertextAcceptedBallot> spoiled_ballots, // Map(BALLOT_ID, CiphertextAcceptedBallot)
          Map<String, TallyDecryptionShare> shares, // Map(AVAILABLE_GUARDIAN_ID, TallyDecryptionShare)
          ElementModQ extended_base_hash) {

    HashMap<String, Map<String, PlaintextTally.PlaintextTallyContest>> plaintext_spoiled_ballots = new HashMap<>();
    for (Ballot.CiphertextAcceptedBallot spoiled_ballot : spoiled_ballots.values()) {
      HashMap<String, BallotDecryptionShare> ballot_shares = new HashMap<>();
      for (Map.Entry<String, TallyDecryptionShare> entry : shares.entrySet()) {
        TallyDecryptionShare share = entry.getValue();
        ballot_shares.put(entry.getKey(), share.spoiled_ballots().get(spoiled_ballot.object_id));
      }

      // Map(CONTEST_ID, PlaintextTallyContest)
      Optional<Map<String, PlaintextTally.PlaintextTallyContest>> decrypted_ballot =
              decrypt_ballot(spoiled_ballot, ballot_shares, extended_base_hash);
      if (decrypted_ballot.isPresent()) {
        plaintext_spoiled_ballots.put(spoiled_ballot.object_id, decrypted_ballot.get());
      } else {
        return Optional.empty();
      }
    }
    return Optional.of(plaintext_spoiled_ballots);
  }

  /** Decrypt a single ballot using the provided decryption shares. */
  static Optional<Map<String, PlaintextTally.PlaintextTallyContest>> decrypt_ballot(
          Ballot.CiphertextAcceptedBallot ballot,
          Map<String, BallotDecryptionShare> shares,
          ElementModQ extended_base_hash) {

    HashMap<String, PlaintextTally.PlaintextTallyContest> contests = new HashMap<>();

    for (Ballot.CiphertextBallotContest contest : ballot.contests) {
      HashMap<String, PlaintextTally.PlaintextTallySelection> selections = new HashMap<>();

      for (Ballot.CiphertextBallotSelection selection : contest.ballot_selections) {
        Map<String, KeyAndSelection> selection_shares = DecryptionShare.get_ballot_shares_for_selection(selection.object_id, shares);
        Optional<PlaintextTally.PlaintextTallySelection> plaintext_selectionO = decrypt_selection_with_decryption_shares(
                selection, selection_shares, extended_base_hash, false);

        // verify the plaintext values are received and add them to the collection
        if (plaintext_selectionO.isEmpty()) {
          logger.atWarning().log("could not decrypt ballot %s for contest %s selection %s",
              ballot.object_id, contest.object_id, selection.object_id);
          return Optional.empty();
        } else {
          PlaintextTally.PlaintextTallySelection plaintext_selection = plaintext_selectionO.get();
          selections.put(plaintext_selection.object_id(), plaintext_selection);
        }
      }
      contests.put(contest.object_id, PlaintextTally.PlaintextTallyContest.create(contest.object_id, selections));
    }
    return Optional.of(contests);
  }

  static Optional<ImmutableMap<String, Ballot.PlaintextBallot>> decrypt_spoiled_ballots2(
          Map<String, Ballot.CiphertextAcceptedBallot> spoiled_ballots, // Map(BALLOT_ID, CiphertextAcceptedBallot)
          Map<String, TallyDecryptionShare> shares, // Map(AVAILABLE_GUARDIAN_ID, TallyDecryptionShare)
          ElementModQ extended_base_hash) {

    ImmutableMap.Builder<String, Ballot.PlaintextBallot> plaintext_spoiled_ballots = ImmutableMap.builder();
    for (Ballot.CiphertextAcceptedBallot spoiled_ballot : spoiled_ballots.values()) {
      HashMap<String, BallotDecryptionShare> ballot_shares = new HashMap<>();
      for (Map.Entry<String, TallyDecryptionShare> entry : shares.entrySet()) {
        TallyDecryptionShare share = entry.getValue();
        ballot_shares.put(entry.getKey(), share.spoiled_ballots().get(spoiled_ballot.object_id));
      }

      Optional<Ballot.PlaintextBallot> decrypted_ballot = decrypt_ballot2(spoiled_ballot, ballot_shares, extended_base_hash);
      if (decrypted_ballot.isPresent()) {
        plaintext_spoiled_ballots.put(spoiled_ballot.object_id, decrypted_ballot.get());
      } else {
        return Optional.empty();
      }
    }
    return Optional.of(plaintext_spoiled_ballots.build());
  }

  /** Decrypt a single ballot using the provided decryption shares. */
  static Optional<Ballot.PlaintextBallot> decrypt_ballot2(
          Ballot.CiphertextAcceptedBallot ballot,
          Map<String, BallotDecryptionShare> shares,
          ElementModQ extended_base_hash) {

    List<Ballot.PlaintextBallotContest> contests = new ArrayList<>();
    for (Ballot.CiphertextBallotContest contest : ballot.contests) {
      List<Ballot.PlaintextBallotSelection> ballot_selections = new ArrayList<>();

      for (Ballot.CiphertextBallotSelection selection : contest.ballot_selections) {
        if (selection.is_placeholder_selection) {
          continue;
        }
        // LOOK is this duplicated in decrypt_spoiled_ballots?
        Map<String, KeyAndSelection> selection_shares = DecryptionShare.get_ballot_shares_for_selection(selection.object_id, shares);
        Optional<PlaintextTally.PlaintextTallySelection> plaintext_selectionO = decrypt_selection_with_decryption_shares(
                selection, selection_shares, extended_base_hash, false);

        // verify the plaintext values are received and add them to the collection
        if (plaintext_selectionO.isEmpty()) {
          logger.atWarning().log("could not decrypt ballot %s for contest %s selection %s",
                  ballot.object_id, contest.object_id, selection.object_id);
          return Optional.empty();
        } else {
          PlaintextTally.PlaintextTallySelection plaintext_selection = plaintext_selectionO.get();
          // String selection_id, String vote, boolean is_placeholder_selection,
          //                                    @Nullable ExtendedData extended_data
          Ballot.PlaintextBallotSelection decryptedBallot = new Ballot.PlaintextBallotSelection(
                  plaintext_selection.object_id(),
                  plaintext_selection.tally() > 0 ? "true" : "false",
                  false, null);
          ballot_selections.add(decryptedBallot);
        }
      }
      contests.add(new Ballot.PlaintextBallotContest(contest.object_id, ballot_selections));
    }

    // String object_id, String ballot_style, List<PlaintextBallotContest> contests
    return Optional.of(new Ballot.PlaintextBallot(ballot.object_id, ballot.ballot_style, contests));
  }
}
