package com.sunya.electionguard;

import com.google.common.base.Preconditions;
import com.google.common.flogger.FluentLogger;
import com.sunya.electionguard.publish.ElectionContext;
import com.sunya.electionguard.publish.ElectionRecord;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static com.sunya.electionguard.Group.*;
import static com.sunya.electionguard.InternalManifest.ContestWithPlaceholders;

public class Encrypt {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private Encrypt() {}

  public static EncryptionDevice createDeviceForTest(String location) {
    Preconditions.checkNotNull(location);
    return new EncryptionDevice(
            location.hashCode(),
            location.hashCode(),
            12345,
            location);
  }

  /**
   * The device that is doing the encryption.
   * @param deviceId Unique identifier for device
   * @param sessionId Used to identify session and protect the timestamp
   * @param launchCode Election initialization value
   * @param location Arbitrary string to designate the location of the device
   */
  public record EncryptionDevice(
          long deviceId,
          long sessionId,
          long launchCode,
          String location) {

    public EncryptionDevice {
      Preconditions.checkNotNull(location);
    }

    public ElementModQ get_hash() {
      return BallotCodes.get_hash_for_device(deviceId, sessionId, launchCode, location);
    }
  }

  /*
   * The device that is doing the encryption.
   *
  public static class EncryptionDevice {
    private long deviceId;
    private long sessionId;
    private long launchCode;
    private String location;
    private ElementModQ cryptoHash;

    /*
     * @param deviceId   Unique identifier for device
     * @param sessionId  Used to identify session and protect the timestamp
     * @param launchCode Election initialization value
     * @param location   Arbitrary string to designate the location of the device
     *
    public EncryptionDevice(long deviceId,
                            long sessionId,
                            long launchCode,
                            String location) {
      Preconditions.checkNotNull(location);
      this.deviceId = deviceId;
      this.sessionId = sessionId;
      this.launchCode = launchCode;
      this.location = location;
      this.cryptoHash = BallotCodes.get_hash_for_device(deviceId, sessionId, launchCode, location);
    }

    public EncryptionDevice(ElementModQ cryptoHash) {
      this.cryptoHash =  cryptoHash;
    }

    public long deviceId() {
      return deviceId;
    }

    public long sessionId() {
      return sessionId;
    }

    public long launchCode() {
      return launchCode;
    }

    public String location() {
      return location;
    }

    public ElementModQ get_hash() {
      return cryptoHash;
    }
  } */

  /**
   * Orchestrates the encryption of Ballots.
   * Mutable, since it has to keep track of the last hash for ballot chaining.
   * See discussion on Issue #272 about "ballot chaining".
   */
  public static class EncryptionMediator {
    private final InternalManifest internalManifest;
    private final ElectionContext context;
    private ElementModQ encryption_seed;

    public EncryptionMediator(InternalManifest internalManifest, ElectionContext context,
                              EncryptionDevice encryption_device) {
      this.internalManifest = internalManifest;
      this.context = context;
      // LOOK does not follow validation spec 6.A, which calls for crypto_base_hash.
      //   Ok to use device hash see Issue #272. Spec should be updated.
      this.encryption_seed = encryption_device.get_hash();
    }

    /** Encrypt the plaintext ballot using the joint public key K. */
    public Optional<CiphertextBallot> encrypt(PlaintextBallot ballot) {
      Optional<CiphertextBallot> encrypted_ballot =
              encrypt_ballot(ballot, this.internalManifest, this.context, this.encryption_seed, Optional.empty(), true);
      encrypted_ballot.ifPresent(ciphertextBallot -> this.encryption_seed = ciphertextBallot.code);
      return encrypted_ballot;
    }
  }


  /**
   * Construct a `BallotSelection` from a specific `SelectionDescription`.
   * This function is useful for filling selections when a voter undervotes a ballot.
   * It is also used to create placeholder representations when generating the `ConstantChaumPedersenProof`
   *
   * @param mselection:    The `SelectionDescription` which provides the relevant `object_id`
   * @param is_placeholder: Mark this selection as a placeholder value
   * @param is_affirmative: Mark this selection as `yes`
   */
  // selection_from( description: SelectionDescription, is_placeholder: bool = False, is_affirmative: bool = False,
  static PlaintextBallot.Selection selection_from(Manifest.SelectionDescription mselection, boolean is_placeholder, boolean is_affirmative) {
    return new PlaintextBallot.Selection(mselection.selectionId(), mselection.sequenceOrder(), is_affirmative ? 1 : 0, null);
  }

  /**
   * Construct a `BallotContest` from a specific `ContestDescription` with all false fields.
   * This function is useful for filling contests and selections when a voter undervotes a ballot.
   *
   * @param mcontest: The `ContestDescription` used to derive the well-formed `BallotContest`
   */
  static PlaintextBallot.Contest contest_from(Manifest.ContestDescription mcontest) {
    List<PlaintextBallot.Selection> selections = new ArrayList<>();

    for (Manifest.SelectionDescription selection_description : mcontest.selections()) {
      selections.add(selection_from(selection_description, false, false));
    }
    return new PlaintextBallot.Contest(mcontest.contestId(), mcontest.sequenceOrder(), selections);
  }

  /**
   * Encrypt a PlaintextBallotSelection in the context of a specific SelectionDescription.
   *
   * @param selection:                 the selection in the valid input form
   * @param selection_description:     the `SelectionDescription` from the `ContestDescription` which defines this selection's structure
   * @param jointPublicKey:        the public key (K) used to encrypt the ballot
   * @param crypto_extended_base_hash: the extended base hash of the election
   * @param nonce_seed:                an `ElementModQ` used as a header to seed the `Nonce` generated for this selection.
   *                                   this value can be (or derived from) the BallotContest nonce, but no relationship is required
   * @param is_placeholder:            specifies if this is a placeholder selection
   * @param should_verify_proofs:      specify if the proofs should be verified prior to returning (default True)
   */
  public static Optional<CiphertextBallot.Selection> encrypt_selection(
          String where,
          PlaintextBallot.Selection selection,
          Manifest.SelectionDescription selection_description,
          ElementModP jointPublicKey,
          ElementModQ crypto_extended_base_hash,
          ElementModQ nonce_seed,
          boolean is_placeholder, // default false
          boolean should_verify_proofs /* default true */) {

    // Validate Input
    if (!selection.is_valid(selection_description.selectionId())) {
      logger.atWarning().log("invalid input selection_id: %s", selection.selectionId);
      return Optional.empty();
    }

    ElementModQ selection_description_hash = selection_description.cryptoHash();
    Nonces nonce_sequence = new Nonces(selection_description_hash, nonce_seed);
    ElementModQ selection_nonce = nonce_sequence.get(selection_description.sequenceOrder());
    logger.atFine().log("encrypt_selection %n  %s%n  %s%n  %d%n%s%n",
            selection_description.cryptoHash(), nonce_seed, selection_description.sequenceOrder(), selection_nonce);

    ElementModQ disjunctive_chaum_pedersen_nonce = nonce_sequence.get(0);

    // Generate the encryption
    Optional<ElGamal.Ciphertext> elgamal_encryption =
            ElGamal.elgamal_encrypt_ver1(selection.vote, selection_nonce, jointPublicKey);

    if (elgamal_encryption.isEmpty()){
      // will have logged about the failure earlier, so no need to log anything here
      return Optional.empty();
    }

    // TODO: ISSUE #47: encrypt/decrypt: encrypt the extended_data field

    CiphertextBallot.Selection encrypted_selection = CiphertextBallot.Selection.create(
            selection.selectionId,
            selection.sequenceOrder,
            selection_description_hash,
            elgamal_encryption.get(),
            jointPublicKey,
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
    if (encrypted_selection.is_valid_encryption(where, selection_description_hash, jointPublicKey, crypto_extended_base_hash)) {
      if (first) {
        System.out.printf("first encryption %s = %s%n", where, encrypted_selection);
        first = false;
      }
      return Optional.of(encrypted_selection);
    } else {
      logger.atWarning().log("Failed selection proof for selection: %s", encrypted_selection.object_id());
      return Optional.empty();
    }
  }
  static boolean first = true;

  /**
   * Encrypt a PlaintextBallotContest into CiphertextBallot.Contest.
   * <p>
   * This method accepts a contest representation that only includes `True` selections.
   * It will fill missing selections for a contest with `False` values, and generate `placeholder`
   * selections to represent the number of seats available for a given contest.  By adding `placeholder`
   * votes
   *
   * @param contest:                   the contest in the valid input form
   * @param contestp:                   the `ContestWithPlaceholders` which defines this contest's structure
   * @param jointPublicKey:        the public key (k) used to encrypt the ballot
   * @param crypto_extended_base_hash: the extended base hash of the election
   * @param nonce_seed:                an `ElementModQ` used as a header to seed the `Nonce` generated for this contest.
   *                                   this value can be (or derived from) the Ballot nonce, but no relationship is required
   * @param should_verify_proofs:      specify if the proofs should be verified prior to returning (default True)
   */
  public static Optional<CiphertextBallot.Contest> encrypt_contest(
          String where,
          PlaintextBallot.Contest contest,
          ContestWithPlaceholders contestp,
          ElementModP jointPublicKey,
          ElementModQ crypto_extended_base_hash,
          ElementModQ nonce_seed,
          boolean should_verify_proofs /* default true */) {

    Manifest.ContestDescription contest_description = contestp.contest;
    // Validate Input
    if (!contest.is_valid(
          contest_description.contestId(),
          contest_description.selections().size(),
          contest_description.numberElected(),
          contest_description.votesAllowed())) {
      logger.atWarning().log("invalid input contest: %s", contest);
      return Optional.empty();
    }

    if (!contest_description.is_valid()) {
      logger.atWarning().log("invalid input contest_description: %s", contest_description);
      return Optional.empty();
    }

    // LOOK using sequence_order. Do we need to check for uniqueness?
    ElementModQ contest_description_hash = contest_description.cryptoHash();
    Nonces nonce_sequence = new Nonces(contest_description_hash, nonce_seed);
    ElementModQ contest_nonce = nonce_sequence.get(contest_description.sequenceOrder());
    ElementModQ chaum_pedersen_nonce = nonce_sequence.get(0);

    int selection_count = 0;
    List<CiphertextBallot.Selection> encrypted_selections = new ArrayList<>();
    // LOOK this will fail if there are duplicate selection_id's
    Map<String, PlaintextBallot.Selection> plaintext_selections = contest.selections.stream().collect(Collectors.toMap(s -> s.selectionId, s -> s));

    // LOOK only iterate on selections that match the manifest. If there are selections contests on the ballot,
    //   they are silently ignored.
    for (Manifest.SelectionDescription description : contest_description.selections()) {
        Optional<CiphertextBallot.Selection> encrypted_selection;

        // Find the actual selection matching the contest description.
        // If there is not one, an explicit false is entered instead and the selection_count is not incremented.
        // This allows ballots to contain only the yes votes, if so desired.
        PlaintextBallot.Selection plaintext_selection = plaintext_selections.get(description.selectionId());
        if (plaintext_selection != null) {
          // track the selection count so we can append the
          // appropriate number of true placeholder votes
          selection_count += plaintext_selection.vote;
          encrypted_selection = encrypt_selection(
                  where + " " + contest.contestId,
                  plaintext_selection,
                  description,
                  jointPublicKey,
                  crypto_extended_base_hash,
                  contest_nonce,
                  false,
                  true);
        } else {
          // No selection was made for this possible value so we explicitly set it to false
          encrypted_selection = encrypt_selection(
                  where + " " + contest.contestId,
                  selection_from(description, false, false),
                  description,
                  jointPublicKey,
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
    for (Manifest.SelectionDescription placeholder : contestp.placeholder_selections) {
      // for undervotes, select the placeholder value as true for each available seat
      // note this pattern is used since DisjunctiveChaumPedersen expects a 0 or 1
      // so each seat can only have a maximum value of 1 in the current implementation
      boolean select_placeholder = false;
      if (selection_count < contest_description.numberElected()) {
        select_placeholder = true;
        selection_count += 1;
      }

      Optional<CiphertextBallot.Selection> encrypted_selection = encrypt_selection(
              where + " " + contest.contestId,

              selection_from(placeholder, true, select_placeholder),
         placeholder,
         jointPublicKey,
         crypto_extended_base_hash,
         contest_nonce, true, true);
      if (encrypted_selection.isEmpty()) {
        return Optional.empty(); // log will have happened earlier
      }
      encrypted_selections.add(encrypted_selection.get());
    }

    // TODO: ISSUE #33: support other cases such as cumulative voting (individual selections being an encryption of > 1)
    if (selection_count < contest_description.votesAllowed()) {
      logger.atWarning().log("mismatching selection count: only n-of-m style elections are currently supported");
    }

    CiphertextBallot.Contest encrypted_contest = CiphertextBallot.Contest.create(
        contest.contestId,
        contest.sequenceOrder,
        contest_description_hash,
        encrypted_selections,
        jointPublicKey,
        crypto_extended_base_hash,
        chaum_pedersen_nonce,
        contest_description.numberElected(),
        Optional.of(contest_nonce));

    if (encrypted_contest.proof.isEmpty()){
        return Optional.empty();  // log error will have happened earlier
      }

    if (!should_verify_proofs) {
      return Optional.of(encrypted_contest);
    }

    // Verify the proof
    if (encrypted_contest.is_valid_encryption(where, contest_description_hash, jointPublicKey, crypto_extended_base_hash)) {
      return Optional.of(encrypted_contest);
    } else {
      encrypted_contest.is_valid_encryption(where, contest_description_hash, jointPublicKey, crypto_extended_base_hash);
      logger.atWarning().log("mismatching contest proof for contest %s", encrypted_contest.contestId);
      return Optional.empty();
    }
  }

  // TODO: ISSUE #57: add the device hash to the function interface so it can be propagated with the ballot.
  //  also propagate the seed hash so that the ballot tracking id's can be regenerated
  //  by traversing the collection of ballots encrypted by a specific device

  /**
   * Encrypt a PlaintextBallot into a CiphertextBallot.
   * <p>
   * This method accepts a ballot representation that only includes `True` selections.
   * It will fill missing selections for a contest with `False` values, and generate `placeholder`
   * selections to represent the number of seats available for a given contest.
   * <p>
   * This method also allows for ballots to exclude passing contests for which the voter made no selections.
   * It will fill missing contests with `False` selections and generate `placeholder` selections that are marked `True`.
   *
   * @param ballot:               the ballot in the valid input form
   * @param internal_manifest:    the InternalManifest which defines this ballot's structure
   * @param context:              all the cryptographic context for the election
   * @param encryption_seed Hash from previous ballot or starting hash from device. python: seed_hash
   * @param nonce:                an optional nonce used to encrypt this contest
   *                              if this value is not provided, a random nonce is used.
   * @param should_verify_proofs: specify if the proofs should be verified prior to returning (default True)
   */
  public static Optional<CiphertextBallot> encrypt_ballot(
          PlaintextBallot ballot,
          InternalManifest internal_manifest,
          ElectionContext context,
          ElementModQ encryption_seed,
          Optional<ElementModQ> nonce,
          boolean should_verify_proofs)  {

    // Determine the relevant range of contests for this ballot style
    Optional<Manifest.BallotStyle> style = internal_manifest.get_ballot_style(ballot.ballotStyleId);

    // Validate Input
    if (style.isEmpty()) {
      logger.atWarning().log("Ballot Style '%s' does not exist in election", ballot.ballotStyleId);
      return Optional.empty();
    }

    // Validate Input LOOK could just call BallotInputValidation? Or rely on it being done externally.
    if (!ballot.is_valid(style.get().ballotStyleId())) {
      return Optional.empty();
    }

    // Generate a random master nonce to use for the contest and selection nonce's on the ballot
    ElementModQ random_master_nonce = nonce.orElse(rand_q());

    // Include a representation of the election and the ballot Id in the nonce's used
    // to derive other nonce values on the ballot
    ElementModQ ballotNonce = Hash.hash_elems(internal_manifest.manifest.cryptoHash(), ballot.object_id(), random_master_nonce);

    Optional<List<CiphertextBallot.Contest>> encrypted_contests = encrypt_ballot_contests(
            ballot, internal_manifest, context, ballotNonce);
    if (encrypted_contests.isEmpty()) {
      return Optional.empty();
    }

    // Create the return object
    CiphertextBallot encrypted_ballot = CiphertextBallot.create(
            ballot.object_id(),
            ballot.ballotStyleId,
            internal_manifest.manifest.cryptoHash(),
            encryption_seed, // python uses Optional
            encrypted_contests.get(),
            Optional.of(random_master_nonce),
            Optional.empty(),
            Optional.empty());

    if (!should_verify_proofs) {
      return Optional.of(encrypted_ballot);
    }

    // Verify the proofs
    if (encrypted_ballot.is_valid_encryption(internal_manifest.manifest.cryptoHash(), context.electionPublicKey(), context.extendedHash())) {
      return Optional.of(encrypted_ballot);
    } else {
      return Optional.empty(); // log error will have happened earlier
    }
  }

  /** Encrypt contests from a plaintext ballot with a specific style. */
  static Optional<List<CiphertextBallot.Contest>> encrypt_ballot_contests(
          PlaintextBallot ballot,
          InternalManifest description,
          ElectionContext context,
          ElementModQ nonce_seed) {

    List<CiphertextBallot.Contest> encrypted_contests = new ArrayList<>();
    // LOOK this will fail if there are duplicate contest_id's
    Map<String, PlaintextBallot.Contest> plaintext_contests = ballot.contests.stream()
            .collect(Collectors.toMap(c -> c.contestId, c -> c));

    // LOOK only iterate on contests that match the manifest. If there are miscoded contests on the ballot,
    //   they are silently ignored.
    for (ContestWithPlaceholders contestp : description.get_contests_for_style(ballot.ballotStyleId)) {
      Manifest.ContestDescription contestm = contestp.contest;
      PlaintextBallot.Contest use_contest = plaintext_contests.get(contestm.contestId());

      // no selections provided for the contest, so create a blank contest
      if (use_contest == null) {
        use_contest = contest_from(contestm);
      }

      Optional<CiphertextBallot.Contest> encrypted_contest = encrypt_contest(
              ballot.object_id(),
              use_contest,
              contestp,
              context.electionPublicKey(),
              context.extendedHash(),
              nonce_seed, true);

      if (encrypted_contest.isEmpty()) {
        return Optional.empty();  //log will have happened earlier
      }
      encrypted_contests.add(encrypted_contest.get());
    }
    return Optional.of(encrypted_contests);
  }

}
