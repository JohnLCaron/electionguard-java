package com.sunya.electionguard;

import com.google.common.base.Preconditions;
import com.google.common.flogger.FluentLogger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static com.sunya.electionguard.DecryptionShare.KeyAndSelection;
import static com.sunya.electionguard.Group.ElementModP;
import static com.sunya.electionguard.Group.ElementModQ;
import static com.sunya.electionguard.Group.div_p;

/** Static methods for decryption with shares. */
class DecryptWithShares {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  /** Decrypt a collection of ciphertext spoiled ballots into decrypted plaintext tallies and ballots. */
  static Optional<List<DecryptionMediator.SpoiledBallotAndTally>> decrypt_spoiled_ballots(
          Iterable<CiphertextAcceptedBallot> ballots,
          Map<String, Map<String, DecryptionShare>> shares, // MAP(AVAILABLE_GUARDIAN_ID, Map(BALLOT_ID, DecryptionShare))
          CiphertextElectionContext context) {

    List<DecryptionMediator.SpoiledBallotAndTally> result = new ArrayList<>();
    for (CiphertextAcceptedBallot ballot : ballots) {
      HashMap<String, DecryptionShare> ballot_shares = new HashMap<>();
      for (Map.Entry<String, Map<String, DecryptionShare>> entry : shares.entrySet()) {
        Map<String, DecryptionShare> map2 = entry.getValue();
        Preconditions.checkArgument(map2.containsKey(ballot.object_id));
        ballot_shares.put(entry.getKey(), map2.get(ballot.object_id));
      }

      Optional<PlaintextTally> decrypted_tally =
              decrypt_ballot(ballot, ballot_shares, context.crypto_extended_base_hash);
      if (decrypted_tally.isEmpty()) {
        return Optional.empty();
      }
      PlaintextTally dtally = decrypted_tally.get();
      PlaintextBallot dballot = PlaintextBallot.from(ballot, dtally);
      result.add(new DecryptionMediator.SpoiledBallotAndTally(dtally, dballot));
    }

    return Optional.of(result);
  }

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
          Map<String, DecryptionShare> shares, // Map(AVAILABLE_GUARDIAN_ID, DecryptionShare)
          CiphertextElectionContext context,
          Map<String, ElementModQ> lagrange_coefficients,
          List<GuardianState> guardianStates) {

    Map<String, PlaintextTally.Contest> contests = new HashMap<>();
    for (CiphertextTally.Contest tallyContest : tally.contests.values()) {
      Optional<PlaintextTally.Contest> pc = decrypt_contest_with_decryption_shares(
              CiphertextContest.createFrom(tallyContest), shares, context.crypto_extended_base_hash);
      if (pc.isEmpty()) {
        return Optional.empty();
      }
      contests.put(tallyContest.object_id, pc.get());
    }

    if (contests.isEmpty()) {
      return Optional.empty();
    } else {
      return Optional.of(new PlaintextTally(tally.object_id, contests, lagrange_coefficients, guardianStates));
    }
  }


  ///////////////////////////////////////////////////////////////////////////////////////////////////////////
  // spoiled ballots -> plaintext ballot

  /** Decrypt a collection of ciphertext spoiled ballots into Map(BALLOT_ID, PlaintextTally). */
  static Optional<Map<String, PlaintextTally>> decrypt_ballots(
          Iterable<CiphertextAcceptedBallot> ballots,
          Map<String, Map<String, DecryptionShare>> shares, // MAP(AVAILABLE_GUARDIAN_ID, Map(BALLOT_ID, DecryptionShare))
          CiphertextElectionContext context) {

    Map<String, PlaintextTally> result = new HashMap<>();
    for (CiphertextAcceptedBallot ballot : ballots) {
      HashMap<String, DecryptionShare> ballot_shares = new HashMap<>();
      for (Map.Entry<String, Map<String, DecryptionShare>> entry : shares.entrySet()) {
        Map<String, DecryptionShare> map2 = entry.getValue();
        ballot_shares.put(entry.getKey(), map2.get(ballot.object_id));
      }

      Optional<PlaintextTally> decrypted_tally =
              decrypt_ballot(ballot, ballot_shares, context.crypto_extended_base_hash);
      if (decrypted_tally.isEmpty()) {
         return Optional.empty();
      }
      result.put(ballot.object_id, decrypted_tally.get());
    }

    return Optional.of(result);
  }

  /** Decrypt a single ciphertext ballot into a decrypted plaintext ballot. */
  static Optional<PlaintextTally> decrypt_ballot(
          CiphertextAcceptedBallot ballot,
          Map<String, DecryptionShare> shares,
          ElementModQ extended_base_hash) {

    Map<String, PlaintextTally.Contest> plaintext_contests = new HashMap<>();

    for (CiphertextBallot.Contest ballotContest : ballot.contests) {
      Optional<PlaintextTally.Contest> plaintext_contest = decrypt_contest_with_decryption_shares(
              CiphertextContest.createFrom(ballotContest),
              shares,
              extended_base_hash);
      if (plaintext_contest.isEmpty()) {
        return Optional.empty();
      }
      plaintext_contests.put(ballotContest.object_id, plaintext_contest.get());
    }

    // String object_id, String ballot_style, List<PlaintextBallotContest> contests
    return Optional.of(new PlaintextTally(ballot.object_id, plaintext_contests, null, null));
  }

  /**
   * Decrypt the CiphertextTally.Contest into a PlaintextTally.Contest.
   *
   * @param contest:            the contest to decrypt.
   * @param shares:             a collection of DecryptionShare's used to decrypt
   * @param extended_base_hash: the extended base hash code (𝑄') for the election
   * @return Map(CONTEST_ID, PlaintextTally.Contest)
   */
  static Optional<PlaintextTally.Contest> decrypt_contest_with_decryption_shares(
          CiphertextContest contest, // Map(CONTEST_ID, CiphertextTallyContest)
          Map<String, DecryptionShare> shares, // Map(AVAILABLE_GUARDIAN_ID, DecryptionShare)
          ElementModQ extended_base_hash) {

      HashMap<String, PlaintextTally.Selection> plaintext_selections = new HashMap<>();

      for (CiphertextSelection selection : contest.selections) {
        // Map(AVAILABLE_GUARDIAN_ID, KeyAndSelection)
        Map<String, KeyAndSelection> tally_shares =
                DecryptionShare.get_tally_shares_for_selection2(selection.object_id, shares);
        Optional<PlaintextTally.Selection> plaintext_selectionO = decrypt_selection_with_decryption_shares(
                selection, tally_shares, extended_base_hash, false);
        if (plaintext_selectionO.isEmpty()) {
          logger.atWarning().log("could not decrypt tally for contest %s", contest.object_id);
          return Optional.empty();
        }
        PlaintextTally.Selection plaintext_selection = plaintext_selectionO.get();
        plaintext_selections.put(plaintext_selection.object_id(), plaintext_selection);
      }

    return Optional.of(PlaintextTally.Contest.create(contest.object_id, plaintext_selections));
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
          ElementModQ extended_base_hash,
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
    ElementModP decrypted_value = div_p(selection.ciphertext().data, all_shares_product_M);
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
}
