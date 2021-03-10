package com.sunya.electionguard;

import com.google.common.base.Preconditions;
import com.google.common.flogger.FluentLogger;

import javax.annotation.concurrent.Immutable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import static com.sunya.electionguard.Group.*;
import static com.sunya.electionguard.InternalManifest.ContestWithPlaceholders;

public class Encrypt {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private Encrypt() {}

  /** The device that is doing the encryption. */
  @Immutable
  public static class EncryptionDevice {
    /** Unique identifier for device. */
    public final long uuid;
    /** Used to identify session and protect the timestamp. */
    public final String session_id;
    /** Election initialization value. */
    public final int launch_code;
    /** Arbitrary string to designate the location of the device. */
    public final String location;

    public EncryptionDevice(long uuid, String session_id, int launch_code, String location) {
      this.uuid = uuid;
      this.session_id = Preconditions.checkNotNull(session_id);
      this.launch_code = launch_code;
      this.location = Preconditions.checkNotNull(location);
    }

    public EncryptionDevice(String location) {
      this.uuid = location.hashCode();
      this.session_id = Preconditions.checkNotNull(location);
      this.launch_code = 1221;
      this.location = Preconditions.checkNotNull(location);
    }

    public ElementModQ get_hash() {
      return BallotCodes.get_hash_for_device(uuid, session_id, launch_code, location);
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      EncryptionDevice that = (EncryptionDevice) o;
      return uuid == that.uuid &&
              launch_code == that.launch_code &&
              session_id.equals(that.session_id) &&
              location.equals(that.location);
    }

    @Override
    public int hashCode() {
      return Objects.hash(uuid, session_id, launch_code, location);
    }
  }

  /**
   * Orchestrates the encryption of Ballots.
   * Mutable, since it has to keep track of the last hash for ballot chaining.
   * See discussion on Issue #272 about "ballot chaining".
   */
  public static class EncryptionMediator {
    private final InternalManifest metadata;
    private final CiphertextElectionContext context;
    private ElementModQ previous_tracking_hash;

    public EncryptionMediator(InternalManifest metadata, CiphertextElectionContext context,
                              EncryptionDevice encryption_device) {
      this.metadata = metadata;
      this.context = context;
      // LOOK does not follow validation spec 6.A, which calls for crypto_base_hash.
      //   Ok to use device hash see Issue #272. Spec should be updated.
      this.previous_tracking_hash = encryption_device.get_hash();
    }

    /** Encrypt the plaintext ballot using the joint public key K. */
    public Optional<CiphertextBallot> encrypt(PlaintextBallot ballot) {
      Optional<CiphertextBallot> encrypted_ballot =
              encrypt_ballot(ballot, this.metadata, this.context, this.previous_tracking_hash, Optional.empty(), true);
      encrypted_ballot.ifPresent(ciphertextBallot -> this.previous_tracking_hash = ciphertextBallot.code);
      return encrypted_ballot;
    }
  }

  /**
   * Construct a `BallotSelection` from a specific `SelectionDescription`.
   * This function is useful for filling selections when a voter undervotes a ballot.
   * It is also used to create placeholder representations when generating the `ConstantChaumPedersenProof`
   *
   * @param description:    The `SelectionDescription` which provides the relevant `object_id`
   * @param is_placeholder: Mark this selection as a placeholder value
   * @param is_affirmative: Mark this selection as `yes`
   */
  // selection_from( description: SelectionDescription, is_placeholder: bool = False, is_affirmative: bool = False,
  static PlaintextBallot.Selection selection_from(Manifest.SelectionDescription description, boolean is_placeholder, boolean is_affirmative) {
    return new PlaintextBallot.Selection(description.object_id, is_affirmative ? 1 : 0, is_placeholder, null);
  }

  /**
   * Construct a `BallotContest` from a specific `ContestDescription` with all false fields.
   * This function is useful for filling contests and selections when a voter undervotes a ballot.
   *
   * @param description: The `ContestDescription` used to derive the well-formed `BallotContest`
   */
  static PlaintextBallot.Contest contest_from(Manifest.ContestDescription description) {
    List<PlaintextBallot.Selection> selections = new ArrayList<>();

    for (Manifest.SelectionDescription selection_description : description.ballot_selections) {
      selections.add(selection_from(selection_description, false, false));
    }
    return new PlaintextBallot.Contest(description.object_id, selections);
  }

  /**
   * Encrypt a PlaintextBallotSelection in the context of a specific SelectionDescription.
   *
   * @param selection:                 the selection in the valid input form
   * @param selection_description:     the `SelectionDescription` from the `ContestDescription` which defines this selection's structure
   * @param elgamal_public_key:        the public key (K) used to encrypt the ballot
   * @param crypto_extended_base_hash: the extended base hash of the election
   * @param nonce_seed:                an `ElementModQ` used as a header to seed the `Nonce` generated for this selection.
   *                                   this value can be (or derived from) the BallotContest nonce, but no relationship is required
   * @param is_placeholder:            specifies if this is a placeholder selection
   * @param should_verify_proofs:      specify if the proofs should be verified prior to returning (default True)
   */
  static Optional<CiphertextBallot.Selection> encrypt_selection(
          PlaintextBallot.Selection selection,
          Manifest.SelectionDescription selection_description,
          ElementModP elgamal_public_key,
          ElementModQ crypto_extended_base_hash,
          ElementModQ nonce_seed,
          boolean is_placeholder, // default false
          boolean should_verify_proofs /* default true */) {

    // Validate Input
    if (!selection.is_valid(selection_description.object_id)) {
      logger.atWarning().log("invalid input selection_id: %s", selection.selection_id);
      return Optional.empty();
    }

    ElementModQ selection_description_hash = selection_description.crypto_hash();
    Nonces nonce_sequence = new Nonces(selection_description_hash, nonce_seed);
    ElementModQ selection_nonce = nonce_sequence.get(selection_description.sequence_order);
    logger.atFine().log("encrypt_selection %n  %s%n  %s%n  %d%n%s%n",
            selection_description.crypto_hash(), nonce_seed, selection_description.sequence_order, selection_nonce);

    ElementModQ disjunctive_chaum_pedersen_nonce = nonce_sequence.get(0);

    // Generate the encryption
    Optional<ElGamal.Ciphertext> elgamal_encryption =
            ElGamal.elgamal_encrypt(selection.vote, selection_nonce, elgamal_public_key);

    if (elgamal_encryption.isEmpty()){
      // will have logged about the failure earlier, so no need to log anything here
      return Optional.empty();
    }

    // TODO: ISSUE #47: encrypt/decrypt: encrypt the extended_data field

    CiphertextBallot.Selection encrypted_selection = CiphertextBallot.Selection.create(
            selection.selection_id,
            selection_description_hash,
            elgamal_encryption.get(),
            elgamal_public_key,
            crypto_extended_base_hash,
            disjunctive_chaum_pedersen_nonce,
            selection.vote,
            is_placeholder,
            Optional.of(selection_nonce),
            Optional.empty(),
            Optional.empty(),
            Optional.empty());

    if (encrypted_selection.proof.isEmpty()){
      return Optional.empty();
    }

    // optionally, skip the verification step
    if (!should_verify_proofs) {
      return Optional.of(encrypted_selection);
    }

    // verify the selection.
    if (encrypted_selection.is_valid_encryption(selection_description_hash, elgamal_public_key, crypto_extended_base_hash)) {
      return Optional.of(encrypted_selection);
    } else {
      logger.atWarning().log("Failed selection proof for selection: %s", encrypted_selection.object_id);
      return Optional.empty();
    }
  }

  /**
   * Encrypt a PlaintextBallotContest in the context of a specific Manifest.ContestDescription.
   * <p>
   * This method accepts a contest representation that only includes `True` selections.
   * It will fill missing selections for a contest with `False` values, and generate `placeholder`
   * selections to represent the number of seats available for a given contest.  By adding `placeholder`
   * votes
   *
   * @param contest:                   the contest in the valid input form
   * @param contest_description:       the `ContestDescriptionWithPlaceholders` from the `ContestDescription` which defines this contest's structure
   * @param elgamal_public_key:        the public key (k) used to encrypt the ballot
   * @param crypto_extended_base_hash: the extended base hash of the election
   * @param nonce_seed:                an `ElementModQ` used as a header to seed the `Nonce` generated for this contest.
   *                                   this value can be (or derived from) the Ballot nonce, but no relationship is required
   * @param should_verify_proofs:      specify if the proofs should be verified prior to returning (default True)
   */
  static Optional<CiphertextBallot.Contest> encrypt_contest(
          PlaintextBallot.Contest contest,
          ContestWithPlaceholders contest_description,
          ElementModP elgamal_public_key,
          ElementModQ crypto_extended_base_hash,
          ElementModQ nonce_seed,
          boolean should_verify_proofs /* default true */) {

    // Validate Input
    if (!contest.is_valid(
          contest_description.object_id,
          contest_description.ballot_selections.size(),
          contest_description.number_elected,
          contest_description.votes_allowed)) {
      logger.atWarning().log("invalid input contest: %s", contest);
      return Optional.empty();
    }

    if (!contest_description.is_valid()) {
      logger.atWarning().log("invalid input contest_description: %s", contest_description);
      return Optional.empty();
    }

    // LOOK using sequence_order. Do we need to check for uniqueness?
    ElementModQ contest_description_hash = contest_description.crypto_hash();
    Nonces nonce_sequence = new Nonces(contest_description_hash, nonce_seed);
    ElementModQ contest_nonce = nonce_sequence.get(contest_description.sequence_order);
    ElementModQ chaum_pedersen_nonce = nonce_sequence.get(0);

    int selection_count = 0;
    List<CiphertextBallot.Selection> encrypted_selections = new ArrayList<>();
    // LOOK this will fail if there are duplicate selection_id's
    Map<String, PlaintextBallot.Selection> plaintext_selections = contest.ballot_selections.stream().collect(Collectors.toMap(s -> s.selection_id, s -> s));

    // LOOK only iterate on selections that match the manifest. If there are selections contests on the ballot,
    //   they are silently ignored.
    for (Manifest.SelectionDescription description : contest_description.ballot_selections) {
        Optional<CiphertextBallot.Selection> encrypted_selection;

        // Find the actual selection matching the contest description.
        // If there is not one, an explicit false is entered instead and the selection_count is not incremented.
        // This allows ballots to contain only the yes votes, if so desired.
        PlaintextBallot.Selection plaintext_selection = plaintext_selections.get(description.object_id);
        if (plaintext_selection != null) {
          // track the selection count so we can append the
          // appropriate number of true placeholder votes
          selection_count += plaintext_selection.vote;
          encrypted_selection = encrypt_selection(
                  plaintext_selection,
                  description,
                  elgamal_public_key,
                  crypto_extended_base_hash,
                  contest_nonce,
                  false,
                  true);
        } else {
          // No selection was made for this possible value so we explicitly set it to false
          encrypted_selection = encrypt_selection(
                  selection_from(description, false, false),
                  description,
                  elgamal_public_key,
                  crypto_extended_base_hash,
                  contest_nonce,
                  false,
                  true);
        }

        if (encrypted_selection.isEmpty()) {
          return Optional.empty(); // log will have happened earlier
        }
        encrypted_selections.add(encrypted_selection.get());
    }

    // Handle Placeholder selections. After we loop through all of the real selections on the ballot,
    // we loop through each placeholder value and determine if it should be filled in

    // Add a placeholder selection for each possible seat in the contest
    for (Manifest.SelectionDescription placeholder : contest_description.placeholder_selections) {
      // for undervotes, select the placeholder value as true for each available seat
      // note this pattern is used since DisjunctiveChaumPedersen expects a 0 or 1
      // so each seat can only have a maximum value of 1 in the current implementation
      boolean select_placeholder = false;
      if (selection_count < contest_description.number_elected) {
        select_placeholder = true;
        selection_count += 1;
      }

      Optional<CiphertextBallot.Selection> encrypted_selection = encrypt_selection(
         selection_from(placeholder, true, select_placeholder),
         placeholder,
         elgamal_public_key,
         crypto_extended_base_hash,
         contest_nonce, true, true);
      if (encrypted_selection.isEmpty()) {
        return Optional.empty(); // log will have happened earlier
      }
      encrypted_selections.add(encrypted_selection.get());
    }

    // TODO: ISSUE #33: support other cases such as cumulative voting (individual selections being an encryption of > 1)
    if (contest_description.votes_allowed.isPresent() && (selection_count < contest_description.votes_allowed.get())) {
      logger.atWarning().log("mismatching selection count: only n-of-m style elections are currently supported");
    }

    CiphertextBallot.Contest encrypted_contest = CiphertextBallot.Contest.create(
        contest.contest_id,
        contest_description_hash,
        encrypted_selections,
        elgamal_public_key,
        crypto_extended_base_hash,
        chaum_pedersen_nonce,
        contest_description.number_elected,
        Optional.of(contest_nonce));

    if (encrypted_contest.proof.isEmpty()){
        return Optional.empty();  // log error will have happened earlier
      }

    if (!should_verify_proofs) {
      return Optional.of(encrypted_contest);
    }

    // Verify the proof
    if (encrypted_contest.is_valid_encryption(contest_description_hash, elgamal_public_key, crypto_extended_base_hash)) {
      return Optional.of(encrypted_contest);
    } else {
      encrypted_contest.is_valid_encryption(contest_description_hash, elgamal_public_key, crypto_extended_base_hash);
      logger.atWarning().log("mismatching contest proof for contest %s", encrypted_contest.object_id);
      return Optional.empty();
    }
  }

  // TODO: ISSUE #57: add the device hash to the function interface so it can be propagated with the ballot.
  //  also propagate the seed hash so that the ballot tracking id's can be regenerated
  //  by traversing the collection of ballots encrypted by a specific device

  /**
   * Encrypt a PlaintextBallot in the context of a specific InternalManifest.
   * <p>
   * This method accepts a ballot representation that only includes `True` selections.
   * It will fill missing selections for a contest with `False` values, and generate `placeholder`
   * selections to represent the number of seats available for a given contest.
   * <p>
   * This method also allows for ballots to exclude passing contests for which the voter made no selections.
   * It will fill missing contests with `False` selections and generate `placeholder` selections that are marked `True`.
   *
   * @param ballot:               the ballot in the valid input form
   * @param metadata:             the InternalManifest which defines this ballot's structure
   * @param context:              all the cryptographic context for the election
   * @param previous_tracking_hash Hash from previous ballot or starting hash from device. python: seed_hash
   * @param nonce:                an optional nonce used to encrypt this contest
   *                              if this value is not provided, a random nonce is used.
   * @param should_verify_proofs: specify if the proofs should be verified prior to returning (default True)
   */
  public static Optional<CiphertextBallot> encrypt_ballot(
          PlaintextBallot ballot,
          InternalManifest metadata,
          CiphertextElectionContext context,
          ElementModQ previous_tracking_hash,
          Optional<ElementModQ> nonce,
          boolean should_verify_proofs)  {

    // Determine the relevant range of contests for this ballot style
    Optional<Manifest.BallotStyle> style = metadata.get_ballot_style(ballot.style_id);

    // Validate Input
    if (style.isEmpty()) {
      logger.atWarning().log("Ballot Style '%s' does not exist in election", ballot.style_id);
      return Optional.empty();
    }

    // Validate Input LOOK could just call BallotInputValidation? Or rely on it being done externally.
    if (!ballot.is_valid(style.get().object_id)) {
      return Optional.empty();
    }

    // Generate a random master nonce to use for the contest and selection nonce's on the ballot
    ElementModQ random_master_nonce = nonce.orElse(rand_q());

    // Include a representation of the election and the ballot Id in the nonce's used
    // to derive other nonce values on the ballot
    ElementModQ nonce_seed = CiphertextBallot.nonce_seed(metadata.election.crypto_hash, ballot.object_id, random_master_nonce);

    List<CiphertextBallot.Contest> encrypted_contests = new ArrayList<>();
    // LOOK this will fail if there are duplicate contest_id's
    Map<String, PlaintextBallot.Contest> plaintext_contests = ballot.contests.stream().collect(Collectors.toMap(c -> c.contest_id, c -> c));
    // LOOK only iterate on contests that match the manifest. If there are miscoded contests on the ballot,
    //   they are silently ignored.
    for (ContestWithPlaceholders contestDescription : metadata.get_contests_for_style(ballot.style_id)) {
      PlaintextBallot.Contest use_contest = plaintext_contests.get(contestDescription.object_id);
      // no selections provided for the contest, so create a placeholder contest
      // LOOK says "create a placeholder contest" but selections are not placeholders, but have all votes = 0.
      if (use_contest == null) {
        use_contest = contest_from(contestDescription);
      }

      Optional<CiphertextBallot.Contest> encrypted_contest = encrypt_contest(
              use_contest,
              contestDescription,
              context.elgamal_public_key,
              context.crypto_extended_base_hash,
              nonce_seed, true);

      if (encrypted_contest.isEmpty()) {
        return Optional.empty();  //log will have happened earlier
      }
      encrypted_contests.add(encrypted_contest.get());
    }

    // Create the return object
    CiphertextBallot encrypted_ballot = CiphertextBallot.create(
          ballot.object_id,
          ballot.style_id,
          metadata.election.crypto_hash,
          previous_tracking_hash, // python uses Optional
          encrypted_contests,
          Optional.of(random_master_nonce), Optional.empty(), Optional.empty());

    if (!should_verify_proofs) {
      return Optional.of(encrypted_ballot);
    }

    // Verify the proofs
    if (encrypted_ballot.is_valid_encryption(metadata.election.crypto_hash, context.elgamal_public_key, context.crypto_extended_base_hash)) {
      return Optional.of(encrypted_ballot);
    } else {
      return Optional.empty(); // log error will have happened earlier
    }
  }

}
