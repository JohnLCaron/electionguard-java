package com.sunya.electionguard;

import java.math.BigInteger;
import java.util.Random;

import static com.sunya.electionguard.Group.*;


public class TestUtils {
  private static Random random = new Random(System.currentTimeMillis());

  static int randomInt() {
    return random.nextInt();
  }

  static int randomInt(int max) {
    return random.nextInt(max);
  }

  /** Generates an arbitrary ElGamal secret/public keypair. from electionguardtest.elgamal. */
  static ElGamal.KeyPair elgamal_keypairs() {
    ElementModQ e = elements_mod_q_no_zero();
    return ElGamal.elgamal_keypair_from_secret(!e.equals(ONE_MOD_Q) ? e : TWO_MOD_Q).get();
    }

  /** Generates an arbitrary element from [1,Q). from electionguardtest.group. */
  static ElementModQ elements_mod_q_no_zero() {
    return new ElementModQ(Secrets.randbetween(BigInteger.ONE, Q));
  }

  /** Generates an arbitrary element from [0,Q). from electionguardtest.group. */
  static ElementModQ elements_mod_q() {
    return new ElementModQ(Secrets.randbelow(Q));
  }

  /** Generates an arbitrary element from [1,P). from electionguardtest.group. */
  static ElementModP elements_mod_p_no_zero() {
    return new ElementModP(Secrets.randbetween(BigInteger.ONE, P));
  }

  /** Generates an arbitrary element from [0,Q). from electionguardtest.group. */
  static ElementModP elements_mod_p() {
    return new ElementModP(Secrets.randbelow(P));
  }

}
