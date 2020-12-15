package com.sunya.electionguard;

import com.google.common.collect.Iterables;
import com.google.common.flogger.FluentLogger;

import javax.annotation.concurrent.Immutable;
import java.util.*;
import java.util.stream.Collectors;

import static com.sunya.electionguard.Group.*;

class Ballot {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  /**
   * ExtendedData represents any arbitrary data expressible as a string with a length.
   * This class is used primarily as a field on a selection to indicate a write-in candidate text value.
   */
  @Immutable
  static class ExtendedData {
    final String value;
    final int length;

    ExtendedData(String value, int length) {
      this.value = value;
      this.length = length;
    }
  }

  /**
   * A BallotSelection represents an individual selection on a ballot.
   * <p>
   * This class accepts a `vote` string field which has no constraints
   * in the ElectionGuard Data Specification, but is constrained logically
   * in the application to resolve to `True` or `False`.  This implies that the
   * data specification supports passing any string that can be represented as
   * an integer, however only 0 and 1 is supported for now.
   * <p>
   * This class can also be designated as `is_placeholder_selection` which has no
   * context to the data specification but is useful for running validity checks internally
   * <p>
   * an `extended_data` field exists to support any arbitrary data to be associated
   * with the selection.  In practice, this field is the cleartext representation
   * of a write-in candidate value.  In the current implementation these values are
   * discarded when encrypting.
   */
  @Immutable
  static class PlaintextBallotSelection extends ElectionObjectBase {
    final String vote;
    final boolean is_placeholder_selection; // default false
    final Optional<ExtendedData> extended_data; // default None

    PlaintextBallotSelection(String objectId, String vote, boolean is_placeholder_selection, Optional<ExtendedData> extendedData) {
      super(objectId);
      this.vote = vote;
      this.is_placeholder_selection = is_placeholder_selection;
      this.extended_data = extendedData;
    }

    boolean is_valid(String expectedObjectId) {
      if (!expectedObjectId.equals(object_id)) {
        // f"invalid object_id: expected({expected_object_id}) actual({self.object_id})"
        return false;
      }

      int choice = to_int();
      if (choice < 0 || choice > 1) {
        logger.atInfo().log("Currently only supporting choices of 0 or 1: %s", this);
        return false;
      }

      return true;
    }

    /**
     * Represent a Truthy string as 1, or if the string is Falsy, 0
     * See: https://docs.python.org/3/distutils/apiref.html#distutils.util.strtobool
     * :return: an integer 0 or 1 for valid data, or 0 if the data is malformed
     */
    int to_int() {
      boolean asBool = false;
      try {
        asBool = Utils.strtobool(vote);
      } catch (Exception e) {
        logger.atInfo().log("to_int could not convert plaintext: %s", vote);
        // f"to_int could not convert plaintext: {self.vote.lower()} to bool"
      }

      // TODO: ISSUE #33: If the boolean coercion above fails, support integer votes
      // greater than 1 for cases such as cumulative voting
      return asBool ? 1 : 0;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      if (!super.equals(o)) return false;
      PlaintextBallotSelection that = (PlaintextBallotSelection) o;
      return is_placeholder_selection == that.is_placeholder_selection &&
              Objects.equals(vote, that.vote) &&
              Objects.equals(extended_data, that.extended_data);
    }

    @Override
    public int hashCode() {
      return Objects.hash(super.hashCode(), vote, is_placeholder_selection, extended_data);
    }
  }

  /**
   * Encrypted selection.
   */
  @Immutable
  static class CiphertextSelection extends ElectionObjectBase {
    final ElementModQ description_hash;
    final ElGamal.Ciphertext ciphertext;

    CiphertextSelection(String object_id, ElementModQ description_hash, ElGamal.Ciphertext ciphertext) {
      super(object_id);
      this.description_hash = description_hash;
      this.ciphertext = ciphertext;
    }
  }

  /**
   * A CiphertextBallotSelection represents an individual encrypted selection on a ballot.
   * <p>
   * This class accepts a `description_hash` and a `ciphertext` as required parameters
   * in its constructor.
   * <p>
   * When a selection is encrypted, the `description_hash` and `ciphertext` required fields must
   * be populated at construction however the `nonce` is also usually provided by convention.
   * <p>
   * After construction, the `crypto_hash` field is populated automatically in the `__post_init__` cycle
   * <p>
   * A consumer of this object has the option to discard the `nonce` and/or discard the `proof`,
   * or keep both values.
   * <p>
   * By discarding the `nonce`, the encrypted representation and `proof`
   * can only be regenerated if the nonce was derived from the ballot's master nonce.  If the nonce
   * used for this selection is truly random, and it is discarded, then the proofs cannot be regenerated.
   * <p>
   * By keeping the `nonce`, or deriving the selection nonce from the ballot nonce, an external system can
   * regenerate the proofs on demand.  This is useful for storage or memory constrained systems.
   * <p>
   * By keeping the `proof` the nonce is not required fotor verify the encrypted selection.
   */
  @Immutable
  static class CiphertextBallotSelection extends CiphertextSelection {
    final ElementModQ crypto_hash;
    final boolean is_placeholder_selection;
    final Optional<ElementModQ> nonce;
    final Optional<ChaumPedersen.DisjunctiveChaumPedersenProof> proof;
    final Optional<ElGamal.Ciphertext> extended_data;

    CiphertextBallotSelection(String object_id, ElementModQ description_hash, ElGamal.Ciphertext ciphertext,
                              ElementModQ crypto_hash, boolean is_placeholder_selection, Optional<ElementModQ> nonce,
                              Optional<ChaumPedersen.DisjunctiveChaumPedersenProof> proof, Optional<ElGamal.Ciphertext> extended_data) {
      super(object_id, description_hash, ciphertext);
      this.crypto_hash = crypto_hash;
      this.is_placeholder_selection = is_placeholder_selection;
      this.nonce = nonce;
      this.proof = proof;
      this.extended_data = extended_data;
    }

    /**
     * Remove nonce and return new object.
     */
    CiphertextBallotSelection removeNonce() {
      return new CiphertextBallotSelection(this.object_id, this.description_hash, this.ciphertext,
              this.crypto_hash, this.is_placeholder_selection, Optional.empty(),
              this.proof, this.extended_data);
    }

    /**
     * Given an encrypted BallotSelection, validates the encryption state against a specific seed hash and public key.
     * Calling this function expects that the object is in a well-formed encrypted state
     * with the elgamal encrypted `message` field populated along with the DisjunctiveChaumPedersenProof `proof` populated.
     * the ElementModQ `description_hash` and the ElementModQ `crypto_hash` are also checked.
     *
     * @param seed_hash:              the hash of the SelectionDescription, or
     *                               whatever `ElementModQ` was used to populate the `description_hash` field.
     * @param elgamelPublicKey:      The election key
     * @param cryptoExtendedBaseHash
     */
    boolean is_valid_encryption(ElementModQ seed_hash, ElementModP elgamelPublicKey, ElementModQ cryptoExtendedBaseHash) {
      if (!seed_hash.equals(this.description_hash)) {
        logger.atInfo().log("mismatching selection hash: %s expected(%s), actual(%s)",
                this.object_id, seed_hash, this.description_hash);
        return false;
      }

      ElementModQ recalculated_crypto_hash = crypto_hash_with(seed_hash);
      if (!recalculated_crypto_hash.equals(this.crypto_hash)) {
        logger.atInfo().log("mismatching crypto hash: %s expected(%s), actual(%s)",
                this.object_id, recalculated_crypto_hash, this.crypto_hash);
        return false;
      }

      if (!this.proof.isPresent()) {
        logger.atInfo().log("no proof exists for: %s", this.object_id);
        return false;
      }

      return proof.get().is_valid(ciphertext, elgamelPublicKey, cryptoExtendedBaseHash);
    }

    /**
     * Given an encrypted BallotSelection, generates a hash, suitable for rolling up
     * into a hash / tracking code for an entire ballot. Of note, this particular hash examines
     * the `seed_hash` and `message`, but not the proof.
     * This is deliberate, allowing for the possibility of ElectionGuard variants running on
     * much more limited hardware, wherein the Disjunctive Chaum-Pedersen proofs might be computed
     * later on.
     * <p>
     * In most cases the seed_hash should match the `description_hash`
     */
    ElementModQ crypto_hash_with(ElementModQ seedHash) {
      return _ciphertext_ballot_selection_crypto_hash_with(this.object_id, seedHash, this.ciphertext);
    }
  }

  private static ElementModQ _ciphertext_ballot_selection_crypto_hash_with(
          String object_id, ElementModQ seed_hash, ElGamal.Ciphertext ciphertext) {
    return Hash.hash_elems(object_id, seed_hash, ciphertext.crypto_hash());
  }

  /**
   * Constructs a `CipherTextBallotSelection` object. Most of the parameters here match up to fields
   * in the class, but this helper function will optionally compute a Chaum-Pedersen proof if the
   * given nonce isn't `None`. Likewise, if a crypto_hash is not provided, it will be derived from
   * the other fields.
   */

  static CiphertextBallotSelection make_ciphertext_ballot_selection(
          String object_id,
          ElementModQ description_hash,
          ElGamal.Ciphertext ciphertext,
          ElementModP elgamal_public_key,
          ElementModQ crypto_extended_base_hash,
          ElementModQ proof_seed,
          int selection_representation,
          boolean is_placeholder_selection,
          Optional<ElementModQ> nonce,
          Optional<ElementModQ> crypto_hash,
          Optional<ChaumPedersen.DisjunctiveChaumPedersenProof> proof,
          Optional<ElGamal.Ciphertext> extended_data) {

    if (crypto_hash.isEmpty()) {
      crypto_hash = Optional.of(_ciphertext_ballot_selection_crypto_hash_with(object_id, description_hash, ciphertext));
    }

    if (proof.isEmpty()) {
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

    return new CiphertextBallotSelection(
            object_id,
            description_hash,
            ciphertext,
            crypto_hash.get(),
            is_placeholder_selection,
            nonce,
            proof,
            extended_data);
  }

  /**
   * A PlaintextBallotContest represents the selections made by a voter for a specific ContestDescription
   * <p>
   * this class can be either a partial or a complete representation of a contest dataset.  Specifically,
   * a partial representation must include at a minimum the "affirmative" selections of a contest.
   * A complete representation of a ballot must include both affirmative and negative selections of
   * the contest, AND the placeholder selections necessary to satisfy the ConstantChaumPedersen proof
   * in the CiphertextBallotContest.
   * <p>
   * Typically partial contests are passed into Electionguard for memory constrained systems,
   * while complete contests are passed into ElectionGuard when running encryption on an existing dataset.
   */
  static class PlaintextBallotContest extends ElectionObjectBase {
    List<PlaintextBallotSelection> ballot_selections; // Collection of ballot selections

    PlaintextBallotContest(String object_id, List<PlaintextBallotSelection> ballot_selections) {
      super(object_id);
      this.ballot_selections = ballot_selections;
    }

    /**
     * Given a PlaintextBallotContest returns true if the state is representative of the expected values.
     * <p>
     * Note: because this class supports partial representations, undervotes are considered a valid state.
     */
    boolean is_valid(
            String expected_object_id,
            int expected_number_selections,
            int expected_number_elected,
            Optional<Integer> votes_allowed) {

      if (!this.object_id.equals(expected_object_id)) {
        logger.atInfo().log("invalid object_id: expected(%s) actual(%s)", expected_object_id, this.object_id);
        return false;
      }

      if (this.ballot_selections.size() > expected_number_selections) {
        logger.atInfo().log("invalid number_selections: expected(%s) actual(%s)", expected_number_selections, this.ballot_selections);
        return false;
      }

      int number_elected = 0;
      int votes = 0;

      // Verify the selections are well-formed
      for (PlaintextBallotSelection selection : this.ballot_selections) {
        int selection_count = selection.to_int();
        votes += selection_count;
        if (selection_count >= 1) {
          number_elected += 1;
        }
      }

      if (number_elected > expected_number_elected) {
        logger.atInfo().log("invalid number_elected: expected(%s) actual(%s)", expected_number_elected, number_elected);
        return false;
      }

      if (votes_allowed.isPresent() && votes > votes_allowed.get()) {
        logger.atInfo().log("invalid votes: expected(%s) actual(%s)", votes_allowed, votes);
        return false;
      }
      return true;
    }
  }

  /**
   * A CiphertextBallotContest represents the selections made by a voter for a specific ContestDescription
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
  static class CiphertextBallotContest extends ElectionObjectBase implements Hash.CryptoHashCheckable {
    ElementModQ description_hash; // Hash from contestDescription

    List<CiphertextBallotSelection> ballot_selections; //Collection of ballot selections

    ElementModQ crypto_hash; // Hash of the encrypted values

    private Optional<ElementModQ> nonce; // The nonce used to generate the encryption. Sensitive & should be treated as a secret.

    //     The proof demonstrates the sum of the selections does not exceed the maximum
    //    available selections for the contest, and that the proof was generated with the nonce
    Optional<ChaumPedersen.ConstantChaumPedersenProof> proof;

    public CiphertextBallotContest(String object_id, ElementModQ description_hash,
                                   List<CiphertextBallotSelection> ballot_selections, ElementModQ crypto_hash, Optional<ElementModQ> nonce,
                                   Optional<ChaumPedersen.ConstantChaumPedersenProof> proof) {
      super(object_id);
      this.description_hash = description_hash;
      this.ballot_selections = ballot_selections;
      this.crypto_hash = crypto_hash;
      this.nonce = nonce;
      this.proof = proof;
    }

    /**
     * Remove nonces and return new object.
     */
    CiphertextBallotContest removeNonces() {
      // new_selections = [replace(selection, nonce = None) for selection in contest.ballot_selections]
      List<CiphertextBallotSelection> new_selections =
              this.ballot_selections.stream().map(s -> s.removeNonce()).collect(Collectors.toList());

      // new_contest = replace(contest, nonce=None, ballot_selections=new_selections)
      return new CiphertextBallotContest(this.object_id, this.description_hash, new_selections, this.crypto_hash,
              Optional.empty(), this.proof);
    }

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
    public ElementModQ crypto_hash_with(ElementModQ seed_hash) {
      return _ciphertext_ballot_context_crypto_hash(this.object_id, this.ballot_selections, seed_hash);
    }

    /**
     * Add the individual ballot_selections `message` fields together, suitable for use
     * in a Chaum-Pedersen proof.
     */
    ElGamal.Ciphertext elgamal_accumulate() {
      return _ciphertext_ballot_elgamal_accumulate(this.ballot_selections);
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
            ElementModQ seed_hash,
            ElementModP elgamal_public_key,
            ElementModQ crypto_extended_base_hash) {

      if (!seed_hash.equals(this.description_hash)) {
        logger.atInfo().log("mismatching contest hash: %s expected(%s), actual(%s)",
                this.object_id, seed_hash, this.description_hash);
        return false;
      }

      ElementModQ recalculated_crypto_hash = this.crypto_hash_with(seed_hash);
      if (!this.crypto_hash.equals(recalculated_crypto_hash)) {
        logger.atInfo().log("mismatching crypto hash: %s expected(%s) actual(%s)", this.object_id, recalculated_crypto_hash, this.crypto_hash);
        return false;
      }

      // NOTE: this check does not verify the proofs of the individual selections by design.

      if (this.proof.isEmpty()) {
        logger.atInfo().log("no proof exists for: %s", this.object_id);
        return false;
      }

      // Verify the sum of the selections matches the proof
      ElGamal.Ciphertext elgamal_accumulation = this.elgamal_accumulate();
      return this.proof.get().is_valid(elgamal_accumulation, elgamal_public_key, crypto_extended_base_hash);
    }
  }

  private static ElGamal.Ciphertext _ciphertext_ballot_elgamal_accumulate(
          List<CiphertextBallotSelection> ballot_selections) {
    // return elgamal_add(*[selection.ciphertext for selection in ballot_selections]);
    List<ElGamal.Ciphertext> texts = ballot_selections.stream().map(selection -> selection.ciphertext).collect(Collectors.toList());
    return ElGamal.elgamal_add(Iterables.toArray(texts, ElGamal.Ciphertext.class));
  }

  private static ElementModQ _ciphertext_ballot_context_crypto_hash(
          String object_id,
          List<CiphertextBallotSelection> ballot_selections,
          ElementModQ seed_hash) {
    if (ballot_selections.size() == 0) {
      //f"mismatching ballot_selections state: {object_id} expected(some), actual(none)"
      return ZERO_MOD_Q;
    }

    // selection_hashes = [selection.crypto_hash for selection in ballot_selections]
    List<ElementModQ> selection_hashes = ballot_selections.stream().map(s -> s.crypto_hash).collect(Collectors.toList());
    return Hash.hash_elems(object_id, seed_hash, selection_hashes.toArray());
  }

  private static Optional<ElementModQ> _ciphertext_ballot_contest_aggregate_nonce(
          String object_id, List<CiphertextBallotSelection> ballot_selections) {

    List<ElementModQ> selection_nonces = new ArrayList<>();
    for (CiphertextBallotSelection selection : ballot_selections) {
      if (selection.nonce.isEmpty()) {
        logger.atInfo().log("missing nonce values for contest %s cannot calculate aggregate nonce", object_id);
        return Optional.empty();
      } else {
        selection_nonces.add(selection.nonce.get());
      }
    }
    return Optional.of(add_q(Iterables.toArray(selection_nonces, ElementModQ.class)));
  }

  /**
   * Constructs a `CipherTextBallotContest` object. Most of the parameters here match up to fields
   * in the class, but this helper function will optionally compute a Chaum-Pedersen proof if the
   * ballot selections include their encryption nonces. Likewise, if a crypto_hash is not provided,
   * it will be derived from the other fields.
   */
  static CiphertextBallotContest make_ciphertext_ballot_contest(
          String object_id,
          ElementModQ description_hash,
          List<CiphertextBallotSelection> ballot_selections,
          ElementModP elgamal_public_key,
          ElementModQ crypto_extended_base_hash,
          ElementModQ proof_seed,
          int number_elected,
          Optional<ElementModQ> crypto_hash,
          Optional<ChaumPedersen.ConstantChaumPedersenProof> proof,
          Optional<ElementModQ> nonce) {

    if (crypto_hash.isEmpty()) {
      crypto_hash = Optional.of(_ciphertext_ballot_context_crypto_hash(object_id, ballot_selections, description_hash));
    }

    Optional<ElementModQ> aggregate = _ciphertext_ballot_contest_aggregate_nonce(object_id, ballot_selections);
    if (proof.isEmpty()) {
      List<CiphertextBallotSelection> finalBallot_selections = ballot_selections;
      proof = aggregate.map(ag ->
              ChaumPedersen.make_constant_chaum_pedersen(
                      _ciphertext_ballot_elgamal_accumulate(finalBallot_selections),
                      number_elected,
                      ag,
                      elgamal_public_key,
                      proof_seed,
                      crypto_extended_base_hash
              )
      );
    }

    return new CiphertextBallotContest(
            object_id,
            description_hash,
            ballot_selections,
            crypto_hash.get(),
            nonce,
            proof);
  }

  /**
   * A PlaintextBallot represents a voters selections for a given ballot and ballot style
   * :field object_id: A unique Ballot ID that is relevant to the external system
   */
  static class PlaintextBallot extends ElectionObjectBase {
    String ballot_style; // The `object_id` of the `BallotStyle` in the `Election` Manifest"""

    List<PlaintextBallotContest> contests; // The list of contests for this ballot

    public PlaintextBallot(String object_id, String ballot_style, List<PlaintextBallotContest> contests) {
      super(object_id);
      this.ballot_style = ballot_style;
      this.contests = contests;
    }

    /**
     * Check if expected ballot style is valid
     * :param expected_ballot_style_id: Expected ballot style id
     * :return: True if valid
     */
    boolean is_valid(String expected_ballot_style_id) {
      if (!this.ballot_style.equals(expected_ballot_style_id)) {
        // f"invalid ballot_style: for: {this.object_id} expected({expected_ballot_style_id}) actual({this.ballot_style})"
        return false;
      }
      return true;
    }
  }

  /**
   * A CiphertextBallot represents a voters encrypted selections for a given ballot and ballot style.
   * <p>
   * When a ballot is in it's complete, encrypted state, the `nonce` is the master nonce
   * from which all other nonces can be derived to encrypt the ballot.  Allong with the `nonce`
   * fields on `Ballotcontest` and `BallotSelection`, this value is sensitive.
   * <p>
   * Don't make this directly. Use `make_ciphertext_ballot` instead.
   * :field object_id: A unique Ballot ID that is relevant to the external system
   */
  @Immutable
  static class CiphertextBallot extends ElectionObjectBase implements Hash.CryptoHashCheckable {
    final String ballot_style;
    final ElementModQ description_hash;
    final ElementModQ previous_tracking_hash;
    final List<CiphertextBallotContest> contests;
    final Optional<ElementModQ> tracking_hash;
    final long timestamp; // TODO something better
    final ElementModQ crypto_hash;
    final Optional<ElementModQ> nonce;

    public CiphertextBallot(String object_id, String ballot_style, ElementModQ description_hash,
                            ElementModQ previous_tracking_hash, List<CiphertextBallotContest> contests,
                            Optional<ElementModQ> tracking_hash, long timestamp, ElementModQ crypto_hash,
                            Optional<ElementModQ> nonce) {
      super(object_id);
      this.ballot_style = ballot_style;
      this.description_hash = description_hash;
      this.previous_tracking_hash = previous_tracking_hash;
      this.contests = contests;
      this.tracking_hash = tracking_hash;
      this.timestamp = timestamp;
      this.crypto_hash = crypto_hash;
      this.nonce = nonce;
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
    public ElementModQ crypto_hash_with(ElementModQ seed_hash) {
      if (this.contests.size() == 0) {
        // f"mismatching contests state: {this.object_id} expected(some), actual(none)"
        return ZERO_MOD_Q;
      }

      // contest_hashes = [contest.crypto_hash for contest in this.contests]
      List<ElementModQ> selection_hashes = contests.stream().map(s -> s.crypto_hash).collect(Collectors.toList());
      return Hash.hash_elems(this.object_id, seed_hash, selection_hashes.toArray());
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
            ElementModQ seed_hash,
            ElementModP elgamal_public_key,
            ElementModQ crypto_extended_base_hash) {

      if (!seed_hash.equals(this.description_hash)) {
        logger.atInfo().log("mismatching ballot hash: %s expected(%s) actual(%s)", this.object_id, seed_hash, this.description_hash);
        return false;
      }

      ElementModQ recalculated_crypto_hash = this.crypto_hash_with(seed_hash);
      if (!recalculated_crypto_hash.equals(this.crypto_hash)) {
        logger.atInfo().log("mismatching crypto hash: %s expected(%s) actual(%s)", this.object_id, recalculated_crypto_hash, this.crypto_hash);
        return false;
      }

      // Check the proofs on the ballot
      boolean valid = true;
      for (CiphertextBallotContest contest : this.contests) {
        for (CiphertextBallotSelection selection : contest.ballot_selections) {
          valid &= selection.is_valid_encryption(selection.description_hash, elgamal_public_key, crypto_extended_base_hash);
        }
        valid &= contest.is_valid_encryption(contest.description_hash, elgamal_public_key, crypto_extended_base_hash);
      }
      return valid;
    }

    /**
     * :return: a representation of the election and the external Id in the nonce's used
     * to derive other nonce values on the ballot
     */
    static ElementModQ nonce_seed(ElementModQ description_hash, String object_id, ElementModQ nonce) {
      return Hash.hash_elems(description_hash, object_id, nonce);
    }
  }

  /**
   * Enumeration used when marking a ballot as cast or spoiled
   */
  enum BallotBoxState {
    CAST, /* A ballot that has been explicitly cast */
    SPOILED, // A ballot that has been explicitly spoiled
    UNKNOWN; // A ballot whose state is unknown to ElectionGuard and will not be included in any election results
  }

  /**
   * A `CiphertextAcceptedBallot` represents a ballot that is accepted for inclusion in election results.
   * An accepted ballot is or is about to be either cast or spoiled.
   * The state supports the `BallotBoxState.UNKNOWN` enumeration to indicate that this object is mutable
   * and has not yet been explicitly assigned a specific state.
   * <p>
   * Note, additionally, this ballot includes all proofs but no nonces.
   * <p>
   * Do not make this class directly. Use `make_ciphertext_accepted_ballot` or `from_ciphertext_ballot` instead.
   */
  static class CiphertextAcceptedBallot extends CiphertextBallot {
    final BallotBoxState state;

    public CiphertextAcceptedBallot(String object_id,
                                    String ballot_style,
                                    ElementModQ description_hash,
                                    ElementModQ previous_tracking_hash,
                                    List<CiphertextBallotContest> contests,
                                    Optional<ElementModQ> tracking_hash,
                                    long timestamp,
                                    ElementModQ crypto_hash,
                                    Optional<ElementModQ> nonce,
                                    BallotBoxState state) {
      super(object_id, ballot_style, description_hash, previous_tracking_hash, contests, tracking_hash, timestamp, crypto_hash, nonce);
      this.state = state;
    }
  }

  /**
   * Makes a `CiphertextBallot`, initially in the state where it's neither been cast nor spoiled.
   * <p>
   * :param object_id: the object_id of this specific ballot
   * :param ballot_style: The `object_id` of the `BallotStyle` in the `Election` Manifest
   * :param description_hash: Hash of the election metadata
   * :param crypto_base_hash: Hash of the cryptographic election context
   * :param contests: List of contests for this ballot
   * :param timestamp: Timestamp at which the ballot encryption is generated in tick
   * :param previous_tracking_hash: Previous tracking hash or seed hash
   * :param nonce: optional nonce used as part of the encryption process
   *
   * @return
   */
  static CiphertextBallot make_ciphertext_ballot(
          String object_id,
          String ballot_style,
          ElementModQ description_hash,
          Optional<ElementModQ> previous_tracking_hashO,
          List<CiphertextBallotContest> contests,
          Optional<ElementModQ> nonce,
          Optional<Long> timestamp,
          Optional<ElementModQ> tracking_hash) {

    if (contests.isEmpty()) {
      logger.atInfo().log("ciphertext ballot with no contests: %s", object_id);
    }

    List<ElementModQ> contest_hashes = contests.stream().map(c -> c.crypto_hash).collect(Collectors.toList());
    ElementModQ contest_hash = Hash.hash_elems(object_id, description_hash, Iterables.toArray(contest_hashes, ElementModQ.class));

    long time = timestamp.orElse(System.currentTimeMillis());
    ElementModQ previous_tracking_hash = previous_tracking_hashO.orElse(description_hash);
    if (tracking_hash.isEmpty()) {
      tracking_hash = Optional.of(Tracker.get_rotating_tracker_hash(previous_tracking_hash, time, contest_hash));
    }

    // String object_id, String ballot_style, ElementModQ description_hash,
    //                              ElementModQ previous_tracking_hash, List<CiphertextBallotContest> contests,
    //                              Optional<ElementModQ> tracking_hash, int timestamp, ElementModQ crypto_hash,
    //                              Optional<ElementModQ> nonce
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

  /**
   * Makes a `CiphertextAcceptedBallot`, ensuring that no nonces are part of the contests.
   * <p>
   * :param object_id: the object_id of this specific ballot
   * :param ballot_style: The `object_id` of the `BallotStyle` in the `Election` Manifest
   * :param description_hash: Hash of the election metadata
   * :param previous_tracking_hash: Previous tracking hash or seed hash
   * :param contests: List of contests for this ballot
   * :param timestamp: Timestamp at which the ballot encryption is generated in tick
   * :param state: ballot box state
   */
  static CiphertextAcceptedBallot make_ciphertext_accepted_ballot(
          String object_id,
          String ballot_style,
          ElementModQ description_hash,
          Optional<ElementModQ> previous_tracking_hashO,
          List<CiphertextBallotContest> contests,
          Optional<ElementModQ> tracking_hash,
          Optional<Long> timestampO,
          BallotBoxState state) { // default BallotBoxState.UNKNOWN,

    if (contests.isEmpty()) {
      logger.atInfo().log("ciphertext ballot with no contest: %s", object_id);
    }

    List<ElementModQ> contest_hashes = contests.stream().map(c -> c.crypto_hash).collect(Collectors.toList());
    ElementModQ contest_hash = Hash.hash_elems(object_id, description_hash, contest_hashes);

    long timestamp = timestampO.orElse(System.currentTimeMillis());
    ElementModQ previous_tracking_hash = previous_tracking_hashO.orElse(description_hash);
    if (tracking_hash.isEmpty()) {
      tracking_hash = Optional.of(Tracker.get_rotating_tracker_hash(previous_tracking_hash, timestamp, contest_hash));
    }

    // copy the contests and selections, removing all nonces
    List<CiphertextBallotContest> new_contests = contests.stream().map(CiphertextBallotContest::removeNonces).collect(Collectors.toList());

    return new CiphertextAcceptedBallot(
            object_id,
            ballot_style,
            description_hash,
            previous_tracking_hash,
            new_contests,
            tracking_hash,
            timestamp,
            contest_hash,
            Optional.empty(),
            state);
  }

  /**
   * Convert a `CiphertextBallot` into a `CiphertextAcceptedBallot`, with all nonces removed.
   */
  static CiphertextAcceptedBallot from_ciphertext_ballot(CiphertextBallot ballot, BallotBoxState state) {

    //           String object_id,
    //          String ballot_style,
    //          ElementModQ description_hash,
    //          Optional<ElementModQ> previous_tracking_hashO,
    //          List<CiphertextBallotContest> contests,
    //          Optional<ElementModQ> tracking_hash,
    //          Optional<Long> timestampO,
    //          BallotBoxState state
    return make_ciphertext_accepted_ballot(
            ballot.object_id,
            ballot.ballot_style,
            ballot.description_hash,
            Optional.of(ballot.previous_tracking_hash),
            ballot.contests,
            ballot.tracking_hash,
            Optional.of(ballot.timestamp),
            state);
  }

}