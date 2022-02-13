package com.sunya.electionguard;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;

import java.math.BigInteger;

import static com.sunya.electionguard.Group.*;

/**
 * Computes the discrete log (base g, mod p) of the given element, with internal caching of results.
 * Should run efficiently when called multiple times when the exponent is at most in the single-digit millions.
 * Performance will degrade if it's much larger.
 */
class Dlog {
  private static final int MAX = 1000; // max vote count - TODO should be settable
  private static final Cache<BigInteger, Integer> cache = CacheBuilder.newBuilder().build();
  static {
    cache.put(BigInteger.ONE, 0);
  }
  private static BigInteger dlog_max_elem = BigInteger.ONE;
  private static int dlog_max_exp = 0;

  static Integer discrete_log(ElementModP elem) {
    Integer result = cache.getIfPresent(elem.elem);
    if (result != null) {
      // System.out.printf("Got a hit on %d%n", result);
      return result;
    }
    return discrete_log_internal(elem.elem);
  }

  // store all integer values up to dlog_max_elem, which increases as needed.
  private static synchronized Integer discrete_log_internal(BigInteger e) {
    BigInteger G = getPrimes().generator;
    while (!e.equals(dlog_max_elem)) {
      dlog_max_exp = dlog_max_exp + 1;
      if (dlog_max_exp > MAX) {
        throw new RuntimeException(String.format("Discrete_log_internal exceeds max %d%n", dlog_max_exp));
      }
      dlog_max_elem = mult_pi(G, dlog_max_elem);
      cache.put(dlog_max_elem, dlog_max_exp);
    }
    return cache.getIfPresent(dlog_max_elem);
  }

}
