package com.sunya.electionguard;

import com.google.common.flogger.FluentLogger;

import java.math.BigInteger;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;

import static com.sunya.electionguard.DecryptionShare.*;
import static com.sunya.electionguard.Election.*;
import static com.sunya.electionguard.Tally.*;

public class Decryption {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  /**
   * Compute a decryptions share for a guardian.
   * <p>
   * @param guardian: The guardian who will partially decrypt the tally
   * @param tally: The election tally to decrypt
   * @param context: The public election encryption context
   * @return a `TallyDecryptionShare` or `None` if there is an error
   */
  static Optional<TallyDecryptionShare> compute_decryption_share(
          Guardian guardian,
          CiphertextTally tally,
          CiphertextElectionContext context) {

    Optional<Map<String, CiphertextDecryptionContest>> contests =
            compute_decryption_share_for_cast_contests(guardian, tally, context);
    if (contests.isEmpty()) {
      return Optional.empty();
    }

    Optional<Map<String, BallotDecryptionShare>> spoiled_ballots =
            compute_decryption_share_for_spoiled_ballots(guardian, tally, context);

    if (spoiled_ballots.isEmpty()) {
      return Optional.empty();
    }

    return Optional.of(TallyDecryptionShare.create(
            guardian.object_id,
            guardian.share_election_public_key().key(),
            contests.get(),
            spoiled_ballots.get()));
  }

  /**
   * Compute a compensated decryptions share for a guardian.
   * <p>
   * @param guardian: The guardian who will partially decrypt the tally
   * @param missing_guardian_id: the missing guardian id to compensate
   * @param tally: The election tally to decrypt
   * @param context: The public election encryption context
   * @return a `TallyDecryptionShare` or `None` if there is an error
   */
  static Optional<CompensatedTallyDecryptionShare> compute_compensated_decryption_share(
          Guardian guardian,
          String missing_guardian_id,
          CiphertextTally tally,
          CiphertextElectionContext context,
          Auxiliary.Decryptor decryptor) {

    Optional<Map<String, CiphertextCompensatedDecryptionContest>> contests =
            compute_compensated_decryption_share_for_cast_contests(
                    guardian, missing_guardian_id, tally, context, decryptor);
    if (contests.isEmpty()) {
      return Optional.empty();
    }

    Optional<Map<String, DecryptionShare.CompensatedBallotDecryptionShare>> spoiled_ballots =
            compute_compensated_decryption_share_for_spoiled_ballots(
                    guardian, missing_guardian_id, tally, context, decryptor);
    if (spoiled_ballots.isEmpty()) {
      return Optional.empty();
    }

    return Optional.of(CompensatedTallyDecryptionShare.create(
            guardian.object_id,
            missing_guardian_id,
            guardian.share_election_public_key().key(),
            contests.get(),
            spoiled_ballots.get()));
  }

  /** Compute the decryption for all of the cast contests in the Ciphertext Tally. */
  static Optional<Map<String, CiphertextDecryptionContest>> compute_decryption_share_for_cast_contests(
          Guardian guardian,
          CiphertextTally tally,
          CiphertextElectionContext context) {

    Map<String, CiphertextDecryptionContest> contests = new HashMap<>();

    for (CiphertextTallyContest contest : tally.cast.values()) {
      Map<String, CiphertextDecryptionSelection> selections = new HashMap<>();

      // [(guardian, selection, context) for (_, selection) in contest.tally_selections.items()],
      List<Callable<Optional<CiphertextDecryptionSelection>>> tasks =
              contest.tally_selections().values().stream().map(selection ->
                      new RunComputeDecryptionShareForSelection(guardian, selection, context)).collect(Collectors.toList());

      Scheduler<Optional<CiphertextDecryptionSelection>> scheduler = new Scheduler<>();
      List<Optional<CiphertextDecryptionSelection>> selection_decryptions = scheduler.schedule(tasks, true);

      // verify the decryptions are received and add them to the collection
      for (Optional<CiphertextDecryptionSelection> decryption : selection_decryptions) {
        if (decryption.isEmpty()) {
          logger.atInfo().log("could not compute share for guardian %s contest %s",
                  guardian.object_id, contest.object_id());
          return Optional.empty();
        }
        selections.put(decryption.get().object_id(), decryption.get());
      }

      contests.put(contest.object_id(), CiphertextDecryptionContest.create(
              contest.object_id(), guardian.object_id, contest.description_hash(), selections));
    }

    return Optional.of(contests);
  }

  /** Compute the compensated decryption for all of the cast contests in the Ciphertext Tally. */
  static Optional<Map<String, CiphertextCompensatedDecryptionContest>> compute_compensated_decryption_share_for_cast_contests(
          Guardian guardian,
          String missing_guardian_id,
          CiphertextTally tally,
          CiphertextElectionContext context,
          Auxiliary.Decryptor decryptor) {

    Map<String, CiphertextCompensatedDecryptionContest> contests = new HashMap<>();

    for (CiphertextTallyContest contest : tally.cast.values()) {
      Map<String, CiphertextCompensatedDecryptionSelection> selections = new HashMap<>();

      // [ (guardian, missing_guardian_id, selection, context, decrypt)
      //          for (_, selection) in contest.tally_selections.items() ],
      List<Callable<Optional<CiphertextCompensatedDecryptionSelection>>> tasks =
              contest.tally_selections().values().stream()
                      .map(selection -> new RunComputeCompensatedDecryptionShareForSelection(
                              guardian, missing_guardian_id, selection, context, decryptor))
                      .collect(Collectors.toList());

      Scheduler<Optional<CiphertextCompensatedDecryptionSelection>> scheduler = new Scheduler<>();
      List<Optional<CiphertextCompensatedDecryptionSelection>>
              selection_decryptions = scheduler.schedule(tasks, true);


      // verify the decryptions are received and add them to the collection
      for (Optional<CiphertextCompensatedDecryptionSelection> decryption : selection_decryptions) {
        if (decryption.isEmpty()) {
          logger.atInfo().log("could not compute share for guardian %s contest %s", guardian.object_id, contest.object_id());
          return Optional.empty();
        }
        selections.put(decryption.get().object_id(), decryption.get());
      }

      contests.put(contest.object_id(), CiphertextCompensatedDecryptionContest.create(
              contest.object_id(),
              guardian.object_id,
              missing_guardian_id,
              contest.description_hash(),
              selections));
    }
    return Optional.of(contests);
  }

  /** Compute the decryption for all spoiled ballots in the Ciphertext Tally. */
  static Optional<Map<String, DecryptionShare.CompensatedBallotDecryptionShare>> compute_compensated_decryption_share_for_spoiled_ballots(
          Guardian guardian,
          String missing_guardian_id,
          CiphertextTally tally,
          CiphertextElectionContext context,
          Auxiliary.Decryptor decrypt) {

    Map<String, DecryptionShare.CompensatedBallotDecryptionShare> spoiled_ballots = new HashMap<>();

    for (Ballot.CiphertextAcceptedBallot spoiled_ballot : tally.spoiled_ballots.values()) {
      Optional<CompensatedBallotDecryptionShare> compensated_ballot =
              compute_compensated_decryption_share_for_ballot(
                      guardian, missing_guardian_id, spoiled_ballot, context, decrypt);

      if (compensated_ballot.isPresent()) {
        spoiled_ballots.put(spoiled_ballot.object_id, compensated_ballot.get());
      } else {
        return Optional.empty();
      }
    }

    return Optional.of(spoiled_ballots);
  }

  /** Compute the decryption for a single ballot. */
  static Optional<CompensatedBallotDecryptionShare> compute_compensated_decryption_share_for_ballot(
          Guardian guardian,
          String missing_guardian_id,
          Ballot.CiphertextAcceptedBallot ballot,
          CiphertextElectionContext context,
          Auxiliary.Decryptor decryptor) {

    Map<String, CiphertextCompensatedDecryptionContest> contests = new HashMap<>();
    for (Ballot.CiphertextBallotContest contest : ballot.contests) {
      Map<String, CiphertextCompensatedDecryptionSelection> selections = new HashMap<>();

      //           [ (guardian, missing_guardian_id, selection, context, decrypt)
      //          for selection in contest.ballot_selections ],
      List<Callable<Optional<CiphertextCompensatedDecryptionSelection>>> tasks =
              contest.ballot_selections.stream()
                      .map(selection -> new RunComputeCompensatedDecryptionShareForSelection(
                              guardian, missing_guardian_id, selection, context, decryptor))
                      .collect(Collectors.toList());

      Scheduler<Optional<CiphertextCompensatedDecryptionSelection>> scheduler = new Scheduler<>();
      List<Optional<CiphertextCompensatedDecryptionSelection>>
              selection_decryptions = scheduler.schedule(tasks, true);

      // verify the decryptions are received and add them to the collection
      for (Optional<CiphertextCompensatedDecryptionSelection> decryption : selection_decryptions) {
        if (decryption.isEmpty()) {
          logger.atInfo().log("could not compute compensated spoiled ballot share for guardian %s missing: %s contest %s",
                  guardian.object_id, missing_guardian_id, contest.object_id);
          return Optional.empty();
        }
        selections.put(decryption.get().object_id(), decryption.get());
      }

      contests.put(contest.object_id, CiphertextCompensatedDecryptionContest.create(
              contest.object_id,
              guardian.object_id,
              missing_guardian_id,
              contest.description_hash,
              selections));
    }
    return Optional.of(CompensatedBallotDecryptionShare.create(
            guardian.object_id,
            missing_guardian_id,
            guardian.share_election_public_key().key(),
            ballot.object_id,
            contests));
  }

  /** Compute the decryption for all spoiled ballots in the Ciphertext Tally. */
  static Optional<Map<String, BallotDecryptionShare>> compute_decryption_share_for_spoiled_ballots(
          Guardian guardian,
          CiphertextTally tally,
          CiphertextElectionContext context) {

    Map<String, BallotDecryptionShare> spoiled_ballots = new HashMap<>();

    for (Ballot.CiphertextAcceptedBallot spoiled_ballot : tally.spoiled_ballots.values()) {
      Optional<BallotDecryptionShare> computed_share = compute_decryption_share_for_ballot(guardian, spoiled_ballot, context);

      if (computed_share.isPresent()) {
        spoiled_ballots.put(spoiled_ballot.object_id, computed_share.get());
      } else {
        return Optional.empty();
      }
    }

    return Optional.of(spoiled_ballots);
  }

  /** Compute the decryption for a single ballot. */
  static Optional<BallotDecryptionShare> compute_decryption_share_for_ballot(
          Guardian guardian,
          Ballot.CiphertextAcceptedBallot ballot,
          CiphertextElectionContext context) {

    Map<String, CiphertextDecryptionContest> contests = new HashMap<>();
    for (Ballot.CiphertextBallotContest contest : ballot.contests) {
      Map<String, CiphertextDecryptionSelection> selections = new HashMap<>();

      // [(guardian, selection, context)for selection in contest.ballot_selections]
      List<Callable<Optional<CiphertextDecryptionSelection>>> tasks =
              contest.ballot_selections.stream().map(selection ->
                      new RunComputeDecryptionShareForSelection(guardian, selection, context)).collect(Collectors.toList());

      Scheduler<Optional<CiphertextDecryptionSelection>> scheduler = new Scheduler<>();
      List<Optional<CiphertextDecryptionSelection>> selection_decryptions = scheduler.schedule(tasks, true);

      // verify the decryptions are received and add them to the collection
      for (Optional<CiphertextDecryptionSelection> decryption : selection_decryptions) {
        if (decryption.isEmpty()) {
          logger.atInfo().log("could not compute spoiled ballot share for guardian %s contest %s",
                  guardian.object_id, contest.object_id);
          return Optional.empty();
        }
        selections.put(decryption.get().object_id(), decryption.get());
      }

      contests.put(contest.object_id, CiphertextDecryptionContest.create(
              contest.object_id,
              guardian.object_id,
              contest.description_hash,
              selections));
    }
    return Optional.of(BallotDecryptionShare.create(
            guardian.object_id,
            guardian.share_election_public_key().key(),
            ballot.object_id,
            contests));
  }

  static class RunComputeDecryptionShareForSelection implements
          Callable<Optional<CiphertextDecryptionSelection>> {
    private final Guardian guardian;
    private final Ballot.CiphertextSelection selection;
    private final CiphertextElectionContext context;

    public RunComputeDecryptionShareForSelection(Guardian guardian, Ballot.CiphertextSelection selection, CiphertextElectionContext context) {
      this.guardian = guardian;
      this.selection = selection;
      this.context = context;
    }

    @Override
    public Optional<CiphertextDecryptionSelection> call() {
      return compute_decryption_share_for_selection(guardian, selection, context);
    }
  }

  /**
   * Compute a partial decryption for a specific selection.
   * @param guardian: The guardian who will partially decrypt the selection
   * @param selection: The specific selection to decrypt
   * @param context: The public election encryption context
   * @return a `CiphertextDecryptionSelection` or `None` if there is an error
   */
  static Optional<CiphertextDecryptionSelection> compute_decryption_share_for_selection(
          Guardian guardian,
          Ballot.CiphertextSelection selection,
          CiphertextElectionContext context) {

    Guardian.Tuple tuple = guardian.partially_decrypt(selection.ciphertext(), context.crypto_extended_base_hash, null);

    if (tuple.proof.is_valid(selection.ciphertext(), guardian.share_election_public_key().key(),
            tuple.decryption, context.crypto_extended_base_hash)) {
      return Optional.of(DecryptionShare.create_ciphertext_decryption_selection(
              selection.object_id,
              guardian.object_id,
              selection.description_hash,
              tuple.decryption,
              Optional.of(tuple.proof),
              Optional.empty()));
    } else {
      logger.atInfo().log("compute decryption share proof failed for %s %s with invalid proof",
              guardian.object_id, selection.object_id);
      return Optional.empty();
    }
  }

  static class RunComputeCompensatedDecryptionShareForSelection implements
          Callable<Optional<CiphertextCompensatedDecryptionSelection>> {
    final Guardian available_guardian;
    final String missing_guardian_id;
    final Ballot.CiphertextSelection selection;
    final CiphertextElectionContext context;
    final Auxiliary.Decryptor decryptor;

    public RunComputeCompensatedDecryptionShareForSelection(Guardian available_guardian,
                                                            String missing_guardian_id,
                                                            Ballot.CiphertextSelection selection,
                                                            CiphertextElectionContext context,
                                                            Auxiliary.Decryptor decryptor) {
      this.available_guardian = available_guardian;
      this.missing_guardian_id = missing_guardian_id;
      this.selection = selection;
      this.context = context;
      this.decryptor = decryptor;
    }

    @Override
    public Optional<CiphertextCompensatedDecryptionSelection> call() {
      return compute_compensated_decryption_share_for_selection(
              available_guardian, missing_guardian_id, selection, context, decryptor);
    }
  }

  /**
   * Compute a compensated decryption share for a specific selection using the
   * available guardian's share of the missing guardian's private key polynomial.
   * <p>
   * @param available_guardian: The available guardian that will partially decrypt the selection
   * @param missing_guardian_id: The id of the guardian that is missing
   * @param selection: The specific selection to decrypt
   * @param context: The public election encryption context
   * @return a `CiphertextCompensatedDecryptionSelection` or `None` if there is an error
   */
  static Optional<CiphertextCompensatedDecryptionSelection> compute_compensated_decryption_share_for_selection(
          Guardian available_guardian,
          String missing_guardian_id,
          Ballot.CiphertextSelection selection,
          CiphertextElectionContext context,
          Auxiliary.Decryptor decryptor) {


    Optional<Guardian.Tuple> compensated = available_guardian.compensate_decrypt(
            missing_guardian_id,
            selection.ciphertext(),
            context.crypto_extended_base_hash,
            null,
            decryptor);
    if (compensated.isEmpty()) {
      logger.atInfo().log("compute compensated decryption share failed for %s missing: %s %s",
              available_guardian.object_id, missing_guardian_id, selection.object_id);
      return Optional.empty();
    }
    Guardian.Tuple tuple = compensated.get();

    Optional<Group.ElementModP> recovery_public_key = available_guardian.recovery_public_key_for(missing_guardian_id);
    if (recovery_public_key.isEmpty()) {
      logger.atInfo().log("compute compensated decryption share failed for %s missing recovery key: %s %s",
              available_guardian.object_id, missing_guardian_id, selection.object_id);
      return Optional.empty();
    }

    if (tuple.proof.is_valid(
            selection.ciphertext(),
            recovery_public_key.get(),
            tuple.decryption,
            context.crypto_extended_base_hash)) {
      CiphertextCompensatedDecryptionSelection share = CiphertextCompensatedDecryptionSelection.create(
              selection.object_id,
              available_guardian.object_id,
              missing_guardian_id,
              selection.description_hash,
              tuple.decryption,
              recovery_public_key.get(),
              tuple.proof);
      return Optional.of(share);
    } else {
      logger.atInfo().log("compute compensated decryption share proof failed for %s missing: %s %s",
              available_guardian.object_id, missing_guardian_id, selection.object_id);
      return Optional.empty();
    }
  }

  /** Produce a Lagrange coefficient for a single Guardian, to be used when reconstructing a missing share. */
  static Group.ElementModQ compute_lagrange_coefficients_for_guardian(
          List<KeyCeremony.PublicKeySet> all_available_guardian_keys, KeyCeremony.PublicKeySet guardian_keys) {

    List<BigInteger> other_guardian_orders = all_available_guardian_keys.stream()
            .filter(g -> !g.owner_id().equals(guardian_keys.owner_id()))
            .map(g -> BigInteger.valueOf(g.sequence_order())).collect(Collectors.toList());

    return ElectionPolynomial.compute_lagrange_coefficient(
            BigInteger.valueOf(guardian_keys.sequence_order()),
            other_guardian_orders);
  }

  /** Produce all Lagrange coefficients for a collection of available Guardians, used when reconstructing a missing share. */
  static Map<String, Group.ElementModQ> compute_lagrange_coefficients_for_guardians(
          List<KeyCeremony.PublicKeySet> all_available_guardian_keys) {

    Map<String, Group.ElementModQ> result = new HashMap<>();
    all_available_guardian_keys
            .forEach(g -> result.put(g.owner_id(),
                    compute_lagrange_coefficients_for_guardian(all_available_guardian_keys, g)));
    return result;
  }

  /** Use all available guardians to reconstruct the missing shares for all missing guardians. */
  static Optional<Map<String, TallyDecryptionShare>> reconstruct_missing_tally_decryption_shares(
          CiphertextTally ciphertext_tally,
          Map<String, KeyCeremony.ElectionPublicKey> missing_guardians,
          Map<String, Map<String, CompensatedTallyDecryptionShare>> compensated_shares,
          Map<String, Map<String, Group.ElementModQ>> lagrange_coefficients) {

    Map<String, TallyDecryptionShare> reconstructed_shares = new HashMap<>();
    for (Map.Entry<String, Map<String, CompensatedTallyDecryptionShare>> entry : compensated_shares.entrySet()) {
      String missing_guardian_id = entry.getKey();
      Map<String, CompensatedTallyDecryptionShare> shares = entry.getValue();

      // Make sure there is a public key
      KeyCeremony.ElectionPublicKey public_key = missing_guardians.get(missing_guardian_id);
      if (public_key == null) {
        logger.atInfo().log("Could not reconstruct tally for %s with no public key", missing_guardian_id);
        return Optional.empty();
      }

      // make sure there are computed lagrange coefficients:
      Map<String, Group.ElementModQ> lagrange_coefficients_for_missing = lagrange_coefficients.get(missing_guardian_id);
      if (lagrange_coefficients_for_missing == null) {
        lagrange_coefficients_for_missing = new HashMap<>();
      }

      //         if not any(lagrange_coefficients):
      boolean haveAny = lagrange_coefficients.values().stream().anyMatch(Objects::nonNull);
      if (!haveAny) {
        logger.atInfo().log("Could not reconstruct tally for %s with no lagrange coefficients", missing_guardian_id);
        return Optional.empty();
      }

      // iterate through the tallies and accumulate all of the shares for this guardian
      Map<String, CiphertextDecryptionContest> contests = reconstruct_decryption_contests(
              missing_guardian_id,
              ciphertext_tally.cast,
              shares,
              lagrange_coefficients_for_missing);

      // iterate through the spoiled ballots and accumulate all of the shares for this guardian
      Map<String, BallotDecryptionShare> spoiled_ballots = reconstruct_decryption_ballots(
              missing_guardian_id,
              public_key,
              ciphertext_tally.spoiled_ballots,
              shares,
              lagrange_coefficients_for_missing);

      reconstructed_shares.put(
              missing_guardian_id,
              TallyDecryptionShare.create(missing_guardian_id, public_key.key(), contests, spoiled_ballots));
    }

    return Optional.of(reconstructed_shares);
  }

  /**
   * Reconstruct the missing Decryption Share for a missing guardian
   * from the collection of compensated decryption shares.
   * <p>
   * @param missing_guardian_id: The guardian id for the missing guardian
   * @param cast_tally: The collection of `CiphertextTallyContest` that is cast
   * @param shares: the collection of `CompensatedTallyDecryptionShare` for the missing guardian
   * @param lagrange_coefficients: the lagrange coefficients corresponding to the available guardians that provided shares
   */
  static Map<String, CiphertextDecryptionContest> reconstruct_decryption_contests(
          String missing_guardian_id,
          Map<String, CiphertextTallyContest> cast_tally,
          Map<String, CompensatedTallyDecryptionShare> shares,
          Map<String, Group.ElementModQ> lagrange_coefficients) {

    // iterate through the tallies and accumulate all of the shares for this guardian
    Map<String, CiphertextDecryptionContest> contests = new HashMap<>();
    for (Map.Entry<String, CiphertextTallyContest> entry : cast_tally.entrySet()) {
      String contest_id = entry.getKey();
      CiphertextTallyContest tally_contest = entry.getValue();

      Map<String, CiphertextCompensatedDecryptionContest> contest_shares = new HashMap<>();
      for (Map.Entry<String, CompensatedTallyDecryptionShare> entry2 : shares.entrySet()) {
        String available_guardian_id = entry2.getKey();
        CompensatedTallyDecryptionShare compensated_share = entry2.getValue();
        contest_shares.put(available_guardian_id, compensated_share.contests().get(tally_contest.object_id()));
      }

      Map<String, CiphertextDecryptionSelection> selections = new HashMap<>();
      for (Map.Entry<String, CiphertextTallySelection> entry3 : tally_contest.tally_selections().entrySet()) {
        String selection_id = entry3.getKey();
        CiphertextTallySelection tally_selection = entry3.getValue();

        // collect all of the shares generated for each selection
        Map<String, CiphertextCompensatedDecryptionSelection> compensated_selection_shares = new HashMap<>();
        for (Map.Entry<String, CiphertextCompensatedDecryptionContest> entry4 : contest_shares.entrySet()) {
          String available_guardian_id = entry4.getKey();
          CiphertextCompensatedDecryptionContest compensated_contest = entry4.getValue();
          compensated_selection_shares.put(available_guardian_id, compensated_contest.selections().get(selection_id));
        }

        List<Group.ElementModP> share_pow_p = new ArrayList<>();
        for (Map.Entry<String, CiphertextCompensatedDecryptionSelection> entry5 : compensated_selection_shares.entrySet()) {
          String available_guardian_id = entry5.getKey();
          CiphertextCompensatedDecryptionSelection share = entry5.getValue();
          share_pow_p.add(Group.pow_p(share.share(), lagrange_coefficients.get(available_guardian_id)));
        }

        Group.ElementModP reconstructed_share = Group.mult_p(share_pow_p);

        selections.put(selection_id, create_ciphertext_decryption_selection(
                selection_id,
                missing_guardian_id,
                tally_selection.description_hash,
                reconstructed_share,
                Optional.empty(),
                Optional.of(compensated_selection_shares)));
      }
      contests.put(contest_id, CiphertextDecryptionContest.create(
              contest_id,
              missing_guardian_id,
              tally_contest.description_hash(),
              selections));
    }

    return contests;
  }

  /**
   * Reconstruct the missing Decryption shares for a missing guardian from the collection of compensated decryption shares.
   * <p>
   * @param missing_guardian_id: The guardian id for the missing guardian
   * @param public_key: the public key for the missing guardian
   * @param spoiled_ballots: The collection of `CiphertextAcceptedBallot` that is spoiled
   * @param shares: the collection of `CompensatedTallyDecryptionShare` for the missing guardian
   * @param lagrange_coefficients: the lagrange coefficients corresponding to the available guardians that provided shares
   */
  static Map<String, BallotDecryptionShare> reconstruct_decryption_ballots(
          String missing_guardian_id,
          KeyCeremony.ElectionPublicKey public_key,
          Map<String, Ballot.CiphertextAcceptedBallot> spoiled_ballots,
          Map<String, CompensatedTallyDecryptionShare> shares,
          Map<String, Group.ElementModQ> lagrange_coefficients) {

    Map<String, BallotDecryptionShare> spoiled_ballot_shares = new HashMap<>();
    for (Map.Entry<String, Ballot.CiphertextAcceptedBallot> entry : spoiled_ballots.entrySet()) {
      String ballot_id = entry.getKey();
      Ballot.CiphertextAcceptedBallot spoiled_ballot = entry.getValue();

      // iterate through the tallies and accumulate all of the shares for this guardian
      Map<String, CompensatedBallotDecryptionShare> shares_for_ballot = new HashMap<>();
      for (Map.Entry<String, CompensatedTallyDecryptionShare> entry2 : shares.entrySet()) {
        String available_guardian_id = entry2.getKey();
        CompensatedTallyDecryptionShare compensated_share = entry2.getValue();

        for (Map.Entry<String, CompensatedBallotDecryptionShare> entry3 : compensated_share.spoiled_ballots().entrySet()) {
          String compensated_ballot_id = entry3.getKey();
          CompensatedBallotDecryptionShare compensated_ballot = entry3.getValue();
          if (compensated_ballot_id.equals(ballot_id)) {
            shares_for_ballot.put(available_guardian_id, compensated_ballot);
          }
        }
      }

      BallotDecryptionShare ballot_share = reconstruct_decryption_ballot(
              missing_guardian_id,
              public_key,
              spoiled_ballot,
              shares_for_ballot,
              lagrange_coefficients);

      spoiled_ballot_shares.put(spoiled_ballot.object_id, ballot_share);
    }
    return spoiled_ballot_shares;
  }

  /**
   * Reconstruct a missing ballot Decryption share for a missing guardian from the collection of compensated decryption shares.
   *
   * @param missing_guardian_id   The guardian id for the missing guardian
   * @param public_key            the public key for the missing guardian
   * @param ballot                The `CiphertextAcceptedBallot` to reconstruct
   * @param shares                the collection of `CompensatedBallotDecryptionShare` for the missing guardian, each keyed by the ID of the guardian that produced it
   * @param lagrange_coefficients the lagrange coefficients corresponding to the available guardians that provided shares
   */
  static BallotDecryptionShare reconstruct_decryption_ballot(
          String missing_guardian_id,
          KeyCeremony.ElectionPublicKey public_key,
          Ballot.CiphertextAcceptedBallot ballot,
          Map<String, CompensatedBallotDecryptionShare> shares,
          Map<String, Group.ElementModQ> lagrange_coefficients) {

    Map<String, CiphertextDecryptionContest> contests = new HashMap<>();
    for (Ballot.CiphertextBallotContest contest : ballot.contests) {

      Map<String, CiphertextCompensatedDecryptionContest> contest_shares = new HashMap<>();
      for (Map.Entry<String, CompensatedBallotDecryptionShare> entry : shares.entrySet()) {
        String available_guardian_id = entry.getKey();
        CompensatedBallotDecryptionShare compensated_ballot = entry.getValue();
        contest_shares.put(available_guardian_id, compensated_ballot.contests().get(contest.object_id));
      }

      Map<String, CiphertextDecryptionSelection> selections = new HashMap<>();
      for (Ballot.CiphertextBallotSelection selection : contest.ballot_selections) {

        Map<String, CiphertextCompensatedDecryptionSelection> compensated_selection_shares = new HashMap<>();
        for (Map.Entry<String, CiphertextCompensatedDecryptionContest> entry : contest_shares.entrySet()) {
          String available_guardian_id = entry.getKey();
          CiphertextCompensatedDecryptionContest compensated_contest = entry.getValue();
          compensated_selection_shares.put(available_guardian_id, compensated_contest.selections().get(selection.object_id));
        }

        // compute the reconstructed share
        List<Group.ElementModP> ps = new ArrayList<>();
        for (Map.Entry<String, CiphertextCompensatedDecryptionSelection> entry : compensated_selection_shares.entrySet()) {
          String available_guardian_id = entry.getKey();
          CiphertextCompensatedDecryptionSelection share = entry.getValue();
          ps.add(Group.pow_p(share.share(), lagrange_coefficients.get(available_guardian_id)));
        }
        Group.ElementModP reconstructed_share = Group.mult_p(ps);

        selections.put(selection.object_id, DecryptionShare.create_ciphertext_decryption_selection(
                selection.object_id,
                missing_guardian_id,
                selection.description_hash,
                reconstructed_share,
                Optional.empty(),
                Optional.of(compensated_selection_shares)));
      }

      contests.put(contest.object_id, CiphertextDecryptionContest.create(
              contest.object_id,
              missing_guardian_id,
              contest.description_hash,
              selections));
    }

    return BallotDecryptionShare.create(
            missing_guardian_id,
            public_key.key(),
            ballot.object_id,
            contests);
  }

}

