package com.sunya.electionguard;

import org.junit.Test;

import java.math.BigInteger;

import static com.google.common.truth.Truth.assertThat;
import static com.sunya.electionguard.Group.*;


public class TestDlog {
  static final ElementModP g_inv = int_to_p_unchecked(mult_inv_p(G));

  // simpler implementation of discrete_log, only meant for comparison testing of the caching version
  int _discrete_log_uncached(ElementModP e) {
    int count = 0;
    while (e != ONE_MOD_P) {
      e = mult_p(e, g_inv);
      count = count + 1;
    }
    return count;
  }

  // @Test
  public void test_uncached() {
    for (int exp = 0; exp < 100; exp++) {
      ElementModQ plaintext = int_to_q(BigInteger.valueOf(exp)).get();
      ElementModP exp_plaintext = g_pow_p(plaintext);
      int plaintext_again = _discrete_log_uncached(exp_plaintext);

      assertThat(exp).isEqualTo(plaintext_again);
    }
  }

  /*
  @given(integers(0, 1000))
  def test_cached(self, exp: int):
  plaintext = get_optional(int_to_q(exp))
  exp_plaintext = g_pow_p(plaintext)
  plaintext_again = discrete_log(exp_plaintext)

        self.assertEqual(exp, plaintext_again)

  def test_cached_one(self):
  plaintext = int_to_q_unchecked(1)
  ciphertext = g_pow_p(plaintext)
  plaintext_again = discrete_log(ciphertext)

        self.assertEqual(1, plaintext_again)

   */
}
