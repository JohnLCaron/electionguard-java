package com.sunya.electionguard.guardian;

import com.google.common.base.Preconditions;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.flogger.FluentLogger;
import com.sunya.electionguard.Auxiliary;
import com.sunya.electionguard.ChaumPedersen;
import com.sunya.electionguard.DecryptionProofTuple;
import com.sunya.electionguard.ElGamal;
import com.sunya.electionguard.ElectionPolynomial;
import com.sunya.electionguard.Group;
import com.sunya.electionguard.KeyCeremony;
import com.sunya.electionguard.Rsa;
import com.sunya.electionguard.SchnorrProof;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;
import java.math.BigInteger;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static com.sunya.electionguard.Group.ElementModP;
import static com.sunya.electionguard.Group.ElementModQ;
import static com.sunya.electionguard.Group.ONE_MOD_P;
import static com.sunya.electionguard.Group.hex_to_q;
import static com.sunya.electionguard.Group.mult_p;
import static com.sunya.electionguard.Group.pow_p;
import static com.sunya.electionguard.Group.rand_q;

/**
 * Simulate a RemoteTrustee/Guardian where object is in a separate process space, and is never shared.
 * Communication happens through the RemoteTrustee.KeyCeremonyProxy.
 */
public class RemoteTrustee {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  public final String id;
  // a unique number in [1, 256) that is the polynomial x value for this guardian
  public final int sequence_order;
  public final int number_of_guardians;

  public final GuardianSecrets guardianSecrets;

  // Other guardians' public keys that are shared with this guardian
  public final Map<String, KeyCeremony.PublicKeySet> allGuardianPublicKeys; // map(GUARDIAN_ID, PublicKeySet)

  // This guardian's partial key backups of other guardians' keys. Needed for making joint public key.
  private final LoadingCache<String, KeyCeremony.ElectionPartialKeyBackup> myPartialKeyBackups; // Map(GUARDIAN_ID, ElectionPartialKeyBackup)

  // Other guardians' partial key backups of this guardian's keys. Needed for decryption.
  public final Map<String, KeyCeremony.ElectionPartialKeyBackup> otherGuardianPartialKeyBackups; // Map(GUARDIAN_ID, ElectionPartialKeyBackup)

  // https://github.com/microsoft/electionguard/discussions/79#discussioncomment-466332
  // 0. Before communication, guardians generate primary public key, associated polynomials, auxiliary public key.
  public RemoteTrustee(String id,
                       int sequence_order,
                       int number_of_guardians,
                       int quorum,
                       @Nullable ElementModQ nonce_seed) {

    Preconditions.checkArgument(sequence_order > 0 && sequence_order < 256);

    this.id = id;
    this.sequence_order = sequence_order;
    this.number_of_guardians = number_of_guardians;

    this.guardianSecrets = GuardianSecrets.generate(quorum, nonce_seed);
    this.allGuardianPublicKeys = new HashMap<>();
    this.otherGuardianPartialKeyBackups = new HashMap<>();

    this.myPartialKeyBackups = CacheBuilder.newBuilder().build(new CacheLoader<>() {
      @Override
      public KeyCeremony.ElectionPartialKeyBackup load(String otherId) {
        return generatePartialKeyBackup(otherId);
      }
    });

    // public keys include itself.
    this.receivePublicKeys(this.sharePublicKeys());
  }

  // yuck, how can we do better than public? pass in the proto?
  public GuardianSecrets secrets() {
    return guardianSecrets;
  }

  /** Share public keys for this guardian. */
  private KeyCeremony.PublicKeySet sharePublicKeys() {
    return KeyCeremony.PublicKeySet.create(
            this.id,
            this.sequence_order,
            this.guardianSecrets.rsa_keypair.getPublic(),
            this.guardianSecrets.election_key_pair.public_key,
            this.guardianSecrets.proof);
  }

  /** Recieve publicKeys from another guardian. */
  private void receivePublicKeys(KeyCeremony.PublicKeySet publicKeys) {
    // if we knew what the guardian ids are supposed to be, we could check that they are not bogus. Use sequence_number?
    if (publicKeys != null) {
      this.allGuardianPublicKeys.put(publicKeys.owner_id(), publicKeys);
    }
  }

  /** True if all public keys have been received. */
  private boolean allPublicKeysReceived() {
    return this.allGuardianPublicKeys.size() == this.number_of_guardians;
  }

  /**
   * Share coefficient validation set to be used for validating the coefficients post election.
   */
  private KeyCeremony.CoefficientValidationSet shareCoefficientValidationSet() {
    return KeyCeremony.CoefficientValidationSet.create(this.id,
            this.guardianSecrets.polynomial.coefficient_commitments,
            this.guardianSecrets.polynomial.coefficient_proofs);
  }

  /** Share public keys for this guardian. */
  @Nullable
  private KeyCeremony.ElectionPartialKeyBackup sharePartialKeyBackup(String guardianId) {
    if (id.equals(guardianId)) {
      return null;
    }
    if (this.allGuardianPublicKeys.get(guardianId) == null) {
      return null;
    }

    return myPartialKeyBackups.getUnchecked(guardianId);
  }

  private KeyCeremony.ElectionPartialKeyBackup generatePartialKeyBackup(String guardianId) {
    KeyCeremony.PublicKeySet otherKeys = this.allGuardianPublicKeys.get(guardianId);
    Preconditions.checkNotNull(otherKeys);

    // Compute my polynomial's y coordinate at the other's x coordinate.
    ElementModQ value = ElectionPolynomial.compute_polynomial_coordinate(
            BigInteger.valueOf(otherKeys.sequence_order()), this.guardianSecrets.polynomial);

    // Encrypt the value with the other guardian's public auxiliary key.
    Optional<Auxiliary.ByteString> encrypted_value = Rsa.encrypt(value.to_hex(), otherKeys.auxiliary_public_key());
    Preconditions.checkArgument(encrypted_value.isPresent());

    return KeyCeremony.ElectionPartialKeyBackup.create(
            this.id,
            guardianId,
            otherKeys.sequence_order(),
            encrypted_value.get(),
            this.guardianSecrets.polynomial.coefficient_commitments,
            this.guardianSecrets.polynomial.coefficient_proofs);
  }

  /** Receive PartialKeyBackup from another guardian. */
  @Nullable
  private KeyCeremony.ElectionPartialKeyVerification receivePartialKeyBackup(KeyCeremony.ElectionPartialKeyBackup backup) {
    if (backup != null) {
      this.otherGuardianPartialKeyBackups.put(backup.owner_id(), backup);
      return this.verifyPartialKeyBackup(backup);
    }
    // LOOK could return ElectionPartialKeyVerification, save a trip.
    return null;
  }

  /** True if all backups have been received. */
  private boolean allBackupsReceived() {
    return this.otherGuardianPartialKeyBackups.size() == this.number_of_guardians - 1;
  }

  @Nullable
  private KeyCeremony.ElectionPartialKeyVerification verifyPartialKeyBackup(String guardian_id) {
    KeyCeremony.ElectionPartialKeyBackup backup = this.otherGuardianPartialKeyBackups.get(guardian_id);
    if (backup == null) {
      return null;
    } else {
      return verifyPartialKeyBackup(backup);
    }
  }

  private KeyCeremony.ElectionPartialKeyVerification verifyPartialKeyBackup(KeyCeremony.ElectionPartialKeyBackup backup) {
      // decrypt the value with my private key.
    Optional<String> decrypted_value = Rsa.decrypt(backup.encrypted_value(), this.guardianSecrets.rsa_keypair.getPrivate());
    if (decrypted_value.isEmpty()) {
      // LOOK if decryption fails, not the same as if verify_polynomial_coordinate failed.
      return KeyCeremony.ElectionPartialKeyVerification.create(backup.owner_id(), backup.designated_id(), this.id, false);
    }
    ElementModQ value = Group.hex_to_q(decrypted_value.get()).orElseThrow(IllegalStateException::new);

    // Is that value on my polynomial?
    boolean verify = ElectionPolynomial.verify_polynomial_coordinate(value, BigInteger.valueOf(backup.designated_sequence_order()),
            backup.coefficient_commitments());

    return KeyCeremony.ElectionPartialKeyVerification.create(
            backup.owner_id(),
            backup.designated_id(),
            this.id,
            verify);
  }

  /**
   * Publish election backup challenge of election partial key verification.
   *
   * @param guardian_id: Owner of election key
   */
  @Nullable
  KeyCeremony.ElectionPartialKeyChallenge makeBackupChallenge(String guardian_id) {
    KeyCeremony.ElectionPartialKeyBackup backup_in_question = this.myPartialKeyBackups.getUnchecked(guardian_id);
    if (backup_in_question == null) {
      return null;
    }
    return KeyCeremony.generate_election_partial_key_challenge(backup_in_question, this.guardianSecrets.polynomial);
  }

  /**
   * Verify challenge of previous verification of election partial key.
   *
   * @param challenge: Manifest partial key challenge
   * @return Manifest partial key verification
   */
  static KeyCeremony.ElectionPartialKeyVerification verifyPartialKeyChallenge(String verifier_id, KeyCeremony.ElectionPartialKeyChallenge challenge) {
    return KeyCeremony.verify_election_partial_key_challenge(verifier_id, challenge);
  }

  /**
   * Creates a joint election key from the public keys of all guardians.
   */
  private ElementModP publishJointKey() {
    List<ElementModP> public_keys = allGuardianPublicKeys.values().stream()
            .map(KeyCeremony.PublicKeySet::election_public_key).collect(Collectors.toList());

    return ElGamal.elgamal_combine_public_keys(public_keys);
  }

  @Immutable
  public static class GuardianSecrets {
    /** The Guardian's polynomial. */
    public final ElectionPolynomial polynomial;
    /** K = (s, g^s), for this Guardian */
    public final ElGamal.KeyPair election_key_pair;
    /** The proof of knowledge of possession of the associated private key. (not secret) */
    public final SchnorrProof proof;
    /** The auxiliary keypair */
    public final java.security.KeyPair rsa_keypair;

    private GuardianSecrets(ElGamal.KeyPair key_pair, SchnorrProof proof, ElectionPolynomial polynomial, java.security.KeyPair rsa_keypair) {
      this.election_key_pair = key_pair;
      this.proof = proof;
      this.polynomial = polynomial;
      this.rsa_keypair = rsa_keypair;
    }

    static GuardianSecrets generate(int quorum, @Nullable ElementModQ nonce) {
      ElectionPolynomial polynomial = ElectionPolynomial.generate_polynomial(quorum, nonce);
      // the 0th coefficient is the secret s for the ith Guardian
      // the 0th commitment is the public key = g^s mod p
      // The key_pair is Ki = election keypair for ith Guardian
      ElGamal.KeyPair key_pair = new ElGamal.KeyPair(
              polynomial.coefficients.get(0), polynomial.coefficient_commitments.get(0));
      SchnorrProof proof = SchnorrProof.make_schnorr_proof(key_pair, rand_q());
      java.security.KeyPair rsa_keypair = Rsa.rsa_keypair();
      return new GuardianSecrets(key_pair, proof, polynomial, rsa_keypair);
    }
  }

  public KeyCeremonyProxy getKeyCeremonyProxy() {
    return new KeyCeremonyProxy();
  }

  /** Simulation of message broker service for Guardians/Trustees. */
  @Immutable
  public class KeyCeremonyProxy {

    String id() {
      return id;
    }


    KeyCeremony.PublicKeySet sendPublicKeys() {
      return RemoteTrustee.this.sharePublicKeys();
    }

    void receivePublicKeys(KeyCeremony.PublicKeySet publicKeys) {
      RemoteTrustee.this.receivePublicKeys(publicKeys);
    }

    boolean allPublicKeysReceived() {
      return RemoteTrustee.this.allPublicKeysReceived();
    }


    @Nullable
    KeyCeremony.ElectionPartialKeyBackup sendPartialKeyBackup(String otherId) {
      return RemoteTrustee.this.sharePartialKeyBackup(otherId);
    }

    void receivePartialKeyBackup(KeyCeremony.ElectionPartialKeyBackup backup) {
      RemoteTrustee.this.receivePartialKeyBackup(backup);
    }

    KeyCeremony.ElectionPartialKeyVerification verifyPartialKeyBackup(KeyCeremony.ElectionPartialKeyBackup backup) {
      return RemoteTrustee.this.verifyPartialKeyBackup(backup);
    }

    boolean allBackupsReceived() {
      return RemoteTrustee.this.allBackupsReceived();
    }


    KeyCeremony.ElectionPartialKeyChallenge sendBackupChallenge(String guardian_id) {
      return RemoteTrustee.this.makeBackupChallenge(guardian_id);
    }

    // static, could be done by anyone.
    KeyCeremony.ElectionPartialKeyVerification verifyPartialKeyChallenge(KeyCeremony.ElectionPartialKeyChallenge challenge) {
      return RemoteTrustee.verifyPartialKeyChallenge(id, challenge);
    }


    KeyCeremony.CoefficientValidationSet sendCoefficientValidationSet() {
      return RemoteTrustee.this.shareCoefficientValidationSet();
    }

    ElementModP sendJointPublicKey() {
      return RemoteTrustee.this.publishJointKey();
    }
  }

  ///////////////////////////////////////////////////////////////////////////////////////////////////////////////////

  /**
   * Compute a compensated partial decryption of an elgamal encryption on behalf of the missing guardian.
   * LOOK this seems to be the only place we need this.auxiliary_keys.secret_key. Whats with ISSUE #47?
   * <p>
   * @param missing_guardian_id: the guardian
   * @param elgamal:             the `ElGamalCiphertext` that will be partially decrypted
   * @param extended_base_hash:  the extended base hash of the election used to generate the ElGamal Ciphertext
   * @param nonce_seed:          an optional value used to generate the `ChaumPedersenProof`
   *                             if no value is provided, a random number will be used.
   * @return the decryption and its proof
   */
  Optional<DecryptionProofTuple> compensate_decrypt(
          String missing_guardian_id,
          ElGamal.Ciphertext elgamal,
          ElementModQ extended_base_hash,
          @Nullable ElementModQ nonce_seed) {

    if (nonce_seed == null) {
      nonce_seed = rand_q();
    }

    KeyCeremony.ElectionPartialKeyBackup backup = this.otherGuardianPartialKeyBackups.get(missing_guardian_id);
    if (backup == null) {
      logger.atInfo().log("compensate decrypt guardian %s missing backup for %s",
              this.id, missing_guardian_id);
      return Optional.empty();
    }

    // LOOK why string?
    Optional<String> decrypted_value = Rsa.decrypt(backup.encrypted_value(), this.guardianSecrets.rsa_keypair.getPrivate());
    if (decrypted_value.isEmpty()) {
      Rsa.decrypt(backup.encrypted_value(), this.guardianSecrets.rsa_keypair.getPrivate());
      logger.atInfo().log("compensate decrypt guardian %s failed decryption for %s",
              this.id, missing_guardian_id);
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
  private DecryptionProofTuple partially_decrypt(
          ElGamal.Ciphertext elgamal,
          ElementModQ extended_base_hash,
          @Nullable ElementModQ nonce_seed) {

    if (nonce_seed == null) {
      nonce_seed = rand_q();
    }

    //TODO: ISSUE #47: Decrypt the election secret key

    // 𝑀_i = 𝐴^𝑠𝑖 mod 𝑝
    ElementModP partial_decryption = elgamal.partial_decrypt(guardianSecrets.election_key_pair.secret_key);

    // 𝑀_i = 𝐴^𝑠𝑖 mod 𝑝 and 𝐾𝑖 = 𝑔^𝑠𝑖 mod 𝑝
    ChaumPedersen.ChaumPedersenProof proof = ChaumPedersen.make_chaum_pedersen(
            elgamal,
            guardianSecrets.election_key_pair.secret_key,
            partial_decryption,
            nonce_seed,
            extended_base_hash);

    return new DecryptionProofTuple(partial_decryption, proof);
  }

  /** Compute the recovery public key for a given guardian. */
  private Optional<ElementModP> recovery_public_key_for(String missing_guardian_id) {
    KeyCeremony.ElectionPartialKeyBackup backup = this.otherGuardianPartialKeyBackups.get(missing_guardian_id);
    if (backup == null) {
      logger.atInfo().log("compensate decrypt guardian %s missing backup for %s", this.id, missing_guardian_id);
      return Optional.empty();
    }

    // compute the recovery public key, corresponding to the secret share Pi(l)
    // K_ij^(l^j) for j in 0..k-1.  K_ij is coefficients[j].public_key
    ElementModP pub_key = ONE_MOD_P;
    int count = 0;
    for (ElementModP commitment : backup.coefficient_commitments()) {
      ElementModQ exponent = Group.pow_q(BigInteger.valueOf(this.sequence_order), BigInteger.valueOf(count));
      pub_key = mult_p(pub_key, pow_p(commitment, exponent));
      count++;
    }

    return Optional.of(pub_key);
  }

  public DecryptorProxy getDecryptorProxy() {
    return new DecryptorProxy();
  }

  /** Simulation of message broker service for Guardians/Trustees. */
  @Immutable
  public class DecryptorProxy {

    String id() {
      return id;
    }

    int sequence_order() {
      return sequence_order;
    }

    ElementModP election_public_key() {
      return RemoteTrustee.this.guardianSecrets.election_key_pair.public_key;
    }

    Optional<DecryptionProofTuple> compensate_decrypt(
            String missing_guardian_id,
            ElGamal.Ciphertext elgamal,
            ElementModQ extended_base_hash,
            @Nullable ElementModQ nonce_seed) {

      return RemoteTrustee.this.compensate_decrypt(missing_guardian_id,
              elgamal,
              extended_base_hash,
              nonce_seed);
    }

    DecryptionProofTuple partially_decrypt(ElGamal.Ciphertext elgamal, ElementModQ extended_base_hash, @Nullable ElementModQ nonce_seed) {
      return RemoteTrustee.this.partially_decrypt(elgamal, extended_base_hash, nonce_seed);
    }

    Optional<ElementModP> recovery_public_key_for(String missing_guardian_id) {
      return RemoteTrustee.this.recovery_public_key_for(missing_guardian_id);
    }
  }

}
