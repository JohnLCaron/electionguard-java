package com.sunya.electionguard.decrypting;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.flogger.FluentLogger;
import com.sunya.electionguard.ChaumPedersen;
import com.sunya.electionguard.DecryptionProofRecovery;
import com.sunya.electionguard.DecryptionProofTuple;
import com.sunya.electionguard.ElGamal;
import com.sunya.electionguard.Group;
import com.sunya.electionguard.Rsa;
import com.sunya.electionguard.keyceremony.KeyCeremony2;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.sunya.electionguard.Group.ONE_MOD_P;
import static com.sunya.electionguard.Group.hex_to_q;
import static com.sunya.electionguard.Group.mult_p;
import static com.sunya.electionguard.Group.pow_p;
import static com.sunya.electionguard.Group.rand_q;

@Immutable
public class DecryptingTrustee {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  public final String id;
  public final int xCoordinate;

  /** The auxiliary private key */
  public final java.security.PrivateKey rsa_private_key;

  /** The election (ElGamal) secret key */
  public final ElGamal.KeyPair election_keypair;

  /** Other guardians' partial key backups of this guardian's keys, keyed by guardian id. */
  public final ImmutableMap<String, KeyCeremony2.PartialKeyBackup> otherGuardianPartialKeyBackups;

  /** All guardians' public coefficient commitments, keyed by guardian id. */
  public final ImmutableMap<String, ImmutableList<Group.ElementModP>> guardianCommittments;

  public DecryptingTrustee(String id, int sequence_order,
                           java.security.PrivateKey rsa_private_key,
                           ElGamal.KeyPair election_keypair,
                           Map<String, KeyCeremony2.PartialKeyBackup> otherGuardianPartialKeyBackups,
                           Map<String, ImmutableList<Group.ElementModP>> guardianCommittments) {
    this.id = Preconditions.checkNotNull(id);
    Preconditions.checkArgument(sequence_order > 0);
    this.xCoordinate = sequence_order;
    this.rsa_private_key = Preconditions.checkNotNull(rsa_private_key);
    this.election_keypair = Preconditions.checkNotNull(election_keypair);

    Preconditions.checkArgument(otherGuardianPartialKeyBackups.size() > 0);
    Preconditions.checkArgument(guardianCommittments.size() == otherGuardianPartialKeyBackups.size() + 1);

    this.otherGuardianPartialKeyBackups = ImmutableMap.copyOf(otherGuardianPartialKeyBackups);
    this.guardianCommittments = ImmutableMap.copyOf(guardianCommittments);
  }

  public Group.ElementModP publicKey() {
    return election_keypair.public_key;
  }

  ///////////////////////////////////////////////////////////////////////////////////////////////////////////////////

  /**
   * Compute a compensated partial decryption of an elgamal encryption on behalf of the missing guardian.
   * LOOK Whats with ISSUE #47?
   * <p>
   *
   * @param missing_guardian_id: the guardian
   * @param texts:               the ciphertext(s) that will be decrypted
   * @param extended_base_hash:  the extended base hash of the election used to generate the ElGamal Ciphertext
   * @param nonce_seed:          an optional value used to generate the `ChaumPedersenProof`
   *                             if no value is provided, a random number will be used.
   * @return the decryption and its proof
   */
  public List<DecryptionProofRecovery> compensatedDecrypt(
          String missing_guardian_id,
          List<ElGamal.Ciphertext> texts,
          Group.ElementModQ extended_base_hash,
          @Nullable Group.ElementModQ nonce_seed) {

    if (nonce_seed == null) {
      nonce_seed = rand_q();
    }

    KeyCeremony2.PartialKeyBackup backup = this.otherGuardianPartialKeyBackups.get(missing_guardian_id);
    if (backup == null) {
      String mess = String.format("compensate_decrypt guardian %s missing backup for %s", this.id, missing_guardian_id);
      logger.atInfo().log(mess);
      throw new IllegalStateException(mess);
    }
    Optional<String> decrypted_value = Rsa.decrypt(backup.encryptedCoordinate(), this.rsa_private_key);
    if (decrypted_value.isEmpty()) {
      String mess = String.format("compensate decrypt guardian %s failed decryption for %s",
              this.id, missing_guardian_id);
      logger.atInfo().log(mess);
      throw new IllegalStateException(mess);
    }
    Optional<Group.ElementModQ> partialSecretKeyO = hex_to_q(decrypted_value.get());
    if (partialSecretKeyO.isEmpty()) {
      String mess = String.format("compensate hex_to_q guardian %s failed decryption for %s",
              this.id, missing_guardian_id);
      logger.atInfo().log(mess);
      throw new IllegalStateException(mess);
    }
    Group.ElementModQ partialSecretKey = partialSecretKeyO.get();

    Group.ElementModP recovered = recoverPublicKey(missing_guardian_id);

    List<DecryptionProofRecovery> results = new ArrayList<>();
    for (ElGamal.Ciphertext text : texts) {
      // 𝑀_{𝑖,l} = 𝐴^P𝑖_{l}
      Group.ElementModP partial_decryption = text.partial_decrypt(partialSecretKey);

      // 𝑀_{𝑖,l} = 𝐴^𝑠𝑖 mod 𝑝 and 𝐾𝑖 = 𝑔^𝑠𝑖 mod 𝑝
      ChaumPedersen.ChaumPedersenProof proof = ChaumPedersen.make_chaum_pedersen(
              text,
              partialSecretKey,
              partial_decryption,
              nonce_seed,
              extended_base_hash);

      Group.ElementModP publicKey = recoverPublicKey(missing_guardian_id);

      boolean valid = proof.is_valid(text, publicKey, partial_decryption, extended_base_hash);
      if (!valid) {
        logger.atWarning().log(
                String.format(" compensatedDecrypt invalid proof for %s = %s%n ", this.id, proof) +
                        String.format("   message = %s %n ", text) +
                        String.format("   public_key = %s %n ", publicKey.toShortString()) +
                        String.format("   partial_decryption = %s %n ", partial_decryption.toShortString()) +
                        String.format("   extended_base_hash = %s %n ", extended_base_hash)
        );
      }

      results.add(new DecryptionProofRecovery(partial_decryption, proof, recovered));
    }
    return results;
  }

  /**
   * Compute a partial decryption of an elgamal encryption.
   *
   * @param texts:            the `ElGamalCiphertext` that will be partially decrypted
   * @param extended_base_hash: the extended base hash of the election that
   *                            was used to generate t he ElGamal Ciphertext
   * @param nonce_seed:         an optional value used to generate the `ChaumPedersenProof`
   *                            if no value is provided, a random number will be used.
   * @return a `Tuple[ElementModP, ChaumPedersenProof]` of the decryption and its proof
   */
  public List<DecryptionProofTuple> partialDecrypt(
          List<ElGamal.Ciphertext> texts,
          Group.ElementModQ extended_base_hash,
          @Nullable Group.ElementModQ nonce_seed) {

    if (nonce_seed == null) {
      nonce_seed = rand_q();
    }

    List<DecryptionProofTuple> results = new ArrayList<>();
    for (ElGamal.Ciphertext text : texts) {
      // 𝑀_i = 𝐴^𝑠𝑖 mod 𝑝
      Group.ElementModP partial_decryption = text.partial_decrypt(this.election_keypair.secret_key);
      // 𝑀_i = 𝐴^𝑠𝑖 mod 𝑝 and 𝐾𝑖 = 𝑔^𝑠𝑖 mod 𝑝
      ChaumPedersen.ChaumPedersenProof proof = ChaumPedersen.make_chaum_pedersen(
              text,
              this.election_keypair.secret_key,
              partial_decryption,
              nonce_seed,
              extended_base_hash);

      boolean valid = proof.is_valid(text, this.election_keypair.public_key, partial_decryption, extended_base_hash);
      if (!valid) {
        logger.atWarning().log(
                String.format(" partialDecrypt invalid proof for %s = %s%n ", this.id, proof) +
                        String.format("   message = %s %n ", text) +
                        String.format("   public_key = %s %n ", this.election_keypair.public_key.toShortString()) +
                        String.format("   partial_decryption = %s %n ", partial_decryption.toShortString()) +
                        String.format("   extended_base_hash = %s %n ", extended_base_hash)
        );
      }

      results.add(new DecryptionProofTuple(partial_decryption, proof));
    }

    return results;
  }

  /** Compute the recovery public key for a given guardian. */
  public Group.ElementModP recoverPublicKey(String missing_guardian_id) {

    List<Group.ElementModP> otherCommitments = this.guardianCommittments.get(missing_guardian_id);
    if (otherCommitments == null) {
      String mess = String.format("recovery_public_key_for guardian %s missing commitments for %s", this.id, missing_guardian_id);
      logger.atSevere().log(mess);
      throw new IllegalStateException(mess);
    }

    // compute the recovery public key, corresponding to the secret share Pi(l)
    // K_ij^(l^j) for j in 0..k-1.  K_ij is coefficients[j].public_key
    Group.ElementModP public_key = ONE_MOD_P;
    int count = 0;
    for (Group.ElementModP commitment : otherCommitments) {
      Group.ElementModQ exponent = Group.pow_q(BigInteger.valueOf(this.xCoordinate), BigInteger.valueOf(count));
      public_key = mult_p(public_key, pow_p(commitment, exponent));
      count++;
    }

    return public_key;
  }
}
