package com.sunya.electionguard;

import com.google.common.flogger.FluentLogger;

import javax.annotation.Nullable;
import java.math.BigInteger;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static com.sunya.electionguard.Group.*;
import static com.sunya.electionguard.KeyCeremony.*;


/**
 * Guardian of election responsible for safeguarding information and decrypting results.
 */
public class Guardian extends ElectionObjectBase {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  final int sequence_order;
  CeremonyDetails ceremony_details;
  Auxiliary.KeyPair _auxiliary_keys;
  ElectionKeyPair _election_keys;
  // The collection of this guardian's partial key backups that will be shared to other guardians
  final Map<String, ElectionPartialKeyBackup> _backups_to_share;

  //// From Other Guardians
  // The collection of other guardians' auxiliary public keys that are shared with this guardian
  final Map<String, Auxiliary.PublicKey> _guardian_auxiliary_public_keys;

  // The collection of other guardians' election public keys that are shared with this guardian
  final Map<String, ElectionPublicKey> _guardian_election_public_keys;

  // The collection of other guardians' partial key backups that are shared with this guardian
  final Map<String, ElectionPartialKeyBackup> _guardian_election_partial_key_backups;

  // The collection of other guardians' verifications that they shared their backups correctly
  final Map<String, ElectionPartialKeyVerification> _guardian_election_partial_key_verifications;

  /**
   * Initialize a guardian with the specified arguments
   * <p>
   * @param id: the unique identifier for the guardian
   * @param sequence_order: a unique number in [0, 256) that identifies this guardian
   * @param number_of_guardians: the total number of guardians that will participate in the election
   * @param quorum: the count of guardians necessary to decrypt
   * @param nonce_seed: an optional `ElementModQ` value that can be used to generate the `ElectionKeyPair`.
   * It is recommended to only use this field for testing.
   */
  public Guardian(String id,
           int sequence_order,
           int number_of_guardians,
           int quorum,
           @Nullable Group.ElementModQ nonce_seed) {

    super(id);
    this.sequence_order = sequence_order;
    this.set_ceremony_details(number_of_guardians, quorum);
    this._backups_to_share = new HashMap<>();
    this._guardian_auxiliary_public_keys = new HashMap<>();
    this._guardian_election_public_keys = new HashMap<>();
    this._guardian_election_partial_key_backups = new HashMap<>();
    this._guardian_election_partial_key_verifications = new HashMap<>();

    this.generate_auxiliary_key_pair();
    this.generate_election_key_pair(nonce_seed);
  }

  /**
   * Reset guardian to initial state
   * @param number_of_guardians: Number of guardians in election
   * @param quorum: Quorum of guardians required to decrypt
   */
  void reset(int number_of_guardians, int quorum) {
    this._backups_to_share.clear();
    this._guardian_auxiliary_public_keys.clear();
    this._guardian_election_public_keys.clear();
    this._guardian_election_partial_key_backups.clear();
    this._guardian_election_partial_key_verifications.clear();
    this.set_ceremony_details(number_of_guardians, quorum);
    this.generate_auxiliary_key_pair();
    this.generate_election_key_pair(null);
  }

  /**
   * Set ceremony details for election
   * @param number_of_guardians: Number of guardians in election
   * @param quorum: Quorum of guardians required to decrypt
   */
  void set_ceremony_details(int number_of_guardians, int quorum) {
    this.ceremony_details = CeremonyDetails.create(number_of_guardians, quorum);
  }

  /**
   * Share public election and auxiliary keys for guardian
   * @return Public set of election and auxiliary keys
   */
  PublicKeySet share_public_keys() {
    return PublicKeySet.create(
            this.object_id,
            this.sequence_order,
            this._auxiliary_keys.public_key,
            this._election_keys.key_pair().public_key,
            this._election_keys.proof());
  }

  /**
   * Save public election and auxiliary keys for another guardian
   * @param public_key_set: Public set of election and auxiliary keys
   */
  void save_guardian_public_keys(PublicKeySet public_key_set) {

    this.save_auxiliary_public_key(
            new Auxiliary.PublicKey(
                    public_key_set.owner_id(),
                    public_key_set.sequence_order(),
                    public_key_set.auxiliary_public_key()));

    this.save_election_public_key(
            ElectionPublicKey.create(
                    public_key_set.owner_id(),
                    public_key_set.election_public_key_proof(),
                    public_key_set.election_public_key()));
  }

  /**
   * True if all election and auxiliary public keys have been received.
   * @return All election and auxiliary public keys backups received
   */
  boolean all_public_keys_received() {
    return this.all_auxiliary_public_keys_received() && this.all_election_public_keys_received();
  }

  /**
   * Generate auxiliary key pair
   */
  void generate_auxiliary_key_pair() {
    this._auxiliary_keys = generate_rsa_auxiliary_key_pair();
    this.save_auxiliary_public_key(this.share_auxiliary_public_key());
  }

  /**
   * Save a guardians auxiliary public key.
   */
  void save_auxiliary_public_key(Auxiliary.PublicKey key) {
    this._guardian_auxiliary_public_keys.put(key.owner_id, key);
  }

  /**
   * True if all auxiliary public keys have been received.
   * @return All auxiliary public keys backups received
   */
  boolean all_auxiliary_public_keys_received() {
    return this._guardian_auxiliary_public_keys.size() == this.ceremony_details.number_of_guardians();
  }

  // guardian_auxiliary_public_keys() not used

  /**         Get a read-only view of the Guardian Election Public Keys shared with this Guardian. */
  Map<String, ElectionPublicKey> guardian_election_public_keys() {
          // TODO make readonly
          return _guardian_election_public_keys;
  }

  /**
   * Share auxiliary public key with another guardian.
   */
  Auxiliary.PublicKey share_auxiliary_public_key() {
    return new Auxiliary.PublicKey(this.object_id, this.sequence_order, this._auxiliary_keys.public_key);
  }

  /** Share coefficient validation set to be used for validating the coefficients post election. */
  CoefficientValidationSet share_coefficient_validation_set() {
    return KeyCeremony.get_coefficient_validation_set(this.object_id, this._election_keys.polynomial());
  }

  /**
   * Generate election key pair for encrypting/decrypting election.
   */
  void generate_election_key_pair(@Nullable Group.ElementModQ nonce) {
    this._election_keys = KeyCeremony.generate_election_key_pair(this.ceremony_details.quorum(), nonce);
    this.save_election_public_key(this.share_election_public_key());
  }

  /**
   * Share election public key with another guardian
   * @return Election public key
   */
  ElectionPublicKey share_election_public_key() {
    return ElectionPublicKey.create(
            this.object_id,
            this._election_keys.proof(),
            this._election_keys.key_pair().public_key);
  }

  /**
   * Save a guardians election public key.
   */
  void save_election_public_key(ElectionPublicKey key) {
    this._guardian_election_public_keys.put(key.owner_id(), key);
  }

  /**
   * True if all election public keys have been received.
   * @return All election public keys backups received
   */
  boolean all_election_public_keys_received() {
    return this._guardian_election_public_keys.size() == this.ceremony_details.number_of_guardians();
  }

  /**
   * Generate all election partial key backups based on existing public keys
   * @param encryptor Encryption function using auxiliary key
   */
  boolean generate_election_partial_key_backups(@Nullable Auxiliary.Encryptor encryptor) {

    if (!this.all_auxiliary_public_keys_received()) {
      logger.atInfo().log("guardian; %s could not generate election partial key backups: missing auxiliary keys",
              this.object_id);
      return false;
    }

    for (Auxiliary.PublicKey auxiliary_key : this._guardian_auxiliary_public_keys.values()) {
      Optional<ElectionPartialKeyBackup> backup =
              generate_election_partial_key_backup(this.object_id, this._election_keys.polynomial(), auxiliary_key, encryptor);
      if (backup.isEmpty()) {
        logger.atInfo().log("guardian; %s could not generate election partial key backups: failed to encrypt",
                this.object_id);
        return false;
      }
      this._backups_to_share.put(auxiliary_key.owner_id, backup.get());
    }

    return true;
  }

  /**
   * Share election partial key backup with another guardian
   * @param designated_id: Designated guardian
   * @return Election partial key backup or None.
   */
  Optional<ElectionPartialKeyBackup> share_election_partial_key_backup(String designated_id) {
    return Optional.ofNullable(this._backups_to_share.get(designated_id));
  }

  /**
   * Save election partial key backup from another guardian
   * @param backup: Election partial key backup
   */
  void save_election_partial_key_backup(ElectionPartialKeyBackup backup) {
    this._guardian_election_partial_key_backups.put(backup.owner_id(), backup);
  }

  /**
   * True if all election partial key backups have been received.
   */
  boolean all_election_partial_key_backups_received() {
    return this._guardian_election_partial_key_backups.size() == this.ceremony_details.number_of_guardians() - 1;
  }

  /**
   * Verify election partial key backup value is in polynomial
   * @param guardian_id: Owner of backup to verify
   * @param decryptor
   * @return Election partial key verification or None
   */
  Optional<ElectionPartialKeyVerification> verify_election_partial_key_backup(
          String guardian_id,
          @Nullable Auxiliary.Decryptor decryptor) {

    ElectionPartialKeyBackup backup = this._guardian_election_partial_key_backups.get(guardian_id);
    if (backup == null) {
      return Optional.empty();
    }
    return Optional.of(KeyCeremony.verify_election_partial_key_backup(this.object_id, backup, this._auxiliary_keys, decryptor));
  }

  /**
   * Publish election backup challenge of election partial key verification
   * @param guardian_id: Owner of election key
   * @return Election partial key challenge or None
   */
  Optional<ElectionPartialKeyChallenge> publish_election_backup_challenge(String guardian_id) {
    ElectionPartialKeyBackup backup_in_question = this._backups_to_share.get(guardian_id);
    if (backup_in_question == null) {
      return Optional.empty();
    }
    return Optional.of(KeyCeremony.generate_election_partial_key_challenge(backup_in_question, this._election_keys.polynomial()));
  }

  /**
   * Verify challenge of previous verification of election partial key
   * @param challenge: Election partial key challenge
   * @return Election partial key verification
   */
  ElectionPartialKeyVerification verify_election_partial_key_challenge(ElectionPartialKeyChallenge challenge) {
    return KeyCeremony.verify_election_partial_key_challenge(this.object_id, challenge);
  }

  /**
   * Save election partial key verification from another guardian
   * @param verification: Election partial key verification
   */
  void save_election_partial_key_verification(ElectionPartialKeyVerification verification) {
    this._guardian_election_partial_key_verifications.put(verification.designated_id(), verification);
  }

  /**
   * True if all election partial key backups have been verified.
   * @return All election partial key backups verified
   */
  boolean all_election_partial_key_backups_verified() {
    int required = this.ceremony_details.number_of_guardians() - 1;
    if (this._guardian_election_partial_key_verifications.size() != required) {
      return false;
    }
    for (ElectionPartialKeyVerification verified : this._guardian_election_partial_key_verifications.values()) {
      if (!verified.verified()) {
        return false;
      }
    }
    return true;
  }

  /**
   * Creates a joint election key from the public keys of all guardians
   * @return Optional joint key for election
   */
  Optional<Group.ElementModP> publish_joint_key() {
    if (!this.all_election_public_keys_received()) {
      return Optional.empty();
    }
    if (!this.all_election_partial_key_backups_verified()) {
      return Optional.empty();
    }
    return Optional.of(KeyCeremony.combine_election_public_keys(this._guardian_election_public_keys));
  }

  /**
   * Compute a partial decryption of an elgamal encryption
   *
   * @param elgamal:            the `ElGamalCiphertext` that will be partially decrypted
   * @param extended_base_hash: the extended base hash of the election that
   *                            was used to generate t he ElGamal Ciphertext
   * @param nonce_seed:         an optional value used to generate the `ChaumPedersenProof`
   *                            if no value is provided, a random number will be used.
   * @return a `Tuple[ElementModP, ChaumPedersenProof]` of the decryption and its proof
   */
  Tuple partially_decrypt(
          ElGamal.Ciphertext elgamal,
          ElementModQ extended_base_hash,
          @Nullable ElementModQ nonce_seed) {

    if (nonce_seed == null) {
      nonce_seed = rand_q();
    }

    //TODO: ISSUE #47: Decrypt the election secret key

    // 𝑀_i = 𝐴^𝑠𝑖 mod 𝑝
    ElementModP partial_decryption = elgamal.partial_decrypt(this._election_keys.key_pair().secret_key);

    // ElGamal.Ciphertext message,
    //          ElementModQ s,
    //          ElementModP m,
    //          ElementModQ seed,
    //          ElementModQ hash_header
    // 𝑀_i = 𝐴^𝑠𝑖 mod 𝑝 and 𝐾𝑖 = 𝑔^𝑠𝑖 mod 𝑝
    ChaumPedersen.ChaumPedersenProof proof = ChaumPedersen.make_chaum_pedersen(
            elgamal,
            this._election_keys.key_pair().secret_key,
            partial_decryption,
            nonce_seed,
            extended_base_hash);

    return new Tuple(partial_decryption, proof);
  }

  static class Tuple {
    final ElementModP decryption;
    final ChaumPedersen.ChaumPedersenProof proof;

    Tuple(ElementModP partial_decryption, ChaumPedersen.ChaumPedersenProof proof) {
      this.decryption = partial_decryption;
      this.proof = proof;
    }
  }

  /**
   * Compute a compensated partial decryption of an elgamal encryption
   * on behalf of the missing guardian
   * <p>
   *
   * @param missing_guardian_id: the guardian
   * @param elgamal:             the `ElGamalCiphertext` that will be partially decrypted
   * @param extended_base_hash:  the extended base hash of the election that
   *                             was used to generate t he ElGamal Ciphertext
   * @param nonce_seed:          an optional value used to generate the `ChaumPedersenProof`
   *                             if no value is provided, a random number will be used.
   * @param decryptor            an `AuxiliaryDecrypt` function to decrypt the missing guardian private key backup
   * @return a `Tuple[ElementModP, ChaumPedersenProof]` of the decryption and its proof
   */
  Optional<Tuple> compensate_decrypt(
          String missing_guardian_id,
          ElGamal.Ciphertext elgamal,
          ElementModQ extended_base_hash,
          @Nullable ElementModQ nonce_seed,
          Auxiliary.Decryptor decryptor) {

    if (nonce_seed == null) {
      nonce_seed = rand_q();
    }

    ElectionPartialKeyBackup backup = this._guardian_election_partial_key_backups.get(missing_guardian_id);
    if (backup == null) {
      logger.atInfo().log("compensate decrypt guardian %s missing backup for %s",
              this.object_id, missing_guardian_id);
      return Optional.empty();
    }

    Optional<String> decrypted_value = decryptor.decrypt(backup.encrypted_value(), this._auxiliary_keys.secret_key);
    if (decrypted_value.isEmpty()) {
      logger.atInfo().log("compensate decrypt guardian %s failed decryption for %s",
              this.object_id, missing_guardian_id);
      return Optional.empty();
    }
    ElementModQ partial_secret_key = hex_to_q(decrypted_value.get()).orElseThrow(IllegalStateException::new);

    // 𝑀_{𝑖,l} = 𝐴^P𝑖_{l}
    ElementModP partial_decryption = elgamal.partial_decrypt(partial_secret_key);

    // 𝑀_{𝑖,l} = 𝐴^𝑠𝑖 mod 𝑝 and 𝐾𝑖 = 𝑔^𝑠𝑖 mod 𝑝
    ChaumPedersen.ChaumPedersenProof proof = ChaumPedersen.make_chaum_pedersen(
            elgamal,
            partial_secret_key,
            partial_decryption,
            nonce_seed,
            extended_base_hash);

    return Optional.of(new Tuple(partial_decryption, proof));
  }

  /**
   * Compute the recovery public key for a given guardian .
   */
  Optional<ElementModP> recovery_public_key_for(String missing_guardian_id) {
    ElectionPartialKeyBackup backup = this._guardian_election_partial_key_backups.get(missing_guardian_id);
    if (backup == null) {
      logger.atInfo().log("compensate decrypt guardian %s missing backup for %s",
              this.object_id, missing_guardian_id);
      return Optional.empty();
    }

    // compute the recovery public key,
    // corresponding to the secret share Pi(l)
    // K_ij^(l^j) for j in 0..k-1.  K_ij is coefficients[j].public_key
    ElementModP pub_key = ONE_MOD_P;
    int count = 0;
    for (ElementModP commitment : backup.coefficient_commitments()) {
      ElementModQ exponent = pow_q(BigInteger.valueOf(this.sequence_order), BigInteger.valueOf(count));
      pub_key = mult_p(pub_key, pow_p(commitment, exponent));
      count++;
    }

    return Optional.of(pub_key);
  }
}
