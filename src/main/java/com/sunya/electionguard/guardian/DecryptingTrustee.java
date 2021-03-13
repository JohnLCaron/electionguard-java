package com.sunya.electionguard.guardian;

import com.google.common.flogger.FluentLogger;
import com.sunya.electionguard.ChaumPedersen;
import com.sunya.electionguard.DecryptionProofTuple;
import com.sunya.electionguard.ElGamal;
import com.sunya.electionguard.Group;
import com.sunya.electionguard.KeyCeremony;
import com.sunya.electionguard.Rsa;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;
import java.math.BigInteger;
import java.util.Map;
import java.util.Optional;

import static com.sunya.electionguard.Group.ONE_MOD_P;
import static com.sunya.electionguard.Group.hex_to_q;
import static com.sunya.electionguard.Group.mult_p;
import static com.sunya.electionguard.Group.pow_p;
import static com.sunya.electionguard.Group.rand_q;

public class DecryptingTrustee {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  public final String id;
  // a unique number in [1, 256) that is the polynomial x value for this guardian
  public final int sequence_order;
  public final int number_of_guardians;

  public final RemoteTrustee.GuardianSecrets guardianSecrets;

  // Other guardians' public keys that are shared with this guardian
  public final Map<String, KeyCeremony.PublicKeySet> allGuardianPublicKeys; // map(GUARDIAN_ID, PublicKeySet)

  // Other guardians' partial key backups of this guardian's keys. Needed for decryption.
  public final Map<String, KeyCeremony.ElectionPartialKeyBackup> otherGuardianPartialKeyBackups; // Map(GUARDIAN_ID, ElectionPartialKeyBackup)

  public DecryptingTrustee(String id, int sequence_order, int number_of_guardians, RemoteTrustee.GuardianSecrets guardianSecrets, Map<String, KeyCeremony.PublicKeySet> allGuardianPublicKeys, Map<String, KeyCeremony.ElectionPartialKeyBackup> otherGuardianPartialKeyBackups) {
    this.id = id;
    this.sequence_order = sequence_order;
    this.number_of_guardians = number_of_guardians;
    this.guardianSecrets = guardianSecrets;
    this.allGuardianPublicKeys = allGuardianPublicKeys;
    this.otherGuardianPartialKeyBackups = otherGuardianPartialKeyBackups;
  }

  ///////////////////////////////////////////////////////////////////////////////////////////////////////////////////

  /**
   * Compute a compensated partial decryption of an elgamal encryption on behalf of the missing guardian.
   * LOOK this seems to be the only place we need this.auxiliary_keys.secret_key. Whats with ISSUE #47?
   * <p>
   *
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
          Group.ElementModQ extended_base_hash,
          @Nullable Group.ElementModQ nonce_seed) {

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
    Group.ElementModQ partial_secret_key = hex_to_q(decrypted_value.get()).orElseThrow(IllegalStateException::new);

    // 𝑀_{𝑖,l} = 𝐴^P𝑖_{l}
    Group.ElementModP partial_decryption = elgamal.partial_decrypt(partial_secret_key);

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
          Group.ElementModQ extended_base_hash,
          @Nullable Group.ElementModQ nonce_seed) {

    if (nonce_seed == null) {
      nonce_seed = rand_q();
    }

    //TODO: ISSUE #47: Decrypt the election secret key

    // 𝑀_i = 𝐴^𝑠𝑖 mod 𝑝
    Group.ElementModP partial_decryption = elgamal.partial_decrypt(guardianSecrets.election_key_pair.secret_key);

    // 𝑀_i = 𝐴^𝑠𝑖 mod 𝑝 and 𝐾𝑖 = 𝑔^𝑠𝑖 mod 𝑝
    ChaumPedersen.ChaumPedersenProof proof = ChaumPedersen.make_chaum_pedersen(
            elgamal,
            guardianSecrets.election_key_pair.secret_key,
            partial_decryption,
            nonce_seed,
            extended_base_hash);

    return new DecryptionProofTuple(partial_decryption, proof);
  }

  /**
   * Compute the recovery public key for a given guardian.
   */
  private Optional<Group.ElementModP> recovery_public_key_for(String missing_guardian_id) {
    KeyCeremony.ElectionPartialKeyBackup backup = this.otherGuardianPartialKeyBackups.get(missing_guardian_id);
    if (backup == null) {
      logger.atInfo().log("compensate decrypt guardian %s missing backup for %s", this.id, missing_guardian_id);
      return Optional.empty();
    }

    // compute the recovery public key, corresponding to the secret share Pi(l)
    // K_ij^(l^j) for j in 0..k-1.  K_ij is coefficients[j].public_key
    Group.ElementModP pub_key = ONE_MOD_P;
    int count = 0;
    for (Group.ElementModP commitment : backup.coefficient_commitments()) {
      Group.ElementModQ exponent = Group.pow_q(BigInteger.valueOf(this.sequence_order), BigInteger.valueOf(count));
      pub_key = mult_p(pub_key, pow_p(commitment, exponent));
      count++;
    }

    return Optional.of(pub_key);
  }

  public Proxy getDecryptorProxy() {
    return new Proxy();
  }

  /**
   * Simulation of message broker service for Guardians/Trustees.
   */
  @Immutable
  public class Proxy {

    String id() {
      return id;
    }

    int sequence_order() {
      return sequence_order;
    }

    Group.ElementModP election_public_key() {
      return DecryptingTrustee.this.guardianSecrets.election_key_pair.public_key;
    }

    Optional<DecryptionProofTuple> compensate_decrypt(
            String missing_guardian_id,
            ElGamal.Ciphertext elgamal,
            Group.ElementModQ extended_base_hash,
            @Nullable Group.ElementModQ nonce_seed) {

      return DecryptingTrustee.this.compensate_decrypt(missing_guardian_id,
              elgamal,
              extended_base_hash,
              nonce_seed);
    }

    DecryptionProofTuple partially_decrypt(ElGamal.Ciphertext elgamal, Group.ElementModQ extended_base_hash, @Nullable Group.ElementModQ nonce_seed) {
      return DecryptingTrustee.this.partially_decrypt(elgamal, extended_base_hash, nonce_seed);
    }

    Optional<Group.ElementModP> recovery_public_key_for(String missing_guardian_id) {
      return DecryptingTrustee.this.recovery_public_key_for(missing_guardian_id);
    }
  }
}
