package com.sunya.electionguard;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableMap;
import com.google.common.flogger.FluentLogger;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;
import java.math.BigInteger;
import java.util.Map;
import java.util.Optional;

import static com.sunya.electionguard.Group.*;

/** Guardian of election (aka Trustee), responsible for safeguarding information and decrypting results. */
@Immutable
public class Guardian extends ElectionObjectBase {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final int sequence_order;
  private final KeyCeremony.CeremonyDetails ceremony_details;
  private final Auxiliary.KeyPair auxiliary_keys;
  private final KeyCeremony.ElectionKeyPair election_keys; // Ki = election keypair for this Guardian

  //// From Other Guardians
  // The collection of other guardians' auxiliary public keys that are shared with this guardian
  private final ImmutableMap<String, Auxiliary.PublicKey> otherGuardianAuxiliaryKeys; // map(GUARDIAN_ID, Auxiliary.PublicKey)

  // The collection of other guardians' election public keys that are shared with this guardian
  private final ImmutableMap<String, KeyCeremony.ElectionPublicKey> otherGuardianElectionKeys; // map(GUARDIAN_ID, ElectionPublicKey)

  // The collection of other guardians' partial key backups that are shared with this guardian
  private final ImmutableMap<String, KeyCeremony.ElectionPartialKeyBackup> otherGuardianPartialKeyBackups; // Map(GUARDIAN_ID, ElectionPartialKeyBackup)

  public Guardian(String object_id, int sequence_order,
                  KeyCeremony.CeremonyDetails ceremony_details,
                  Auxiliary.KeyPair auxiliary_keys,
                  KeyCeremony.ElectionKeyPair election_keys,
                  Map<String, Auxiliary.PublicKey> otherGuardianAuxiliaryKeys,
                  Map<String, KeyCeremony.ElectionPublicKey> otherGuardianElectionKeys,
                  Map<String, KeyCeremony.ElectionPartialKeyBackup> otherGuardianPartialKeyBackups) {
    super(object_id);
    this.sequence_order = sequence_order;
    this.ceremony_details = Preconditions.checkNotNull(ceremony_details);
    this.auxiliary_keys = Preconditions.checkNotNull(auxiliary_keys);
    this.election_keys = Preconditions.checkNotNull(election_keys);
    this.otherGuardianAuxiliaryKeys = ImmutableMap.copyOf(otherGuardianAuxiliaryKeys);
    this.otherGuardianElectionKeys = ImmutableMap.copyOf(otherGuardianElectionKeys);
    this.otherGuardianPartialKeyBackups = ImmutableMap.copyOf(otherGuardianPartialKeyBackups);
  }

  /**
   * Create a guardian used for testing. The crypto_base_hash is random.
   * @param id: the unique identifier for the guardian
   * @param sequence_order: a unique number in (0, 256) that identifies this guardian
   * @param number_of_guardians: the total number of guardians that will participate in the election
   * @param quorum: the count of guardians necessary to decrypt
   * @param nonce_seed: an optional `ElementModQ` value that can be used to generate the `ElectionKeyPair`,
   *                    recommended to only use this field for testing. When null, a random nonce is used.
   */
  public static Guardian createForTesting(String id,
                                                 int sequence_order,
                                                 int number_of_guardians,
                                                 int quorum,
                                                 @Nullable ElementModQ nonce_seed,
                                                 @Nullable ElementModQ crypto_base_hash) {

    return new Guardian(id, sequence_order, number_of_guardians, quorum, nonce_seed, crypto_base_hash);
  }

  private Guardian(String id,
                    int sequence_order,
                    int number_of_guardians,
                    int quorum,
                    @Nullable ElementModQ nonce_seed,
                    @Nullable ElementModQ crypto_base_hash) {

    super(id);
    Preconditions.checkArgument(sequence_order > 0 && sequence_order < 256);
    this.sequence_order = sequence_order;
    this.ceremony_details = KeyCeremony.CeremonyDetails.create(number_of_guardians, quorum);
    this.auxiliary_keys = KeyCeremony.generate_rsa_auxiliary_key_pair();
    if (crypto_base_hash == null) {
      crypto_base_hash = Group.rand_q();
    }
    this.election_keys =  KeyCeremony.generate_election_key_pair(quorum, nonce_seed, crypto_base_hash);

    this.otherGuardianAuxiliaryKeys = ImmutableMap.of(this.object_id, this.share_auxiliary_public_key());
    this.otherGuardianElectionKeys = ImmutableMap.of(this.object_id, this.share_election_public_key());
    this.otherGuardianPartialKeyBackups = ImmutableMap.of();
  }

  public KeyCeremony.CeremonyDetails ceremony_details() {
    return ceremony_details;
  }
  public Auxiliary.KeyPair auxiliary_keys() {
    return auxiliary_keys;
  }
  public ImmutableCollection<Auxiliary.PublicKey> auxiliary_public_keys() {
    return otherGuardianAuxiliaryKeys.values();
  }
  public ImmutableCollection<KeyCeremony.ElectionPublicKey> election_public_keys() {
    return otherGuardianElectionKeys.values();
  }
  public ImmutableCollection<KeyCeremony.ElectionPartialKeyBackup> election_partial_key_backups() {
    return otherGuardianPartialKeyBackups.values();
  }

  public KeyCeremony.CoefficientSet coefficients() {
    return KeyCeremony.CoefficientSet.create(object_id, sequence_order, this.election_keys.polynomial().coefficients);
  }

  int sequence_order() {
    return sequence_order;
  }

  /////////////////////////////////////////////////////////////////////////////////////////////////

  /**
   * Share public election and auxiliary keys for guardian.
   * @return Public set of election and auxiliary keys
   */
  KeyCeremony.PublicKeySet share_public_keys() {
    return KeyCeremony.PublicKeySet.create(
            this.object_id,
            this.sequence_order,
            this.auxiliary_keys.public_key,
            this.election_keys.key_pair().public_key,
            this.election_keys.proof());
  }

  /** Get a read-only view of the Guardian Election Public Keys shared with this Guardian. */
  ImmutableMap<String, KeyCeremony.ElectionPublicKey> otherGuardianElectionKeys() {
    return otherGuardianElectionKeys;
  }

  /** From the Guardian Election Public Keys shared with this Guardian, find the ElectionPublicKey by guardian_id. */
  @Nullable KeyCeremony.ElectionPublicKey otherGuardianElectionKey(String guardian_id) {
    return otherGuardianElectionKeys.get(guardian_id);
  }

  /** Share auxiliary public key with another guardian. */
  Auxiliary.PublicKey share_auxiliary_public_key() {
    return new Auxiliary.PublicKey(this.object_id, this.sequence_order, this.auxiliary_keys.public_key);
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

  /////////////////////////////////////////////////////////////////////////////////////////////////


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

  static class DecryptionProofTuple {
    final ElementModP decryption;
    final ChaumPedersen.ChaumPedersenProof proof;

    DecryptionProofTuple(ElementModP partial_decryption, ChaumPedersen.ChaumPedersenProof proof) {
      this.decryption = partial_decryption;
      this.proof = proof;
    }
  }

  /**
   * Compute a compensated partial decryption of an elgamal encryption on behalf of the missing guardian.
   * <p>
   * @param missing_guardian_id: the guardian
   * @param elgamal:             the `ElGamalCiphertext` that will be partially decrypted
   * @param extended_base_hash:  the extended base hash of the election used to generate the ElGamal Ciphertext
   * @param nonce_seed:          an optional value used to generate the `ChaumPedersenProof`
   *                             if no value is provided, a random number will be used.
   * @param decryptor            an `AuxiliaryDecrypt` function to decrypt the missing guardian private key backup
   * @return the decryption and its proof
   */
  Optional<DecryptionProofTuple> compensate_decrypt(
          String missing_guardian_id,
          ElGamal.Ciphertext elgamal,
          ElementModQ extended_base_hash,
          @Nullable ElementModQ nonce_seed,
          Auxiliary.Decryptor decryptor) {

    if (nonce_seed == null) {
      nonce_seed = rand_q();
    }

    KeyCeremony.ElectionPartialKeyBackup backup = this.otherGuardianPartialKeyBackups.get(missing_guardian_id);
    if (backup == null) {
      logger.atInfo().log("compensate decrypt guardian %s missing backup for %s",
              this.object_id, missing_guardian_id);
      return Optional.empty();
    }

    // LOOK why string?
    Optional<String> decrypted_value = decryptor.decrypt(backup.encrypted_value(), this.auxiliary_keys.secret_key);
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

    return Optional.of(new DecryptionProofTuple(partial_decryption, proof));
  }

  /** Compute the recovery public key for a given guardian. */
  Optional<ElementModP> recovery_public_key_for(String missing_guardian_id) {
    KeyCeremony.ElectionPartialKeyBackup backup = this.otherGuardianPartialKeyBackups.get(missing_guardian_id);
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
