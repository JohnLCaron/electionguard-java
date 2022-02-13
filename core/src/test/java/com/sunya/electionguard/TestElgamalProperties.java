package com.sunya.electionguard;

import com.google.common.base.Stopwatch;
import com.google.common.collect.ImmutableList;
import net.jqwik.api.Example;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.IntRange;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth8.assertThat;
import static com.sunya.electionguard.ElGamal.*;
import static com.sunya.electionguard.Group.*;
import static org.junit.Assert.fail;

public class TestElgamalProperties extends TestProperties {

  @Example
  public void test_simple_elgamal_encryption_decryption() {
    ElementModQ nonce = ONE_MOD_Q;
    ElementModQ secret_key = TWO_MOD_Q;
    KeyPair keypair = elgamal_keypair_from_secret(secret_key).orElseThrow();
    ElementModP public_key = keypair.public_key();

    assertThat(Group.lessThan(public_key.getBigInt(), Group.getPrimes().large_prime)).isTrue();
    ElementModP elem = g_pow_p(ZERO_MOD_Q);
    assertThat(elem).isEqualTo(ONE_MOD_P);  // g^0 == 1

    Ciphertext ciphertext = elgamal_encrypt(0, nonce, keypair.public_key()).orElseThrow();
    assertThat(getPrimes().generator).isEqualTo(ciphertext.pad().getBigInt());
    assertThat(pow_pi(ciphertext.pad().getBigInt(), secret_key.getBigInt()))
            .isEqualTo(pow_pi(public_key.getBigInt(), nonce.getBigInt()));
    assertThat(ciphertext.data().getBigInt())
            .isEqualTo(pow_pi(public_key.getBigInt(), nonce.getBigInt()));

    Integer plaintext = ciphertext.decrypt(keypair.secret_key());
    assertThat(plaintext).isEqualTo(0);
  }

  @Property
  public void test_elgamal_encrypt_requires_nonzero_nonce(
          @ForAll("elgamal_keypairs") ElGamal.KeyPair keypair,
          @ForAll @IntRange(min = 0, max = 100) int message) {
      assertThat(elgamal_encrypt(message, ZERO_MOD_Q, keypair.public_key())).isEmpty();
  }

  @Example
  public void test_elgamal_keypair_from_secret_requires_key_greater_than_one() {
    assertThat(elgamal_keypair_from_secret(ZERO_MOD_Q)).isEmpty();
    assertThat(elgamal_keypair_from_secret(ONE_MOD_Q)).isEmpty();
  }

  @Property
  public void test_elgamal_encryption_decryption_inverses(
          @ForAll("elgamal_keypairs") ElGamal.KeyPair keypair,
          @ForAll @IntRange(min = 0, max = 100) int message,
          @ForAll("elements_mod_q_no_zero") ElementModQ nonce) {
      Ciphertext ciphertext = elgamal_encrypt(message, nonce, keypair.public_key()).orElseThrow();
      Integer plaintext = ciphertext.decrypt(keypair.secret_key());
      assertThat(plaintext).isEqualTo(message);
  }

  @Property
  public void test_elgamal_encryption_decryption_with_known_nonce_inverses(
          @ForAll("elgamal_keypairs") ElGamal.KeyPair keypair,
          @ForAll @IntRange(min = 0, max = 100) int message,
          @ForAll("elements_mod_q_no_zero") ElementModQ nonce) {
      Ciphertext ciphertext = elgamal_encrypt(message, nonce, keypair.public_key()).orElseThrow();
      Integer plaintext = ciphertext.decrypt_known_nonce(keypair.public_key(), nonce);
      assertThat(plaintext).isEqualTo(message); // TODO FAILS
  }

  @Property
  public void test_elgamal_generated_keypairs_are_within_range(
          @ForAll("elgamal_keypairs") ElGamal.KeyPair keypair) {
    assertThat(Group.lessThan(keypair.public_key().getBigInt(), Group.getPrimes().large_prime)).isTrue();
    assertThat(Group.lessThan(keypair.secret_key().getBigInt(), Group.getPrimes().small_prime)).isTrue();
    assertThat(g_pow_p(keypair.secret_key())).isEqualTo(keypair.public_key());
  }

  @Property
  public void test_elgamal_add_homomorphic_accumulation_decrypts_successfully(
          @ForAll("elgamal_keypairs") ElGamal.KeyPair keypair,
          @ForAll @IntRange(min = 0, max = 100) int m1,
          @ForAll("elements_mod_q_no_zero") ElementModQ r1,
          @ForAll @IntRange(min = 0, max = 100) int m2,
          @ForAll("elements_mod_q_no_zero") ElementModQ r2) {
    Ciphertext c1 = elgamal_encrypt(m1, r1, keypair.public_key()).orElseThrow();
    Ciphertext c2 = elgamal_encrypt(m2, r2, keypair.public_key()).orElseThrow();
    Ciphertext c_sum = elgamal_add(c1, c2);
    Integer total = c_sum.decrypt(keypair.secret_key());
    assertThat(total).isEqualTo(m1 + m2);
  }

  @Example
  public void test_elgamal_add_requires_args() {
    try {
      elgamal_add();
      fail();
    } catch (Exception e) {
      //correct
    }
  }

  @Example
  public void test_elgamal_keypair_produces_valid_residue() {
    ElGamal.KeyPair keypair = TestUtils.elgamal_keypairs();
    assertThat(keypair.public_key().is_valid_residue()).isTrue();
  }

  @Example
  public void test_elgamal_keypair_random() {
    ElGamal.KeyPair random_keypair = elgamal_keypair_random();
    ElGamal.KeyPair random_keypair_two = elgamal_keypair_random();

    assertThat(random_keypair).isNotNull();
    assertThat(random_keypair.public_key()).isNotNull();
    assertThat(random_keypair.secret_key()).isNotNull();
    // TODO seems like this could fail
    assertThat(random_keypair).isNotEqualTo(random_keypair_two);
  }

  @Example
  public void test_elgamal_combine_public_keys() {
    ElGamal.KeyPair random_keypair = elgamal_keypair_random();
    ElGamal.KeyPair random_keypair_two = elgamal_keypair_random();

    ElementModP joint_key = elgamal_combine_public_keys(
            ImmutableList.of(random_keypair.public_key(), random_keypair_two.public_key()));

    assertThat(joint_key).isNotNull();
    assertThat(joint_key).isNotEqualTo(random_keypair.public_key());
    assertThat(joint_key).isNotEqualTo(random_keypair_two.public_key());
  }

  static class TestKeypairFromSecret implements Callable<KeyPair> {
    Group.ElementModQ nosh;

    public TestKeypairFromSecret(Group.ElementModQ nosh) {
      this.nosh = nosh;
    }

    @Override
    public KeyPair call() {
      return ElGamal.elgamal_keypair_from_secret(nosh).orElseThrow();
    }
  }

  /**
   * Ensures running lots of parallel exponentiations still yields the correct answer.
   */
  @Example
  public void test_parallelism_is_safe() {
    int problem_size = 1000;
    Nonces nonces = new Nonces(int_to_q_unchecked(BigInteger.valueOf(3)));

    List<Group.ElementModQ> nosh = new ArrayList<>();
    for (int i = 0; i < problem_size; i++) {
      nosh.add(nonces.get(i));
    }

    List<Callable<KeyPair>> tasks = new ArrayList<>();
    for (int i = 0; i < problem_size; i++) {
      tasks.add(new TestKeypairFromSecret(nonces.get(i)));
    }

    Scheduler<KeyPair> subject = new Scheduler<>();
    Stopwatch stopwatch = Stopwatch.createStarted();
    List<KeyPair> keypairs = subject.schedule(tasks, false);
    double ptime = stopwatch.elapsed(TimeUnit.MILLISECONDS);
    System.out.printf("Parallel %.3f%n", ptime);

    Stopwatch stopwatch2 = Stopwatch.createStarted();
    for (KeyPair keypair : keypairs) {
      assertThat(keypair.public_key()).isEqualTo(elgamal_keypair_from_secret(keypair.secret_key()).orElseThrow().public_key());
    }
    double stime = stopwatch2.elapsed(TimeUnit.MILLISECONDS);
    System.out.printf("Serial %.3f%n", stime);

    double speedup = stime / ptime;
    System.out.printf("Serial/Parallel speedup: %.3f%n", speedup);
  }

}
