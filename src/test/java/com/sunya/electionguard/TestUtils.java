package com.sunya.electionguard;

import java.math.BigInteger;
import java.util.Random;

import static com.sunya.electionguard.ChaumPedersen.make_chaum_pedersen;
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

  /** Generates an arbitrary ElGamal Ciphertext. */
  public static ElGamal.Ciphertext elgamal_ciphertext() {
    return new ElGamal.Ciphertext(elements_mod_p_no_zero(), elements_mod_p_no_zero());
  }

  /** Generates an arbitrary element from [1,Q). from electionguardtest.group. */
  static ElementModQ elements_mod_q_no_zero() {
    return Group.int_to_q_unchecked(Utils.randbetween(BigInteger.ONE, Group.getPrimes().small_prime));
  }

  /** Generates an arbitrary element from [0,Q). from electionguardtest.group. */
  public static ElementModQ elements_mod_q() {
    return Group.int_to_q_unchecked(Utils.randbelow(Group.getPrimes().small_prime));
  }

  /** Generates an arbitrary element from [1,P). from electionguardtest.group. */
  static ElementModP elements_mod_p_no_zero() {
    return Group.int_to_p_unchecked(Utils.randbetween(BigInteger.ONE, Group.getPrimes().large_prime));
  }

  /** Generates an arbitrary element from [0,Q). from electionguardtest.group. */
  public static ElementModP elements_mod_p() {
    return Group.int_to_p_unchecked(Utils.randbelow(Group.getPrimes().large_prime));
  }

  /** Generates a fake ChaumPedersenProof. */
  public static ChaumPedersen.ChaumPedersenProof chaum_pedersen_proof() {
    ElGamal.KeyPair  keypair = ElGamal.elgamal_keypair_from_secret(TWO_MOD_Q).orElseThrow();
    ElGamal.Ciphertext message = ElGamal.elgamal_encrypt(0, ONE_MOD_Q, keypair.public_key()).orElseThrow();
    ElementModP decryption = message.partial_decrypt(keypair.secret_key());

    return make_chaum_pedersen(message, keypair.secret_key(), decryption, TWO_MOD_Q, ONE_MOD_Q);
  }


}
