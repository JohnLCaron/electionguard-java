package com.sunya.electionguard.decrypting;

import com.google.common.base.Preconditions;
import com.google.common.flogger.FluentLogger;
import com.sunya.electionguard.BallotBox;
import com.sunya.electionguard.CiphertextBallot;
import com.sunya.electionguard.CiphertextContest;
import com.sunya.electionguard.ElectionContext;
import com.sunya.electionguard.CiphertextSelection;
import com.sunya.electionguard.CiphertextTally;
import com.sunya.electionguard.DecryptionShare;
import com.sunya.electionguard.ElGamal;
import com.sunya.electionguard.Group;
import com.sunya.electionguard.SubmittedBallot;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.sunya.electionguard.DecryptionShare.CiphertextCompensatedDecryptionContest;
import static com.sunya.electionguard.DecryptionShare.CiphertextCompensatedDecryptionSelection;
import static com.sunya.electionguard.DecryptionShare.CiphertextDecryptionContest;
import static com.sunya.electionguard.DecryptionShare.CiphertextDecryptionSelection;
import static com.sunya.electionguard.DecryptionShare.CompensatedDecryptionShare;

/** Static methods for remote decryption. Replaces Decryptions in the main library. */
public class RemoteDecryptions {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  ////////////////////////////////////////////////////////////////////////////
  // decryption shares

  /**
   * Compute a guardian's share of a decryption for the tally, aka a 'partial decyrption'.
   * <p>
   * @param guardian: The guardian who will partially decrypt the tally
   * @param tally: The election tally to decrypt
   * @param context: The public election encryption context
   * @return a DecryptionShare
   */
  public static DecryptionShare computeDecryptionShareForTally(
          DecryptingTrusteeIF guardian,
          CiphertextTally tally,
          ElectionContext context) {

    // Get all the Ciphertext that need to be decrypted, and do so in one call
    List<ElGamal.Ciphertext> texts = new ArrayList<>();
    for (CiphertextTally.Contest tallyContest : tally.contests.values()) {
      for (CiphertextSelection selection : tallyContest.selections.values()) {
        texts.add(selection.ciphertext());
      }
    }
    List<BallotBox.DecryptionProofTuple> results = guardian.partialDecrypt(texts, context.crypto_extended_base_hash, null);

    // Create the guardian's DecryptionShare for the tally
    int count = 0;
    Map<String, CiphertextDecryptionContest> contests = new HashMap<>();
    for (CiphertextTally.Contest tallyContest : tally.contests.values()) {

      Map<String, CiphertextDecryptionSelection> selections = new HashMap<>();
      for (CiphertextSelection tallySelection : tallyContest.selections.values()) {
        BallotBox.DecryptionProofTuple tuple = results.get(count);
        if (tuple.proof.is_valid(tallySelection.ciphertext(), guardian.electionPublicKey(),
                tuple.decryption, context.crypto_extended_base_hash)) {

          CiphertextDecryptionSelection share = DecryptionShare.create_ciphertext_decryption_selection(
                  tallySelection.object_id(),
                  guardian.id(),
                  tuple.decryption,
                  Optional.of(tuple.proof),
                  Optional.empty());
          selections.put(tallySelection.object_id(), share);
          count++;
        } else {
          // LOOK?
        }
      }

      CiphertextDecryptionContest contest = new CiphertextDecryptionContest(
              tallyContest.object_id(), guardian.id(), tallyContest.contestDescriptionHash, selections);
      contests.put(tallyContest.object_id(), contest);
    }

    return new DecryptionShare(
            tally.object_id(),
            guardian.id(),
            guardian.electionPublicKey(),
            contests);
  }

  /** Compute the DecryptionShare for a list of ballots for a guardian. */
  public static Optional<Map<String, DecryptionShare>> computeDecryptionShareForBallots(
          DecryptingTrusteeIF guardian,
          Iterable<SubmittedBallot> ballots,
          ElectionContext context) {

    Map<String, DecryptionShare> shares = new HashMap<>();
    for (SubmittedBallot ballot : ballots) {
      DecryptionShare ballot_share = computeDecryptionShareForBallot(guardian, ballot, context);
      shares.put(ballot.object_id(), ballot_share);
    }

    return Optional.of(shares);
  }


  /**
   * Compute a guardian's share of a decryption for the tally, aka a 'partial decyrption'.
   * <p>
   * @param guardian: The guardian who will partially decrypt the tally
   * @param ballot: The encrypted ballot to decrypt
   * @param context: The public election encryption context
   * @return a DecryptionShare
   */
  private static DecryptionShare computeDecryptionShareForBallot(
          DecryptingTrusteeIF guardian,
          SubmittedBallot ballot,
          ElectionContext context) {

    // Get all the Ciphertext that need to be decrypted, and do so in one call
    List<ElGamal.Ciphertext> texts = new ArrayList<>();
    for (CiphertextBallot.Contest ballotContest : ballot.contests) {
      for (CiphertextBallot.Selection ballotSelection : ballotContest.ballot_selections) {
        texts.add(ballotSelection.ciphertext());
      }
    }
    List<BallotBox.DecryptionProofTuple> results = guardian.partialDecrypt(texts, context.crypto_extended_base_hash, null);

    // Create the guardian's DecryptionShare for the ballot
    int count = 0;
    Map<String, CiphertextDecryptionContest> contests = new HashMap<>();
    for (CiphertextBallot.Contest ballotContest : ballot.contests) {

      Map<String, CiphertextDecryptionSelection> selections = new HashMap<>();
      for (CiphertextBallot.Selection ballotSelection : ballotContest.ballot_selections) {
        BallotBox.DecryptionProofTuple tuple = results.get(count);
        if (tuple.proof.is_valid(ballotSelection.ciphertext(), guardian.electionPublicKey(),
                tuple.decryption, context.crypto_extended_base_hash)) {

          CiphertextDecryptionSelection share = DecryptionShare.create_ciphertext_decryption_selection(
                  ballotSelection.object_id(),
                  guardian.id(),
                  tuple.decryption,
                  Optional.of(tuple.proof),
                  Optional.empty());
          selections.put(ballotSelection.object_id(), share);
          count++;
        } else {
          // LOOK?
        }
      }

      CiphertextDecryptionContest contest = new CiphertextDecryptionContest(
              ballotContest.object_id, guardian.id(), ballotContest.contest_hash, selections);
      contests.put(ballotContest.object_id, contest);
    }

    return new DecryptionShare(
            ballot.object_id(),
            guardian.id(),
            guardian.electionPublicKey(),
            contests);
  }


  /////////////////////////////////////////////////////////////////////////////////////////////
  // compensated decryption shares

  /**
   * Compute a guardian's share of a compensated decryption for the tally, aka a 'compensated decryption share'.
   * <p>
   * @param guardian: The guardian who will partially decrypt the tally
   * @param tally: The election tally to decrypt
   * @param context: The public election encryption context
   * @return a CompensatedDecryptionShare
   */
  public static CompensatedDecryptionShare computeCompensatedDecryptionShareForTally(
          DecryptingTrusteeIF guardian,
          String missing_guardian_id,
          CiphertextTally tally,
          ElectionContext context) {

    // Get all the Ciphertext that need to be decrypted, and do so in one call
    List<ElGamal.Ciphertext> texts = new ArrayList<>();
    for (CiphertextTally.Contest tallyContest : tally.contests.values()) {
      for (CiphertextSelection selection : tallyContest.selections.values()) {
        texts.add(selection.ciphertext());
      }
    }
    List<DecryptionProofRecovery> results = guardian.compensatedDecrypt(
            missing_guardian_id,
            texts,
            context.crypto_extended_base_hash,
            null);

    // Create the guardian's DecryptionShare for the tally
    int count = 0;
    Map<String, CiphertextCompensatedDecryptionContest> contests = new HashMap<>();
    for (CiphertextTally.Contest tallyContest : tally.contests.values()) {

      Map<String, CiphertextCompensatedDecryptionSelection> selections = new HashMap<>();
      for (CiphertextSelection tallySelection : tallyContest.selections.values()) {
        DecryptionProofRecovery tuple = results.get(count);

        if (tuple.proof().is_valid(
                tallySelection.ciphertext(),
                tuple.recoveryPublicKey(),
                tuple.decryption(),
                context.crypto_extended_base_hash)) {

          CiphertextCompensatedDecryptionSelection share = new CiphertextCompensatedDecryptionSelection(
                  tallySelection.object_id(),
                  guardian.id(),
                  missing_guardian_id,
                  tuple.decryption(),
                  tuple.recoveryPublicKey(),
                  tuple.proof());

          selections.put(tallySelection.object_id(), share);
          count++;
        } else {
          // LOOK?
        }
      }

      CiphertextCompensatedDecryptionContest contest = new CiphertextCompensatedDecryptionContest(
              tallyContest.object_id(),
              guardian.id(),
              missing_guardian_id,
              tallyContest.contestDescriptionHash,
              selections);
      contests.put(tallyContest.object_id(), contest);
    }

    return new CompensatedDecryptionShare(
            tally.object_id(),
            guardian.id(),
            missing_guardian_id,
            guardian.electionPublicKey(),
            contests);
  }

  /**
   * Compute a guardian's share of a compensated decryption for a single ballot.
   * <p>
   * @param guardian: The guardian who will partially decrypt the tally
   * @param ballot: The encyypted ballot to decrypt
   * @param context: The public election encryption context
   * @return a CompensatedDecryptionShare
   */
  public static CompensatedDecryptionShare computeCompensatedDecryptionShareForBallot(
          DecryptingTrusteeIF guardian,
          String missing_guardian_id,
          SubmittedBallot ballot,
          ElectionContext context) {

    // Get all the Ciphertext that need to be decrypted, and do so in one call
    List<ElGamal.Ciphertext> texts = new ArrayList<>();
    for (CiphertextBallot.Contest ballotContest : ballot.contests) {
      for (CiphertextBallot.Selection selection : ballotContest.ballot_selections) {
        texts.add(selection.ciphertext());
      }
    }
    List<DecryptionProofRecovery> results = guardian.compensatedDecrypt(
            missing_guardian_id,
            texts,
            context.crypto_extended_base_hash,
            null);

    // Create the guardian's DecryptionShare for the tally
    int count = 0;
    Map<String, CiphertextCompensatedDecryptionContest> contests = new HashMap<>();
    for (CiphertextBallot.Contest ballotContest : ballot.contests) {

      Map<String, CiphertextCompensatedDecryptionSelection> selections = new HashMap<>();
      for (CiphertextBallot.Selection selection : ballotContest.ballot_selections) {
        DecryptionProofRecovery tuple = results.get(count);

        if (tuple.proof().is_valid(
                selection.ciphertext(),
                tuple.recoveryPublicKey(),
                tuple.decryption(),
                context.crypto_extended_base_hash)) {

          CiphertextCompensatedDecryptionSelection share = new CiphertextCompensatedDecryptionSelection(
                  selection.object_id(),
                  guardian.id(),
                  missing_guardian_id,
                  tuple.decryption(),
                  tuple.recoveryPublicKey(),
                  tuple.proof());

          selections.put(selection.object_id(), share);
          count++;
        } else {
          // LOOK?
        }
      }

      CiphertextCompensatedDecryptionContest contest = new CiphertextCompensatedDecryptionContest(
              ballotContest.object_id,
              guardian.id(),
              missing_guardian_id,
              ballotContest.contest_hash,
              selections);
      contests.put(ballotContest.object_id, contest);
    }

    return new CompensatedDecryptionShare(
            ballot.object_id(),
            guardian.id(),
            missing_guardian_id,
            guardian.electionPublicKey(),
            contests);
  }

  ///////////////////////////////////////////////////////////////////////////////////////////////

  /**
   * Reconstruct the missing Decryption Share for a missing guardian
   * from the collection of compensated decryption shares.
   */
  // Map(MISSING_GUARDIAN_ID, DecryptionShare)
  public static DecryptionShare reconstruct_decryption_share_for_tally(
          String missing_guardian_id,
          Group.ElementModP missing_public_key,
          CiphertextTally tally,
          Map<String, CompensatedDecryptionShare> shares, // Map(GUARDIAN_ID, CompensatedDecryptionShare)
          Map<String, Group.ElementModQ> lagrange_coefficients) {

    Map<String, CiphertextDecryptionContest> contests = new HashMap<>();
    for (CiphertextTally.Contest contest : tally.contests.values()) {
      CiphertextDecryptionContest dcontest = reconstruct_decryption_contest(
              missing_guardian_id,
              CiphertextContest.createFrom(contest),
              shares,
              lagrange_coefficients);
      contests.put(contest.object_id(), dcontest);
    }

    return new DecryptionShare(tally.object_id(), missing_guardian_id, missing_public_key, contests);
  }

  /**
   * Reconstruct a missing ballot Decryption share for a missing guardian from the collection of compensated decryption shares.
   *
   * @param missing_guardian_id   The guardian id for the missing guardian
   * @param missing_public_key    the public key for the missing guardian
   * @param ballot                The `SubmittedBallot` to reconstruct
   * @param shares                the collection of `CompensatedDecryptionShare` for the missing guardian, each keyed by the ID of the guardian that produced it
   * @param lagrange_coefficients the lagrange coefficients for the available guardians that provided shares
   */
  public static DecryptionShare reconstruct_decryption_share_for_ballot(
          String missing_guardian_id,
          Group.ElementModP missing_public_key,
          SubmittedBallot ballot,
          Map<String, CompensatedDecryptionShare> shares, // Dict[AVAILABLE_GUARDIAN_ID, CompensatedBallotDecryptionShare]
          Map<String, Group.ElementModQ> lagrange_coefficients) { // Dict[AVAILABLE_GUARDIAN_ID, ElementModQ]

    Map<String, CiphertextDecryptionContest> contests = new HashMap<>();
    for (CiphertextBallot.Contest contest : ballot.contests) {
      CiphertextDecryptionContest dcontest = reconstruct_decryption_contest(
              missing_guardian_id,
              CiphertextContest.createFrom(contest),
              shares,
              lagrange_coefficients);
      contests.put(contest.object_id, dcontest);
    }

    return new DecryptionShare(
            ballot.object_id(),
            missing_guardian_id,
            missing_public_key,
            contests);
  }

  /**
   * Reconstruct the missing Decryption Share for a missing guardian
   * from the collection of compensated decryption shares.
   * <p>
   * @param missing_guardian_id: The guardian id for the missing guardian
   * @param contest: The CiphertextContest to decrypt
   * @param shares: the collection of `CompensatedDecryptionShare` for the missing guardian
   * @param lagrange_coefficients: the lagrange coefficients corresponding to the available guardians that provided shares
   */
  private static CiphertextDecryptionContest reconstruct_decryption_contest(
          String missing_guardian_id,
          CiphertextContest contest,
          Map<String, CompensatedDecryptionShare> shares,
          Map<String, Group.ElementModQ> lagrange_coefficients) {

    Map<String, CiphertextCompensatedDecryptionContest> contest_shares = new HashMap<>();
    for (Map.Entry<String, CompensatedDecryptionShare> entry2 : shares.entrySet()) {
      String available_guardian_id = entry2.getKey();
      CompensatedDecryptionShare compensated_share = entry2.getValue();
      Preconditions.checkArgument(compensated_share.contests.containsKey(contest.object_id));
      contest_shares.put(available_guardian_id, compensated_share.contests.get(contest.object_id));
    }

    Map<String, CiphertextDecryptionSelection> selections = new HashMap<>();
    for (CiphertextSelection selection : contest.selections) {
      // collect all of the shares generated for each selection
      Map<String, CiphertextCompensatedDecryptionSelection> compensated_selection_shares = new HashMap<>();
      for (Map.Entry<String, CiphertextCompensatedDecryptionContest> entry4 : contest_shares.entrySet()) {
        String available_guardian_id = entry4.getKey();
        CiphertextCompensatedDecryptionContest compensated_contest = entry4.getValue();
        Preconditions.checkArgument(compensated_contest.selections().containsKey(selection.object_id()));
        compensated_selection_shares.put(available_guardian_id, compensated_contest.selections().get(selection.object_id()));
      }

      List<Group.ElementModP> share_pow_p = new ArrayList<>();
      for (Map.Entry<String, CiphertextCompensatedDecryptionSelection> entry5 : compensated_selection_shares.entrySet()) {
        String available_guardian_id = entry5.getKey();
        CiphertextCompensatedDecryptionSelection share = entry5.getValue();
        Group.ElementModQ c = lagrange_coefficients.get(available_guardian_id);
        Group.ElementModP p = Group.pow_p(share.share(), c);
        share_pow_p.add(p);
      }

      // product M_il^w_l
      Group.ElementModP reconstructed_share = Group.mult_p(share_pow_p);

      selections.put(selection.object_id(), DecryptionShare.create_ciphertext_decryption_selection(
              selection.object_id(),
              missing_guardian_id,
              reconstructed_share,
              Optional.empty(),
              Optional.of(compensated_selection_shares)));
    }
    return new CiphertextDecryptionContest(
            contest.object_id,
            missing_guardian_id,
            contest.description_hash,
            selections);
  }

}