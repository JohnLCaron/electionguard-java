package com.sunya.electionguard.standard;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.collect.Streams;
import com.google.common.flogger.FluentLogger;
import com.sunya.electionguard.BallotBox;
import com.sunya.electionguard.ChaumPedersen;
import com.sunya.electionguard.CiphertextBallot;
import com.sunya.electionguard.CiphertextContest;
import com.sunya.electionguard.CiphertextElectionContext;
import com.sunya.electionguard.CiphertextSelection;
import com.sunya.electionguard.CiphertextTally;
import com.sunya.electionguard.DecryptionShare;
import com.sunya.electionguard.ElGamal;
import com.sunya.electionguard.ElectionPolynomial;
import com.sunya.electionguard.Group;
import com.sunya.electionguard.Scheduler;
import com.sunya.electionguard.SubmittedBallot;

import javax.annotation.Nullable;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;

import static com.sunya.electionguard.DecryptionShare.CiphertextCompensatedDecryptionContest;
import static com.sunya.electionguard.DecryptionShare.CiphertextCompensatedDecryptionSelection;
import static com.sunya.electionguard.DecryptionShare.CiphertextDecryptionContest;
import static com.sunya.electionguard.DecryptionShare.CiphertextDecryptionSelection;
import static com.sunya.electionguard.DecryptionShare.CompensatedDecryptionShare;
import static com.sunya.electionguard.DecryptionShare.create_ciphertext_decryption_selection;

/** Static methods for decryption. */
public class Decryptions {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  ////////////////////////////////////////////////////////////////////////////
  // decryption shares

  /**
   * Compute a decryption share for a guardian. Parallizable over each of the tally's contests.
   * <p>
   * @param guardian_keys: The guardian's election key pair
   * @param tally: The election tally to decrypt
   * @param context: The public election encryption context
   */
  public static Optional<DecryptionShare> compute_decryption_share(
          KeyCeremony.ElectionKeyPair guardian_keys,
          CiphertextTally tally,
          CiphertextElectionContext context) {

    Map<String, CiphertextDecryptionContest> contests = new HashMap<>();
    for (CiphertextTally.Contest tallyContest : tally.contests.values()) {
      Optional<CiphertextDecryptionContest> contest_share =
              compute_decryption_share_for_contest(guardian_keys,
                      CiphertextContest.createFrom(tallyContest),
                      context);
      if (contest_share.isEmpty()) {
        return Optional.empty();
      }
      contests.put(tallyContest.object_id(), contest_share.get());
    }

    return Optional.of(new DecryptionShare(
            tally.object_id(),
            guardian_keys.owner_id(),
            guardian_keys.share().key(),
            contests));
  }

  /** Compute the DecryptionShare for a list of ballots for a guardian. */
  static Optional<Map<String, DecryptionShare>> compute_decryption_share_for_ballots(
          KeyCeremony.ElectionKeyPair guardian_keys,
          Iterable<SubmittedBallot> ballots,
          CiphertextElectionContext context) {

  Map<String, DecryptionShare> shares = new HashMap<>();

    for (SubmittedBallot ballot : ballots) {
      Optional<DecryptionShare> ballot_share = compute_decryption_share_for_ballot(
              guardian_keys, ballot, context);
      if (ballot_share.isEmpty()) {
        return Optional.empty();
      }
      shares.put(ballot.object_id(), ballot_share.get());
    }

    return Optional.of(shares);
  }

  /** Compute the DecryptionShare for a single ballot for a guardian. */
  public static Optional<DecryptionShare> compute_decryption_share_for_ballot(
          KeyCeremony.ElectionKeyPair guardian_keys,
          SubmittedBallot ballot,
          CiphertextElectionContext context) {

    // Map(CONTEST_ID, CiphertextDecryptionContest)
    Map<String, CiphertextDecryptionContest> contests = new HashMap<>();

    for (CiphertextBallot.Contest contest : ballot.contests) {
      Optional<CiphertextDecryptionContest> contest_share = compute_decryption_share_for_contest(
              guardian_keys,
              CiphertextContest.createFrom(contest),
              context);
        if (contest_share.isEmpty()) {
          logger.atInfo().log("could not compute ballot share for guardian %s contest %s",
                  guardian_keys.owner_id(), contest.object_id);
          return Optional.empty();
        }
        contests.put(contest.object_id, contest_share.get());
    }
    return Optional.of(new DecryptionShare(
            ballot.object_id(),
            guardian_keys.owner_id(),
            guardian_keys.key_pair().public_key,
            contests));
  }

  /** Compute the decryption share for a single contest. */
  private static Optional<CiphertextDecryptionContest> compute_decryption_share_for_contest(
          KeyCeremony.ElectionKeyPair guardian_keys,
          CiphertextContest ciphertextContest,
          CiphertextElectionContext context) {

    Map<String, CiphertextDecryptionSelection> selections = new HashMap<>();

    List<Callable<Optional<CiphertextDecryptionSelection>>> tasks =
            Streams.stream(ciphertextContest.selections).map(selection ->
                    new RunComputeDecryptionShareForSelection(guardian_keys, selection, context)).collect(Collectors.toList());

    Scheduler<Optional<CiphertextDecryptionSelection>> scheduler = new Scheduler<>();
    List<Optional<CiphertextDecryptionSelection>> selection_decryptions = scheduler.schedule(tasks, true);

    // verify the decryptions are received and add them to the collection
    for (Optional<CiphertextDecryptionSelection> decryption : selection_decryptions) {
      if (decryption.isEmpty()) {
        logger.atWarning().log("could not compute share for guardian %s contest %s",
                guardian_keys.owner_id(), ciphertextContest.object_id);
        return Optional.empty();
      }
      selections.put(decryption.get().object_id(), decryption.get());
    }

    return Optional.of(CiphertextDecryptionContest.create(
            ciphertextContest.object_id, guardian_keys.owner_id(), ciphertextContest.description_hash, selections));
  }

  private static class RunComputeDecryptionShareForSelection implements Callable<Optional<CiphertextDecryptionSelection>> {
    KeyCeremony.ElectionKeyPair guardian_keys;
    private final CiphertextSelection selection;
    private final CiphertextElectionContext context;

    RunComputeDecryptionShareForSelection(KeyCeremony.ElectionKeyPair guardian_keys, CiphertextSelection selection, CiphertextElectionContext context) {
      this.guardian_keys = Preconditions.checkNotNull(guardian_keys);
      this.selection = Preconditions.checkNotNull(selection);
      this.context = Preconditions.checkNotNull(context);
    }

    @Override
    public Optional<CiphertextDecryptionSelection> call() {
      return compute_decryption_share_for_selection(guardian_keys, selection, context);
    }
  }

  /**
   * Compute a partial decryption for a specific selection and guardian.
   * @param guardian_keys: Election keys for the guardian who will partially decrypt the selection
   * @param selection: The specific selection to decrypt
   * @param context: The public election encryption context
   * @return a `CiphertextDecryptionSelection` or `None` if there is an error
   */
  @VisibleForTesting
  public static Optional<CiphertextDecryptionSelection> compute_decryption_share_for_selection(
          KeyCeremony.ElectionKeyPair guardian_keys,
          CiphertextSelection selection,
          CiphertextElectionContext context) {

    try {
      BallotBox.DecryptionProofTuple tuple =
              partially_decrypt(guardian_keys, selection.ciphertext(), context.crypto_extended_base_hash, null);

      if (tuple.proof.is_valid(selection.ciphertext(), guardian_keys.key_pair().public_key,
              tuple.decryption, context.crypto_extended_base_hash)) {
        return Optional.of(DecryptionShare.create_ciphertext_decryption_selection(
                selection.object_id(),
                guardian_keys.owner_id(),
                tuple.decryption,
                Optional.of(tuple.proof),
                Optional.empty()));
      } else {
        logger.atWarning().log("compute decryption share proof failed for %s %s with invalid proof",
                guardian_keys.owner_id(), selection.object_id());
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
   * @param guardian_key: Guardian's election public key
   * @param missing_guardian_key: Missing guardian's election public key
   * @param missing_guardian_backup: Missing guardian's election partial key backup
   * @param tally: The election tally to decrypt
   * @param context: The public election encryption context
   * @return guardian's compensated decryption share of tally for the missing guardian
   */
  public static Optional<CompensatedDecryptionShare> compute_compensated_decryption_share(
          KeyCeremony.ElectionPublicKey guardian_key,
          KeyCeremony.ElectionPublicKey missing_guardian_key,
          KeyCeremony.ElectionPartialKeyBackup missing_guardian_backup,
          CiphertextTally tally,
          CiphertextElectionContext context) {

    Map<String, CiphertextCompensatedDecryptionContest> contests = new HashMap<>();

    for (CiphertextTally.Contest contest : tally.contests.values()) {
      Optional<CiphertextCompensatedDecryptionContest> dcontest = compute_compensated_decryption_share_for_contest(
              guardian_key,
              missing_guardian_key,
              missing_guardian_backup,
              CiphertextContest.createFrom(contest),
              context);
      if (dcontest.isEmpty()) {
        return Optional.empty();
      }
      contests.put(contest.object_id(), dcontest.get());
    }

    return Optional.of(new CompensatedDecryptionShare(
            tally.object_id(),
            guardian_key.owner_id(),
            missing_guardian_key.owner_id(),
            guardian_key.key(),
            contests));
  }

  /**
   * Compute the compensated decryption share for a single contest.
   */
  private static Optional<CiphertextCompensatedDecryptionContest>
  compute_compensated_decryption_share_for_contest(
          KeyCeremony.ElectionPublicKey guardian_key,
          KeyCeremony.ElectionPublicKey missing_guardian_key,
          KeyCeremony.ElectionPartialKeyBackup missing_guardian_backup,
          CiphertextContest contest,
          CiphertextElectionContext context) {

      Map<String, CiphertextCompensatedDecryptionSelection> selections = new HashMap<>();

      List<Callable<Optional<CiphertextCompensatedDecryptionSelection>>> tasks =
          Streams.stream(contest.selections)
                  .map(selection -> new RunComputeCompensatedDecryptionShareForSelection(
                          guardian_key, missing_guardian_key, missing_guardian_backup, selection, context))
                  .collect(Collectors.toList());

      Scheduler<Optional<CiphertextCompensatedDecryptionSelection>> scheduler = new Scheduler<>();
      List<Optional<CiphertextCompensatedDecryptionSelection>>
              selection_decryptions = scheduler.schedule(tasks, true);

      // verify the decryptions are received and add them to the collection
      for (Optional<CiphertextCompensatedDecryptionSelection> decryption : selection_decryptions) {
        if (decryption.isEmpty()) {
          logger.atWarning().log("could not compute share for missing guardian %s contest %s", 
                  missing_guardian_key.owner_id(), contest.object_id);
          return Optional.empty();
        }
        selections.put(decryption.get().object_id(), decryption.get());
      }

      return Optional.of(CiphertextCompensatedDecryptionContest.create(
              contest.object_id,
              guardian_key.owner_id(),
              missing_guardian_key.owner_id(),
              contest.description_hash,
              selections));
  }

  /**
   * Compute the compensated decryption for the given ballots, for a specific guardian.
   * @return Map(BALLOT_ID, CompensatedDecryptionShare)
   */
  static Optional<Map<String, CompensatedDecryptionShare>> compute_compensated_decryption_share_for_ballots(
          KeyCeremony.ElectionPublicKey guardian_key,
          KeyCeremony.ElectionPublicKey missing_guardian_key,
          KeyCeremony.ElectionPartialKeyBackup missing_guardian_backup,
          Iterable<SubmittedBallot> ballots,
          CiphertextElectionContext context) {

    Map<String, CompensatedDecryptionShare> decrypted_ballots = new HashMap<>();

    for (SubmittedBallot spoiled_ballot : ballots) {
      Optional<CompensatedDecryptionShare> compensated_ballot = compute_compensated_decryption_share_for_ballot(
              guardian_key,
              missing_guardian_key,
              missing_guardian_backup,
              spoiled_ballot,
              context);

      if (compensated_ballot.isPresent()) {
        decrypted_ballots.put(spoiled_ballot.object_id(), compensated_ballot.get());
      } else {
        return Optional.empty();
      }
    }

    return Optional.of(decrypted_ballots);
  }

  /** Compute the compensated decryption for a single ballot. */
  public static Optional<CompensatedDecryptionShare> compute_compensated_decryption_share_for_ballot(
          KeyCeremony.ElectionPublicKey guardian_key,
          KeyCeremony.ElectionPublicKey missing_guardian_key,
          KeyCeremony.ElectionPartialKeyBackup missing_guardian_backup,
          SubmittedBallot ballot,
          CiphertextElectionContext context) {

    Map<String, CiphertextCompensatedDecryptionContest> contests = new HashMap<>();

    for (CiphertextBallot.Contest contest : ballot.contests) {
      Optional<CiphertextCompensatedDecryptionContest> contest_share =
              compute_compensated_decryption_share_for_contest(
                      guardian_key,
                      missing_guardian_key,
                      missing_guardian_backup,
                      CiphertextContest.createFrom(contest),
                      context);
         if (contest_share.isEmpty()) {
          logger.atWarning().log("could not compute compensated spoiled ballot share for guardian %s missing: %s contest %s",
                  guardian_key.owner_id(), missing_guardian_key.owner_id(), contest.object_id);
          return Optional.empty();
        }
      contests.put(contest.object_id, contest_share.get());
    }

    return Optional.of(new CompensatedDecryptionShare(
            ballot.object_id(),
            guardian_key.owner_id(),
            missing_guardian_key.owner_id(),
            guardian_key.key(),
            contests));
  }

  private static class RunComputeCompensatedDecryptionShareForSelection implements
          Callable<Optional<CiphertextCompensatedDecryptionSelection>> {
    final KeyCeremony.ElectionPublicKey guardian_key;
    final KeyCeremony.ElectionPublicKey missing_guardian_key;
    final KeyCeremony.ElectionPartialKeyBackup missing_guardian_backup;
    final CiphertextSelection selection;
    final CiphertextElectionContext context;

    RunComputeCompensatedDecryptionShareForSelection(
            KeyCeremony.ElectionPublicKey guardian_key,
            KeyCeremony.ElectionPublicKey missing_guardian_key,
            KeyCeremony.ElectionPartialKeyBackup missing_guardian_backup,
            CiphertextSelection selection,
            CiphertextElectionContext context) {
      this.guardian_key = guardian_key;
      this.missing_guardian_key = missing_guardian_key;
      this.missing_guardian_backup = missing_guardian_backup;
      this.selection = selection;
      this.context = context;
    }

    @Override
    public Optional<CiphertextCompensatedDecryptionSelection> call() {
      return compute_compensated_decryption_share_for_selection(
              guardian_key, missing_guardian_key, missing_guardian_backup, selection, context);
    }
  }

  /**
   * Compute a compensated decryption share for a specific selection using the
   * available guardians' share of the missing guardian's private key polynomial.
   * <p>
   * @param guardian_key: Guardian's election public key
   * @param missing_guardian_key: Missing guardian's election public key
   * @param missing_guardian_backup: Missing guardian's election partial key backup
   * @param selection: The specific selection to decrypt
   * @param context: The public election encryption context
   * @return a `CiphertextCompensatedDecryptionSelection` or `None` if there is an error
   */
  @VisibleForTesting
  public static Optional<CiphertextCompensatedDecryptionSelection> compute_compensated_decryption_share_for_selection(
          KeyCeremony.ElectionPublicKey guardian_key,
          KeyCeremony.ElectionPublicKey missing_guardian_key,
          KeyCeremony.ElectionPartialKeyBackup missing_guardian_backup,
          CiphertextSelection selection,
          CiphertextElectionContext context) {

    Optional<BallotBox.DecryptionProofTuple> compensated = compensate_decrypt(
            missing_guardian_backup,
            selection.ciphertext(),
            context.crypto_extended_base_hash,
            null);
    if (compensated.isEmpty()) {
      logger.atWarning().log("compute compensated decryption share failed for %s missing: %s %s",
              guardian_key.owner_id(), missing_guardian_key.owner_id(), selection.object_id());
      return Optional.empty();
    }
    BallotBox.DecryptionProofTuple tuple = compensated.get();

    Group.ElementModP recovery_public_key = compute_recovery_public_key(guardian_key, missing_guardian_key);

    if (tuple.proof.is_valid(
            selection.ciphertext(),
            recovery_public_key,
            tuple.decryption,
            context.crypto_extended_base_hash)) {

      CiphertextCompensatedDecryptionSelection share = CiphertextCompensatedDecryptionSelection.create(
              selection.object_id(),
              guardian_key.owner_id(),
              missing_guardian_key.owner_id(),
              tuple.decryption,
              recovery_public_key,
              tuple.proof);
      return Optional.of(share);
    } else {
      logger.atWarning().log("compute compensated decryption share proof failed for %s missing: %s %s",
              guardian_key.owner_id(), missing_guardian_key.owner_id(), selection.object_id());
      return Optional.empty();
    }
  }

  ////////////////////////////////////////////////////////////////////////////////////////////////

  /** Produce all Lagrange coefficients for a collection of available Guardians, used when reconstructing a missing share. */
  public static Map<String, Group.ElementModQ> compute_lagrange_coefficients_for_guardians(
          List<KeyCeremony.ElectionPublicKey> available_guardians_keys) {

    Map<String, Group.ElementModQ> result = new HashMap<>();
    available_guardians_keys.forEach(g -> result.put(g.owner_id(),
                    compute_lagrange_coefficients_for_guardian(g, available_guardians_keys)));
    return result;
  }

  /** Produce a Lagrange coefficient for a single Guardian, to be used when reconstructing a missing share. */
  private static Group.ElementModQ compute_lagrange_coefficients_for_guardian(
          KeyCeremony.ElectionPublicKey guardian_key,
          List<KeyCeremony.ElectionPublicKey> other_guardians_keys) {

    List<Integer> other_guardian_orders = other_guardians_keys.stream()
            .filter(g -> !g.owner_id().equals(guardian_key.owner_id()))
            .map(g -> g.sequence_order()).collect(Collectors.toList());

    return ElectionPolynomial.compute_lagrange_coefficient(guardian_key.sequence_order(), other_guardian_orders);
  }
  
  ////////////////////////////////////////////////////////////////////////////////////////////////

  /**
   * Compute a partial decryption of an elgamal encryption
   *
   * @param elgamal: the `ElGamalCiphertext` that will be partially decrypted
   * @param extended_base_hash: the extended base hash of the election that was used to generate t he ElGamal Ciphertext
   * @param nonce_seed: an optional value used to generate the `ChaumPedersenProof` if no value is provided, a random number will be used.
   * @return: a `Tuple[ElementModP, ChaumPedersenProof]` of the decryption and its proof
   */
  static BallotBox.DecryptionProofTuple partially_decrypt(
          KeyCeremony.ElectionKeyPair guardian_keys,
          ElGamal.Ciphertext elgamal,
          Group.ElementModQ extended_base_hash,
          @Nullable Group.ElementModQ nonce_seed) {

    if (nonce_seed == null) {
      nonce_seed = Group.rand_q();
    }

    // TODO: ISSUE #47: Decrypt the election secret key

    // 𝑀_i = 𝐴^𝑠𝑖 mod 𝑝
    Group.ElementModP partial_decryption = elgamal.partial_decrypt(guardian_keys.key_pair().secret_key);

    // 𝑀_i = 𝐴^𝑠𝑖 mod 𝑝 and 𝐾𝑖 = 𝑔^𝑠𝑖 mod 𝑝
    ChaumPedersen.ChaumPedersenProof proof = ChaumPedersen.make_chaum_pedersen(
            elgamal,
            guardian_keys.key_pair().secret_key,
            partial_decryption,
            nonce_seed,
            extended_base_hash
            );

    return new BallotBox.DecryptionProofTuple(partial_decryption, proof);
  }


  /**
   * Compute a compensated partial decryption of an elgamal encryption on behalf of the missing guardian
   *
   * @param missing_guardian_backup Missing guardians backup
   * @param ciphertext              the ElGamal.Ciphertext that will be partially decrypted
   * @param extended_base_hash      the extended base hash of the election that was used to generate t he ElGamal Ciphertext
   * @param nonce_seed              an optional value used to generate the `ChaumPedersenProof` if no value is provided, a random number will be used.
   * @return a `Tuple[ElementModP, ChaumPedersenProof]` of the decryption and its proof
   */
  static Optional<BallotBox.DecryptionProofTuple> compensate_decrypt(
          KeyCeremony.ElectionPartialKeyBackup missing_guardian_backup,
          ElGamal.Ciphertext ciphertext,
          Group.ElementModQ extended_base_hash,
          @Nullable Group.ElementModQ nonce_seed) {

    if (nonce_seed == null) {
      nonce_seed = Group.rand_q();
    }

    Group.ElementModQ partial_secret_key = missing_guardian_backup.value();

    // 𝑀_{𝑖,l} = 𝐴^P𝑖_{l}
    Group.ElementModP partial_decryption = ciphertext.partial_decrypt(partial_secret_key);

    // 𝑀_{𝑖,l} = 𝐴^𝑠𝑖 mod 𝑝 and 𝐾𝑖 = 𝑔^𝑠𝑖 mod 𝑝
    ChaumPedersen.ChaumPedersenProof proof = ChaumPedersen.make_chaum_pedersen(
            ciphertext,
            partial_secret_key,
            partial_decryption,
            nonce_seed,
            extended_base_hash);

    /* System.out.printf("ChaumPedersen.ChaumPedersenProof proof %s valid = %s%n", proof,
            proof.is_valid(ciphertext,
                    recovery_public_key,
                    partial_decryption,
                    extended_base_hash)); */

    return Optional.of(new BallotBox.DecryptionProofTuple(partial_decryption, proof));
  }

  /**
   * Compute the recovery public key,
   * corresponding to the secret share Pi(l)
   * K_ij^(l^j) for j in 0..k-1.  K_ij is coefficients[j].public_key
   */
  @VisibleForTesting
  public static Group.ElementModP compute_recovery_public_key(
          KeyCeremony.ElectionPublicKey guardian_key,
          KeyCeremony.ElectionPublicKey missing_guardian_key) {

    int index = 0;
    Group.ElementModP pub_key = Group.ONE_MOD_P;
    for (Group.ElementModP commitment : missing_guardian_key.coefficient_commitments()) {
      Group.ElementModQ exponent = Group.pow_q(BigInteger.valueOf(guardian_key.sequence_order()), BigInteger.valueOf(index));
      pub_key = Group.mult_p(pub_key, Group.pow_p(commitment, exponent));
      index++;
    }
    
    return pub_key;
  }


  ///////////////////////////////////////////////////////////////////////////////////////////////

  /**
   * Reconstruct the missing Decryption Share for a missing guardian
   * from the collection of compensated decryption shares.
   */
  public static DecryptionShare reconstruct_decryption_share(
          KeyCeremony.ElectionPublicKey missing_guardian_key,
          CiphertextTally tally,
          Map<String, CompensatedDecryptionShare> shares, // Map(GUARDIAN_ID, CompensatedDecryptionShare)
          Map<String, Group.ElementModQ> lagrange_coefficients) {

    Map<String, CiphertextDecryptionContest> contests = new HashMap<>();
    for (CiphertextTally.Contest contest : tally.contests.values()) {
      CiphertextDecryptionContest dcontest = reconstruct_decryption_contest(
              missing_guardian_key.owner_id(),
              CiphertextContest.createFrom(contest),
              shares,
              lagrange_coefficients);
      contests.put(contest.object_id(), dcontest);
    }

    return new DecryptionShare(tally.object_id(), missing_guardian_key.owner_id(), missing_guardian_key.key(), contests);
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
          share_pow_p.add(Group.pow_p(share.share(), c));
        }

        // product M_il^w_l
        Group.ElementModP reconstructed_share = Group.mult_p(share_pow_p);

        selections.put(selection.object_id(), create_ciphertext_decryption_selection(
                selection.object_id(),
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
   * Reconstruct the missing Decryption shares for a missing guardian from the collection of compensated decryption shares.
   * <p>
   * @param public_key: the public key for the missing guardian
   * @param ballots: The collection of `SubmittedBallot` that are spoiled
   * @param shares: the collection of CompensatedDecryptionShare's for each ballot, for each missing LOOK or available? guardian
   * @param lagrange_coefficients: the lagrange coefficients corresponding to the available guardians that provided shares
   */
  // Map(BALLOT_ID, DecryptionShare)
  static Map<String, DecryptionShare> reconstruct_decryption_shares_for_ballots(
          KeyCeremony.ElectionPublicKey public_key,
          Iterable<SubmittedBallot> ballots,
          Map<String, Map<String, CompensatedDecryptionShare>> shares, // Map(BALLOT_ID, Map(available_guardian, CompensatedDecryptionShare))
          Map<String, Group.ElementModQ> lagrange_coefficients) { // Map(available_guardian, ElementModQ)

    Map<String, DecryptionShare> result = new HashMap<>();
    for (SubmittedBallot ballot : ballots) {
      Preconditions.checkArgument(shares.containsKey(ballot.object_id()));
      DecryptionShare ballot_share = reconstruct_decryption_share_for_ballot(
              public_key,
              ballot,
              shares.get(ballot.object_id()),
              lagrange_coefficients);
      result.put(ballot.object_id(), ballot_share);
    }
    return result;
  }

  /**
   * Reconstruct a missing ballot Decryption share for a missing guardian from the collection of compensated decryption shares.
   *
   * @param public_key            the public key for the missing guardian
   * @param ballot                The `SubmittedBallot` to reconstruct
   * @param shares                the collection of `CompensatedDecryptionShare` for the missing guardian, each keyed by the ID of the guardian that produced it
   * @param lagrange_coefficients the lagrange coefficients for the available guardians that provided shares
   */
  public static DecryptionShare reconstruct_decryption_share_for_ballot(
          KeyCeremony.ElectionPublicKey public_key,
          SubmittedBallot ballot,
          Map<String, CompensatedDecryptionShare> shares, // Dict[AVAILABLE_GUARDIAN_ID, CompensatedBallotDecryptionShare]
          Map<String, Group.ElementModQ> lagrange_coefficients) { // Dict[AVAILABLE_GUARDIAN_ID, ElementModQ]

    Map<String, CiphertextDecryptionContest> contests = new HashMap<>();
    for (CiphertextBallot.Contest contest : ballot.contests) {
      CiphertextDecryptionContest dcontest = reconstruct_decryption_contest(
              public_key.owner_id(),
              CiphertextContest.createFrom(contest),
              shares,
              lagrange_coefficients);
      contests.put(contest.object_id, dcontest);
    }

    return new DecryptionShare(
            ballot.object_id(),
            public_key.owner_id(),
            public_key.key(),
            contests);
  }

}

