package com.sunya.electionguard;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;

import java.math.BigInteger;
import java.util.concurrent.ExecutionException;

/**
 *     Computes the discrete log (base g, mod p) of the given element,
 *     with internal caching of results. Should run efficiently when called
 *     multiple times when the exponent is at most in the single-digit millions.
 *     Performance will degrade if it's much larger.
 *
 *     Note: *this function is thread-safe*. For the best possible performance,
 *     pre-compute the discrete log of a number you expect to have the biggest
 *     exponent you'll ever see. After that, the cache will be fully loaded,
 *     and every call will be nothing more than a dictionary lookup.
 */
public class Dlog {
  private static final Group.ElementModP g = Group.int_to_p_unchecked(Group.G);

  private static final LoadingCache<Group.ElementModP, BigInteger> cache =
          CacheBuilder.newBuilder().build(new CacheLoader<>() {
            @Override
            public BigInteger load(Group.ElementModP elem) {
              return Group.mult_p(g, elem).elem;
            }
          });

  static BigInteger discrete_log(Group.ElementModP elem) {
    try {
      return cache.get(elem);
    } catch (ExecutionException e) {
      throw new IllegalStateException(e.getCause());
    }
  }

}
