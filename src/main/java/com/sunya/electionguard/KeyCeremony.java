package com.sunya.electionguard;

import com.google.auto.value.AutoValue;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;

import javax.annotation.Nullable;
import java.math.BigInteger;
import java.security.KeyPair;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static com.sunya.electionguard.Group.*;

public class KeyCeremony {

  /** Details of key ceremony: number of guardians and quorum size. */
  @AutoValue
  public abstract static class CeremonyDetails {
    abstract int number_of_guardians();
    abstract int quorum();

    public static CeremonyDetails create(int number_of_guardians, int quorum) {
      return new AutoValue_KeyCeremony_CeremonyDetails(number_of_guardians, quorum);
    }
  }

  /** Pair of keys (public and secret) used to encrypt/decrypt election. */
  @AutoValue
  public abstract static class ElectionKeyPair {
    public abstract ElGamal.KeyPair key_pair(); // Ki = (si, g^si)
    public abstract SchnorrProof proof(); // proof of knowledge of possession of the associated private key
    public abstract ElectionPolynomial polynomial();

    public static ElectionKeyPair create(ElGamal.KeyPair key_pair, SchnorrProof proof, ElectionPolynomial polynomial) {
      return new AutoValue_KeyCeremony_ElectionKeyPair(key_pair, proof, polynomial);
    }
  }

  /** A Guardian's public key and proof. */
  @AutoValue
  public abstract static class ElectionPublicKey {
    public abstract String owner_id(); // guardian object_id
    public abstract int sequence_order();
    public abstract SchnorrProof proof();
    public abstract ElementModP key();

    public static ElectionPublicKey create(String owner_id, int sequence_order, SchnorrProof proof, ElementModP key) {
      return new AutoValue_KeyCeremony_ElectionPublicKey(owner_id, sequence_order, proof, key);
    }
  }

  /** A Guardian's public key set of auxiliary and election keys and proofs. */
  @AutoValue
  abstract static class PublicKeySet {
    abstract String owner_id(); // guardian object_id
    abstract int sequence_order(); // guardian sequence_order
    abstract java.security.PublicKey auxiliary_public_key();
    abstract ElementModP election_public_key();
    abstract SchnorrProof election_public_key_proof();

    public static PublicKeySet create(String owner_id, int sequence_order, java.security.PublicKey auxiliary_public_key,
                                      ElementModP election_public_key, SchnorrProof election_public_key_proof) {
      return new AutoValue_KeyCeremony_PublicKeySet(
              owner_id, sequence_order, auxiliary_public_key, election_public_key, election_public_key_proof);
    }
  }

  /** Pair of guardians involved in sharing. */
  @AutoValue
  abstract static class GuardianPair {
    abstract String owner_id();
    abstract String designated_id();

    public static GuardianPair create(String owner_id, String designated_id) {
      return new AutoValue_KeyCeremony_GuardianPair(owner_id, designated_id);
    }
  }

  /** A point on a secret polynomial, and commitments to verify this point for a designated guardian. */
  @AutoValue
  public abstract static class ElectionPartialKeyBackup {
    /** The Id of the guardian that generated this backup. */
    public abstract String owner_id();
    /** The Id of the guardian to receive this backup. */
    public abstract String designated_id();
    /** // The sequence order of the designated guardian. */
    public abstract int designated_sequence_order();
    /** The encrypted coordinate corresponding to a secret election polynomial. */
    public abstract Auxiliary.ByteString encrypted_value();
    /** The public keys `K_ij`generated from the election polynomial coefficients. */
    public abstract ImmutableList<ElementModP> coefficient_commitments();
    /** The proofs of possession of the private keys for the election polynomial secret coefficients. */
    public abstract ImmutableList<SchnorrProof> coefficient_proofs();

    public static ElectionPartialKeyBackup create(String owner_id,
                                                  String designated_id,
                                                  int designated_sequence_order,
                                                  Auxiliary.ByteString encrypted_value,
                                                  List<ElementModP> coefficient_commitments,
                                                  List<SchnorrProof> coefficient_proofs) {
      return new AutoValue_KeyCeremony_ElectionPartialKeyBackup(owner_id,
              designated_id,
              designated_sequence_order,
              encrypted_value,
              ImmutableList.copyOf(coefficient_commitments),
              ImmutableList.copyOf(coefficient_proofs));
    }
  }

  /** The public validation pieces for election key coefficients for one Guardian. */
  @AutoValue
  public abstract static class CoefficientValidationSet {
    public abstract String owner_id(); // Guardian.object_id
    public abstract ImmutableList<ElementModP> coefficient_commitments(); // the Kij of the specification
    public abstract ImmutableList<SchnorrProof> coefficient_proofs();

    /**
     * Create a CoefficientValidationSet for a guardian
     * @param guardian_id the Guardian.object_id
     * @param coefficient_commitments the public polynomial coefficient commitments
     * @param coefficient_proofs the proofs for the coefficient commitments
     */
    public static CoefficientValidationSet create(String guardian_id, List<ElementModP> coefficient_commitments,
                                                  List<SchnorrProof> coefficient_proofs) {
      Preconditions.checkArgument(!Strings.isNullOrEmpty(guardian_id));
      return new AutoValue_KeyCeremony_CoefficientValidationSet(
              guardian_id,
              ImmutableList.copyOf(coefficient_commitments),
              ImmutableList.copyOf(coefficient_proofs));
    }
  }

  /** The secret polynomial coefficients for one Guardian. */
  @AutoValue
  public abstract static class CoefficientSet {
    public abstract String guardianId(); // Guardian.object_id
    public abstract int guardianSequence(); // i: a unique number in [1, 256) that is the polynomial x value for this guardian
    public abstract ImmutableList<ElementModQ> coefficients(); // the secret polynomial coefficients

    /**
     * Create a CoefficientSet for the ith guardian, i &gt; 0
     * @param guardian_id the Guardian.object_id
     * @param guardian the guardian value i, must be &gt; 0
     * @param coefficients the secret polynomial coefficients
     */
    public static CoefficientSet create(String guardian_id, int guardian, List<ElementModQ> coefficients) {
      Preconditions.checkArgument(!Strings.isNullOrEmpty(guardian_id));
      Preconditions.checkArgument(guardian > 0);
      Preconditions.checkNotNull(coefficients);
      return new AutoValue_KeyCeremony_CoefficientSet(guardian_id, guardian, ImmutableList.copyOf(coefficients));
    }

    // This is what the Guardian needs.
    ElectionKeyPair generate_election_key_pair() {
      ElectionPolynomial polynomial = ElectionPolynomial.generate_polynomial(coefficients());
      ElGamal.KeyPair key_pair = new ElGamal.KeyPair(
              polynomial.coefficients.get(0), polynomial.coefficient_commitments.get(0));
      SchnorrProof proof = SchnorrProof.make_schnorr_proof(key_pair, rand_q());
      return ElectionKeyPair.create(key_pair, proof, polynomial);
    }
  }

  /** Verification of election partial key used in key sharing. */
  @AutoValue
  public abstract static class ElectionPartialKeyVerification {
    public abstract String owner_id();
    public abstract String designated_id();
    public abstract String verifier_id();
    public abstract boolean verified();

    public static ElectionPartialKeyVerification create(String owner_id, String designated_id, String verifier_id, boolean verified) {
      return new AutoValue_KeyCeremony_ElectionPartialKeyVerification(owner_id, designated_id, verifier_id, verified);
    }
  }

  /** Challenge of election partial key used in key sharing. */
  @AutoValue
  abstract static class ElectionPartialKeyChallenge {
    abstract String owner_id();
    abstract String designated_id();
    abstract int designated_sequence_order(); // The sequence order of the designated guardian
    abstract ElementModQ value();
    abstract ImmutableList<ElementModP> coefficient_commitments();
    abstract ImmutableList<SchnorrProof> coefficient_proofs();

    public static ElectionPartialKeyChallenge create(String owner_id, String designated_id, int designated_sequence_order, ElementModQ value, List<ElementModP> coefficient_commitments, List<SchnorrProof> coefficient_proofs) {
      return new AutoValue_KeyCeremony_ElectionPartialKeyChallenge(owner_id, designated_id, designated_sequence_order, value,
              ImmutableList.copyOf(coefficient_commitments), ImmutableList.copyOf(coefficient_proofs));
    }
  }

  /** Generate auxiliary key pair using RSA . */
  static Auxiliary.KeyPair generate_rsa_auxiliary_key_pair() {
    KeyPair rsa_key_pair = Rsa.rsa_keypair();
    return new Auxiliary.KeyPair(rsa_key_pair.getPrivate(), rsa_key_pair.getPublic());
  }

  /**
   * Generate election key pair, proof, and polynomial.
   * @param quorum: Quorum of guardians needed to decrypt
   * @param nonce: Optional nonce for determinism, use null when generating in production.
   */
  static ElectionKeyPair generate_election_key_pair(int quorum, @Nullable ElementModQ nonce) {
    ElectionPolynomial polynomial = ElectionPolynomial.generate_polynomial(quorum, nonce);
    // the 0th coefficient is the secret s for the ith Guardian
    // the 0th commitment is the public key = g^s mod p
    // The key_pair is Ki = election keypair for ith Guardian
    ElGamal.KeyPair key_pair = new ElGamal.KeyPair(
            polynomial.coefficients.get(0), polynomial.coefficient_commitments.get(0));
    SchnorrProof proof = SchnorrProof.make_schnorr_proof(key_pair, rand_q());
    return ElectionKeyPair.create(key_pair, proof, polynomial);
  }

  /**
   * Generate election partial key backup for sharing.
   * @param owner_id: Owner of election key
   * @param polynomial: The owner's Election polynomial
   * @param auxiliary_public_key: The Auxiliary public key
   * @param encryptor Function to encrypt using auxiliary key
   * @return Election partial key backup
   */
  static Optional<ElectionPartialKeyBackup> generate_election_partial_key_backup(
          String owner_id,
          ElectionPolynomial polynomial,
          Auxiliary.PublicKey auxiliary_public_key,
          @Nullable Auxiliary.Encryptor encryptor) {
    if (encryptor == null) {
      encryptor = Rsa::encrypt;
    }

    ElementModQ value = ElectionPolynomial.compute_polynomial_coordinate(
            BigInteger.valueOf(auxiliary_public_key.sequence_order), polynomial);
    Optional<Auxiliary.ByteString> encrypted_value = encryptor.encrypt(value.to_hex(), auxiliary_public_key.key);
    if (encrypted_value.isEmpty()) {
      return Optional.empty();
    }
    return Optional.of(ElectionPartialKeyBackup.create(
            owner_id,
            auxiliary_public_key.owner_id,
            auxiliary_public_key.sequence_order,
            encrypted_value.get(),
            polynomial.coefficient_commitments,
            polynomial.coefficient_proofs));
  }

  /** Get coefficient validation set from polynomial. */
   static CoefficientValidationSet get_coefficient_validation_set(
          String guardian_id,
          ElectionPolynomial polynomial) {
     return CoefficientValidationSet.create(guardian_id, polynomial.coefficient_commitments, polynomial.coefficient_proofs);
   }

  /**
   * Verify election partial key backup contains point on owners polynomial.
   * @param verifier_id: Verifier of the partial key backup
   * @param backup: Election partial key backup
   * @param auxiliary_key_pair: Auxiliary key pair
   * @param decryptor Decryption function using auxiliary key, or null for default.
   */
  static ElectionPartialKeyVerification verify_election_partial_key_backup(
          String verifier_id,
          ElectionPartialKeyBackup backup,
          Auxiliary.KeyPair auxiliary_key_pair,
          @Nullable Auxiliary.Decryptor decryptor) {
    if (decryptor == null) {
      decryptor = Rsa::decrypt;
    }

    Optional<String> decrypted_value = decryptor.decrypt(backup.encrypted_value(), auxiliary_key_pair.secret_key);
    if (decrypted_value.isEmpty()) {
      return ElectionPartialKeyVerification.create(backup.owner_id(), backup.designated_id(), verifier_id, false);
    }
    ElementModQ value = Group.hex_to_q(decrypted_value.get()).orElseThrow(IllegalStateException::new);
    return ElectionPartialKeyVerification.create(
            backup.owner_id(),
            backup.designated_id(),
            verifier_id,
            ElectionPolynomial.verify_polynomial_coordinate(value, BigInteger.valueOf(backup.designated_sequence_order()),
                    backup.coefficient_commitments()));
  }

  /**
   * Generate challenge to a previous verification of a partial key backup.
   * @param backup: Election partial key backup in question
   * @param polynomial: Polynomial to regenerate point
   * @return Election partial key verification
   */
  static ElectionPartialKeyChallenge generate_election_partial_key_challenge(
          ElectionPartialKeyBackup backup,
          ElectionPolynomial polynomial) {

    return ElectionPartialKeyChallenge.create(
            backup.owner_id(),
            backup.designated_id(),
            backup.designated_sequence_order(),
            ElectionPolynomial.compute_polynomial_coordinate(BigInteger.valueOf(backup.designated_sequence_order()), polynomial),
            backup.coefficient_commitments(),
            backup.coefficient_proofs());
  }

  /**
   * Verify a challenge to a previous verification of a partial key backup.
   * @param verifier_id: Verifier of the challenge
   * @param challenge: Election partial key challenge
   * @return Election partial key verification
   */
  static ElectionPartialKeyVerification verify_election_partial_key_challenge(
          String verifier_id, ElectionPartialKeyChallenge challenge) {

    return ElectionPartialKeyVerification.create(
            challenge.owner_id(),
            challenge.designated_id(),
            verifier_id,
            ElectionPolynomial.verify_polynomial_coordinate(challenge.value(),
                    BigInteger.valueOf(challenge.designated_sequence_order()),
                    challenge.coefficient_commitments()));
  }

  /**
   * Creates a joint election key from the public keys of all guardians.
   * @return Joint key for election
   */
  static ElementModP combine_election_public_keys(Map<String, ElectionPublicKey> election_public_keys) {
    List<ElementModP> public_keys = election_public_keys.values().stream()
            .map(pk -> pk.key()).collect(Collectors.toList());

    return ElGamal.elgamal_combine_public_keys(public_keys);
  }
}
