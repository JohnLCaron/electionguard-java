package com.sunya.electionguard;

import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
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

  @Property
  public void test_uncached(@ForAll BigInteger exp) {
    ElementModQ plaintext = int_to_q(exp).get();
    ElementModP exp_plaintext = g_pow_p(plaintext);
    BigInteger plaintext_again = Dlog.discrete_log(exp_plaintext);
    assertThat(exp).isEqualTo(plaintext_again);
  }

  /*
    @given(integers(0, 100))
    def test_uncached(self, exp: int):
        plaintext = get_optional(int_to_q(exp))
        exp_plaintext = g_pow_p(plaintext)
        plaintext_again = _discrete_log_uncached(exp_plaintext)

        self.assertEqual(exp, plaintext_again)

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
