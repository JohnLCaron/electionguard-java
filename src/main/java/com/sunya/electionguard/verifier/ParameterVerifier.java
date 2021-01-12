package com.sunya.electionguard.verifier;

import com.sunya.electionguard.Group;
import com.sunya.electionguard.Grp;

import java.math.BigInteger;

/**
 * This module is for checking the given baseline parameters, as mentioned in the specification document in green box 1.
 * Baseline parameters include p, q, r, g which are used across the whole election verification process.
 */
public class ParameterVerifier {
  private static final int NUMBER_OF_ITERATIONS = 5;

  private final ElectionParameters electionParameters;
  private final Grp grp;

  ParameterVerifier(ElectionParameters electionParameters) {
    this.electionParameters = electionParameters;
    this.grp = new Grp(electionParameters.large_prime(), electionParameters.small_prime());
  }

  /** verify all parameters including p, q, r, g */
  public boolean verify_all_params() {
    boolean error = false;

    // check if p and q are the expected values
    if (!electionParameters.large_prime().equals(Group.P)) {
      // if not, use Miller-Rabin algorithm to check the primality of p and q, 5 iterations by default
      if (!Grp.is_prime(electionParameters.large_prime(), NUMBER_OF_ITERATIONS)) {
        error = true;
        System.out.printf("Large prime value error. %n");
      }
    }

    if (!electionParameters.small_prime().equals(Group.Q)) {
      if (!Grp.is_prime(electionParameters.small_prime(), NUMBER_OF_ITERATIONS)) {
        error = true;
        System.out.printf("Small prime value error. %n");
      }
    }

    // get basic parameters
    BigInteger cofactor = electionParameters.cofactor();

    // check equation p - 1 = q * r
    if (!(electionParameters.large_prime().subtract(BigInteger.ONE)).equals(electionParameters.small_prime().multiply(cofactor))) {
      error = true;
      System.out.printf("p - 1 is not equal to r * q.%n");
    }

    // check q is not a divisor of r
    if (Grp.is_divisor(electionParameters.small_prime(), cofactor)) {
      error = true;
      System.out.printf("q is a divisor of r.%n");
    }

    BigInteger generator = electionParameters.generator();

    // check 1 < g < p
    if (!grp.is_within_set_zstarp(generator)) {
      error = true;
      System.out.printf("g is not in the range of 1 to p. %n");
    }

    // check g^q mod p = 1
    BigInteger product = generator.modPow(electionParameters.small_prime(), electionParameters.large_prime());
    if (!product.equals(BigInteger.ONE)) {
      error = true;
      System.out.printf("g^q mod p does not equal to 1. %n");
    }

    if (error) {
      System.out.printf("Baseline parameter check failure%n");
    } else {
      System.out.printf("Baseline parameter check success%n");
    }

    return !error;
  }
}
