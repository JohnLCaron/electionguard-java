package com.sunya.electionguard.decrypting;

import at.favre.lib.bytes.Bytes;
import com.google.common.base.Preconditions;
import com.google.common.flogger.FluentLogger;
import com.sunya.electionguard.BallotBox;
import com.sunya.electionguard.ChaumPedersen;
import com.sunya.electionguard.ElGamal;
import com.sunya.electionguard.ElectionPolynomial;
import com.sunya.electionguard.Group;
import com.sunya.electionguard.core.UInt256;
import com.sunya.electionguard.keyceremony.KeyCeremony2;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static com.sunya.electionguard.Group.rand_q;

/**
 * A Trustee/Guardian used in Decryptions, with secrets hidden as much as possible.
 * This object must not be used with untrusted code.
 */
public class DecryptingTrustee implements DecryptingTrusteeIF {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();
  private static final boolean validate = true; // expensive, debugging only

  final String id;
  final int xCoordinate;
  final ElGamal.KeyPair electionKeypair;
  final Map<String, KeyCeremony2.SecretKeyShare> secretKeyShares;
  final Map<String, List<Group.ElementModP>> coefficientCommitments;

  // lazy
  private final HashMap<String, Group.ElementModQ> generatingGuardianValues = new HashMap<>();
  private final HashMap<String, Group.ElementModP> gPilMap = new HashMap<>();

  /**
   * @param id The Guardian id
   * @param xCoordinate A unique number in [1, 256) that is the polynomial x value for this guardian. aka sequence_order.
   * @param electionKeypair The election (ElGamal) secret key
   * @param secretKeyShares Other guardians' partial key backups of this guardian's keys, keyed by guardian id.
   * @param coefficientCommitments All guardians' public coefficient commitments, keyed by guardian id.
   */
  public DecryptingTrustee(
          String id,
          int xCoordinate,
          ElGamal.KeyPair electionKeypair,
          Map<String, KeyCeremony2.SecretKeyShare> secretKeyShares,
          Map<String, List<Group.ElementModP>> coefficientCommitments) {
    this.id = Preconditions.checkNotNull(id);
    this.electionKeypair = Preconditions.checkNotNull(electionKeypair);

    Preconditions.checkArgument(xCoordinate > 0);
    Preconditions.checkArgument(secretKeyShares.size() > 0);
    Preconditions.checkArgument(coefficientCommitments.size() == secretKeyShares.size() + 1);

    this.xCoordinate = xCoordinate;
    this.secretKeyShares = Map.copyOf(secretKeyShares);
    this.coefficientCommitments = Map.copyOf(coefficientCommitments);
  }

  @Override
  public String id() {
    return id;
  }

  @Override
  public int xCoordinate() {
    return xCoordinate;
  }

  @Override
  public Group.ElementModP electionPublicKey() {
    return electionKeypair.public_key();
  }

  public ElGamal.KeyPair electionKeypair() {
    return electionKeypair;
  }

  public Collection<KeyCeremony2.SecretKeyShare> secretKeyShares() {
    return secretKeyShares.values();
  }

  public Map<String, List<Group.ElementModP>> coefficientCommitments() {
    return coefficientCommitments;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    DecryptingTrustee that = (DecryptingTrustee) o;
    return xCoordinate == that.xCoordinate && Objects.equals(id, that.id) && Objects.equals(electionKeypair, that.electionKeypair) && Objects.equals(secretKeyShares, that.secretKeyShares) && Objects.equals(coefficientCommitments, that.coefficientCommitments) && Objects.equals(generatingGuardianValues, that.generatingGuardianValues);
  }

  @Override
  public int hashCode() {
    return Objects.hash(id, xCoordinate, electionKeypair, secretKeyShares, coefficientCommitments, generatingGuardianValues);
  }

  ///////////////////////////////////////////////////////////////////////////////////////////////////////////////////

  /**
   * Compute a partial decryption of an elgamal encryption.
   *
   * @param texts:            the `ElGamalCiphertext` that will be partially decrypted
   * @param extended_base_hash: the extended base hash of the election that
   *                            was used to generate the ElGamal Ciphertext
   * @param nonce_seed:         an optional value used to generate the `ChaumPedersenProof`
   *                            if no value is provided, a random number will be used.
   * @return a DecryptionProofTuple of the decryption and its proof
   */
  public List<BallotBox.DecryptionProofTuple> partialDecrypt(
          List<ElGamal.Ciphertext> texts,
          Group.ElementModQ extended_base_hash,
          @Nullable Group.ElementModQ nonce_seed) {

    if (nonce_seed == null) {
      nonce_seed = rand_q();
    }

    List<BallotBox.DecryptionProofTuple> results = new ArrayList<>();
    for (ElGamal.Ciphertext text : texts) {
      // 𝑀_i = 𝐴^𝑠𝑖 mod 𝑝
      Group.ElementModP partial_decryption = text.partial_decrypt(this.electionKeypair.secret_key());
      // 𝑀_i = 𝐴^𝑠𝑖 mod 𝑝 and 𝐾𝑖 = 𝑔^𝑠𝑖 mod 𝑝
      ChaumPedersen.ChaumPedersenProof proof = ChaumPedersen.make_chaum_pedersen(
              text,
              this.electionKeypair.secret_key(),
              partial_decryption,
              nonce_seed,
              extended_base_hash);

      boolean valid = proof.is_valid(text, this.electionKeypair.public_key(), partial_decryption, extended_base_hash);
      if (!valid) {
        logger.atWarning().log(
                String.format(" partialDecrypt invalid proof for %s = %s%n ", this.id, proof) +
                        String.format("   message = %s %n ", text) +
                        String.format("   public_key = %s %n ", this.electionKeypair.public_key().toShortString()) +
                        String.format("   partial_decryption = %s %n ", partial_decryption.toShortString()) +
                        String.format("   extended_base_hash = %s %n ", extended_base_hash)
        );
        // throw new IllegalArgumentException(String.format("PartialDecrypt invalid proof for %s", this.id));
      }
      results.add(new BallotBox.DecryptionProofTuple(partial_decryption, proof));
    }
    return results;
  }

  /**
   * Compute a compensated partial decryption of an elgamal encryption on behalf of the missing guardian.
   * LOOK Whats with ISSUE #47?
   * <p>
   *
   * @param missingGuardianId: the guardian
   * @param texts:               the ciphertext(s) that will be decrypted
   * @param extended_base_hash:  the extended base hash of the election used to generate the ElGamal Ciphertext
   * @param nonce_seed:          an optional value used to generate the `ChaumPedersenProof`
   *                             if no value is provided, a random number will be used.
   * @return a DecryptionProofRecovery with the decryption and its proof and a recovery key
   */
  public List<DecryptionProofRecovery> compensatedDecrypt(
          String missingGuardianId,
          List<ElGamal.Ciphertext> texts,
          Group.ElementModQ extended_base_hash,
          @Nullable Group.ElementModQ nonce_seed) {

    if (nonce_seed == null) {
      nonce_seed = rand_q();
    }

    KeyCeremony2.SecretKeyShare backup = this.secretKeyShares.get(missingGuardianId);
    if (backup == null) {
      String mess = String.format("compensate_decrypt guardian %s missing backup for %s", this.id, missingGuardianId);
      logger.atInfo().log(mess);
      throw new IllegalStateException(mess);
    }

    // lazy decryption of key share.
    var generatingGuardianValue = this.generatingGuardianValues.get(missingGuardianId);
    if (generatingGuardianValue == null) {
      KeyCeremony2.SecretKeyShare sshare = this.secretKeyShares.get(missingGuardianId);
      if (sshare == null) {
        throw new IllegalStateException(String.format("compensate_decrypt guardian %s missing backup for %s",
                id, missingGuardianId));
      }
      Bytes byteArray = sshare.encryptedCoordinate().decrypt(this.electionKeypair.secret_key());
      if (byteArray == null) {
        throw new IllegalStateException(String.format("%s backup for %s couldnt decrypt encryptedCoordinate", id, missingGuardianId));
      }
      generatingGuardianValue = new UInt256(byteArray.array()).toModQ();
      this.generatingGuardianValues.put(missingGuardianId, generatingGuardianValue);
    }

    // lazy calculation of g^Pi(l).
    var gPil = this.gPilMap.get(missingGuardianId);
    if (gPil == null) {
      List<Group.ElementModP> coefficientCommitments = this.coefficientCommitments.get(missingGuardianId);
      if (coefficientCommitments == null) {
        throw new IllegalStateException(String.format( "guardian %s missing coefficientCommitments for %s",
                id, missingGuardianId));
      }
      gPil = ElectionPolynomial.calculateGexpPiAtL(this.xCoordinate, coefficientCommitments);
      this.gPilMap.put(missingGuardianId, gPil);
    }

    List<DecryptionProofRecovery> results = new ArrayList<>();
    for (ElGamal.Ciphertext text : texts) {
      // 𝑀_{𝑖,l} = 𝐴^P𝑖_{l}
      Group.ElementModP partial_decryption = text.partial_decrypt(generatingGuardianValue);

      // 𝑀_{𝑖,l} = 𝐴^𝑠𝑖 mod 𝑝 and 𝐾𝑖 = 𝑔^𝑠𝑖 mod 𝑝
      ChaumPedersen.ChaumPedersenProof proof = ChaumPedersen.make_chaum_pedersen(
              text,
              generatingGuardianValue,
              partial_decryption,
              nonce_seed,
              extended_base_hash);

      if (validate) {
        boolean valid = proof.is_valid(text, gPil, partial_decryption, extended_base_hash);
        if (!valid) {
          logger.atWarning().log(
                  String.format(" compensatedDecrypt invalid proof for %s = %s%n ", this.id, proof) +
                          String.format("   message = %s %n ", text) +
                          String.format("   public_key = %s %n ", gPil.toShortString()) +
                          String.format("   partial_decryption = %s %n ", partial_decryption.toShortString()) +
                          String.format("   extended_base_hash = %s %n ", extended_base_hash)
          );
          throw new IllegalArgumentException(String.format("CompensatedDecrypt invalid proof for %s missing = %s",
                  this.id, missingGuardianId));
        }
      }
      results.add(new DecryptionProofRecovery(partial_decryption, proof, gPil));
    }
    return results;
  }
}
