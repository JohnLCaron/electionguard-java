package com.sunya.electionguard;

import com.google.common.flogger.FluentLogger;

import javax.annotation.concurrent.Immutable;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import static com.sunya.electionguard.Ballot.*;
import static com.sunya.electionguard.Election.*;
import static com.sunya.electionguard.Group.*;


public class Encrypt {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  /**
   * Metadata for encryption device.
   */
  @Immutable
  public static class EncryptionDevice {
    public final String uuid;
    final String location;

    public EncryptionDevice(String location) {
      this.location = location;
      uuid = generate_device_uuid();
    }

    ElementModQ get_hash() {
      return Hash.hash_elems(uuid, location);
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      EncryptionDevice that = (EncryptionDevice) o;
      return uuid.equals(that.uuid) &&
              location.equals(that.location);
    }

    @Override
    public int hashCode() {
      return Objects.hash(uuid, location);
    }
  }

  /**
   *    An object for caching election and encryption state.
   *
   *     It composes Elections and Ballots.
   */
  public static class EncryptionMediator {
    private final InternalElectionDescription _metadata;
    private final CiphertextElectionContext _encryption;
    private ElementModQ _seed_hash; // LOOK mutable

    public EncryptionMediator(InternalElectionDescription metadata, CiphertextElectionContext encryption,
                              EncryptionDevice encryption_device) {
      this._metadata = metadata;
      this._encryption = encryption;
      this._seed_hash = encryption_device.get_hash();
    }

    /** Encrypt the specified ballot using the cached election context. */
    Optional<CiphertextBallot> encrypt(PlaintextBallot ballot) {
      Optional<CiphertextBallot> encrypted_ballot =
              encrypt_ballot(ballot, this._metadata, this._encryption, this._seed_hash, Optional.empty(), true);
      if (encrypted_ballot.isPresent() && encrypted_ballot.get().tracking_hash.isPresent()) {
        // TODO mutable why?
        this._seed_hash = encrypted_ballot.get().tracking_hash.get();
      }
      return encrypted_ballot;
    }
  }

  /**
   * Get unique identifier for device.
   */
  private static String generate_device_uuid() {
    return java.util.UUID.randomUUID().toString();
  }

  /**
   *     Construct a `BallotSelection` from a specific `SelectionDescription`.
   *     This function is useful for filling selections when a voter undervotes a ballot.
   *     It is also used to create placeholder representations when generating the `ConstantChaumPedersenProof`
   *
   *     @param description: The `SelectionDescription` which provides the relevant `object_id`
   *     @param is_placeholder: Mark this selection as a placeholder value
   *     @param is_affirmative: Mark this selection as `yes`
   *     @return A BallotSelection
   */
  // selection_from( description: SelectionDescription, is_placeholder: bool = False, is_affirmative: bool = False,
  static PlaintextBallotSelection selection_from(SelectionDescription description, boolean is_placeholder, boolean is_affirmative) {
    return new PlaintextBallotSelection(description.object_id, is_affirmative ? "true" : "false", is_placeholder, Optional.empty());
  }

  /**
   *     Construct a `BallotContest` from a specific `ContestDescription` with all false fields.
   *     This function is useful for filling contests and selections when a voter undervotes a ballot.
   *
   *     @param description: The `ContestDescription` used to derive the well-formed `BallotContest`
   *     @return a `BallotContest`
   */
  static PlaintextBallotContest contest_from(ContestDescription description) {
    List<PlaintextBallotSelection> selections = new ArrayList<>();

    for (SelectionDescription selection_description : description.ballot_selections) {
      selections.add(selection_from(selection_description, false, false));
    }

    return new PlaintextBallotContest(description.object_id, selections);
  }

  /**
   *     Encrypt a specific `BallotSelection` in the context of a specific `BallotContest`
   *
   *     @param selection: the selection in the valid input form
   *     @param selection_description: the `SelectionDescription` from the `ContestDescription` which defines this selection's structure
   *     @param elgamal_public_key: the public key (K) used to encrypt the ballot
   *     @param crypto_extended_base_hash: the extended base hash of the election
   *     @param nonce_seed: an `ElementModQ` used as a header to seed the `Nonce` generated for this selection.
   *                  this value can be (or derived from) the BallotContest nonce, but no relationship is required
   *     @param is_placeholder: specifies if this is a placeholder selection
   *     @param should_verify_proofs: specify if the proofs should be verified prior to returning (default True)
   */
  static Optional<CiphertextBallotSelection> encrypt_selection(
          PlaintextBallotSelection selection,
          SelectionDescription selection_description,
          ElementModP elgamal_public_key,
          ElementModQ crypto_extended_base_hash,
          ElementModQ nonce_seed,
          boolean is_placeholder, // default false
          boolean should_verify_proofs /* default true */) {

    // Validate Input
    if (!selection.is_valid(selection_description.object_id)) {
      logger.atInfo().log("malformed input selection: %s", selection);
      return Optional.empty();
    }

    ElementModQ selection_description_hash = selection_description.crypto_hash();
    Nonces nonce_sequence = new Nonces(selection_description_hash, nonce_seed);
    ElementModQ selection_nonce = nonce_sequence.get(selection_description.sequence_order);
    logger.atFine().log("encrypt_selection %n  %s%n  %s%n  %d%n%s%n",
            selection_description.crypto_hash(), nonce_seed, selection_description.sequence_order, selection_nonce);

    ElementModQ disjunctive_chaum_pedersen_nonce = nonce_sequence.get(0);

    int selection_representation = selection.to_int();

    // Generate the encryption
    Optional<ElGamal.Ciphertext> elgamal_encryption =
            ElGamal.elgamal_encrypt(selection_representation, selection_nonce, elgamal_public_key);

    if (elgamal_encryption.isEmpty()){
      // will have logged about the failure earlier, so no need to log anything here
      return Optional.empty();
    }

    // TODO: ISSUE #35: encrypt/decrypt: encrypt the extended_data field

    // Create the return object
    CiphertextBallotSelection encrypted_selection = make_ciphertext_ballot_selection(
            selection.object_id,
            selection_description_hash,
            elgamal_encryption.get(),
            elgamal_public_key,
            crypto_extended_base_hash,
            disjunctive_chaum_pedersen_nonce,
            selection_representation,
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
      logger.atInfo().log("mismatching selection proof for selection: %s", encrypted_selection.object_id);
      return Optional.empty();
    }
  }

  /**
   *     Encrypt a specific `BallotContest` in the context of a specific `Ballot`.
   *
   *     This method accepts a contest representation that only includes `True` selections.
   *     It will fill missing selections for a contest with `False` values, and generate `placeholder`
   *     selections to represent the number of seats available for a given contest.  By adding `placeholder`
   *     votes
   *
   *     @param contest: the contest in the valid input form
   *     @param contest_description: the `ContestDescriptionWithPlaceholders` from the `ContestDescription` which defines this contest's structure
   *     @param elgamal_public_key: the public key (k) used to encrypt the ballot
   *     @param crypto_extended_base_hash: the extended base hash of the election
   *     @param nonce_seed: an `ElementModQ` used as a header to seed the `Nonce` generated for this contest.
   *                  this value can be (or derived from) the Ballot nonce, but no relationship is required
   *     @param should_verify_proofs: specify if the proofs should be verified prior to returning (default True)
   */
  static Optional<CiphertextBallotContest> encrypt_contest(
          PlaintextBallotContest contest,
          ContestDescriptionWithPlaceholders contest_description,
          ElementModP elgamal_public_key,
          ElementModQ crypto_extended_base_hash,
          ElementModQ nonce_seed,
          boolean should_verify_proofs /* default true */) {

    //Validate Input
    if (!contest.is_valid(
          contest_description.object_id,
          contest_description.ballot_selections.size(),
          contest_description.number_elected,
          contest_description.votes_allowed)) {
      logger.atInfo().log("malformed input contest: %s", contest);
      return Optional.empty();
    }

    if (!contest_description.is_valid()) {
      logger.atInfo().log("malformed input contest_description: %s", contest_description);
      return Optional.empty();
    }

    // account for sequence id
    ElementModQ contest_description_hash = contest_description.crypto_hash();
    Nonces nonce_sequence = new Nonces(contest_description_hash, nonce_seed);
    ElementModQ contest_nonce = nonce_sequence.get(contest_description.sequence_order);
    // TODO wtf?   chaum_pedersen_nonce = next(iter(nonce_sequence)) the one following contest_nonce? the first ??
    ElementModQ chaum_pedersen_nonce = nonce_sequence.get(0);

    List<CiphertextBallotSelection> encrypted_selections = new ArrayList<>();

    int selection_count = 0;

    // TODO: ISSUE #54 this code could be inefficient if we had a contest
    // with a lot of choices, although the O(n^2) iteration here is small
    // compared to the huge cost of doing the cryptography.

    //  Generate the encrypted selections
    for (SelectionDescription description : contest_description.ballot_selections) {
        boolean has_selection = false;
        Optional<CiphertextBallotSelection> encrypted_selection = Optional.empty();

        // iterate over the actual selections for each contest description
        // and apply the selected value if it exists.  If it does not, an explicit
        // false is entered instead and the selection_count is not incremented
        // this allows consumers to only pass in the relevant selections made by a voter
        for (PlaintextBallotSelection selection : contest.ballot_selections) {
          if (selection.object_id.equals(description.object_id)) {
            // track the selection count so we can append the
            // appropriate number of true placeholder votes
            has_selection = true;
            selection_count += selection.to_int();
            encrypted_selection = encrypt_selection(
                    selection,
                    description,
                    elgamal_public_key,
                    crypto_extended_base_hash,
                    contest_nonce,
                    false,
                    true);
            break;
          }
        }

        if (!has_selection) {
          // No selection was made for this possible value
          // so we explicitly set it to false
          encrypted_selection = encrypt_selection(
                  selection_from(description, false, false),
                  description,
                  elgamal_public_key,
                  crypto_extended_base_hash,
                  contest_nonce, false, true);
        }

        if (encrypted_selection.isEmpty()) {
          return Optional.empty(); // log will have happened earlier
        }
        encrypted_selections.add(encrypted_selection.get());
      }

    // Handle Placeholder selections
    // After we loop through all of the real selections on the ballot,
    // we loop through each placeholder value and determine if it should be filled in

    // Add a placeholder selection for each possible seat in the contest
    for (SelectionDescription placeholder : contest_description.placeholder_selections) {
      // for undervotes, select the placeholder value as true for each available seat
      // note this pattern is used since DisjunctiveChaumPedersen expects a 0 or 1
      // so each seat can only have a maximum value of 1 in the current implementation
      boolean select_placeholder = false;
      if (selection_count < contest_description.number_elected) {
        select_placeholder = true;
        selection_count += 1;
      }

      Optional<CiphertextBallotSelection> encrypted_selection = encrypt_selection(
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

    // TODO: ISSUE #33: support other cases such as cumulative voting
    // (individual selections being an encryption of > 1)
    if (contest_description.votes_allowed.isPresent() && (selection_count < contest_description.votes_allowed.get())) {
      logger.atInfo().log("mismatching selection count: only n-of-m style elections are currently supported");
    }

    CiphertextBallotContest encrypted_contest = make_ciphertext_ballot_contest(
        contest.object_id,
        contest_description_hash,
        encrypted_selections,
        elgamal_public_key,
        crypto_extended_base_hash,
        chaum_pedersen_nonce,
        contest_description.number_elected,
        Optional.empty(),
        Optional.empty(),
        Optional.of(contest_nonce));

    if (encrypted_contest.proof.isEmpty()){
        return Optional.empty();  // log will have happened earlier
      }

    if (!should_verify_proofs) {
      return Optional.of(encrypted_contest);
    }

    // Verify the proof
    if (encrypted_contest.is_valid_encryption(contest_description_hash, elgamal_public_key, crypto_extended_base_hash)) {
      return Optional.of(encrypted_contest);
    } else {
      encrypted_contest.is_valid_encryption(contest_description_hash, elgamal_public_key, crypto_extended_base_hash);
      logger.atInfo().log("mismatching contest proof for contest %s", encrypted_contest.object_id);
      return Optional.empty();
    }
  }

  // TODO: ISSUE #57: add the device hash to the function interface so it can be propagated with the ballot.
  // also propagate the seed hash so that the ballot tracking id's can be regenerated
  // by traversing the collection of ballots encrypted by a specific device

  /**
   *     Encrypt a specific `Ballot` in the context of a specific `CiphertextElectionContext`.
   *
   *     This method accepts a ballot representation that only includes `True` selections.
   *     It will fill missing selections for a contest with `False` values, and generate `placeholder`
   *     selections to represent the number of seats available for a given contest.
   *
   *     This method also allows for ballots to exclude passing contests for which the voter made no selections.
   *     It will fill missing contests with `False` selections and generate `placeholder` selections that are marked `True`.
   *
   *     @param ballot: the ballot in the valid input form
   *     @param election_metadata: the `InternalElectionDescription` which defines this ballot's structure
   *     @param context: all the cryptographic context for the election
   *     @param seed_hash: Hash from previous ballot or starting hash from device
   *     @param nonce: an optional `int` used to seed the `Nonce` generated for this contest
   *                  if this value is not provided, the secret generating mechanism of the OS provides its own
   *     @param should_verify_proofs: specify if the proofs should be verified prior to returning (default True)
   */
  // def encrypt_ballot( ballot: PlaintextBallot, election_metadata: InternalElectionDescription, context:
  //    CiphertextElectionContext, seed_hash: ElementModQ, nonce: Optional[ElementModQ] = None, should_verify_proofs:
  //    bool = True,
  static Optional<CiphertextBallot> encrypt_ballot(
          PlaintextBallot ballot,
          InternalElectionDescription election_metadata,
          CiphertextElectionContext context,
          ElementModQ seed_hash,
          Optional<ElementModQ> nonce,
          boolean should_verify_proofs)  {

    // Determine the relevant range of contests for this ballot style
    Optional<BallotStyle> style = election_metadata.get_ballot_style(ballot.ballot_style);

    // Validate Input
    if (style.isEmpty() || !ballot.is_valid(style.get().object_id)) {
      logger.atInfo().log("malformed input ballot: %s", ballot);
      return Optional.empty();
    }

    // Generate a random master nonce to use for the contest and selection nonce's on the ballot
    ElementModQ random_master_nonce = nonce.orElse(rand_q());

    // Include a representation of the election and the external Id in the nonce's used
    // to derive other nonce values on the ballot
    ElementModQ nonce_seed = CiphertextBallot.nonce_seed(election_metadata.description_hash, ballot.object_id, random_master_nonce);

    List<CiphertextBallotContest> encrypted_contests = new ArrayList<>();

    // only iterate on contests for this specific ballot style
    for (ContestDescriptionWithPlaceholders description : election_metadata.get_contests_for(ballot.ballot_style)) {
      PlaintextBallotContest use_contest = null;
      for (PlaintextBallotContest contest : ballot.contests) {
        if (contest.object_id.equals(description.object_id)) {
          use_contest = contest;
          break;
        }
      }
      // no selections provided for the contest, so create a placeholder contest
      if (use_contest == null) {
        use_contest = contest_from(description);
      }

      Optional<CiphertextBallotContest> encrypted_contest = encrypt_contest(
              use_contest,
              description,
              context.elgamal_public_key,
              context.crypto_extended_base_hash,
              nonce_seed, true);

      if (encrypted_contest.isEmpty()) {
        return Optional.empty();  //log will have happened earlier
      }
      encrypted_contests.add(encrypted_contest.get());
    }

    // Create the return object
    CiphertextBallot encrypted_ballot = Ballot.make_ciphertext_ballot(
          ballot.object_id,
          ballot.ballot_style,
          election_metadata.description_hash,
          Optional.of(seed_hash),
          encrypted_contests,
          Optional.of(random_master_nonce), Optional.empty(), Optional.empty());

    if (encrypted_ballot.tracking_hash.isEmpty()) {
        return Optional.empty();
      }

    if (!should_verify_proofs) {
      return Optional.of(encrypted_ballot);
    }

    // Verify the proofs
    if (encrypted_ballot.is_valid_encryption(election_metadata.description_hash, context.elgamal_public_key, context.crypto_extended_base_hash)) {
      return Optional.of(encrypted_ballot);
    } else {
      return Optional.empty(); // log will have happened earlier
    }
  }
}
