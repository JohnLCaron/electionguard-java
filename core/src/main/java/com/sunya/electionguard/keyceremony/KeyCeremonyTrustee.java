package com.sunya.electionguard.keyceremony;

import at.favre.lib.bytes.Bytes;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.flogger.FluentLogger;
import com.sunya.electionguard.ElGamal;
import com.sunya.electionguard.ElectionPolynomial;
import com.sunya.electionguard.Group;
import com.sunya.electionguard.Rsa;
import com.sunya.electionguard.SchnorrProof;
import com.sunya.electionguard.core.HashedElGamalCiphertext;
import com.sunya.electionguard.core.UInt256;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;
import java.math.BigInteger;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.sunya.electionguard.Group.ElementModP;
import static com.sunya.electionguard.Group.ElementModQ;
import static com.sunya.electionguard.Group.rand_q;

/**
 * A Trustee/Guardian used in the KeyCeremony, with secrets hidden as much as possible.
 * Mutable.
 * This object must not be used with untrusted code.
 */
public class KeyCeremonyTrustee {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  public final String id;

  // a unique number in [1, 256) that is the polynomial x value for this guardian. aka sequence_order.
  public final int xCoordinate;

  // All of the guardians' public keys (including this one), keyed by guardian id.
  public final Map<String, KeyCeremony2.PublicKeys> guardianPublicKeys;

  // This guardian's partial key backups of other guardians' keys, keyed by designated guardian id.
  public final Map<String, KeyCeremony2.SecretKeyShare> mySecretKeyShares;

  // Other guardians' partial key backups of this guardian's keys, keyed by generating guardian id.
  public final Map<String, KeyCeremony2.SecretKeyShare> guardianSecretKeyShares;

  // All secret info is in here.
  private final GuardianSecrets guardianSecrets;

  /**
   * Create a random polynomial of rank quorum.
   */
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
    this.guardianPublicKeys = new HashMap<>();
    this.guardianSecretKeyShares = new HashMap<>();
    this.mySecretKeyShares = new HashMap<>();

    // allGuardianPublicKeys include itself.
    this.guardianPublicKeys.put(id, this.sharePublicKeys());
  }

  // yuck, how can we do better than public? pass in the proto?
  public GuardianSecrets secrets() {
    return guardianSecrets;
  }

  /**
   * Share public keys for this guardian.
   */
  public KeyCeremony2.PublicKeys sharePublicKeys() {
    ElectionPolynomial polynomial = this.guardianSecrets.polynomial;
    return new KeyCeremony2.PublicKeys(
            this.id,
            this.xCoordinate,
            polynomial.coefficient_commitments,
            polynomial.coefficient_proofs);
  }

  /**
   * Receive publicKeys from another guardian. Return error message or empty string on success.
   */
  public String receivePublicKeys(KeyCeremony2.PublicKeys publicKeys) {
    Preconditions.checkNotNull(publicKeys);
    if (publicKeys.guardianId().equals(this.id)) {
      return "Guardian Id equals mine";
    }
    if (publicKeys.isValid()) {
      this.guardianPublicKeys.put(publicKeys.guardianId(), publicKeys);
      return "";
    }
    return "receivePublicKeys: Invalid Schnorr proof";
  }

  /**
   * Share this guardians backup for otherGuardian.
   */
  public KeyCeremony2.SecretKeyShare sendPartialKeyBackup(String otherGuardian) {
    if (id.equals(otherGuardian)) {
      return new KeyCeremony2.SecretKeyShare(
              this.id,
              otherGuardian,
              0,
              null,
              String.format("sendPartialKeyBackup cannot ask for its own backup '%s'", this.id));
    }
    if (this.mySecretKeyShares.containsKey(otherGuardian)) {
      return this.mySecretKeyShares.get(otherGuardian);
    }

    KeyCeremony2.SecretKeyShare backup = generatePartialKeyBackup(otherGuardian);
    if (backup.error().isEmpty()) {
      // only save if no error, otherwise we could use computeIfEmpty()
      this.mySecretKeyShares.put(otherGuardian, backup);
    }

    return backup;
  }

  private KeyCeremony2.SecretKeyShare generatePartialKeyBackup(String otherGuardian) {
    KeyCeremony2.PublicKeys otherKeys = this.guardianPublicKeys.get(otherGuardian);
    if (otherKeys == null) {
      return new KeyCeremony2.SecretKeyShare(
              this.id,
              otherGuardian,
              0,
              null,
              String.format("Trustee '%s', does not have public key for '%s'", this.id, otherGuardian));
    }

    // Compute my polynomial's y value at the other's x coordinate = Pi(ℓ)
    ElementModQ Pil = this.guardianSecrets.polynomial.valueAt(otherKeys.guardianXCoordinate());
    // The encryption of that, using the other's public key. See spe 1.03 eq 17
    HashedElGamalCiphertext EPil = HashedElGamalCiphertext.create(
            Pil.bytes(),
            otherKeys.publicKey(),
            Group.rand_q());

    return new KeyCeremony2.SecretKeyShare(
            id,
            otherGuardian,
            otherKeys.guardianXCoordinate(),
            EPil,
            null);
  }

  // The designated guardian verifies a backup of its own key from the generatingGuardian
  public KeyCeremony2.PartialKeyVerification verifyPartialKeyBackup(KeyCeremony2.SecretKeyShare backup) {
    if (!backup.designatedGuardianId().equals(this.id)) {
      return new KeyCeremony2.PartialKeyVerification(
              backup.generatingGuardianId(),
              backup.designatedGuardianId(),
              String.format("Sent backup to wrong trustee '%s', should be trustee '%s'", this.id, backup.designatedGuardianId()));
    }

    this.guardianSecretKeyShares.put(backup.generatingGuardianId(), backup);

    // Is that value consistent with the generating guardian's commitments?
    KeyCeremony2.PublicKeys generatingKeys = guardianPublicKeys.get(backup.generatingGuardianId());
    if (generatingKeys == null) {
      return new KeyCeremony2.PartialKeyVerification(
              backup.generatingGuardianId(),
              backup.designatedGuardianId(),
              String.format("'%s' trustee does not have public key for '%s' trustee", this.id, backup.generatingGuardianId()));
    }

    Bytes byteArray = backup.encryptedCoordinate().decrypt(this.guardianSecrets.election_key_pair.secret_key());
    if (byteArray == null) {
      throw new IllegalStateException(String.format(
              "Trustee%s backup for %s couldnt decrypt encryptedCoordinate", id, backup.generatingGuardianId()));
    }
    ElementModQ expected = new UInt256(byteArray.array()).toModQ();

    boolean verify = ElectionPolynomial.verifyPolynomialCoordinate(expected, backup.designatedGuardianXCoordinate(),
            generatingKeys.coefficientCommitments());

    return new KeyCeremony2.PartialKeyVerification(
            backup.generatingGuardianId(),
            backup.designatedGuardianId(),
            verify ? null : "verify_polynomial_coordinate against public committments failed");
  }

  /**
   * Publish response to a challenge of this guardian's backup for otherGuardian.
   * The response is the unencrypted value of the otherGuardian's coordinate in this guardians' secret polynomial.
   */
  public KeyCeremony2.PartialKeyChallengeResponse sendBackupChallenge(String otherGuardian) {
    KeyCeremony2.SecretKeyShare backup = this.mySecretKeyShares.get(otherGuardian);
    if (backup == null) {
      return new KeyCeremony2.PartialKeyChallengeResponse(
              this.id,
              otherGuardian,
              0,
              null,
              String.format("Trustee '%s' does not have backup for '%s' trustee", this.id, otherGuardian));
    }
    if (!backup.designatedGuardianId().equals(otherGuardian)) {
      return new KeyCeremony2.PartialKeyChallengeResponse(
              this.id,
              otherGuardian,
              0,
              null,
              String.format("Trustee %s' backup for '%s' does not match the designatedGuardianId %s", this.id, otherGuardian, backup.designatedGuardianId()));
    }
    if (!backup.generatingGuardianId().equals(this.id)) {
      return new KeyCeremony2.PartialKeyChallengeResponse(
              this.id,
              otherGuardian,
              0,
              null,
              String.format("Trustee %s' backup for '%s' does not match the generatingGuardianId %s", this.id, otherGuardian, backup.generatingGuardianId()));
    }
    ElectionPolynomial polynomial = this.guardianSecrets.polynomial;
    return new KeyCeremony2.PartialKeyChallengeResponse(
            backup.generatingGuardianId(),
            backup.designatedGuardianId(),
            backup.designatedGuardianXCoordinate(),
            polynomial.valueAt(backup.designatedGuardianXCoordinate()),
            null);
  }

  /**
   * Creates a joint election key from the public keys of all guardians.
   */
  public ElementModP publishJointKey() {
    List<ElementModP> public_keys = guardianPublicKeys.values().stream()
            .map(KeyCeremony2.PublicKeys::publicKey)
            .toList();

    return ElGamal.elgamal_combine_public_keys(public_keys);
  }

  @Immutable
  public static class GuardianSecrets {
    /**
     * The Guardian's polynomial.
     */
    public final ElectionPolynomial polynomial;
    /**
     * K = (s, g^s), for this Guardian
     */
    public final ElGamal.KeyPair election_key_pair;
    /**
     * The proof of knowledge of possession of the associated private key. (not secret)
     */
    public final SchnorrProof proof;

    private GuardianSecrets(ElGamal.KeyPair key_pair, SchnorrProof proof, ElectionPolynomial polynomial, java.security.KeyPair rsa_keypair) {
      this.election_key_pair = key_pair;
      this.proof = proof;
      this.polynomial = polynomial;
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

    @Override
    public String toString() {
      return "GuardianSecrets{" +
              "polynomial=" + polynomial +
              ", election_key_pair=" + election_key_pair +
              ", proof=" + proof +
              '}';
    }
  }

  @Override
  public String toString() {
    return "KeyCeremonyTrustee{" +
            "id='" + id + '\'' +
            ", xCoordinate=" + xCoordinate +
            ", allGuardianPublicKeys=" + guardianPublicKeys +
            ", myPartialKeyBackups=" + mySecretKeyShares +
            ", otherGuardianPartialKeyBackups=" + guardianSecretKeyShares +
            ", guardianSecrets=" + guardianSecrets +
            '}';
  }
}
