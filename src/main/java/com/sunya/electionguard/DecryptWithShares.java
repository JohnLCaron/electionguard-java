package com.sunya.electionguard;

import com.google.common.annotations.VisibleForTesting;
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
public class DecryptWithShares {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  /** Decrypt a collection of ciphertext spoiled ballots into decrypted plaintext tallies and ballots. */
  public static Optional<List<SpoiledBallotAndTally>> decrypt_spoiled_ballots(
          Iterable<SubmittedBallot> ballots,
          Map<String, Map<String, DecryptionShare>> shares, // MAP(AVAILABLE_GUARDIAN_ID, Map(BALLOT_ID, DecryptionShare))
          CiphertextElectionContext context) {

    List<SpoiledBallotAndTally> result = new ArrayList<>();
    for (SubmittedBallot ballot : ballots) {
      HashMap<String, DecryptionShare> ballot_shares = new HashMap<>();
      for (Map.Entry<String, Map<String, DecryptionShare>> entry : shares.entrySet()) {
        Map<String, DecryptionShare> map2 = entry.getValue();
        Preconditions.checkArgument(map2.containsKey(ballot.object_id()));
        ballot_shares.put(entry.getKey(), map2.get(ballot.object_id()));
      }

      Optional<PlaintextTally> decrypted_tally =
              decrypt_ballot(ballot, ballot_shares, context.crypto_extended_base_hash);
      if (decrypted_tally.isEmpty()) {
        return Optional.empty();
      }
      PlaintextTally dtally = decrypted_tally.get();
      PlaintextBallot dballot = PlaintextBallot.from(ballot, dtally);
      result.add(new SpoiledBallotAndTally(dtally, dballot));
    }

    return Optional.of(result);
  }

  /**
   * Decrypt the CiphertextTally into a PlaintextTally.
   *
   * @param tally:   The CiphertextTally to decrypt
   * @param shares:  The guardian Decryption Shares for all guardians
   * @param context: the CiphertextElectionContext
   * @return A PlaintextTally or None if there is an error
   */
  public static Optional<PlaintextTally> decrypt_tally(
          CiphertextTally tally,
          Map<String, DecryptionShare> shares, // Map(AVAILABLE_GUARDIAN_ID, DecryptionShare)
          CiphertextElectionContext context) {

    Map<String, PlaintextTally.Contest> contests = new HashMap<>();
    for (CiphertextTally.Contest tallyContest : tally.contests.values()) {
      Optional<PlaintextTally.Contest> pc = decrypt_contest_with_decryption_shares(
              CiphertextContest.createFrom(tallyContest), shares, context.crypto_extended_base_hash);
      if (pc.isEmpty()) {
        logger.atWarning().log("contest: %s failed to decrypt with shares", tallyContest.object_id());
        return Optional.empty();
      }
      contests.put(tallyContest.object_id(), pc.get());
    }

    if (contests.isEmpty()) {
      return Optional.empty();
    } else {
      return Optional.of(new PlaintextTally(tally.object_id(), contests));
    }
  }


  ///////////////////////////////////////////////////////////////////////////////////////////////////////////
  // spoiled ballots -> plaintext ballot

  /** Decrypt a collection of ciphertext spoiled ballots into Map(BALLOT_ID, PlaintextTally). */
  public static Optional<Map<String, PlaintextTally>> decrypt_ballots(
          Iterable<SubmittedBallot> ballots,
          Map<String, Map<String, DecryptionShare>> shares, // MAP(AVAILABLE_GUARDIAN_ID, Map(BALLOT_ID, DecryptionShare))
          CiphertextElectionContext context) {

    Map<String, PlaintextTally> result = new HashMap<>();
    for (SubmittedBallot ballot : ballots) {
      HashMap<String, DecryptionShare> ballot_shares = new HashMap<>();
      for (Map.Entry<String, Map<String, DecryptionShare>> entry : shares.entrySet()) {
        Map<String, DecryptionShare> map2 = entry.getValue();
        ballot_shares.put(entry.getKey(), map2.get(ballot.object_id()));
      }

      Optional<PlaintextTally> decrypted_tally =
              decrypt_ballot(ballot, ballot_shares, context.crypto_extended_base_hash);
      if (decrypted_tally.isEmpty()) {
         return Optional.empty();
      }
      result.put(ballot.object_id(), decrypted_tally.get());
    }

    return Optional.of(result);
  }

  /** Decrypt a single ciphertext ballot into a decrypted plaintext ballot. */
  public static Optional<PlaintextTally> decrypt_ballot(
          SubmittedBallot ballot,
          Map<String, DecryptionShare> shares,
          ElementModQ extended_base_hash) {

    Map<String, PlaintextTally.Contest> plaintext_contests = new HashMap<>();

    for (CiphertextBallot.Contest ballotContest : ballot.contests) {
      Optional<PlaintextTally.Contest> plaintext_contest = decrypt_contest_with_decryption_shares(
              CiphertextContest.createFrom(ballotContest),
              shares,
              extended_base_hash);
      if (plaintext_contest.isEmpty()) {
        logger.atWarning().log("contest: %s failed to decrypt with shares", ballotContest.object_id);
        return Optional.empty();
      }
      plaintext_contests.put(ballotContest.object_id, plaintext_contest.get());
    }

    return Optional.of(new PlaintextTally(ballot.object_id(), plaintext_contests));
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
        if (selection.is_placeholder) {
          continue;
        }
        // Map(AVAILABLE_GUARDIAN_ID, KeyAndSelection)
        Map<String, KeyAndSelection> tally_shares =
                DecryptionShare.get_tally_shares_for_selection2(selection.object_id(), shares);
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
  @VisibleForTesting
  public static Optional<PlaintextTally.Selection> decrypt_selection_with_decryption_shares(
          CiphertextSelection selection,
          Map<String, KeyAndSelection> shares, // Map(AVAILABLE_GUARDIAN_ID, KeyAndSelection)
          ElementModQ extended_base_hash,
          boolean suppress_validity_check) {

    if (!suppress_validity_check) {
      // Verify that all of the shares are computed correctly
      for (KeyAndSelection tuple : shares.values()) {
        // verify we have a proof or recovered parts
        if (!tuple.decryption.is_valid(selection.ciphertext(), tuple.public_key, extended_base_hash)) {
          logger.atWarning().log("share: %s has invalid proof or recovered parts", tuple.decryption.object_id());
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
            selection.object_id(),
            dlogM,
            decrypted_value,
            selection.ciphertext(),
            selections
    ));
  }
}
