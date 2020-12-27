package com.sunya.electionguard;

import com.google.auto.value.AutoValue;

import javax.annotation.Nullable;
import java.math.BigInteger;
import java.security.KeyPair;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static com.sunya.electionguard.Group.*;

public class KeyCeremony {

  /**
   * Details of key ceremony.
   */
  @AutoValue
  abstract static class CeremonyDetails {
    abstract int number_of_guardians();

    abstract int quorum();

    public static CeremonyDetails create(int number_of_guardians, int quorum) {
      return new AutoValue_KeyCeremony_CeremonyDetails(number_of_guardians, quorum);
    }
  }

  /**
   * A tuple of election key pair, proof and polynomial
   */
  @AutoValue
  abstract static class ElectionKeyPair {
    abstract ElGamal.KeyPair key_pair();

    abstract SchnorrProof proof();

    abstract ElectionPolynomial polynomial();

    public static ElectionKeyPair create(ElGamal.KeyPair key_pair, SchnorrProof proof, ElectionPolynomial polynomial) {
      return new AutoValue_KeyCeremony_ElectionKeyPair(key_pair, proof, polynomial);
    }
  }

  /**
   * A tuple of election public key and owner information.
   */
  @AutoValue
  abstract static class ElectionPublicKey {
    abstract String owner_id();

    abstract SchnorrProof proof();

    abstract ElementModP key();

    public static ElectionPublicKey create(String owner_id, SchnorrProof proof, ElementModP key) {
      return new AutoValue_KeyCeremony_ElectionPublicKey(owner_id, proof, key);
    }
  }

  /**
   * Public key set of auxiliary and election keys and owner information.
   */
  @AutoValue
  abstract static class PublicKeySet {
    abstract String owner_id();

    abstract int sequence_order();

    abstract java.security.PublicKey auxiliary_public_key();

    abstract ElementModP election_public_key();

    abstract SchnorrProof election_public_key_proof();

    public static PublicKeySet create(String owner_id, int sequence_order, java.security.PublicKey auxiliary_public_key,
                                      ElementModP election_public_key, SchnorrProof election_public_key_proof) {
      return new AutoValue_KeyCeremony_PublicKeySet(
              owner_id, sequence_order, auxiliary_public_key, election_public_key, election_public_key_proof);
    }

  }

  /**
   * Pair of guardians involved in sharing.
   */
  @AutoValue
  abstract static class GuardianPair {
    abstract String owner_id();

    abstract String designated_id();

    public static GuardianPair create(String owner_id, String designated_id) {
      return new AutoValue_KeyCeremony_GuardianPair(owner_id, designated_id);
    }
  }

  /**
   * Election partial key backup used for key sharing.
   */
  @AutoValue
  abstract static class ElectionPartialKeyBackup {
    abstract String owner_id(); // The Id of the guardian that generated this backup

    abstract String designated_id(); // The Id of the guardian to receive this backup

    abstract int designated_sequence_order(); // The sequence order of the designated guardian

    abstract Auxiliary.ByteString encrypted_value(); // The encrypted coordinate corresponding to a secret election polynomial

    abstract List<ElementModP> coefficient_commitments(); // The public keys `K_ij`generated from the election polynomial coefficients

    abstract List<SchnorrProof> coefficient_proofs(); // the proofs of posession of the private keys for the election polynomial secret coefficients

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
              coefficient_commitments,
              coefficient_proofs);
    }
  }

  /**
   * Set of validation pieces for election key coefficients.
   */
  @AutoValue
  public abstract static class CoefficientValidationSet {
    public abstract String owner_id();

    abstract List<ElementModP> coefficient_commitments();

    abstract List<SchnorrProof> coefficient_proofs();

    public static CoefficientValidationSet create(String owner_id, List<ElementModP> coefficient_commitments, List<SchnorrProof> coefficient_proofs) {
      return new AutoValue_KeyCeremony_CoefficientValidationSet(owner_id, coefficient_commitments, coefficient_proofs);
    }
  }

  /**
   * verification of election partial key used in key sharing.
   */
  @AutoValue
  abstract static class ElectionPartialKeyVerification {
    abstract String owner_id();

    abstract String designated_id();

    abstract String verifier_id();

    abstract boolean verified();

    public static ElectionPartialKeyVerification create(String owner_id, String designated_id, String verifier_id, boolean verified) {
      return new AutoValue_KeyCeremony_ElectionPartialKeyVerification(owner_id, designated_id, verifier_id, verified);
    }
  }

  /**
   * Challenge of election partial key used in key sharing.
   */
  @AutoValue
  abstract static class ElectionPartialKeyChallenge {
    abstract String owner_id();

    abstract String designated_id();

    abstract int designated_sequence_order(); // The sequence order of the designated guardian

    abstract ElementModQ value();

    abstract List<ElementModP> coefficient_commitments();

    abstract List<SchnorrProof> coefficient_proofs();

    public static ElectionPartialKeyChallenge create(String owner_id, String designated_id, int designated_sequence_order, ElementModQ value, List<ElementModP> coefficient_commitments, List<SchnorrProof> coefficient_proofs) {
      return new AutoValue_KeyCeremony_ElectionPartialKeyChallenge(owner_id, designated_id, designated_sequence_order, value, coefficient_commitments, coefficient_proofs);
    }

  }

  /**
   * Generate auxiliary key pair using RSA .
   */
  static Auxiliary.KeyPair generate_rsa_auxiliary_key_pair() {
    KeyPair rsa_key_pair = Rsa.rsa_keypair();
    return new Auxiliary.KeyPair(rsa_key_pair.getPrivate(), rsa_key_pair.getPublic());
  }

  /**
   * Generate election key pair, proof, and polynomial
   * @param quorum: Quorum of guardians needed to decrypt
   * @return Election key pair
   */
  static ElectionKeyPair generate_election_key_pair(int quorum, @Nullable ElementModQ nonce) {
    ElectionPolynomial polynomial = ElectionPolynomial.generate_polynomial(quorum, nonce);
    ElGamal.KeyPair key_pair = new ElGamal.KeyPair(
            polynomial.coefficients.get(0), polynomial.coefficient_commitments.get(0));
    SchnorrProof proof = SchnorrProof.make_schnorr_proof(key_pair, rand_q());
    return ElectionKeyPair.create(key_pair, proof, polynomial);
  }

  /**
   * Generate election partial key backup for sharing
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
          String owner_id,
          ElectionPolynomial polynomial) {
     return CoefficientValidationSet.create(owner_id, polynomial.coefficient_commitments, polynomial.coefficient_proofs);
   }

  /**
   * Verify election partial key backup contain point on owners polynomial
   * @param verifier_id: Verifier of the partial key backup
   * @param backup: Election partial key backup
   * @param auxiliary_key_pair: Auxiliary key pair
   * @param decryptor Decryption function using auxiliary key
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
    ElementModQ value = Group.hex_to_q(decrypted_value.get()).get();
    return ElectionPartialKeyVerification.create(
            backup.owner_id(),
            backup.designated_id(),
            verifier_id,
            ElectionPolynomial.verify_polynomial_coordinate(value, BigInteger.valueOf(backup.designated_sequence_order()),
                    backup.coefficient_commitments()));
  }

  /**
   * Generate challenge to a previous verification of a partial key backup
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
   * Verify a challenge to a previous verification of a partial key backup
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
      Creates a joint election key from the public keys of all guardians
    @return Joint key for election
   */
  static ElementModP combine_election_public_keys(Map<String, ElectionPublicKey> election_public_keys) {
    // public_keys = map(lambda public_key:public_key.key, election_public_keys.values())
    List<ElementModP> public_keys = election_public_keys.values().stream().map(pk -> pk.key()).collect(Collectors.toList());

    return ElGamal.elgamal_combine_public_keys(public_keys);
  }
}
