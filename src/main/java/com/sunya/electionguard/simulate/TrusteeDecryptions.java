package com.sunya.electionguard.simulate;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.flogger.FluentLogger;
import com.sunya.electionguard.CiphertextBallot;
import com.sunya.electionguard.CiphertextContest;
import com.sunya.electionguard.CiphertextElectionContext;
import com.sunya.electionguard.CiphertextSelection;
import com.sunya.electionguard.CiphertextTally;
import com.sunya.electionguard.DecryptionProofTuple;
import com.sunya.electionguard.DecryptionShare;
import com.sunya.electionguard.Group;
import com.sunya.electionguard.SubmittedBallot;
import com.sunya.electionguard.decrypting.DecryptingTrusteeIF;
import com.sunya.electionguard.decrypting.DecryptionProofRecovery;

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

/** Static methods for decryption for trustees. */
public class TrusteeDecryptions {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  ////////////////////////////////////////////////////////////////////////////
  // decryption shares

  /**
   * Compute a decryption share for a guardian. Parallizable over each of the tally's contests.
   * <p>
   * @param guardian: The guardian who will partially decrypt the tally
   * @param tally: The election tally to decrypt
   * @param context: The public election encryption context
   * @return a `DecryptionShare` or `None` if there is an error
   */
  public static Optional<DecryptionShare> compute_decryption_share(
          DecryptingTrusteeIF guardian,
          CiphertextTally tally,
          CiphertextElectionContext context) {

    Map<String, CiphertextDecryptionContest> contests = new HashMap<>();
    for (CiphertextTally.Contest tallyContest : tally.contests.values()) {
      Optional<CiphertextDecryptionContest> contest =
              compute_decryption_share_for_contest(guardian, CiphertextContest.createFrom(tallyContest), context);
      if (contest.isEmpty()) {
        return Optional.empty();
      }
      contests.put(tallyContest.object_id, contest.get());
    }

    return Optional.of(new DecryptionShare(
            tally.object_id,
            guardian.id(),
            guardian.electionPublicKey(),
            contests));
  }

  /** Compute the DecryptionShare for a list of ballots for a guardian. */
  public static Optional<Map<String, DecryptionShare>> compute_decryption_share_for_ballots(
          DecryptingTrusteeIF guardian,
          Iterable<SubmittedBallot> ballots,
          CiphertextElectionContext context) {

  Map<String, DecryptionShare> shares = new HashMap<>();

    for (SubmittedBallot ballot : ballots) {
      Optional<DecryptionShare> ballot_share = compute_decryption_share_for_ballot(
              guardian, ballot, context);
      if (ballot_share.isEmpty()) {
        return Optional.empty();
      }
      shares.put(ballot.object_id, ballot_share.get());
    }

    return Optional.of(shares);
  }

  /** Compute the DecryptionShare for a single ballot for a guardian. */
  private static Optional<DecryptionShare> compute_decryption_share_for_ballot(
          DecryptingTrusteeIF guardian,
          SubmittedBallot ballot,
          CiphertextElectionContext context) {

    // Map(CONTEST_ID, CiphertextDecryptionContest)
    Map<String, CiphertextDecryptionContest> contests = new HashMap<>();

    for (CiphertextBallot.Contest contest : ballot.contests) {
      Optional<CiphertextDecryptionContest> contest_share = compute_decryption_share_for_contest(
              guardian,
              CiphertextContest.createFrom(contest),
              context);
        if (contest_share.isEmpty()) {
          logger.atInfo().log("could not compute ballot share for guardian %s contest %s",
                  guardian.id(), contest.object_id);
          return Optional.empty();
        }
        contests.put(contest.object_id, contest_share.get());
    }
    return Optional.of(new DecryptionShare(
            ballot.object_id,
            guardian.id(),
            guardian.electionPublicKey(),
            contests));
  }

  /**
   * Compute the decryption for all of the contests in the Ciphertext Tally for a specific guardian.
   * Parallizable over each of the tally's contests.
   * @return Map(CONTEST_ID, CiphertextDecryptionContest)
   */
  private static Optional<CiphertextDecryptionContest> compute_decryption_share_for_contest(
          DecryptingTrusteeIF guardian,
          CiphertextContest ciphertextContest,
          CiphertextElectionContext context) {

    Map<String, CiphertextDecryptionSelection> selections = new HashMap<>();

    for (CiphertextSelection selection : ciphertextContest.selections) {
      Optional<CiphertextDecryptionSelection> decryption =
              compute_decryption_share_for_selection(guardian, selection, context);

      // verify the decryptions are received and add them to the collection
      // for (Optional<CiphertextDecryptionSelection> decryption : selection_decryptions) {
      if (decryption.isEmpty()) {
        logger.atWarning().log("could not compute share for guardian %s contest %s",
                guardian.id(), ciphertextContest.object_id);
        return Optional.empty();
      }
      selections.put(decryption.get().object_id(), decryption.get());
    }

    return Optional.of(CiphertextDecryptionContest.create(
            ciphertextContest.object_id, guardian.id(), ciphertextContest.description_hash, selections));
  }

  /**
   * Compute a partial decryption for a specific selection and guardian.
   * @param guardian: The guardian who will partially decrypt the selection
   * @param selection: The specific selection to decrypt
   * @param context: The public election encryption context
   * @return a `CiphertextDecryptionSelection` or `None` if there is an error
   */
  // @VisibleForTesting
  private static Optional<CiphertextDecryptionSelection> compute_decryption_share_for_selection(
          DecryptingTrusteeIF guardian,
          CiphertextSelection selection,
          CiphertextElectionContext context) {

    try {
      List<DecryptionProofTuple> results = guardian.partialDecrypt(ImmutableList.of(selection.ciphertext()), context.crypto_extended_base_hash, null);
      if (results.isEmpty()) {
        logger.atWarning().log("compute_decryption_share_for_selection guardian.partialDecrypt failed",
                guardian.id(), selection.object_id);
        return Optional.empty();
      }
      DecryptionProofTuple tuple = results.get(0);
      if (tuple.proof.is_valid(selection.ciphertext(), guardian.electionPublicKey(),
              tuple.decryption, context.crypto_extended_base_hash)) {
        return Optional.of(DecryptionShare.create_ciphertext_decryption_selection(
                selection.object_id,
                guardian.id(),
                tuple.decryption,
                Optional.of(tuple.proof),
                Optional.empty()));
      } else {
        logger.atWarning().log("compute decryption share proof failed for %s %s with invalid proof",
                guardian.id(), selection.object_id);
        return Optional.empty();
      }
    } catch (Throwable t) {
      t.printStackTrace();
      return Optional.empty();
    }
  }

  /////////////////////////////////////////////////////////////////////////////////////////////
  // compensated decryption shares

  /**
   * Compute a compensated decryptions share for a guardian.
   * <p>
   * @param guardian: The guardian who will partially decrypt the tally
   * @param missing_guardian_id: the missing guardian id to compensate
   * @param tally: The election tally to decrypt
   * @param context: The public election encryption context
   * @return a `DecryptionShare` or `None` if there is an error
   */
  public static Optional<CompensatedDecryptionShare> compute_compensated_decryption_share(
          DecryptingTrusteeIF guardian,
          String missing_guardian_id,
          CiphertextTally tally,
          CiphertextElectionContext context) {

    Map<String, CiphertextCompensatedDecryptionContest> contests = new HashMap<>();

    for (CiphertextTally.Contest contest : tally.contests.values()) {
      Optional<CiphertextCompensatedDecryptionContest> dcontest = compute_compensated_decryption_share_for_contest(
              guardian, missing_guardian_id, CiphertextContest.createFrom(contest), context);
      if (dcontest.isEmpty()) {
        return Optional.empty();
      }
      contests.put(contest.object_id, dcontest.get());
    }

    return Optional.of(new CompensatedDecryptionShare(
            tally.object_id,
            guardian.id(),
            missing_guardian_id,
            guardian.electionPublicKey(),
            contests));
  }

  /**
   * Compute the compensated decryption for all of the cast contests in the Ciphertext Tally.
   * Parallizable over each of the tally's contests.
   */
  private static Optional<CiphertextCompensatedDecryptionContest> compute_compensated_decryption_share_for_contest(
          DecryptingTrusteeIF guardian,
          String missing_guardian_id,
          CiphertextContest contest,
          CiphertextElectionContext context) {

      Map<String, CiphertextCompensatedDecryptionSelection> selections = new HashMap<>();

      for (CiphertextSelection selection : contest.selections) {
        Optional<CiphertextCompensatedDecryptionSelection> decryption =
             compute_compensated_decryption_share_for_selection(guardian, missing_guardian_id, selection, context);
        if (decryption.isEmpty()) {
          logger.atWarning().log("could not compute share for guardian %s contest %s", guardian.id(), contest.object_id);
          continue;
          // return Optional.empty();
        }
        selections.put(decryption.get().object_id(), decryption.get());
      }

      return Optional.of(CiphertextCompensatedDecryptionContest.create(
              contest.object_id,
              guardian.id(),
              missing_guardian_id,
              contest.description_hash,
              selections));
  }

  /**
   * Compute the compensated decryption for a single ballot.
   * Parallizable over each of the ballot's selections.
   */
  @VisibleForTesting
  static Optional<CompensatedDecryptionShare> compute_compensated_decryption_share_for_ballot(
          DecryptingTrusteeIF guardian,
          String missing_guardian_id,
          SubmittedBallot ballot,
          CiphertextElectionContext context) {

    Map<String, CiphertextCompensatedDecryptionContest> contests = new HashMap<>();

    for (CiphertextBallot.Contest contest : ballot.contests) {
      Optional<CiphertextCompensatedDecryptionContest> contest_share =
              compute_compensated_decryption_share_for_contest(
              guardian,
              missing_guardian_id,
              CiphertextContest.createFrom(contest),
              context);
         if (contest_share.isEmpty()) {
          logger.atWarning().log("could not compute compensated spoiled ballot share for guardian %s missing: %s contest %s",
                  guardian.id(), missing_guardian_id, contest.object_id);
          return Optional.empty();
        }
      contests.put(contest.object_id, contest_share.get());
    }

    return Optional.of(new CompensatedDecryptionShare(
            ballot.object_id,
            guardian.id(),
            missing_guardian_id,
            guardian.electionPublicKey(),
            contests));
  }

  /**
   * Compute a compensated decryption share for a specific selection using the
   * available guardians' share of the missing guardian's private key polynomial.
   * <p>
   * @param guardian: The available guardian that will partially decrypt the selection
   * @param missing_guardian_id: The id of the guardian that is missing
   * @param selection: The specific selection to decrypt
   * @param context: The public election encryption context
   * @return a `CiphertextCompensatedDecryptionSelection` or `None` if there is an error
   */
  // @VisibleForTesting
  private  static Optional<CiphertextCompensatedDecryptionSelection> compute_compensated_decryption_share_for_selection(
          DecryptingTrusteeIF guardian,
          String missing_guardian_id,
          CiphertextSelection selection,
          CiphertextElectionContext context) {

    List<DecryptionProofRecovery> compensated = guardian.compensatedDecrypt(
            missing_guardian_id,
            ImmutableList.of(selection.ciphertext()),
            context.crypto_extended_base_hash,
            null);

    if (compensated.isEmpty()) {
      logger.atWarning().log("compute compensated decryption share failed for %s missing: %s %s",
              guardian.id(), missing_guardian_id, selection.object_id);
      return Optional.empty();
    }
    DecryptionProofRecovery tuple = compensated.get(0);

    if (tuple.proof.is_valid(
            selection.ciphertext(),
            tuple.recoveryPublicKey,
            tuple.decryption,
            context.crypto_extended_base_hash)) {

      CiphertextCompensatedDecryptionSelection share = CiphertextCompensatedDecryptionSelection.create(
              selection.object_id,
              guardian.id(),
              missing_guardian_id,
              tuple.decryption,
              tuple.recoveryPublicKey,
              tuple.proof);
      return Optional.of(share);
    } else {
      logger.atWarning().log("compute compensated decryption share proof failed for %s missing: %s %s",
              guardian.id(), missing_guardian_id, selection.object_id);
      return Optional.empty();
    }
  }

  ///////////////////////////////////////////////////////////////////////////////////////////////

  /**
   * Reconstruct the missing Decryption Share for a missing guardian
   * from the collection of compensated decryption shares.
   */
  // Map(MISSING_GUARDIAN_ID, DecryptionShare)
  public static DecryptionShare reconstruct_decryption_share(
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
      contests.put(contest.object_id, dcontest);
    }

    return new DecryptionShare(tally.object_id, missing_guardian_id, missing_public_key, contests);
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
          Preconditions.checkArgument(compensated_contest.selections().containsKey(selection.object_id));
          compensated_selection_shares.put(available_guardian_id, compensated_contest.selections().get(selection.object_id));
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

        selections.put(selection.object_id, DecryptionShare.create_ciphertext_decryption_selection(
                selection.object_id,
                missing_guardian_id,
                reconstructed_share,
                Optional.empty(),
                Optional.of(compensated_selection_shares)));
      }
      return CiphertextDecryptionContest.create(
              contest.object_id,
              missing_guardian_id,
              contest.description_hash,
              selections);
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
            ballot.object_id,
            missing_guardian_id,
            missing_public_key,
            contests);
  }

}

