package com.sunya.electionguard.decrypting;

import com.google.common.base.Preconditions;
import com.google.common.flogger.FluentLogger;
import com.sunya.electionguard.BallotBox;
import com.sunya.electionguard.ChaumPedersen;
import com.sunya.electionguard.ElGamal;
import com.sunya.electionguard.Group;
import com.sunya.electionguard.keyceremony.KeyCeremony2;

import javax.annotation.Nullable;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.sunya.electionguard.Group.ONE_MOD_P;
import static com.sunya.electionguard.Group.ONE_MOD_Q;
import static com.sunya.electionguard.Group.mult_p;
import static com.sunya.electionguard.Group.pow_p;
import static com.sunya.electionguard.Group.rand_q;

/**
 * A Trustee/Guardian used in Decryptions, with secrets hidden as much as possible.
 * This object must not be used with untrusted code.
 * @param id The Guardian id
 * @param xCoordinate A unique number in [1, 256) that is the polynomial x value for this guardian. aka sequence_order.
 * @param election_keypair The election (ElGamal) secret key
 * @param otherGuardianPartialKeyBackups Other guardians' partial key backups of this guardian's keys, keyed by guardian id.
 * @param guardianCommittments All guardians' public coefficient commitments, keyed by guardian id.
 */
public record DecryptingTrustee(
  String id,
  int xCoordinate,
  ElGamal.KeyPair election_keypair,
  Map<String, KeyCeremony2.PartialKeyBackup> otherGuardianPartialKeyBackups,
  Map<String, List<Group.ElementModP>> guardianCommittments)  implements DecryptingTrusteeIF {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  // TODO id and sequence_order are not matching
  public DecryptingTrustee {
    id = Preconditions.checkNotNull(id);
    Preconditions.checkArgument(xCoordinate > 0);
    Preconditions.checkNotNull(election_keypair);
    Preconditions.checkArgument(otherGuardianPartialKeyBackups.size() > 0);
    Preconditions.checkArgument(guardianCommittments.size() == otherGuardianPartialKeyBackups.size() + 1);

    otherGuardianPartialKeyBackups = Map.copyOf(otherGuardianPartialKeyBackups);
    guardianCommittments = Map.copyOf(guardianCommittments);
  }

  @Override
  public Group.ElementModP electionPublicKey() {
    return election_keypair.public_key();
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
      Group.ElementModP partial_decryption = text.partial_decrypt(this.election_keypair.secret_key());
      // 𝑀_i = 𝐴^𝑠𝑖 mod 𝑝 and 𝐾𝑖 = 𝑔^𝑠𝑖 mod 𝑝
      ChaumPedersen.ChaumPedersenProof proof = ChaumPedersen.make_chaum_pedersen(
              text,
              this.election_keypair.secret_key(),
              partial_decryption,
              nonce_seed,
              extended_base_hash);

      boolean valid = proof.is_valid(text, this.election_keypair.public_key(), partial_decryption, extended_base_hash);
      if (!valid) {
        logger.atWarning().log(
                String.format(" partialDecrypt invalid proof for %s = %s%n ", this.id, proof) +
                        String.format("   message = %s %n ", text) +
                        String.format("   public_key = %s %n ", this.election_keypair.public_key().toShortString()) +
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
   * @param missing_guardian_id: the guardian
   * @param texts:               the ciphertext(s) that will be decrypted
   * @param extended_base_hash:  the extended base hash of the election used to generate the ElGamal Ciphertext
   * @param nonce_seed:          an optional value used to generate the `ChaumPedersenProof`
   *                             if no value is provided, a random number will be used.
   * @return a DecryptionProofRecovery with the decryption and its proof and a recovery key
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

    Group.ElementModP recovered = recoverPublicKey(missing_guardian_id);
    List<DecryptionProofRecovery> results = new ArrayList<>();
    for (ElGamal.Ciphertext text : texts) {
      // 𝑀_{𝑖,l} = 𝐴^P𝑖_{l}
      Group.ElementModP partial_decryption = text.partial_decrypt(backup.coordinate());

      // 𝑀_{𝑖,l} = 𝐴^𝑠𝑖 mod 𝑝 and 𝐾𝑖 = 𝑔^𝑠𝑖 mod 𝑝
      ChaumPedersen.ChaumPedersenProof proof = ChaumPedersen.make_chaum_pedersen(
              text,
              backup.coordinate(),
              partial_decryption,
              nonce_seed,
              extended_base_hash);

      boolean valid = proof.is_valid(text, recovered, partial_decryption, extended_base_hash);
      if (!valid) {
        logger.atWarning().log(
                String.format(" compensatedDecrypt invalid proof for %s = %s%n ", this.id, proof) +
                        String.format("   message = %s %n ", text) +
                        String.format("   public_key = %s %n ", recovered.toShortString()) +
                        String.format("   partial_decryption = %s %n ", partial_decryption.toShortString()) +
                        String.format("   extended_base_hash = %s %n ", extended_base_hash)
        );
        throw new IllegalArgumentException(String.format("CompensatedDecrypt invalid proof for %s missing = %s",
                this.id, missing_guardian_id));
      }

      results.add(new DecryptionProofRecovery(partial_decryption, proof, recovered));
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
    Group.ElementModQ xcoordQ = Group.int_to_q_unchecked(BigInteger.valueOf(this.xCoordinate));


    // compute the recovery public key, corresponding to the secret share Pi(l)
    // K_ij^(l^j) for j in 0..k-1.  K_ij is coefficients[j].public_key
    Group.ElementModP result = ONE_MOD_P;
    Group.ElementModQ exponent = ONE_MOD_Q;
    for (Group.ElementModP commitment : otherCommitments) {
      result = mult_p(result, pow_p(commitment, exponent));
      exponent = Group.mult_q(exponent, xcoordQ);
    }

    return result;
  }
}
