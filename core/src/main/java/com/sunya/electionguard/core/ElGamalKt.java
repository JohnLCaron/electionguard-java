package com.sunya.electionguard.core;

import com.google.common.base.Preconditions;
import com.google.common.flogger.FluentLogger;
import com.sunya.electionguard.Dlog;
import com.sunya.electionguard.Group;
import com.sunya.electionguard.Hash;

import java.math.BigInteger;

import static com.sunya.electionguard.Group.ElementModP;
import static com.sunya.electionguard.Group.ElementModQ;
import static com.sunya.electionguard.Group.TWO_MOD_Q;
import static com.sunya.electionguard.Group.ZERO_MOD_Q;
import static com.sunya.electionguard.Group.g_pow_p;
import static com.sunya.electionguard.Group.int_to_q_unchecked;
import static com.sunya.electionguard.Group.mult_p;
import static com.sunya.electionguard.Group.pow_p;
import static com.sunya.electionguard.Group.rand_range_q;

public class ElGamalKt {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private ElGamalKt() {}

  /** A tuple of an ElGamal secret key and public key. */
  public record KeyPair(
    ElementModQ secret_key,
    ElementModP public_key) {

    public KeyPair {
      Preconditions.checkNotNull(secret_key);
      Preconditions.checkNotNull(public_key);
    }
  }

  /**
   * An "exponential ElGamal ciphertext" (i.e., with the plaintext in the exponent to allow for
   * homomorphic addition).
   * @param pad pad or alpha
   * @param data encrypted data or beta
   */
  public record Ciphertext(
    ElementModP pad,
    ElementModP data) implements Hash.CryptoHashableString {

    public Ciphertext {
      Preconditions.checkNotNull(pad);
      Preconditions.checkNotNull(data);
      if (!data.is_valid_residue()) {
        throw new IllegalStateException();
      }
    }

    /**
     * Decrypt an ElGamal ciphertext using the given ElGamal secret key.
     *
     * @param secretKey : The corresponding ElGamal secret key.
     * @return An exponentially encoded plaintext message.
     */
    public Integer decrypt(ElementModQ secretKey) {
      ElementModQ minusKey = Group.negate_q(secretKey);
      ElementModP blind = Group.pow_p(this.pad, minusKey);
      ElementModP gPowM = Group.mult_p(data, blind);
      return Dlog.discrete_log(gPowM);
    }

    /**
     * Decrypt an ElGamal ciphertext using a known nonce and the ElGamal public key.
     *
     * @param publicKey : The corresponding ElGamal public key.
     * @param nonce :     The secret nonce used to create the ciphertext.
     * @return An exponentially encoded plaintext message.
     */
    public Integer decryptWithNonce(ElementModP publicKey, ElementModQ nonce) {
      ElementModP blind = Group.pow_p(publicKey, nonce);
      ElementModP gPowM = Group.mult_p(data, blind);
      return Dlog.discrete_log(gPowM);
    }

    /**
     * Partially Decrypts an ElGamal ciphertext with a known ElGamal secret key.
     * <p>
     * 𝑀_i = 𝐴^𝑠𝑖 mod 𝑝 in the spec
     *
     * @param secretKey: The corresponding ElGamal secret key.
     * @return An exponentially encoded plaintext message.
     */
    public ElementModP partial_decrypt(ElementModQ secretKey) {
      return Group.pow_p(this.pad, secretKey);
    }

    /** Computes a cryptographic hash of this ciphertext. */
    public ElementModQ crypto_hash() {
      return Hash.hash_elems(this.pad, this.data);
    }

    @Override
    public String cryptoHashString() {
      return Hash.hash_elems(pad, data).to_hex();
    }
  }

  /**
   * Given an ElGamal secret key (typically, a random number in [2,Q)), returns
   * an ElGamal keypair, consisting of the given secret key a and public key g^a.
   */
  public static KeyPair elGamalKeyPairFromSecret(ElementModQ a) {
    BigInteger secret_key_int = a.getBigInt();
    if (Group.lessThan(secret_key_int, BigInteger.TWO)) {
      logger.atSevere().log("ElGamal secret key needs to be in [2,Q).");
      throw new ArithmeticException("secret key must be in [2, Q)");
    }
    return new KeyPair(a, g_pow_p(a));
  }

  /** Create a random elgamal keypair. */
  static KeyPair elGamalKeyPairFromRandom() {
    return elGamalKeyPairFromSecret(rand_range_q(TWO_MOD_Q));
  }

  /**
   * Encrypts a message with a given random nonce and an ElGamal public key.
   *
   * @param message    must be an integer in [0,Q).
   * @param nonce      Randomly chosen nonce in [1,Q).
   * @param public_key ElGamal public key.
   * @return An ElGamal.Ciphertext.
   */
  public static Ciphertext encrypt(int message, ElementModP public_key, ElementModQ nonce) {
    if (nonce.equals(ZERO_MOD_Q)) {
      logger.atSevere().log("ElGamal encryption requires a non-zero nonce");
      throw new ArithmeticException("ElGamal encryption requires a non-zero nonce");
    }
    if (message < 0) {
      logger.atSevere().log("Can't encrypt a negative message");
      throw new ArithmeticException("Can't encrypt a negative message");
    }

    ElementModP pad = g_pow_p(nonce);
    ElementModP gpowp_m = g_pow_p(int_to_q_unchecked(BigInteger.valueOf(message)));
    ElementModP pubkey_pow_n = pow_p(public_key, nonce);
    ElementModP data = mult_p(gpowp_m, pubkey_pow_n);

    return new Ciphertext(pad, data);
  }

  /**
   * Homomorphically accumulates one or more ElGamal ciphertexts by pairwise multiplication. The exponents
   * of vote counters will add.
   */
  public static Ciphertext encryptedSum(Ciphertext... ciphertexts) {
    Preconditions.checkArgument(ciphertexts.length > 0, "Must have one or more ciphertexts for elgamal_add");

    Ciphertext result = ciphertexts[0];
    for (int i = 1; i < ciphertexts.length; i++) {
      Ciphertext next = ciphertexts[i];
      result = new Ciphertext(Group.mult_p(result.pad, next.pad), Group.mult_p(result.data, next.data));
    }
    return result;
  }
}
