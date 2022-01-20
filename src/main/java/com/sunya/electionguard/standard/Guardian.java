package com.sunya.electionguard.standard;

import com.google.common.base.Preconditions;
import com.google.common.flogger.FluentLogger;
import com.sunya.electionguard.Auxiliary;
import com.sunya.electionguard.ChaumPedersen;
import com.sunya.electionguard.CiphertextElectionContext;
import com.sunya.electionguard.CiphertextTally;
import com.sunya.electionguard.DecryptionProofTuple;
import com.sunya.electionguard.DecryptionShare;
import com.sunya.electionguard.Decryptions;
import com.sunya.electionguard.ElGamal;
import com.sunya.electionguard.GuardianRecord;
import com.sunya.electionguard.GuardianRecordPrivate;
import com.sunya.electionguard.KeyCeremony;
import com.sunya.electionguard.SubmittedBallot;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static com.sunya.electionguard.Group.ElementModP;
import static com.sunya.electionguard.Group.ElementModQ;
import static com.sunya.electionguard.Group.rand_q;

/**
 * Builder of Guardians for an election.
 * DO NOT USE IN PRODUCTION, as Guardian is inherently unsafe.
 */
class Guardian {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  public final String object_id;
  public final int sequence_order;
  private final KeyCeremony.CeremonyDetails ceremony_details;
  private final Auxiliary.KeyPair auxiliary_keys;
  private final KeyCeremony.ElectionKeyPair election_keys; // Ki = election keypair for this Guardian

  // The collection of this guardian's partial key backups that will be shared to other guardians
  private final Map<String, KeyCeremony.ElectionPartialKeyBackup> backups_to_share; // Map(GUARDIAN_ID, ElectionPartialKeyBackup)

  //// From Other Guardians
  // The collection of other guardians' auxiliary public keys that are shared with this guardian
  private final Map<String, Auxiliary.PublicKey> otherGuardianAuxiliaryKeys; // map(GUARDIAN_ID, Auxiliary.PublicKey)

  // The collection of other guardians' election public keys that are shared with this guardian
  private final Map<String, KeyCeremony.ElectionPublicKey> guardian_election_public_keys; // map(GUARDIAN_ID, ElectionPublicKey)

  // The collection of other guardians' partial key backups that are shared with this guardian
  private final Map<String, KeyCeremony.ElectionPartialKeyBackup> guardian_election_partial_key_backups; // Map(GUARDIAN_ID, ElectionPartialKeyBackup)

  // The collection of other guardians' verifications that they shared their backups correctly
  private final Map<String, KeyCeremony.ElectionPartialKeyVerification> guardian_election_partial_key_verifications;

  /**
   * Recreate a guardian for production, passing in all needed values.
   * @param coeff the secret polynomial coefficients
   * @param number_of_guardians: the total number of guardians that will participate in the election
   * @param quorum: the count of guardians necessary to decrypt
   */
  Guardian(KeyCeremony.CoefficientSet coeff,
                  int number_of_guardians,
                  int quorum,
                  Auxiliary.KeyPair auxiliary_keys,
                  KeyCeremony.ElectionKeyPair election_keys) {

    this.object_id = coeff.guardianId();
    Preconditions.checkArgument(coeff.guardianSequence() > 0 && coeff.guardianSequence() < 256);
    Preconditions.checkArgument(quorum <= number_of_guardians);
    this.sequence_order = coeff.guardianSequence();
    this.ceremony_details = KeyCeremony.CeremonyDetails.create(number_of_guardians, quorum);
    this.auxiliary_keys = auxiliary_keys;
    this.election_keys =  election_keys;

    this.backups_to_share = new HashMap<>();
    this.otherGuardianAuxiliaryKeys = new HashMap<>();
    this.guardian_election_public_keys = new HashMap<>();
    this.guardian_election_partial_key_backups = new HashMap<>();
    this.guardian_election_partial_key_verifications = new HashMap<>();

    this.save_auxiliary_public_key(this.share_auxiliary_public_key());
    this.save_election_public_key(this.share_election_public_key());
  }

  /**
   * Create a guardian for production with chosen coefficients, rest is random.
   * @param coeff the secret polynomial coefficients
   * @param number_of_guardians: the total number of guardians that will participate in the election
   * @param quorum: the count of guardians necessary to decrypt
   */
  static Guardian createRandom(
         KeyCeremony.CoefficientSet coeff,
         int number_of_guardians,
         int quorum) {

    return new Guardian(coeff, number_of_guardians, quorum, null);
  }

  // Coefficients are externally chosen
  private Guardian(KeyCeremony.CoefficientSet coeff,
                   int number_of_guardians,
                   int quorum,
                   @Nullable Auxiliary.KeyPair auxiliary_keys) {

    this.object_id = coeff.guardianId();
    Preconditions.checkArgument(coeff.guardianSequence() > 0 && coeff.guardianSequence() < 256);
    Preconditions.checkArgument(quorum <= number_of_guardians);
    this.sequence_order = coeff.guardianSequence();
    this.ceremony_details = KeyCeremony.CeremonyDetails.create(number_of_guardians, quorum);
    this.auxiliary_keys = (auxiliary_keys != null) ? auxiliary_keys :
            KeyCeremony.generate_rsa_auxiliary_key_pair(this.object_id, coeff.guardianSequence());
    this.election_keys =  coeff.generate_election_key_pair();

    this.backups_to_share = new HashMap<>();
    this.otherGuardianAuxiliaryKeys = new HashMap<>();
    this.guardian_election_public_keys = new HashMap<>();
    this.guardian_election_partial_key_backups = new HashMap<>();
    this.guardian_election_partial_key_verifications = new HashMap<>();

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
  public static Guardian createForTesting(String id,
                                          int sequence,
                                          int number_of_guardians,
                                          int quorum,
                                          @Nullable ElementModQ nonce_seed) {

    return new Guardian(id, sequence, number_of_guardians, quorum, nonce_seed);
  }

  // Coefficients are generated at random. python __init__
  private Guardian(String id,
                   int sequence_order,
                   int number_of_guardians,
                   int quorum,
                   @Nullable ElementModQ nonce_seed) {

    this.object_id = id;
    Preconditions.checkArgument(sequence_order > 0 && sequence_order < 256);
    this.sequence_order = sequence_order;
    this.ceremony_details = KeyCeremony.CeremonyDetails.create(number_of_guardians, quorum);
    this.auxiliary_keys = KeyCeremony.generate_rsa_auxiliary_key_pair(id, sequence_order);
    this.election_keys =  KeyCeremony.generate_election_key_pair(id, sequence_order, quorum, nonce_seed);

    this.backups_to_share = new HashMap<>();
    this.otherGuardianAuxiliaryKeys = new HashMap<>();
    this.guardian_election_public_keys = new HashMap<>();
    this.guardian_election_partial_key_backups = new HashMap<>();
    this.guardian_election_partial_key_verifications = new HashMap<>();

    this.save_auxiliary_public_key(this.share_auxiliary_public_key());
    this.save_election_public_key(this.share_election_public_key());
  }

  public KeyCeremony.ElectionKeyPair election_keys() {
    return this.election_keys;
  }

  public Auxiliary.KeyPair auxiliary_keys() {
    return this.auxiliary_keys;
  }

  /////////////////////////////////////////////////////////////////////////////////////////////////
  // key ceremony

  /** Publish record of guardian with all required information. */
  public GuardianRecord publish() {
    KeyCeremony.ElectionPublicKey election_public_key = this.election_keys.share();

    return GuardianRecord.create(
            election_public_key.owner_id(),
            election_public_key.sequence_order(),
            election_public_key.key(),
            election_public_key.coefficient_commitments(),
            election_public_key.coefficient_proofs());
  }

  public GuardianRecordPrivate export_private_data() {
    return GuardianRecordPrivate.create(
            election_keys,
            auxiliary_keys,
            backups_to_share,
            otherGuardianAuxiliaryKeys,
            guardian_election_public_keys,
            guardian_election_partial_key_backups,
            guardian_election_partial_key_verifications
            );
  }

  /**
   * Share public election and auxiliary keys for guardian.
   * @return Public set of election and auxiliary keys
   */
  public KeyCeremony.PublicKeySet share_public_keys() {
    return KeyCeremony.PublicKeySet.create(
            this.election_keys.share(),
            this.auxiliary_keys.share());
  }

  /**
   * Save public election and auxiliary keys for another guardian.
   * @param public_key_set: Public set of election and auxiliary keys
   */
  public void save_guardian_public_keys(KeyCeremony.PublicKeySet public_key_set) {
    this.save_auxiliary_public_key(public_key_set.auxiliary());
    this.save_election_public_key(public_key_set.election());
  }

  /**
   * True if all election and auxiliary public keys have been received.
   * @return All election and auxiliary public keys backups received
   */
  boolean all_public_keys_received() {
    return this.all_auxiliary_public_keys_received() && this.all_election_public_keys_received();
  }

  /** Share auxiliary public key with another guardian. */
  Auxiliary.PublicKey share_auxiliary_public_key() {
    return new Auxiliary.PublicKey(this.object_id, this.sequence_order, this.auxiliary_keys.public_key);
  }

  /** Save a guardians auxiliary public key. */
  public void save_auxiliary_public_key(Auxiliary.PublicKey key) {
    this.otherGuardianAuxiliaryKeys.put(key.owner_id, key);
  }

  /** True if all auxiliary public keys have been received. */
  boolean all_auxiliary_public_keys_received() {
    return this.otherGuardianAuxiliaryKeys.size() == this.ceremony_details.number_of_guardians();
  }

  /** Share election public key with another guardian. */
  public KeyCeremony.ElectionPublicKey share_election_public_key() {
    return this.election_keys.share();
  }

  /** Save a guardians election public key. */
  public void save_election_public_key(KeyCeremony.ElectionPublicKey key) {
    this.guardian_election_public_keys.put(key.owner_id(), key);
  }

  /** True if all election public keys have been received. */
  boolean all_election_public_keys_received() {
    return this.guardian_election_public_keys.size() == this.ceremony_details.number_of_guardians();
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

  /** Share all election partial key backups . */
  List<KeyCeremony.ElectionPartialKeyBackup> share_election_partial_key_backups() {
    return new ArrayList<>(this.backups_to_share.values());
  }

  /**
   * Share election partial key backup with another guardian.
   * @param designated_id: Designated guardian
   * @return Election partial key backup or None.
   */
  public Optional<KeyCeremony.ElectionPartialKeyBackup> share_election_partial_key_backup(String designated_id) {
    return Optional.ofNullable(this.backups_to_share.get(designated_id));
  }

  /**
   * Save election partial key backup from another guardian.
   * @param backup: Manifest partial key backup
   */
  public void save_election_partial_key_backup(KeyCeremony.ElectionPartialKeyBackup backup) {
    this.guardian_election_partial_key_backups.put(backup.owner_id(), backup);
  }

  /** True if all election partial key backups have been received. */
  boolean all_election_partial_key_backups_received() {
    return this.guardian_election_partial_key_backups.size() == this.ceremony_details.number_of_guardians() - 1;
  }

  /**
   * Verify election partial key backup value is in polynomial.
   * @param guardian_id: Owner of backup to verify
   * @param decryptor Use default if null.
   * @return Manifest partial key verification or None
   */
  public Optional<KeyCeremony.ElectionPartialKeyVerification> verify_election_partial_key_backup(
          String guardian_id,
          @Nullable Auxiliary.Decryptor decryptor) {

    KeyCeremony.ElectionPartialKeyBackup backup = this.guardian_election_partial_key_backups.get(guardian_id);
    if (backup == null) {
      return Optional.empty();
    }
    KeyCeremony.ElectionPublicKey public_key = this.guardian_election_public_keys.get(guardian_id);

    return Optional.of(KeyCeremony.verify_election_partial_key_backup(this.object_id, backup, public_key, this.auxiliary_keys, decryptor));
  }

  /**
   * Publish election backup challenge of election partial key verification.
   * @param guardian_id: Owner of election key
   * @return Manifest partial key challenge or None
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
   * @param challenge: Manifest partial key challenge
   * @return Manifest partial key verification
   */
  KeyCeremony.ElectionPartialKeyVerification verify_election_partial_key_challenge(KeyCeremony.ElectionPartialKeyChallenge challenge) {
    return KeyCeremony.verify_election_partial_key_challenge(this.object_id, challenge);
  }

  /**
   * Save election partial key verification from another guardian.
   * @param verification: Manifest partial key verification
   * LOOK not called by the key ceremony, duplicated in KeyCeremonyMediator, so probably not needed
   */
  public void save_election_partial_key_verification(KeyCeremony.ElectionPartialKeyVerification verification) {
    this.guardian_election_partial_key_verifications.put(verification.designated_id(), verification);
  }

  /**
   * True if all election partial key backups have been verified.
   * @return All election partial key backups verified
   */
  boolean all_election_partial_key_backups_verified() {
    int required = this.ceremony_details.number_of_guardians() - 1;
    if (this.guardian_election_partial_key_verifications.size() != required) {
      return false;
    }
    for (KeyCeremony.ElectionPartialKeyVerification verified : this.guardian_election_partial_key_verifications.values()) {
      if (!verified.verified()) {
        return false;
      }
    }
    return true;
  }

  /**
   * Create a joint election key from the public keys of all guardians.
   * @return Optional joint key for election
   */
  Optional<ElementModP> publish_joint_key() {
    if (!this.all_election_public_keys_received()) {
      return Optional.empty();
    }
    if (!this.all_election_partial_key_backups_verified()) {
      return Optional.empty();
    }

    //         public_keys = map(
    //            lambda public_key: public_key.key,
    //            self._guardian_election_public_keys.values(),
    //        )
    // LOOK does this include itself?
    Collection<ElementModP> public_keys = this.guardian_election_public_keys.values().stream()
            .map(pk -> pk.key()).collect(Collectors.toList());
    return Optional.of(ElGamal.elgamal_combine_public_keys(public_keys));
  }

  // Getters
  public KeyCeremony.CeremonyDetails ceremony_details() {
    return ceremony_details;
  }

  /////////////////////////////////////////////////////////////////////////////////////////////////
  // decrypting

  /**
   * Compute the decryption share of tally.
   * <p>
   * @param tally: Ciphertext tally to get share of
   * @param context: Election context
   * @return Decryption share of tally or None if failure
   */
  public Optional<DecryptionShare> compute_tally_share(CiphertextTally tally, CiphertextElectionContext context) {
    return Decryptions.compute_decryption_share(
            this.election_keys,
            tally,
            context);
  }

  /**
   * Compute the decryption shares of ballots.
   *
   * @param ballots: List of ciphertext ballots to get shares of
   * @param context: Election context
   * @return Map[BALLOT_ID, DecryptionShare]
   */
  public Map<String, Optional<DecryptionShare>> compute_ballot_shares(Iterable<SubmittedBallot> ballots, CiphertextElectionContext context) {

    Map<String, Optional<DecryptionShare>> shares = new HashMap<>();
    for (SubmittedBallot ballot : ballots) {
      Optional<DecryptionShare> share = Decryptions.compute_decryption_share_for_ballot(
              this.election_keys,
              ballot,
              context);
      shares.put(ballot.object_id, share);
    }
    return shares;
  }

  /** Compute the compensated decryption share of a tally for a missing guardian. */
  public Optional<DecryptionShare.CompensatedDecryptionShare> compute_compensated_tally_share(
          String missing_guardian_id,
          CiphertextTally tally,
          CiphertextElectionContext context,
          @Nullable Auxiliary.Decryptor decryptor) {

    // Ensure missing guardian information available
    KeyCeremony.ElectionPublicKey missing_guardian_key = this.guardian_election_public_keys.get(missing_guardian_id);
    KeyCeremony.ElectionPartialKeyBackup missing_guardian_backup = this.guardian_election_partial_key_backups.get(missing_guardian_id);
    if (missing_guardian_key == null || missing_guardian_backup == null) {
      return Optional.empty();
    }

    return Decryptions.compute_compensated_decryption_share(
            this.share_election_public_key(),
            this.auxiliary_keys(),
            missing_guardian_key,
            missing_guardian_backup,
            tally,
            context,
            decryptor);
  }

  /** Compute the compensated decryption share of each ballots for a missing guardian. */
  public Map<String, Optional<DecryptionShare.CompensatedDecryptionShare>> compute_compensated_ballot_shares(
          String missing_guardian_id,
          List<SubmittedBallot> ballots,
          CiphertextElectionContext context,
          @Nullable Auxiliary.Decryptor decryptor) {

    Map<String, Optional<DecryptionShare.CompensatedDecryptionShare>> shares = new HashMap<>();

    // Ensure missing guardian information available
    KeyCeremony.ElectionPublicKey missing_guardian_key = this.guardian_election_public_keys.get(missing_guardian_id);
    KeyCeremony.ElectionPartialKeyBackup missing_guardian_backup = this.guardian_election_partial_key_backups.get(missing_guardian_id);
    if (missing_guardian_key == null || missing_guardian_backup == null) {
      return shares;
    }

    for (SubmittedBallot ballot : ballots) {
      Optional<DecryptionShare.CompensatedDecryptionShare> share = Decryptions.compute_compensated_decryption_share_for_ballot(
              this.share_election_public_key(),
              this.auxiliary_keys,
              missing_guardian_key,
              missing_guardian_backup,
              ballot,
              context,
              decryptor);
      shares.put(ballot.object_id, share);
    }
    return shares;
  }

  /**
   * Compute a partial decryption of an elgamal encryption.
   *
   * @param elgamal:            the `ElGamalCiphertext` that will be partially decrypted
   * @param extended_base_hash: the extended base hash of the election that
   *                            was used to generate t he ElGamal Ciphertext
   * @param nonce_seed:         an optional value used to generate the `ChaumPedersenProof`
   *                            if no value is provided, a random number will be used.
   * @return a `Tuple[ElementModP, ChaumPedersenProof]` of the decryption and its proof
   */
  DecryptionProofTuple partially_decrypt(
          ElGamal.Ciphertext elgamal,
          ElementModQ extended_base_hash,
          @Nullable ElementModQ nonce_seed) {

    if (nonce_seed == null) {
      nonce_seed = rand_q();
    }

    //TODO: ISSUE #47: Decrypt the election secret key

    // 𝑀_i = 𝐴^𝑠𝑖 mod 𝑝
    ElementModP partial_decryption = elgamal.partial_decrypt(this.election_keys.key_pair().secret_key);

    // 𝑀_i = 𝐴^𝑠𝑖 mod 𝑝 and 𝐾𝑖 = 𝑔^𝑠𝑖 mod 𝑝
    ChaumPedersen.ChaumPedersenProof proof = ChaumPedersen.make_chaum_pedersen(
            elgamal,
            this.election_keys.key_pair().secret_key,
            partial_decryption,
            nonce_seed,
            extended_base_hash);

    return new DecryptionProofTuple(partial_decryption, proof);
  }

}
