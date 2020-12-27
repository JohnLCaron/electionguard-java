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
public class Dlog {
  private static final Cache<BigInteger, BigInteger> cache = CacheBuilder.newBuilder().build();
  static {
    cache.put(BigInteger.ONE, BigInteger.ZERO);
  }
  private static BigInteger dlog_max_elem = BigInteger.ONE;
  private static int dlog_max_exp = 0;

  static BigInteger discrete_log(ElementModP elem) {
    BigInteger result = cache.getIfPresent(elem.elem);
    if (result != null) {
      return result;
    }
    return discrete_log_internal(elem.elem);
  }

  // TODO need synch?
  private static BigInteger discrete_log_internal(BigInteger e) {
    int start = dlog_max_exp;
    while (!e.equals(dlog_max_elem)) {
      dlog_max_exp = dlog_max_exp + 1;
      dlog_max_elem = mult_pi(G, dlog_max_elem);
      cache.put(dlog_max_elem, BigInteger.valueOf(dlog_max_exp));
    }
    System.out.printf(" from %d to %d%n", start, dlog_max_exp);
    return cache.getIfPresent(dlog_max_elem);
  }

}
