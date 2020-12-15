package com.sunya.electionguard;

import com.google.common.base.Preconditions;

import java.math.BigInteger;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

public class Secrets {

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

}
