package com.sunya.electionguard.keyceremony;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.flogger.FluentLogger;
import com.sunya.electionguard.Auxiliary;
import com.sunya.electionguard.ElGamal;
import com.sunya.electionguard.ElectionPolynomial;
import com.sunya.electionguard.Group;
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
 * A Trustee/Guardian used in the KeyCeremony, with secrets hidden as much as possible.
 * This object must not be used with untrusted code.
 */
public class KeyCeremonyTrustee {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  public final String id;

  // a unique number in [1, 256) that is the polynomial x value for this guardian. aka sequence_order.
  public final int xCoordinate;

  // All of the guardians' public keys (including this one), keyed by guardian id.
  public final Map<String, KeyCeremony2.PublicKeySet> allGuardianPublicKeys;

  // This guardian's partial key backups of other guardians' keys, keyed by designated guardian id.
  private final Map<String, KeyCeremony2.PartialKeyBackup> myPartialKeyBackups;

  // Other guardians' partial key backups of this guardian's keys, keyed by generating guardian id.
  public final Map<String, KeyCeremony2.PartialKeyBackup> otherGuardianPartialKeyBackups;

  // All secret info is in here.
  private final GuardianSecrets guardianSecrets;

  /** Create a random polynomial of rank quorum. */
  public KeyCeremonyTrustee(String id,
                            int xCoordinate,
                            int quorum,
                            @Nullable ElementModQ nonce_seed) {

    Preconditions.checkArgument(!Strings.isNullOrEmpty(id));
    Preconditions.checkArgument(xCoordinate > 0 && xCoordinate < 256);
    Preconditions.checkArgument(quorum > 0);

    this.id = id;
    this.xCoordinate = xCoordinate;

    this.guardianSecrets = GuardianSecrets.generate(quorum, nonce_seed);
    this.allGuardianPublicKeys = new HashMap<>();
    this.otherGuardianPartialKeyBackups = new HashMap<>();
    this.myPartialKeyBackups = new HashMap<>();

    // allGuardianPublicKeys include itself.
    this.allGuardianPublicKeys.put(id, this.sharePublicKeys());
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
    Preconditions.checkNotNull(publicKeys);
    if (publicKeys.ownerId().equals(this.id)) {
      return false;
    }
    if (publicKeys.isValid()) {
      this.allGuardianPublicKeys.put(publicKeys.ownerId(), publicKeys);
      return true;
    }
    return false;
  }

  /** Share this guardians backup for otherGuardian. */
  public KeyCeremony2.PartialKeyBackup sendPartialKeyBackup(String otherGuardian) {
    if (id.equals(otherGuardian)) {
      return KeyCeremony2.PartialKeyBackup.create(
              this.id,
              otherGuardian,
              0,
              null,
              String.format("sendPartialKeyBackup cannot ask for its own backup '%s'", this.id));
    }
    if (this.myPartialKeyBackups.containsKey(otherGuardian)) {
      return this.myPartialKeyBackups.get(otherGuardian);
    }

    KeyCeremony2.PartialKeyBackup backup = generatePartialKeyBackup(otherGuardian);
    if (backup.error().isEmpty()) {
      // only save if no error, otherwise we could use computeIfEmpty()
      this.myPartialKeyBackups.put(otherGuardian, backup);
    }

    return backup;
  }

  private KeyCeremony2.PartialKeyBackup generatePartialKeyBackup(String otherGuardian) {
    KeyCeremony2.PublicKeySet otherKeys = this.allGuardianPublicKeys.get(otherGuardian);
    if (otherKeys == null) {
      return KeyCeremony2.PartialKeyBackup.create(
              this.id,
              otherGuardian,
              0,
              null,
              String.format("Trustee '%s', does not have public key for '%s'", this.id, otherGuardian));
    }

    // Compute my polynomial's y value at the other's x coordinate.
    ElementModQ value = ElectionPolynomial.compute_polynomial_coordinate(
            BigInteger.valueOf(otherKeys.guardianXCoordinate()), this.guardianSecrets.polynomial);

    // Encrypt the value with the other guardian's public auxiliary key.
    Optional<Auxiliary.ByteString> encrypted_value = Rsa.encrypt(value.to_hex(), otherKeys.auxiliaryPublicKey());
    if (encrypted_value.isEmpty()) {
      return KeyCeremony2.PartialKeyBackup.create(
              this.id,
              otherGuardian,
              0,
              null,
              String.format("Trustee '%s', Rsa.encrypt key for '%s' failed", this.id, otherGuardian));
    }

    return KeyCeremony2.PartialKeyBackup.create(
            this.id,
            otherGuardian,
            otherKeys.guardianXCoordinate(),
            encrypted_value.get(),
            null);
  }

  // The designated guardian verifies a backup of its own key from the generatingGuardian
  public KeyCeremony2.PartialKeyVerification verifyPartialKeyBackup(KeyCeremony2.PartialKeyBackup backup) {
    if (!backup.designatedGuardianId().equals(this.id)) {
      return KeyCeremony2.PartialKeyVerification.create(
              backup.generatingGuardianId(),
              backup.designatedGuardianId(),
              String.format("Sent backup to wrong trustee '%s', should be trustee '%s'", this.id, backup.designatedGuardianId()));
    }

    this.otherGuardianPartialKeyBackups.put(backup.generatingGuardianId(), backup);

    // decrypt the coordinate with my private key.
    Optional<String> decryptedCoordinate = Rsa.decrypt(backup.encryptedCoordinate(), this.guardianSecrets.rsa_keypair.getPrivate());
    if (decryptedCoordinate.isEmpty()) {
      return KeyCeremony2.PartialKeyVerification.create(backup.generatingGuardianId(), backup.designatedGuardianId(),"Rsa.decrypt failed");
    }
    Optional<ElementModQ> valueO = Group.hex_to_q(decryptedCoordinate.get());
    if (valueO.isEmpty()) {
      return KeyCeremony2.PartialKeyVerification.create(backup.generatingGuardianId(), backup.designatedGuardianId(),
              "Convert decrypted coordinate to ElementModQ failed");
    }
    ElementModQ value = valueO.get();

    // Is that value consistent with the generating guardian's commitments?
    KeyCeremony2.PublicKeySet generatingKeys = allGuardianPublicKeys.get(backup.generatingGuardianId());
    if (generatingKeys == null) {
      return KeyCeremony2.PartialKeyVerification.create(
              backup.generatingGuardianId(),
              backup.designatedGuardianId(),
              String.format("'%s' trustee does not have public key for '%s' trustee", this.id, backup.generatingGuardianId()));
    }
    boolean verify = ElectionPolynomial.verify_polynomial_coordinate(value, BigInteger.valueOf(backup.designatedGuardianXCoordinate()),
            generatingKeys.coefficientCommitments());

    return KeyCeremony2.PartialKeyVerification.create(
            backup.generatingGuardianId(),
            backup.designatedGuardianId(),
            verify ? null : "verify_polynomial_coordinate against public committments failed");
  }

  /**
   * Publish response to a challenge of this guardian's backup for otherGuardian.
   * The response is the unencrypted value of the otherGuardian's coordinate in this guardians' secret polynomial.
   */
  public KeyCeremony2.PartialKeyChallengeResponse sendBackupChallenge(String otherGuardian) {
    KeyCeremony2.PartialKeyBackup backup = this.myPartialKeyBackups.get(otherGuardian);
    if (backup == null) {
      return KeyCeremony2.PartialKeyChallengeResponse.create(
              this.id,
              otherGuardian,
              0,
              null,
              String.format("Trustee '%s' does not have backup for '%s' trustee", this.id, otherGuardian));
    }
    if (!backup.designatedGuardianId().equals(otherGuardian)) {
      return KeyCeremony2.PartialKeyChallengeResponse.create(
              this.id,
              otherGuardian,
              0,
              null,
              String.format("Trustee %s' backup for '%s' does not match the designatedGuardianId %s", this.id, otherGuardian, backup.designatedGuardianId()));
    }
    if (!backup.generatingGuardianId().equals(this.id)) {
      return KeyCeremony2.PartialKeyChallengeResponse.create(
              this.id,
              otherGuardian,
              0,
              null,
              String.format("Trustee %s' backup for '%s' does not match the generatingGuardianId %s", this.id, otherGuardian, backup.generatingGuardianId()));
    }
    ElectionPolynomial polynomial = this.guardianSecrets.polynomial;
    return KeyCeremony2.PartialKeyChallengeResponse.create(
            backup.generatingGuardianId(),
            backup.designatedGuardianId(),
            backup.designatedGuardianXCoordinate(),
            ElectionPolynomial.compute_polynomial_coordinate(BigInteger.valueOf(backup.designatedGuardianXCoordinate()), polynomial),
            null);
  }

  /** Creates a joint election key from the public keys of all guardians. */
  public ElementModP publishJointKey() {
    List<ElementModP> public_keys = allGuardianPublicKeys.values().stream()
            .map(KeyCeremony2.PublicKeySet::electionPublicKey).collect(Collectors.toList());

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

}
