package com.sunya.electionguard;

import com.google.common.base.Preconditions;
import com.google.common.flogger.FluentLogger;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static com.sunya.electionguard.Group.ElementModP;
import static com.sunya.electionguard.Group.ElementModQ;

/** Builder of Guardian of election. */
public class GuardianBuilder extends ElectionObjectBase {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  // a unique number in [1, 256) that is the polynomial x value for this guardian
  private final int sequence_order;
  private final KeyCeremony.CeremonyDetails ceremony_details;
  private final Auxiliary.KeyPair auxiliary_keypair;
  private final KeyCeremony.ElectionKeyPair election_keys; // Ki = election keypair for this Guardian

  // The collection of this guardian's partial key backups that will be shared to other guardians
  private final Map<String, KeyCeremony.ElectionPartialKeyBackup> backups_to_share; // Map(GUARDIAN_ID, ElectionPartialKeyBackup)

  //// From Other Guardians
  // The collection of other guardians' auxiliary public keys that are shared with this guardian
  private final Map<String, Auxiliary.PublicKey> otherGuardianAuxiliaryKeys; // map(GUARDIAN_ID, Auxiliary.PublicKey)

  // The collection of other guardians' election public keys that are shared with this guardian
  private final Map<String, KeyCeremony.ElectionPublicKey> otherGuardianElectionKeys; // map(GUARDIAN_ID, ElectionPublicKey)

  // The collection of other guardians' partial key backups that are shared with this guardian
  private final Map<String, KeyCeremony.ElectionPartialKeyBackup> otherGuardianPartialKeyBackups; // Map(GUARDIAN_ID, ElectionPartialKeyBackup)

  // The collection of other guardians' verifications that they shared their backups correctly
  // LOOK not needed, functionality in KeyCeremonyMediator
  private final Map<String, KeyCeremony.ElectionPartialKeyVerification> otherGuardianVerifications;

  /**
   * Recreate a guardian for production, passing in all needed values.
   * @param coeff the secret polynomial coefficients
   * @param number_of_guardians: the total number of guardians that will participate in the election
   * @param quorum: the count of guardians necessary to decrypt
   */
  public GuardianBuilder(KeyCeremony.CoefficientSet coeff,
                          int number_of_guardians,
                          int quorum,
                          Auxiliary.KeyPair auxiliary_keypair,
                          KeyCeremony.ElectionKeyPair election_keys) {

    super(coeff.guardianId());
    Preconditions.checkArgument(coeff.guardianSequence() > 0 && coeff.guardianSequence() < 256);
    Preconditions.checkArgument(quorum <= number_of_guardians);
    this.sequence_order = coeff.guardianSequence();
    this.ceremony_details = KeyCeremony.CeremonyDetails.create(number_of_guardians, quorum);
    this.auxiliary_keypair = auxiliary_keypair;
    this.election_keys =  election_keys;

    this.backups_to_share = new HashMap<>();
    this.otherGuardianAuxiliaryKeys = new HashMap<>();
    this.otherGuardianElectionKeys = new HashMap<>();
    this.otherGuardianPartialKeyBackups = new HashMap<>();
    this.otherGuardianVerifications = new HashMap<>();

    this.save_auxiliary_public_key(this.share_auxiliary_public_key());
    this.save_election_public_key(this.share_election_public_key());
  }

  /**
   * Create a guardian for production with chosen coefficients, rest is random.
   * @param coeff the secret polynomial coefficients
   * @param number_of_guardians: the total number of guardians that will participate in the election
   * @param quorum: the count of guardians necessary to decrypt
   */
  public static GuardianBuilder createRandom(
         KeyCeremony.CoefficientSet coeff,
         int number_of_guardians,
         int quorum) {

    return new GuardianBuilder(coeff, number_of_guardians, quorum, null);
  }

  // Coefficients are externally chosen
  private GuardianBuilder(KeyCeremony.CoefficientSet coeff,
                          int number_of_guardians,
                          int quorum,
                          @Nullable Auxiliary.KeyPair auxiliary_keypair) {

    super(coeff.guardianId());
    Preconditions.checkArgument(coeff.guardianSequence() > 0 && coeff.guardianSequence() < 256);
    Preconditions.checkArgument(quorum <= number_of_guardians);
    this.sequence_order = coeff.guardianSequence();
    this.ceremony_details = KeyCeremony.CeremonyDetails.create(number_of_guardians, quorum);
    this.auxiliary_keypair = (auxiliary_keypair != null) ? auxiliary_keypair :
            KeyCeremony.generate_rsa_auxiliary_key_pair(); // random, can be overridden in setAuxiliaryKeyPair()
    this.election_keys =  coeff.generate_election_key_pair();

    this.backups_to_share = new HashMap<>();
    this.otherGuardianAuxiliaryKeys = new HashMap<>();
    this.otherGuardianElectionKeys = new HashMap<>();
    this.otherGuardianPartialKeyBackups = new HashMap<>();
    this.otherGuardianVerifications = new HashMap<>();

    this.save_auxiliary_public_key(this.share_auxiliary_public_key());
    this.save_election_public_key(this.share_election_public_key());
  }

  /**
   * Create a guardian used only for testing, everything chosen at random.
   * @param id: the unique identifier for the guardian
   * @param sequence: a unique number in [1, 256) that is the polynomial x value for this guardian
   * @param number_of_guardians: the total number of guardians that will participate in the election
   * @param quorum: the count of guardians necessary to decrypt
   * @param nonce_seed: an optional value that can be used to generate the `ElectionKeyPair`,
   *                    can only use this field for testing. When null, a random nonce is used.
   */
  public static GuardianBuilder createForTesting(String id,
                                                 int sequence,
                                                 int number_of_guardians,
                                                 int quorum,
                                                 @Nullable ElementModQ nonce_seed) {

    return new GuardianBuilder(id, sequence, number_of_guardians, quorum, nonce_seed);
  }

  // Coefficients are generated at random
  private GuardianBuilder(String id,
                          int sequence_order,
                          int number_of_guardians,
                          int quorum,
                          @Nullable ElementModQ nonce_seed) {

    super(id);
    Preconditions.checkArgument(sequence_order > 0 && sequence_order < 256);
    this.sequence_order = sequence_order;
    this.ceremony_details = KeyCeremony.CeremonyDetails.create(number_of_guardians, quorum);
    this.auxiliary_keypair = KeyCeremony.generate_rsa_auxiliary_key_pair();
    this.election_keys =  KeyCeremony.generate_election_key_pair(quorum, nonce_seed);

    this.backups_to_share = new HashMap<>();
    this.otherGuardianAuxiliaryKeys = new HashMap<>();
    this.otherGuardianElectionKeys = new HashMap<>();
    this.otherGuardianPartialKeyBackups = new HashMap<>();
    this.otherGuardianVerifications = new HashMap<>();

    this.save_auxiliary_public_key(this.share_auxiliary_public_key());
    this.save_election_public_key(this.share_election_public_key());
  }

  /**
   * Share public election and auxiliary keys for guardian.
   * @return Public set of election and auxiliary keys
   */
  KeyCeremony.PublicKeySet share_public_keys() {
    return KeyCeremony.PublicKeySet.create(
            this.object_id,
            this.sequence_order,
            this.auxiliary_keypair.public_key,
            this.election_keys.key_pair().public_key,
            this.election_keys.proof());
  }

  /**
   * Save public election and auxiliary keys for another guardian.
   * @param public_key_set: Public set of election and auxiliary keys
   */
  void save_guardian_public_keys(KeyCeremony.PublicKeySet public_key_set) {

    this.save_auxiliary_public_key(
            new Auxiliary.PublicKey(
                    public_key_set.owner_id(),
                    public_key_set.sequence_order(),
                    public_key_set.auxiliary_public_key()));

    this.save_election_public_key(
            KeyCeremony.ElectionPublicKey.create(
                    public_key_set.owner_id(),
                    public_key_set.sequence_order(),
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

  /** Save a guardians auxiliary public key. */
  public void save_auxiliary_public_key(Auxiliary.PublicKey key) {
    this.otherGuardianAuxiliaryKeys.put(key.owner_id, key);
  }

  /** True if all auxiliary public keys have been received. */
  boolean all_auxiliary_public_keys_received() {
    return this.otherGuardianAuxiliaryKeys.size() == this.ceremony_details.number_of_guardians();
  }

  /** Share auxiliary public key with another guardian. */
  Auxiliary.PublicKey share_auxiliary_public_key() {
    return new Auxiliary.PublicKey(this.object_id, this.sequence_order, this.auxiliary_keypair.public_key);
  }

  /** Share coefficient validation set to be used for validating the coefficients post election. */
  public KeyCeremony.CoefficientValidationSet share_coefficient_validation_set() {
    return KeyCeremony.get_coefficient_validation_set(this.object_id, this.election_keys.polynomial());
  }

  /**
   * Share election public key with another guardian.
   * @return Election public key
   */
  KeyCeremony.ElectionPublicKey share_election_public_key() {
    return KeyCeremony.ElectionPublicKey.create(
            this.object_id,
            this.sequence_order,
            this.election_keys.proof(),
            this.election_keys.key_pair().public_key);
  }

  /** Save a guardians election public key. */
  public void save_election_public_key(KeyCeremony.ElectionPublicKey key) {
    this.otherGuardianElectionKeys.put(key.owner_id(), key);
  }

  /** True if all election public keys have been received. */
  boolean all_election_public_keys_received() {
    return this.otherGuardianElectionKeys.size() == this.ceremony_details.number_of_guardians();
  }

  /**
   * Generate all election partial key backups based on existing public keys
   * @param encryptor Encryption function using auxiliary key
   */
  public boolean generate_election_partial_key_backups(@Nullable Auxiliary.Encryptor encryptor) {
    if (!this.all_auxiliary_public_keys_received()) {
      logger.atInfo().log("guardian; %s could not generate election partial key backups: missing auxiliary keys",
              this.object_id);
      return false;
    }

    for (Auxiliary.PublicKey auxiliary_key : this.otherGuardianAuxiliaryKeys.values()) {
      Optional<KeyCeremony.ElectionPartialKeyBackup> backup =
              KeyCeremony.generate_election_partial_key_backup(this.object_id, this.election_keys.polynomial(), auxiliary_key, encryptor);
      if (backup.isEmpty()) {
        logger.atInfo().log("guardian; %s could not generate election partial key backups: failed to encrypt",
                this.object_id);
        return false;
      }
      this.backups_to_share.put(auxiliary_key.owner_id, backup.get());
    }

    return true;
  }

  /**
   * Share election partial key backup with another guardian.
   * @param designated_id: Designated guardian
   * @return Election partial key backup or None.
   */
  Optional<KeyCeremony.ElectionPartialKeyBackup> share_election_partial_key_backup(String designated_id) {
    return Optional.ofNullable(this.backups_to_share.get(designated_id));
  }

  /**
   * Save election partial key backup from another guardian.
   * @param backup: Election partial key backup
   */
  public void save_election_partial_key_backup(KeyCeremony.ElectionPartialKeyBackup backup) {
    this.otherGuardianPartialKeyBackups.put(backup.owner_id(), backup);
  }

  /** True if all election partial key backups have been received. */
  boolean all_election_partial_key_backups_received() {
    return this.otherGuardianPartialKeyBackups.size() == this.ceremony_details.number_of_guardians() - 1;
  }

  /**
   * Verify election partial key backup value is in polynomial.
   * @param guardian_id: Owner of backup to verify
   * @param decryptor Use default if null.
   * @return Election partial key verification or None
   */
  Optional<KeyCeremony.ElectionPartialKeyVerification> verify_election_partial_key_backup(
          String guardian_id,
          @Nullable Auxiliary.Decryptor decryptor) {

    KeyCeremony.ElectionPartialKeyBackup backup = this.otherGuardianPartialKeyBackups.get(guardian_id);
    if (backup == null) {
      return Optional.empty();
    }
    return Optional.of(KeyCeremony.verify_election_partial_key_backup(this.object_id, backup, this.auxiliary_keypair, decryptor));
  }

  /**
   * Publish election backup challenge of election partial key verification.
   * @param guardian_id: Owner of election key
   * @return Election partial key challenge or None
   */
  Optional<KeyCeremony.ElectionPartialKeyChallenge> publish_election_backup_challenge(String guardian_id) {
    KeyCeremony.ElectionPartialKeyBackup backup_in_question = this.backups_to_share.get(guardian_id);
    if (backup_in_question == null) {
      return Optional.empty();
    }
    return Optional.of(KeyCeremony.generate_election_partial_key_challenge(backup_in_question, this.election_keys.polynomial()));
  }

  /**
   * Verify challenge of previous verification of election partial key.
   * @param challenge: Election partial key challenge
   * @return Election partial key verification
   */
  KeyCeremony.ElectionPartialKeyVerification verify_election_partial_key_challenge(KeyCeremony.ElectionPartialKeyChallenge challenge) {
    return KeyCeremony.verify_election_partial_key_challenge(this.object_id, challenge);
  }

  /**
   * Save election partial key verification from another guardian.
   * @param verification: Election partial key verification
   * LOOK not called by the key ceremony, duplicated in KeyCeremonyMediator, so probably not needed
   */
  public void save_election_partial_key_verification(KeyCeremony.ElectionPartialKeyVerification verification) {
    this.otherGuardianVerifications.put(verification.designated_id(), verification);
  }

  /**
   * True if all election partial key backups have been verified.
   * @return All election partial key backups verified
   * LOOK not needed, functionality in KeyCeremonyMediator
   */
  boolean all_election_partial_key_backups_verified() {
    int required = this.ceremony_details.number_of_guardians() - 1;
    if (this.otherGuardianVerifications.size() != required) {
      return false;
    }
    for (KeyCeremony.ElectionPartialKeyVerification verified : this.otherGuardianVerifications.values()) {
      if (!verified.verified()) {
        return false;
      }
    }
    return true;
  }

  /**
   * Creates a joint election key from the public keys of all guardians.
   * @return Optional joint key for election
   * LOOK not needed, functionality in KeyCeremonyMediator
   */
  Optional<ElementModP> publish_joint_key() {
    if (!this.all_election_public_keys_received()) {
      return Optional.empty();
    }
    if (!this.all_election_partial_key_backups_verified()) {
      return Optional.empty();
    }
    return Optional.of(KeyCeremony.combine_election_public_keys(this.otherGuardianElectionKeys));
  }

  // Getters
  KeyCeremony.CeremonyDetails ceremony_details() {
    return ceremony_details;
  }

  public Guardian build() {
    return new Guardian(object_id, sequence_order,
      ceremony_details,
      auxiliary_keypair,
      election_keys,
      otherGuardianAuxiliaryKeys,
      otherGuardianElectionKeys,
      otherGuardianPartialKeyBackups);
  }
}
