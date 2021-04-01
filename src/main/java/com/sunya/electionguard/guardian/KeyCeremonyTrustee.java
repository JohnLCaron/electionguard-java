package com.sunya.electionguard.guardian;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.flogger.FluentLogger;
import com.sunya.electionguard.Auxiliary;
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
import static com.sunya.electionguard.Group.rand_q;

/**
 * A Trustee/Guardian used in the KeyCeremony, with secrets hidden as well as possible.
 * This object must not be used with untrusted code.
 */
public class KeyCeremonyTrustee {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  public final String id;

  // a unique number in [1, 256) that is the polynomial x value for this guardian
  public final int xCoordinate;

  final int quorum;

  // All of the guardians' public keys (including this one), keyed by guardian id.
  public final Map<String, KeyCeremony2.PublicKeySet> allGuardianPublicKeys;

  // This guardian's partial key backups of other guardians' keys, keyed by designated guardian id.
  private final LoadingCache<String, KeyCeremony2.PartialKeyBackup> myPartialKeyBackups;

  // Other guardians' partial key backups of this guardian's keys, keyed by generating guardian id.
  public final Map<String, KeyCeremony2.PartialKeyBackup> otherGuardianPartialKeyBackups;

  // All secret info is in here.
  private final GuardianSecrets guardianSecrets;

  /**
   *  Create a random polynomial of rank quorum.
   */
  public KeyCeremonyTrustee(String id,
                            int sequence_order,
                            int quorum,
                            @Nullable ElementModQ nonce_seed) {

    Preconditions.checkArgument(!Strings.isNullOrEmpty(id));
    Preconditions.checkArgument(sequence_order > 0 && sequence_order < 256);
    Preconditions.checkArgument(quorum > 0);

    this.id = id;
    this.xCoordinate = sequence_order;
    this.quorum = quorum;

    this.guardianSecrets = GuardianSecrets.generate(quorum, nonce_seed);
    this.allGuardianPublicKeys = new HashMap<>();
    this.otherGuardianPartialKeyBackups = new HashMap<>();

    // not obvious this needs to be cached?
    this.myPartialKeyBackups = CacheBuilder.newBuilder().build(new CacheLoader<>() {
      @Override
      public KeyCeremony2.PartialKeyBackup load(String otherId) {
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
  public KeyCeremony2.PublicKeySet sharePublicKeys() {
    ElectionPolynomial polynomial = this.guardianSecrets.polynomial;
    return KeyCeremony2.PublicKeySet.create(
            this.id,
            this.xCoordinate,
            this.guardianSecrets.rsa_keypair.getPublic(),
            polynomial.coefficient_proofs);
  }

  /** Receive publicKeys from another guardian. */
  public boolean receivePublicKeys(KeyCeremony2.PublicKeySet publicKeys) {
    if (publicKeys.isValid()) {
      this.allGuardianPublicKeys.put(publicKeys.ownerId(), publicKeys);
      return true;
    }
    return false;
  }

  /** Share this guardians backup for otherGuardian. */
  @Nullable
  public KeyCeremony2.PartialKeyBackup sendPartialKeyBackup(String otherGuardian) {
    if (id.equals(otherGuardian)) {
      return null; // LOOK
    }
    if (this.allGuardianPublicKeys.get(otherGuardian) == null) {
      return null; // LOOK
    }

    return myPartialKeyBackups.getUnchecked(otherGuardian);
  }

  private KeyCeremony2.PartialKeyBackup generatePartialKeyBackup(String otherGuardian) {
    KeyCeremony2.PublicKeySet otherKeys = this.allGuardianPublicKeys.get(otherGuardian);
    Preconditions.checkNotNull(otherKeys);

    // Compute my polynomial's y coordinate at the other's x coordinate.
    ElementModQ value = ElectionPolynomial.compute_polynomial_coordinate(
            BigInteger.valueOf(otherKeys.guardianXCoordinate()), this.guardianSecrets.polynomial);

    // Encrypt the value with the other guardian's public auxiliary key.
    Optional<Auxiliary.ByteString> encrypted_value = Rsa.encrypt(value.to_hex(), otherKeys.auxiliaryPublicKey());
    Preconditions.checkArgument(encrypted_value.isPresent());

    return KeyCeremony2.PartialKeyBackup.create(
            this.id,
            otherGuardian,
            otherKeys.guardianXCoordinate(),
            encrypted_value.get());
  }

  public KeyCeremony2.PartialKeyVerification verifyPartialKeyBackup(KeyCeremony2.PartialKeyBackup backup) {
    this.otherGuardianPartialKeyBackups.put(backup.generatingGuardianId(), backup);

      // decrypt the value with my private key.
    Optional<String> decrypted_value = Rsa.decrypt(backup.encryptedCoordinate(), this.guardianSecrets.rsa_keypair.getPrivate());
    if (decrypted_value.isEmpty()) {
      // LOOK if decryption fails, not the same as if verify_polynomial_coordinate failed.
      return KeyCeremony2.PartialKeyVerification.create(backup.generatingGuardianId(), backup.designatedGuardianId(),false);
    }
    // LOOK something else besides an exception.
    ElementModQ value = Group.hex_to_q(decrypted_value.get()).orElseThrow(IllegalStateException::new);

    // Is that value consistent with the generating guardian's commitments?
    KeyCeremony2.PublicKeySet generatingKeys = allGuardianPublicKeys.get(backup.generatingGuardianId());
    boolean verify = ElectionPolynomial.verify_polynomial_coordinate(value, BigInteger.valueOf(backup.designatedGuardianXCoordinate()),
            generatingKeys.coefficientCommitments());

    return KeyCeremony2.PartialKeyVerification.create(
            backup.generatingGuardianId(),
            backup.designatedGuardianId(),
            verify);
  }

  /**
   * Publish election backup challenge of election partial key verification.
   *
   * @param guardian_id: Owner of election key
   */
  @Nullable
  public KeyCeremony2.PartialKeyChallengeResponse sendBackupChallenge(String guardian_id) {
    KeyCeremony2.PartialKeyBackup backup = this.myPartialKeyBackups.getUnchecked(guardian_id);
    if (backup == null) {
      return null;
    }
    ElectionPolynomial polynomial = this.guardianSecrets.polynomial;
    return KeyCeremony2.PartialKeyChallengeResponse.create(
            backup.generatingGuardianId(),
            backup.designatedGuardianId(),
            backup.designatedGuardianXCoordinate(),
            ElectionPolynomial.compute_polynomial_coordinate(BigInteger.valueOf(backup.designatedGuardianXCoordinate()), polynomial));
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
  public ElementModP publishJointKey() {
    List<ElementModP> public_keys = allGuardianPublicKeys.values().stream()
            .map(KeyCeremony2.PublicKeySet::electionPublicKey).collect(Collectors.toList());

    return ElGamal.elgamal_combine_public_keys(public_keys);
  }

  /**
   * https://www.electionguard.vote/spec/0.95.0/4_Key_generation/#overview-of-key-generation
   * <li>Each guardian generates an independent ElGamal public-private key pair. </li>
   * <li>Each guardian provides a non-interactive zero-knowledge Schnorr proof of knowledge of possession of the associated private key. </li>
   * <li>Each guardian generates random polynomial coefficients. (threshold verification) </li>
   * <li>Each guardian provides a Schnorr proof of knowledge of the secret coefficient value associated with each published commitment. </li>
   * <li>Each guardian provides an auxiliary public encryption function </li>
   */
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

}
