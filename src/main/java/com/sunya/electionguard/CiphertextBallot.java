package com.sunya.electionguard;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.flogger.FluentLogger;

import javax.annotation.concurrent.Immutable;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import static com.sunya.electionguard.Group.ZERO_MOD_Q;
import static com.sunya.electionguard.Group.add_q;

/**
 * An encrypted representation of a voter's filled-in ballot.
 * The object_id is from the original input ballot: a unique id given by the external system
 * <p>
 * When a ballot is in its complete, encrypted state, the `nonce` is the master nonce
 * from which all other nonces can be derived to encrypt the ballot.  Along with the `nonce`
 * fields on `Ballotcontest` and `BallotSelection`, this value is sensitive.
 * <p>
 * Don't make this directly. Use `make_ciphertext_ballot` instead.
 * The field object_id is a unique Ballot ID that is relevant to the external system
 */
@Immutable
public class CiphertextBallot extends ElectionObjectBase implements Hash.CryptoHashCheckable {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  /**
   * Makes a `CiphertextBallot`, initially in the state where it's neither been cast nor spoiled.
   * <p>
   *
   * @param object_id:               the object_id of this specific ballot
   * @param ballot_style:            The `object_id` of the `BallotStyle` in the `Election` Manifest
   * @param description_hash:        Hash of the election description
   * @param previous_tracking_hashO: Previous tracking hash or seed hash
   * @param contests:                List of contests for this ballot
   * @param nonce:                   optional nonce used as part of the encryption process
   * @param timestamp:               Timestamp at which the ballot encryption is generated in tick
   */
  public static CiphertextBallot create(
          String object_id,
          String ballot_style,
          Group.ElementModQ description_hash,
          Optional<Group.ElementModQ> previous_tracking_hashO,
          List<Contest> contests,
          Optional<Group.ElementModQ> nonce,
          Optional<Long> timestamp,
          Optional<Group.ElementModQ> tracking_hashO) {

    if (contests.isEmpty()) {
      logger.atInfo().log("ciphertext ballot with no contests: %s", object_id);
    }

    List<Group.ElementModQ> contest_hashes = contests.stream().map(c -> c.crypto_hash).collect(Collectors.toList());
    Group.ElementModQ contest_hash = Hash.hash_elems(object_id, description_hash, contest_hashes);

    long time = timestamp.orElse(System.currentTimeMillis());
    Group.ElementModQ previous_tracking_hash = previous_tracking_hashO.orElse(description_hash);
    Group.ElementModQ tracking_hash = tracking_hashO.orElse(Tracker.get_rotating_tracker_hash(previous_tracking_hash, time, contest_hash));

    return new CiphertextBallot(
            object_id,
            ballot_style,
            description_hash,
            previous_tracking_hash,
            contests,
            tracking_hash,
            time,
            contest_hash,
            nonce);
  }

  ////////////////////////////////////////////////////////////////////////////////////////////////////////////
  public final String ballot_style;
  public final Group.ElementModQ description_hash; // election description hash
  public final Group.ElementModQ previous_tracking_hash;
  public final ImmutableList<Contest> contests;
  public final Group.ElementModQ tracking_hash; // not optional
  public final long timestamp; // LOOK Timestamp at which the ballot encryption is generated, in seconds since the epoch UTC.
  public final Group.ElementModQ crypto_hash;
  public final Optional<Group.ElementModQ> nonce;

  public CiphertextBallot(String object_id, String ballot_style, Group.ElementModQ description_hash,
                          Group.ElementModQ previous_tracking_hash, List<Contest> contests,
                          Group.ElementModQ tracking_hash, long timestamp, Group.ElementModQ crypto_hash,
                          Optional<Group.ElementModQ> nonce) {
    super(object_id);
    Preconditions.checkArgument(!Strings.isNullOrEmpty(ballot_style));
    this.ballot_style = ballot_style;
    this.description_hash = Preconditions.checkNotNull(description_hash);
    this.previous_tracking_hash = Preconditions.checkNotNull(previous_tracking_hash);
    this.contests = ImmutableList.copyOf(Preconditions.checkNotNull(contests));
    this.tracking_hash = Preconditions.checkNotNull(tracking_hash);
    this.timestamp = timestamp;
    this.crypto_hash = Preconditions.checkNotNull(crypto_hash);
    this.nonce = Preconditions.checkNotNull(nonce);
  }

  private static ElGamal.Ciphertext ciphertext_ballot_elgamal_accumulate(List<Selection> ballot_selections) {
    // return elgamal_add(*[selection.ciphertext for selection in ballot_selections]);
    List<ElGamal.Ciphertext> texts = ballot_selections.stream()
            .map(CiphertextSelection::ciphertext)
            .collect(Collectors.toList());
    return ElGamal.elgamal_add(Iterables.toArray(texts, ElGamal.Ciphertext.class));
  }

  private static Group.ElementModQ ciphertext_ballot_context_crypto_hash(
          String object_id,
          List<Selection> ballot_selections,
          Group.ElementModQ seed_hash) {
    if (ballot_selections.size() == 0) {
      logger.atWarning().log("mismatching ballot_selections state: %s expected(some), actual(none)", object_id);
      return ZERO_MOD_Q;
    }

    // selection_hashes = [selection.crypto_hash for selection in ballot_selections]
    List<Group.ElementModQ> selection_hashes = ballot_selections.stream().map(s -> s.crypto_hash).collect(Collectors.toList());
    Group.ElementModQ result = Hash.hash_elems(object_id, seed_hash, selection_hashes);
    logger.atFine().log("%n%n _ciphertext_ballot_context_crypto_hash:%n %s%n %s%n %s%n%s%n", object_id, seed_hash, selection_hashes, result);
    return result;
  }

  private static Optional<Group.ElementModQ> ciphertext_ballot_contest_aggregate_nonce(
          String object_id, List<Selection> ballot_selections) {

    List<Group.ElementModQ> selection_nonces = new ArrayList<>();
    for (Selection selection : ballot_selections) {
      if (selection.nonce.isEmpty()) {
        logger.atInfo().log("missing nonce values for contest %s cannot calculate aggregate nonce", object_id);
        return Optional.empty();
      } else {
        selection_nonces.add(selection.nonce.get());
      }
    }
    return Optional.of(add_q(Iterables.toArray(selection_nonces, Group.ElementModQ.class)));
  }

  /**
   * Convert into a `CiphertextAcceptedBallot`, with the given state, and all nonces removed.
   */
  CiphertextAcceptedBallot acceptWithState(BallotBox.State state) {
    return CiphertextAcceptedBallot.create(
            this.object_id,
            this.ballot_style,
            this.description_hash,
            Optional.of(this.previous_tracking_hash),
            this.contests,
            this.tracking_hash,
            Optional.of(this.timestamp),
            state);
  }

  /**
   * Makea hash of the election, the external object_id and nonce, used
   * to derive other nonce values on the ballot.
   */
  static Group.ElementModQ nonce_seed(Group.ElementModQ description_hash, String object_id, Group.ElementModQ nonce) {
    return Hash.hash_elems(description_hash, object_id, nonce);
  }

  /**
   * @return a hash value derived from the description hash, the object id, and the nonce value
   * suitable for deriving other nonce values on the ballot
   */
  Optional<Group.ElementModQ> hashed_ballot_nonce() {
    if (this.nonce.isEmpty()) {
      logger.atWarning().log("missing nonce for ballot %s could not derive from null nonce", this.object_id);
      return Optional.empty();
    }
    return Optional.of(nonce_seed(this.description_hash, this.object_id, this.nonce.get()));
  }

  /**
   * Get a tracker hash as a code in friendly readable words for sharing
   *
   * @return Tracker in words or None.
   */
  Optional<String> get_tracker_code() {
    return Tracker.tracker_hash_to_words(this.tracking_hash, null);
  }

  /**
   * Given an encrypted Ballot, generates a hash, suitable for rolling up
   * into a hash / tracking code for an entire ballot. Of note, this particular hash examines
   * the `description_hash` and `ballot_selections`, but not the proof.
   * This is deliberate, allowing for the possibility of ElectionGuard variants running on
   * much more limited hardware, wherein the Disjunctive Chaum-Pedersen proofs might be computed
   * later on.
   */
  @Override
  public Group.ElementModQ crypto_hash_with(Group.ElementModQ seed_hash) {
    if (this.contests.size() == 0) {
      logger.atWarning().log("mismatching contests state: %s expected(some), actual(none)", object_id);
      return ZERO_MOD_Q;
    }

    // contest_hashes = [contest.crypto_hash for contest in this.contests]
    List<Group.ElementModQ> selection_hashes = contests.stream().map(s -> s.crypto_hash).collect(Collectors.toList());
    return Hash.hash_elems(this.object_id, seed_hash, selection_hashes);
  }

  /**
   * Given an encrypted Ballot, validates the encryption state against a specific seed hash and public key
   * by verifying the states of this ballot's children (BallotContest's and BallotSelection's).
   * Calling this function expects that the object is in a well-formed encrypted state
   * with the `contests` populated with valid encrypted ballot selections,
   * and the ElementModQ `description_hash` also populated.
   * Specifically, the seed hash in this context is the hash of the Election Manifest,
   * or whatever `ElementModQ` was used to populate the `description_hash` field.
   */
  boolean is_valid_encryption(
          Group.ElementModQ seed_hash,
          Group.ElementModP elgamal_public_key,
          Group.ElementModQ crypto_extended_base_hash) {

    if (!seed_hash.equals(this.description_hash)) {
      logger.atInfo().log("mismatching ballot hash: %s expected(%s) actual(%s)", this.object_id, seed_hash, this.description_hash);
      return false;
    }

    Group.ElementModQ recalculated_crypto_hash = this.crypto_hash_with(seed_hash);
    if (!recalculated_crypto_hash.equals(this.crypto_hash)) {
      logger.atInfo().log("mismatching crypto hash: %s expected(%s) actual(%s)", this.object_id, recalculated_crypto_hash, this.crypto_hash);
      return false;
    }

    // Check the proofs on the ballot
    boolean valid = true;
    for (Contest contest : this.contests) {
      for (Selection selection : contest.ballot_selections) {
        valid &= selection.is_valid_encryption(selection.description_hash, elgamal_public_key, crypto_extended_base_hash);
      }
      valid &= contest.is_valid_encryption(contest.description_hash, elgamal_public_key, crypto_extended_base_hash);
    }
    return valid;
  }

  @Override
  public String toString() {
    return "CiphertextBallot{" +
            "ballot_style='" + ballot_style + '\'' +
            ", description_hash=" + description_hash +
            ", previous_tracking_hash=" + previous_tracking_hash +
            ", contests=" + contests +
            ", tracking_hash=" + tracking_hash +
            ", timestamp=" + timestamp +
            ", crypto_hash=" + crypto_hash +
            ", nonce=" + nonce +
            ", object_id='" + object_id + '\'' +
            '}';
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    if (!super.equals(o)) return false;
    CiphertextBallot that = (CiphertextBallot) o;
    return timestamp == that.timestamp &&
            ballot_style.equals(that.ballot_style) &&
            description_hash.equals(that.description_hash) &&
            previous_tracking_hash.equals(that.previous_tracking_hash) &&
            contests.equals(that.contests) &&
            tracking_hash.equals(that.tracking_hash) &&
            crypto_hash.equals(that.crypto_hash) &&
            nonce.equals(that.nonce);
  }

  @Override
  public int hashCode() {
    return Objects.hash(super.hashCode(), ballot_style, description_hash, previous_tracking_hash, contests, tracking_hash, timestamp, crypto_hash, nonce);
  }

  /** Used as a field on a selection to indicate a write-in candidate. */
  @Immutable
  public static class ExtendedData {
    public final String value;
    public final int length;

    public ExtendedData(String value, int length) {
      this.value = value;
      this.length = length;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      ExtendedData that = (ExtendedData) o;
      return length == that.length &&
              value.equals(that.value);
    }

    @Override
    public int hashCode() {
      return Objects.hash(value, length);
    }

    @Override
    public String toString() {
      return "ExtendedData{" +
              "value='" + value + '\'' +
              ", length=" + length +
              '}';
    }
  }

  /**
   * An encrypted selection for a particular contest.
   * <p>
   * When a selection is encrypted, the `description_hash` and `ciphertext` required fields must
   * be populated at construction however the `nonce` is also usually provided by convention.
   * A consumer of this object has the option to discard the `nonce` and/or discard the `proof`,
   * or keep both values.
   * By discarding the `nonce`, the encrypted representation and `proof`
   * can only be regenerated if the nonce was derived from the ballot's master nonce.  If the nonce
   * used for this selection is truly random, and it is discarded, then the proofs cannot be regenerated.
   * By keeping the `nonce`, or deriving the selection nonce from the ballot nonce, an external system can
   * regenerate the proofs on demand.  This is useful for storage or memory constrained systems.
   * By keeping the `proof` the nonce is not required to verify the encrypted selection.
   */
  @Immutable
  public static class Selection extends CiphertextSelection {

    /**
     * Constructs a CipherTextBallotSelection object. Compute the Chaum-Pedersen proof if not
     * given but a nonce is. Compute the crypto_hash if not given.
     * python: make_ciphertext_ballot_selection
     */
    static Selection create(
            String object_id,
            Group.ElementModQ description_hash,
            ElGamal.Ciphertext ciphertext,
            Group.ElementModP elgamal_public_key,
            Group.ElementModQ crypto_extended_base_hash,
            Group.ElementModQ proof_seed,
            int selection_representation,
            boolean is_placeholder_selection,
            Optional<Group.ElementModQ> nonce,
            Optional<Group.ElementModQ> crypto_hash,
            Optional<ChaumPedersen.DisjunctiveChaumPedersenProof> proof,
            Optional<ElGamal.Ciphertext> extended_data) {

      if (crypto_hash.isEmpty()) {
        // python: _ciphertext_ballot_selection_crypto_hash_with
        crypto_hash = Optional.of(Hash.hash_elems(object_id, description_hash, ciphertext.crypto_hash()));
      }

      if (proof.isEmpty() && nonce.isPresent()) { // LOOK python?
        proof = nonce.map(n ->
                ChaumPedersen.make_disjunctive_chaum_pedersen(
                        ciphertext,
                        n,
                        elgamal_public_key,
                        crypto_extended_base_hash,
                        proof_seed,
                        selection_representation)
        );
      }

      return new Selection(
              object_id,
              description_hash,
              ciphertext,
              crypto_hash.get(),
              is_placeholder_selection,
              nonce,
              proof,
              extended_data);
    }

    /////////////////////////////////////////////////////////////////////////////////////////////////////////

    public final Group.ElementModQ crypto_hash;
    public final boolean is_placeholder_selection;
    public final Optional<Group.ElementModQ> nonce;
    public final Optional<ChaumPedersen.DisjunctiveChaumPedersenProof> proof;
    public final Optional<ElGamal.Ciphertext> extended_data;

    public Selection(String object_id, Group.ElementModQ description_hash, ElGamal.Ciphertext ciphertext,
                     Group.ElementModQ crypto_hash, boolean is_placeholder_selection, Optional<Group.ElementModQ> nonce,
                     Optional<ChaumPedersen.DisjunctiveChaumPedersenProof> proof, Optional<ElGamal.Ciphertext> extended_data) {
      super(object_id, description_hash, ciphertext);

      this.crypto_hash = Preconditions.checkNotNull(crypto_hash);
      this.is_placeholder_selection = is_placeholder_selection;
      this.nonce = Preconditions.checkNotNull(nonce);
      this.proof = Preconditions.checkNotNull(proof);
      this.extended_data = Preconditions.checkNotNull(extended_data);
    }

    /** Remove nonce and return new object without the nonce. */
    Selection removeNonce() {
      return new Selection(this.object_id, this.description_hash, this.ciphertext(),
              this.crypto_hash, this.is_placeholder_selection, Optional.empty(),
              this.proof, this.extended_data);
    }

    /**
     * Given an encrypted BallotSelection, validates the encryption state against a specific seed hash and public key.
     * Calling this function expects that the object is in a well-formed encrypted state
     * with the elgamal encrypted `message` field populated along with the DisjunctiveChaumPedersenProof `proof` populated.
     * the ElementModQ `description_hash` and the ElementModQ `crypto_hash` are also checked.
     *
     * @param seed_hash:             the hash of the SelectionDescription, or
     *                               whatever `ElementModQ` was used to populate the `description_hash` field.
     * @param elgamelPublicKey:      The election key
     * @param cryptoExtendedBaseHash crypto_extended_base_hash for the election (Qbar)
     */
    boolean is_valid_encryption(Group.ElementModQ seed_hash, Group.ElementModP elgamelPublicKey, Group.ElementModQ cryptoExtendedBaseHash) {
      if (!seed_hash.equals(this.description_hash)) {
        logger.atInfo().log("mismatching selection hash: %s expected(%s), actual(%s)",
                this.object_id, seed_hash, this.description_hash);
        return false;
      }

      Group.ElementModQ recalculated_crypto_hash = crypto_hash_with(seed_hash);
      if (!recalculated_crypto_hash.equals(this.crypto_hash)) {
        logger.atInfo().log("mismatching crypto hash: %s expected(%s), actual(%s)",
                this.object_id, recalculated_crypto_hash, this.crypto_hash);
        return false;
      }

      if (this.proof.isEmpty()) {
        logger.atInfo().log("no proof exists for: %s", this.object_id);
        return false;
      }

      return proof.get().is_valid(ciphertext(), elgamelPublicKey, cryptoExtendedBaseHash);
    }

    /**
     * Generates a hash, suitable for rolling up into a hash / tracking code for an entire ballot.
     * Of note, this particular hash uses the `seed_hash` and `message`, but not the proof.
     * This is deliberate, allowing for the possibility of ElectionGuard variants running on
     * much more limited hardware, where the Disjunctive Chaum-Pedersen proofs might be computed later.
     * <p>
     * In most cases the seed_hash should match the `description_hash`
     */
    Group.ElementModQ crypto_hash_with(Group.ElementModQ seedHash) {
      // python: _ciphertext_ballot_selection_crypto_hash_with
      return Hash.hash_elems(this.object_id, seedHash, this.ciphertext().crypto_hash());
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      if (!super.equals(o)) return false;
      Selection that = (Selection) o;
      return is_placeholder_selection == that.is_placeholder_selection &&
              crypto_hash.equals(that.crypto_hash) &&
              Objects.equals(nonce, that.nonce) &&
              Objects.equals(proof, that.proof) &&
              Objects.equals(extended_data, that.extended_data);
    }

    @Override
    public int hashCode() {
      return Objects.hash(super.hashCode(), crypto_hash, is_placeholder_selection, nonce, proof, extended_data);
    }
  }

  /**
   * The encrypted selections made by a voter for a specific contest.
   * The object_id is the PlaintextBallotContest.object_id.
   * <p>
   * CiphertextBallotContest can only be a complete representation of a contest dataset.  While
   * PlaintextBallotContest supports a partial representation, a CiphertextBallotContest includes all data
   * necessary for a verifier to verify the contest.  Specifically, it includes both explicit affirmative
   * and negative selections of the contest, as well as the placeholder selections that satisfy
   * the ConstantChaumPedersen proof.
   * <p>
   * Similar to `CiphertextBallotSelection` the consuming application can choose to discard or keep both
   * the `nonce` and the `proof` in some circumstances.  For deterministic nonce's derived from the
   * master nonce, both values can be regenerated.  If the `nonce` for this contest is completely random,
   * then it is required in order to regenerate the proof.
   */
  @Immutable
  public static class Contest extends ElectionObjectBase implements Hash.CryptoHashCheckable {

    /**
     * Constructs a CiphertextBallotContest object. Computes a Chaum-Pedersen proof if the
     * ballot selections include their encryption nonces.
     * Changed for Issue #280: A crypto_hash and a contest_total are always computed and saved.
     */
    static Contest create(
            String object_id,
            Group.ElementModQ description_hash,
            List<Selection> ballot_selections,
            Group.ElementModP elgamal_public_key,
            Group.ElementModQ crypto_extended_base_hash,
            Group.ElementModQ proof_seed,
            int number_elected,
            Optional<Group.ElementModQ> nonce) {

      Group.ElementModQ crypto_hash = ciphertext_ballot_context_crypto_hash(object_id, ballot_selections, description_hash);
      ElGamal.Ciphertext contest_total = ciphertext_ballot_elgamal_accumulate(ballot_selections);
      Optional<Group.ElementModQ> aggregate = ciphertext_ballot_contest_aggregate_nonce(object_id, ballot_selections);

      Optional<ChaumPedersen.ConstantChaumPedersenProof> proof = aggregate.map(ag ->
              ChaumPedersen.make_constant_chaum_pedersen(
                      contest_total,
                      number_elected,
                      ag,
                      elgamal_public_key,
                      proof_seed,
                      crypto_extended_base_hash
              )
      );

      return new Contest(
              object_id,
              description_hash,
              ballot_selections,
              crypto_hash,
              contest_total,
              nonce,  // Optional
              proof); // Optional
    }

    /////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    public final Group.ElementModQ description_hash; // Hash from contestDescription
    public final ImmutableList<Selection> ballot_selections; // Collection of ballot selections
    public final Group.ElementModQ crypto_hash; // Hash of the encrypted values
    public final ElGamal.Ciphertext encrypted_total; // The contest total (A, B). python name: ciphertext_accumulation
    public final Optional<Group.ElementModQ> nonce; // The nonce used to generate the encryption. Sensitive & should be treated as a secret.

    // The proof demonstrates the sum of the selections does not exceed the maximum
    // available selections for the contest, and that the proof was generated with the nonce
    public final Optional<ChaumPedersen.ConstantChaumPedersenProof> proof;

    public Contest(String object_id, Group.ElementModQ description_hash,
                   List<Selection> ballot_selections,
                   Group.ElementModQ crypto_hash,
                   ElGamal.Ciphertext encrypted_total, // PR #304 calls it "ciphertext"
                   Optional<Group.ElementModQ> nonce,
                   Optional<ChaumPedersen.ConstantChaumPedersenProof> proof) {
      super(object_id);
      this.description_hash = Preconditions.checkNotNull(description_hash);
      this.ballot_selections = ImmutableList.copyOf(Preconditions.checkNotNull(ballot_selections));
      this.crypto_hash = Preconditions.checkNotNull(crypto_hash);
      this.encrypted_total = Preconditions.checkNotNull(encrypted_total);
      this.nonce = Preconditions.checkNotNull(nonce);
      this.proof = Preconditions.checkNotNull(proof);
    }

    /** Remove nonces from selections and return new contest. */
    Contest removeNonces() {
      // new_selections = [replace(selection, nonce = None) for selection in contest.ballot_selections]
      List<Selection> new_selections =
              this.ballot_selections.stream().map(s -> s.removeNonce()).collect(Collectors.toList());

      // new_contest = replace(contest, nonce=None, ballot_selections=new_selections)
      return new Contest(this.object_id, this.description_hash, new_selections, this.crypto_hash,
              this.encrypted_total, Optional.empty(), this.proof);
    }

    // not used aggregate_nonce()

    /**
     * Given an encrypted BallotContest, generates a hash, suitable for rolling up
     * into a hash / tracking code for an entire ballot. Of note, this particular hash examines
     * the `seed_hash` and `ballot_selections`, but not the proof.
     * This is deliberate, allowing for the possibility of ElectionGuard variants running on
     * much more limited hardware, wherein the Disjunctive Chaum-Pedersen proofs might be computed
     * later on.
     * <p>
     * In most cases, the seed_hash is the description_hash
     */
    public Group.ElementModQ crypto_hash_with(Group.ElementModQ seed_hash) {
      return ciphertext_ballot_context_crypto_hash(this.object_id, this.ballot_selections, seed_hash);
    }

    /**
     * Add the individual ballot_selections `message` fields together, suitable for use
     * in a Chaum-Pedersen proof.
     */
    ElGamal.Ciphertext elgamal_accumulate() {
      return ciphertext_ballot_elgamal_accumulate(this.ballot_selections);
    }

    /**
     * Given an encrypted BallotContest, validates the encryption state against a specific seed hash and public key
     * by verifying the accumulated sum of selections match the proof.
     * Calling this function expects that the object is in a well-formed encrypted state
     * with the `ballot_selections` populated with valid encrypted ballot selections,
     * the ElementModQ `description_hash`, the ElementModQ `crypto_hash`, and the ConstantChaumPedersenProof all populated.
     * Specifically, the seed hash in this context is the hash of the ContestDescription,
     * or whatever `ElementModQ` was used to populate the `description_hash` field.
     */
    boolean is_valid_encryption(
            Group.ElementModQ seed_hash,
            Group.ElementModP elgamal_public_key,
            Group.ElementModQ crypto_extended_base_hash) {

      if (!seed_hash.equals(this.description_hash)) {
        logger.atInfo().log("mismatching contest hash: %s expected(%s), actual(%s)",
                this.object_id, seed_hash, this.description_hash);
        return false;
      }

      Group.ElementModQ recalculated_crypto_hash = this.crypto_hash_with(seed_hash);
      if (!this.crypto_hash.equals(recalculated_crypto_hash)) {
        logger.atInfo().log("mismatching crypto hash: %s expected(%s) actual(%s)", this.object_id, recalculated_crypto_hash, this.crypto_hash);
        return false;
      }

      // NOTE: this check does not verify the proofs of the individual selections by design.

      if (this.proof.isEmpty()) {
        logger.atInfo().log("no proof exists for: %s", this.object_id);
        return false;
      }

      ElGamal.Ciphertext elgamal_accumulation = this.elgamal_accumulate();

      // Verify that the contest ciphertext matches the elgamal accumulation of all selections
      if (!this.encrypted_total.equals(elgamal_accumulation)) {
        logger.atInfo().log("ciphertext does not equal elgamal accumulation for: %s", this.object_id);
        return false;
      }

      // Verify the sum of the selections matches the proof
      return this.proof.get().is_valid(elgamal_accumulation, elgamal_public_key, crypto_extended_base_hash);
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      if (!super.equals(o)) return false;
      Contest that = (Contest) o;
      return description_hash.equals(that.description_hash) &&
              ballot_selections.equals(that.ballot_selections) &&
              crypto_hash.equals(that.crypto_hash) &&
              nonce.equals(that.nonce) &&
              proof.equals(that.proof);
    }

    @Override
    public int hashCode() {
      return Objects.hash(super.hashCode(), description_hash, ballot_selections, crypto_hash, nonce, proof);
    }
  }
}
