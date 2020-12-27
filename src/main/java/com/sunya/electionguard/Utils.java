package com.sunya.electionguard;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.io.BaseEncoding;

import java.math.BigInteger;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Covers for Python library methods.
 */
public class Utils {

  private static final List<String> truthy = ImmutableList.of("y", "yes", "t", "true", "on", "1");
  private static final List<String> falsey = ImmutableList.of("n", "no", "f", "false", "off", "0");

  /**
   * Mimic python Convert a string representation of truth to true (1) or false (0).
   * True values are 'y', 'yes', 't', 'true', 'on', and '1'; false values
   * are 'n', 'no', 'f', 'false', 'off', and '0'.  Raises ValueError if
   * 'val' is anything else.
   */
  static boolean strtobool(String val) {
    String clean = val.trim().toLowerCase();
    if (truthy.contains(clean)) return true;
    if (falsey.contains(clean)) return false;
    throw new RuntimeException("invalid truth value " + val);
  }

  static String isTrue(BigInteger val) {
    return val.equals(BigInteger.ZERO) ? "false" : "true";
  }

  /** Return a random BigInteger in the range [0, n). */
  static BigInteger randbelow(BigInteger exclusive_upper_bound) {
    Preconditions.checkArgument(BigInteger.ZERO.compareTo(exclusive_upper_bound) <= 0);
    return randbetween(BigInteger.ZERO, exclusive_upper_bound);
  }

  /** Return a random BigInteger in the range [lower, upper). */
  static BigInteger randbetween(BigInteger inclusive_lower_bound, BigInteger exclusive_upper_bound) {
    Preconditions.checkArgument(inclusive_lower_bound.compareTo(exclusive_upper_bound) < 0);
    Random random = ThreadLocalRandom.current();
    int numBits = exclusive_upper_bound.bitLength();
    BigInteger candidate = new BigInteger(numBits, random);
    while (!Group.between(inclusive_lower_bound, candidate, exclusive_upper_bound)) {
      candidate = new BigInteger(numBits, random);
    }
    return candidate;
  }

  /////////////////////////////////////////////////////////////////////////////////
  // RFC 3548, Base 16 Alphabet specifies uppercase, but hexlify() returns
  // lowercase.  The RFC also recommends against accepting input case insensitively

  /**
   * Encode the bytes-like object s using Base16 and return a bytes object.
   */
  static String b16encode(byte[] s) {
    // return binascii.hexlify(s).upper();
    return BaseEncoding.base16().encode(s).toUpperCase();
  }

  /**
   * Decode the Base16 encoded bytes-like object or ASCII string s.
   * <p>
   * Optional casefold is a flag specifying whether a lowercase alphabet is
   * acceptable as input.  For security purposes, the default is False.
   * <p>
   * The result is returned as a bytes object.  A binascii.Error is raised if
   * s is incorrectly padded or if there are non-alphabet characters present
   * in the input.
   */
  static byte[] b16decode(String s, boolean casefold) {
    /* s = _bytes_from_decode_data(s); TODO not sure what this does
    if (casefold) {
      s = s.toUpperCase();
    } */
    s = s.toUpperCase();

    /* TODO
    if (re.search(b '[^0-9A-F]', s)){
      raise binascii.Error('Non-base16 digit found');
    } */

    // binascii.unhexlify(s)
    return BaseEncoding.base16().decode(s);
  }

  /*
  private static String _bytes_from_decode_data(String s) {
    try {
      return s.encode('ascii');
    } catch (UnicodeEncodeError e) {
      throw ValueError('string argument should contain only ASCII characters');
    }
  } */
}
