package com.sunya.electionguard;

import java.math.BigInteger;

public class Grp {
  private final BigInteger q; // small prime
  private final BigInteger p; // large prime

  public Grp(BigInteger large_prime, BigInteger small_prime) {
    this.p = large_prime;
    this.q = small_prime;
  }

  /** (b1 * b2 * b3 * ...) mod p */
  public BigInteger mult_p(BigInteger... bees) {
    BigInteger product = BigInteger.ONE;
    for (BigInteger x : bees) {
      product = product.multiply(x).mod(this.p);
    }
    return product;
  }

  /** (b1 * b2 * b3 * ...) mod q */
  public BigInteger mult_q(BigInteger... bees) {
    BigInteger product = BigInteger.ONE;
    for (BigInteger x : bees) {
      product = product.multiply(x).mod(this.q);
    }
    return product;
  }

  /** b mod p */
  public BigInteger mod_p(BigInteger biggy) {
    return biggy.mod(this.p);
  }

  /** b mod q */
  public BigInteger mod_q(BigInteger biggy) {
    return biggy.mod(this.q);
  }

  /** b^e mod p */
  public BigInteger pow_p(BigInteger b, BigInteger e) {
    return b.modPow(e, p);
  }

  /** check if a number is within set Zq, Z𝑝 ={0,1,2,...,q−1} the additive group of the integers modulo q. */
  public boolean is_within_set_zq(BigInteger num) {
    return Group.between(BigInteger.ZERO, num, q);
  }

  /** check if a number is within set Z*p, Z*𝑝 ={1,2,...,𝑝−1} is the multiplicative subgroup of Zp */
  public boolean is_within_set_zstarp(BigInteger num) {
    // exclusive bounds, set lower bound to -1
    return Group.between(BigInteger.ONE, num, p);
  }

  /** check if a number is within set Zrp, 0 < num < p and num ^ q mod p = 1 */
  public boolean is_within_set_zrp(BigInteger num) {
    return is_within_set_zstarp(num) && pow_p(num, q).equals(BigInteger.ONE);
  }


  /** check if a is a divisor of b. */
  public static boolean is_divisor(BigInteger a, BigInteger b) {
    return a.mod(b).equals(BigInteger.ZERO);
  }

  private final static BigInteger THREE = BigInteger.valueOf(3);
  private final static BigInteger FOUR = BigInteger.valueOf(4);

  /**
   * implements Miller-Rabin algorithm to test the primality of a number
   * :param num: a positive integer
   * :param k: the number of iterations, impacting accuracy; the larger the number, the higher accuracy will be.
   * set default as 5
   * :return: True if it's a prime, False otherwise
   */
  public static boolean is_prime(BigInteger num, int numberOfIterations) {
    // Corner cases
    if (Group.lessThan(num, BigInteger.TWO) || num.equals(FOUR)) {
      return false;
    }
    if (Group.lessThan(num, FOUR)) {
      return true;
    }


    return num.isProbablePrime(numberOfIterations);
  }

}
