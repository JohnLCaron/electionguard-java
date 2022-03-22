package com.sunya.electionguard;

import com.google.common.collect.Iterables;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Optional;

/**
 * Create a cryptographic hash using SHA256.
 *  @see <a href="https://www.electionguard.vote/spec/0.95.0/9_Verifier_construction/#hash-computation">Hash computation</a>
 *  TODO test agreement across implementations
 */
public class Hash {

  interface CryptoHashable {
    Group.ElementModQ cryptoHash();
  }

  public interface CryptoHashableString {
    /** Returns a string suitable for input to a cryptographic hash function. */
    String cryptoHashString();
  }

  // LOOK not used?
  interface CryptoHashCheckable {
    Group.ElementModQ crypto_hash_with(Group.ElementModQ seed);
  }

  /**
   * Given zero or more elements, calculate their cryptographic hash
   * using SHA256. Allowed element types are `ElementModP`, `ElementModQ`,
   * `str`, or `int`, anything implementing `CryptoHashable`, and lists
   * or optionals of any of those types.
   *
   * @param a Zero or more elements of any of the accepted types.
   * @return A cryptographic hash of these elements, concatenated.
   * <p>
   */
  public static Group.ElementModQ hash_elems(Object... a) {
    // TODO is Guava Hashing.sha256() better?
    MessageDigest digest;
    try {
      digest = MessageDigest.getInstance("SHA-256");
      digest.update("|".getBytes(StandardCharsets.UTF_8));
    } catch (NoSuchAlgorithmException e) {
      throw new RuntimeException(e);
    }

    if (a.length == 0) {
      String hash_me = "null|";
      digest.update(hash_me.getBytes(StandardCharsets.UTF_8));
    }

    for (Object x : a) {
          //We could just use str(x) for everything, but then we 'd have a resulting string
          //that 's a bit Python-specific, and we' d rather make it easier for other languages
        //to exactly match this hash function.

      String hash_me;
      if (x == null) {
        // This case captures empty lists and None, nicely guaranteeing that we don 't
        // need to do a recursive call if the list is empty.So we need a string to
        //feed in for both of these cases."None" would be a Python -specific thing,
        // so we 'll go with the more JSON-ish "null".
        hash_me = "null";
      } else if (x instanceof Optional) {
        Optional xopt = (Optional) x;
        if (xopt.isEmpty()) {
          hash_me = "null";
        } else {
          hash_me = hash_elems(xopt.get()).to_hex();
        }
      } else if (x instanceof Group.ElementMod) {
        hash_me = ((Group.ElementMod)x).to_hex();
      } else if (x instanceof CryptoHashable) {
        hash_me = ((CryptoHashable)x).cryptoHash().to_hex();
      } else if (x instanceof CryptoHashableString) {
        hash_me = ((CryptoHashableString)x).cryptoHashString();
      } else if (x instanceof String) {
          // strings are iterable, so it 's important to handle them before list-like types
        hash_me = (String) x;
      } else if (x instanceof Iterable) {
          // The simplest way to deal with lists, tuples, and such are to crunch them recursively.
        Object[] asArray = Iterables.toArray((Iterable) x, Object.class);
        hash_me = (asArray.length == 0) ? "null" : hash_elems(asArray).to_hex();
      } else {
        hash_me = x.toString();
      }
      // System.out.printf("  hash_me: %s%n", hash_me);
      hash_me += "|";
      digest.update(hash_me.getBytes(StandardCharsets.UTF_8));
    }

    // must be positive
    BigInteger bi = new BigInteger(1, digest.digest());
    BigInteger bim = bi.mod(Group.getPrimes().smallPrime);
    return Group.int_to_q_unchecked(bim);
  }
}