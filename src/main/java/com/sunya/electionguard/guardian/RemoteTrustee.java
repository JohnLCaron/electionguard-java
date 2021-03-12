package com.sunya.electionguard.guardian;

import com.google.common.base.Preconditions;
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
 * Simulate a RemoteTrustee/Guardian where object is in a separate process space, and is never shared.
 * Communication happens through the RemoteTrustee.Proxy.
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

  public Proxy getProxy() {
    return new Proxy();
  }

  /** Simulation of message broker service for Guardians/Trustees. */
  @Immutable
  public class Proxy {

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
}
