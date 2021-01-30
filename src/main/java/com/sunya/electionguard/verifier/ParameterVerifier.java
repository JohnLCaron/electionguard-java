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
  private final ElectionRecord electionRecord;

  ParameterVerifier(ElectionRecord electionRecord) {
    this.electionRecord = electionRecord;
  }

  /** verify all parameters including p, q, r, g */
  public boolean verify_all_params() {
    boolean error = false;

    // check if p, q, g are the expected values
    if (!electionRecord.large_prime().equals(Group.P)) {
      System.out.printf(" Large prime value not equal to P. %n");
      error = true;
    }
    if (!electionRecord.small_prime().equals(Group.Q)) {
      error = true;
      System.out.printf(" Small prime value not equal to Q. %n");
    }
    if (!electionRecord.generator().equals(Group.G)) {
      error = true;
      System.out.printf(" Generator value not equal to G. %n");
    }
    if (!electionRecord.cofactor().equals(Group.R)) {
      error = true;
      System.out.printf(" Generator value not equal to G. %n");
    }

    // check equation p - 1 = q * r
    BigInteger cofactor = electionRecord.cofactor();
    if (!(electionRecord.large_prime().subtract(BigInteger.ONE)).equals(electionRecord.small_prime().multiply(cofactor))) {
      error = true;
      System.out.printf(" p - 1 is not equal to r * q.%n");
    }

    // check q is not a divisor of r
    if (Group.is_divisor(electionRecord.small_prime(), cofactor)) {
      error = true;
      System.out.printf(" q is a divisor of r.%n");
    }

    // check g is in Z^r_p
    Group.ElementModP generator = electionRecord.generatorP();
    if (!generator.is_valid_residue()) {
      error = true;
      System.out.printf(" g is not in the range of 1 to p. %n");
    }

    if (error) {
      System.out.printf(" ***Baseline parameter check failure%n");
    } else {
      System.out.printf(" Baseline parameter check success%n");
    }
    return !error;
  }
}
