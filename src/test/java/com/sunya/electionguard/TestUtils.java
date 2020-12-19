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

  /**     Generates an arbitrary ElGamal secret/public keypair. */
  static ElGamal.KeyPair elgamal_keypairs() {
    ElementModQ e = elements_mod_q_no_zero();
    return ElGamal.elgamal_keypair_from_secret(!e.equals(ONE_MOD_Q) ? e : TWO_MOD_Q).get();
    }

  static ElementModQ elements_mod_q_no_zero() {
    return new ElementModQ(Secrets.randbetween(BigInteger.ONE, Q));
  }

  static ElementModQ elements_mod_q() {
    return new ElementModQ(Secrets.randbelow(Q));
  }


}
