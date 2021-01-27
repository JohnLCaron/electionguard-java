package com.sunya.electionguard;

import com.google.common.base.Preconditions;
import com.google.common.flogger.FluentLogger;

import javax.annotation.concurrent.Immutable;
import java.math.BigInteger;
import java.util.Collection;
import java.util.Objects;
import java.util.Optional;

import static com.sunya.electionguard.Group.*;

public class ElGamal {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  /** A tuple of an ElGamal secret key and public key. */
  @Immutable
  static class KeyPair {
    final ElementModQ secret_key;
    final ElementModP public_key;

    public KeyPair(ElementModQ secret_key, ElementModP public_key) {
      this.secret_key = Preconditions.checkNotNull(secret_key);
      this.public_key = Preconditions.checkNotNull(public_key);
    }
  }

  /**
   * An "exponential ElGamal ciphertext" (i.e., with the plaintext in the exponent to allow for
   * homomorphic addition). Create one with `elgamal_encrypt`. Add them with `elgamal_add`.
   * Decrypt using one of the supplied instance methods.
   */
  @Immutable
  public static class Ciphertext {
    /** pad or alpha. */
    public final ElementModP pad;
    /** encrypted data or beta. */
    public final ElementModP data;

    public Ciphertext(ElementModP pad, ElementModP data) {
      this.pad = Preconditions.checkNotNull(pad);
      this.data = Preconditions.checkNotNull(data);
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      Ciphertext that = (Ciphertext) o;
      return pad.equals(that.pad) &&
              data.equals(that.data);
    }

    @Override
    public int hashCode() {
      return Objects.hash(pad, data);
    }

    /**
     * Decrypts an ElGamal ciphertext with a "known product" (the blinding factor used in the encryption).
     *
     * @param product: The known product (blinding factor).
     * @return An exponentially encoded plaintext message.
     */
    Integer decrypt_known_product(ElementModP product) {
      return Dlog.discrete_log(Group.mult_p(this.data, Group.mult_inv_p(product)));
    }

    /**
     * Decrypt an ElGamal ciphertext using the given ElGamal secret key.
     *
     * @param secretKey : The corresponding ElGamal secret key.
     * @return An exponentially encoded plaintext message.
     */
    Integer decrypt(ElementModQ secretKey) {
      return decrypt_known_product(Group.pow_p(this.pad, secretKey));
    }

    /**
     * Decrypt an ElGamal ciphertext using a known nonce and the ElGamal public key.
     *
     * @param publicKey : The corresponding ElGamal public key.
     * @param nonce :     The secret nonce used to create the ciphertext.
     * @return An exponentially encoded plaintext message.
     */
    Integer decrypt_known_nonce(ElementModP publicKey, ElementModQ nonce) {
      return decrypt_known_product(Group.pow_p(publicKey, nonce));
    }

    /**
     * Partially Decrypts an ElGamal ciphertext with a known ElGamal secret key.
     * <p>
     * 𝑀_i = 𝐴^𝑠𝑖 mod 𝑝 in the spec
     *
     * @param secretKey: The corresponding ElGamal secret key.
     * @return An exponentially encoded plaintext message.
     */
    ElementModP partial_decrypt(ElementModQ secretKey) {
      return Group.pow_p(this.pad, secretKey);
    }

    /** Computes a cryptographic hash of this ciphertext. */
    ElementModQ crypto_hash() {
      return Hash.hash_elems(this.pad, this.data);
    }
  }

  /**
   * Given an ElGamal secret key (typically, a random number in [2,Q)), returns
   * an ElGamal keypair, consisting of the given secret key a and public key g^a.
   */
  static Optional<KeyPair> elgamal_keypair_from_secret(ElementModQ a) {
    BigInteger secret_key_int = a.getBigInt();
    //     if secret_key_int < 2:
    if (Group.lessThan(secret_key_int, BigInteger.TWO)) {
      logger.atSevere().log("ElGamal secret key needs to be in [2,Q).");
      return Optional.empty();
    }
    return Optional.of(new KeyPair(a, g_pow_p(a)));
  }

  /** Create a random elgamal keypair. LOOK Move to test. */
  static KeyPair elgamal_keypair_random() {
    return elgamal_keypair_from_secret(rand_range_q(TWO_MOD_Q)).orElseThrow(RuntimeException::new);
  }

  /**
   * Combine multiple elgamal public keys into a joint key.
   *
   * @param keys list of public elgamal keys
   * @return joint key of elgamal keys
   */
  public static ElementModP elgamal_combine_public_keys(Collection<ElementModP> keys) {
    return Group.mult_p(keys);
  }

  /**
   * Encrypts a message with a given random nonce and an ElGamal public key.
   *
   * @param m          Message to elgamal_encrypt; must be an integer in [0,Q).
   * @param nonce      Randomly chosen nonce in [1,Q).
   * @param public_key ElGamal public key.
   * @return A ciphertext tuple.
   */
  static Optional<Ciphertext> elgamal_encrypt(int m, ElementModQ nonce, ElementModP public_key) {
    if (nonce.equals(ZERO_MOD_Q)) {
      logger.atSevere().log("ElGamal encryption requires a non-zero nonce");
      return Optional.empty();
    }
    return int_to_q(BigInteger.valueOf(m)).map(bm ->
            new Ciphertext(g_pow_p(nonce), mult_p(g_pow_p(bm), pow_p(public_key, nonce))));
  }

  /**
   * Homomorphically accumulates one or more ElGamal ciphertexts by pairwise multiplication. The exponents
   * of vote counters will add.
   */
  public static Ciphertext elgamal_add(Ciphertext... ciphertexts) {
    Preconditions.checkArgument(ciphertexts.length > 0, "Must have one or more ciphertexts for elgamal_add");

    Ciphertext result = ciphertexts[0];
    for (int i = 1; i < ciphertexts.length; i++) {
      Ciphertext next = ciphertexts[i];
      result = new Ciphertext(Group.mult_p(result.pad, next.pad), Group.mult_p(result.data, next.data));
    }
    return result;
  }

  static Ciphertext elgamal_add_show(Ciphertext... ciphertexts) {
    Preconditions.checkArgument(ciphertexts.length > 0, "Must have one or more ciphertexts for elgamal_add");

    Ciphertext result = ciphertexts[0];
    for (int i = 1; i < ciphertexts.length; i++) {
      Ciphertext next = ciphertexts[i];
      result = new Ciphertext(Group.mult_p(result.pad, next.pad), Group.mult_p(result.data, next.data));
      System.out.printf("     accum_alpha %s%n", result.pad);
      System.out.printf("     accum_beta %s%n", result.data);

    }
    return result;
  }
}
