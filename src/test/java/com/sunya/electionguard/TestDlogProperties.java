package com.sunya.electionguard;

import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.IntRange;

import java.math.BigInteger;

import static com.google.common.truth.Truth.assertThat;
import static com.sunya.electionguard.Group.*;


public class TestDlogProperties {
  static final ElementModP g_inv = int_to_p_unchecked(mult_inv_p(G));

  @Property
  public void test_cached(@ForAll @IntRange(min = 0, max = 10000) int exp) {
    BigInteger bigExp = BigInteger.valueOf(exp);
    ElementModQ plaintext = int_to_q(bigExp).get();
    ElementModP exp_plaintext = g_pow_p(plaintext); // g^e mod p.
    BigInteger plaintext_again = Dlog.discrete_log(exp_plaintext); // log (base g, mod p)
    assertThat(bigExp).isEqualTo(plaintext_again);
  }

}
