package com.sunya.electionguard.verifier;

import com.sunya.electionguard.ElectionConstants;
import com.sunya.electionguard.Group;

import java.math.BigInteger;

/**
 * This verifies specification section "1 Parameter Validation".
 * Currently just checks that the constants are the standard ones.
 * @see <a href="https://www.electionguard.vote/spec/0.95.0/9_Verifier_construction/#parameter-validation">Parameter validation</a>
 */
public class ParameterVerifier {
  private final ElectionRecord electionRecord;

  ParameterVerifier(ElectionRecord electionRecord) {
    this.electionRecord = electionRecord;
  }

  /** verify all parameters including p, q, r, g */
  public boolean verify_all_params() {
    boolean error = false;
    ElectionConstants primes = Group.getPrimes();

    // check if p, q, g are the expected values
    if (!electionRecord.largePrime().equals(primes.large_prime)) {
      System.out.printf(" Large prime value not equal to P. %s%n", electionRecord.largePrime());
      error = true;
    }
    if (!electionRecord.smallPrime().equals(primes.small_prime)) {
      error = true;
      System.out.printf(" Small prime value not equal to Q. %n");
    }
    if (!electionRecord.generator().equals(primes.generator)) {
      error = true;
      System.out.printf(" Generator value not equal to G. %n");
    }
    if (!electionRecord.cofactor().equals(primes.cofactor)) {
      error = true;
      System.out.printf(" cofactor value not equal to R. %n");
    }

    // check equation p - 1 = q * r
    BigInteger cofactor = electionRecord.cofactor();
    if (!(electionRecord.largePrime().subtract(BigInteger.ONE)).equals(electionRecord.smallPrime().multiply(cofactor))) {
      error = true;
      System.out.printf(" p - 1 is not equal to r * q.%n");
    }

    // check q is not a divisor of r
    if (Group.is_divisor(electionRecord.smallPrime(), cofactor)) {
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