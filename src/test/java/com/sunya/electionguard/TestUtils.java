package com.sunya.electionguard;

import java.math.BigInteger;
import java.util.Random;

import static com.sunya.electionguard.Group.*;

public class TestUtils {
  private static final Random random = new Random(System.currentTimeMillis());

  static boolean randomBool() {
    return random.nextBoolean();
  }

  static int randomInt() {
    return random.nextInt();
  }

  static int randomInt(int max) {
    return random.nextInt(max);
  }

  /** Generates an arbitrary ElGamal secret/public keypair. from electionguardtest.elgamal. */
  static ElGamal.KeyPair elgamal_keypairs() {
    ElementModQ e = elements_mod_q_no_zero();
    return ElGamal.elgamal_keypair_from_secret(!e.equals(ONE_MOD_Q) ? e : TWO_MOD_Q).orElseThrow();
    }

  /** Generates an arbitrary element from [1,Q). from electionguardtest.group. */
  static ElementModQ elements_mod_q_no_zero() {
    return Group.int_to_q_unchecked(Utils.randbetween(BigInteger.ONE, Q));
  }

  /** Generates an arbitrary element from [0,Q). from electionguardtest.group. */
  static ElementModQ elements_mod_q() {
    return Group.int_to_q_unchecked(Utils.randbelow(Q));
  }

  /** Generates an arbitrary element from [1,P). from electionguardtest.group. */
  static ElementModP elements_mod_p_no_zero() {
    return Group.int_to_p_unchecked(Utils.randbetween(BigInteger.ONE, P));
  }

  /** Generates an arbitrary element from [0,Q). from electionguardtest.group. */
  static ElementModP elements_mod_p() {
    return Group.int_to_p_unchecked(Utils.randbelow(P));
  }

}
