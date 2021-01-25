package com.sunya.electionguard.verifier;

import com.sunya.electionguard.Group;

import java.math.BigInteger;

/**
 * This verifies specification section "1. Parameter Validation".
 * <p>
 * If alternative parameters are allowed, election verifiers must confirm that p, q, r, g, and ghat are such that
 * <ol>
 * <li> both p and q are prime (this may be done probabilistically using the Miller-Rabin algorithm),
 * <li> p − 1 = q * r is satisfied,
 * <li> q is not a divisor of r
 * <li> 1 < g < p
 * <li> g^q mod p = 1
 * <li> g * ghat mod p = 1 LOOK what is ghat?
 * <li> and that generation of the parameters is consistent with the cited standard. LOOK what does this mean ??
 * </ol>
 */
public class ParameterVerifier {
  private static final int NUMBER_OF_ITERATIONS = 5;

  private final ElectionRecord electionRecord;
  private final Grp grp;

  ParameterVerifier(ElectionRecord electionRecord) {
    this.electionRecord = electionRecord;
    this.grp = new Grp(electionRecord.large_prime(), electionRecord.small_prime());
  }

  /** verify all parameters including p, q, r, g */
  public boolean verify_all_params() {
    boolean error = false;

    // check if p and q are the expected values, or prime
    if (!electionRecord.large_prime().equals(Group.P)) {
      // if not, use Miller-Rabin algorithm to check the primality of p and q, 5 iterations by default
      if (!Grp.is_prime(electionRecord.large_prime(), NUMBER_OF_ITERATIONS)) {
        error = true;
        System.out.printf(" Large prime value error. %n");
      }
    }
    if (!electionRecord.small_prime().equals(Group.Q)) {
      if (!Grp.is_prime(electionRecord.small_prime(), NUMBER_OF_ITERATIONS)) {
        error = true;
        System.out.printf(" Small prime value error. %n");
      }
    }

    // check equation p - 1 = q * r
    BigInteger cofactor = electionRecord.cofactor();
    if (!(electionRecord.large_prime().subtract(BigInteger.ONE)).equals(electionRecord.small_prime().multiply(cofactor))) {
      error = true;
      System.out.printf(" p - 1 is not equal to r * q.%n");
    }

    // check q is not a divisor of r
    if (Grp.is_divisor(electionRecord.small_prime(), cofactor)) {
      error = true;
      System.out.printf(" q is a divisor of r.%n");
    }

    // check 1 < g < p
    BigInteger generator = electionRecord.generator();
    if (!grp.is_within_set_zstarp(generator)) {
      error = true;
      System.out.printf(" g is not in the range of 1 to p. %n");
    }

    // check g^q mod p = 1
    BigInteger product = generator.modPow(electionRecord.small_prime(), electionRecord.large_prime());
    if (!product.equals(BigInteger.ONE)) {
      error = true;
      System.out.printf(" g^q mod p does not equal to 1. %n");
    }

    if (error) {
      System.out.printf(" ***Baseline parameter check failure%n");
    } else {
      System.out.printf(" Baseline parameter check success%n");
    }
    return !error;
  }
}
