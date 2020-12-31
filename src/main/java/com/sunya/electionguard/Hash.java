package com.sunya.electionguard;

import com.google.common.collect.Iterables;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Optional;

/** Given zero or more elements, calculate their cryptographic hash using SHA256. */
public class Hash {

  interface CryptoHashable {
    Group.ElementModQ crypto_hash();
  }

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
   * TODO does this have to agree exactly with python code ?
   * TODO add tests.
   */
  static Group.ElementModQ hash_elems(Object... a) {
    // TODO is Guava Hashing.sha256() better?
    MessageDigest digest;
    try {
      digest = MessageDigest.getInstance("SHA-256");
    } catch (NoSuchAlgorithmException e) {
      throw new RuntimeException(e);
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
        hash_me = ((CryptoHashable)x).crypto_hash().to_hex();
      } else if (x instanceof String) {
          // strings are iterable, so it 's important to handle them before the following check
        hash_me = (String) x;
      } else if (x instanceof Iterable) {
          // The simplest way to deal with lists, tuples, and such are to crunch them recursively.
        hash_me = hash_elems(Iterables.toArray((Iterable) x, Object.class)).to_hex();
      } else {
        hash_me = x.toString();
      }
      hash_me += "|";
      digest.update(hash_me.getBytes(StandardCharsets.UTF_8));
    }

    // return int_to_q_unchecked(int.from_bytes(h.digest(), byteorder = "big") % Q_MINUS_ONE);
    byte[] bytes = digest.digest();
    BigInteger biggy = new BigInteger(bytes).mod(Group.Q_MINUS_ONE);
    return new Group.ElementModQ(biggy);
  }
}
