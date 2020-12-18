package com.sunya.electionguard;

import org.junit.Before;
import org.junit.Test;

import java.math.BigInteger;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth8.assertThat;
import static com.sunya.electionguard.Group.*;
import static org.junit.Assert.fail;

public class TestGroup {
  ElementModQ q;
  ElementModQ q2;
  ElementModP p;
  ElementModP p_no_zero;
  ElementModQ q_no_zero;

  @Before
  public void setup() {
    q = new ElementModQ(Secrets.randbelow(Q));
    q2 = new ElementModQ(Secrets.randbelow(Q));
    p = new ElementModP(Secrets.randbelow(P));
    p_no_zero = new ElementModP(Secrets.randbetween(BigInteger.ONE, P));
    q_no_zero = new ElementModQ(Secrets.randbetween(BigInteger.ONE, Q));
  }

  //// TestEquality
  @Test
  public void testPsNotEqualToQs() {
    ElementModP p = int_to_p_unchecked(q.getBigInt());
    ElementModP p2 = int_to_p_unchecked(q2.getBigInt());

    // same value should imply they 're equal
    assertThat(p).isEqualTo(q);
    assertThat(q).isEqualTo(p);

    if (!q.getBigInt().equals(q2.getBigInt())) {
      // these are genuinely different numbers
      assertThat(q).isNotEqualTo(q2);
      assertThat(p).isNotEqualTo(p2);
      assertThat(q).isNotEqualTo(p2);
      assertThat(p).isNotEqualTo(q2);

      // of course, we 're going to make sure that a number is equal to itself
      assertThat(p).isEqualTo(p);
      assertThat(q).isEqualTo(q);
    }
  }

  //// TestModularArithmetic
  @Test
  public void test_add_q() {
    ElementModQ as_int = add_qi(q.elem, BigInteger.ONE);
    ElementModQ as_elem = add_q(q, new ElementModQ(BigInteger.ONE));
    assertThat(as_int).isEqualTo(as_elem);
  }

  @Test
  public void test_a_plus_bc_q() {
    ElementModQ as_int = a_plus_bc_q(q.elem, BigInteger.ONE, BigInteger.ONE);
    ElementModQ as_elem = a_plus_bc_q(q, new ElementModQ(BigInteger.ONE), new ElementModQ(BigInteger.ONE));
    assertThat(as_int).isEqualTo(as_elem);
  }

  @Test
  public void test_a_minus_b_q() {
    ElementModQ as_int = a_minus_b_q(q.elem, BigInteger.ONE);
    ElementModQ as_elem = a_minus_b_q(q, new ElementModQ(BigInteger.ONE));
    assertThat(as_int).isEqualTo(as_elem);
  }

  @Test
  public void test_div_q() {
    ElementModQ as_int = div_q(q.elem, BigInteger.ONE);
    ElementModQ as_elem = div_q(q, new ElementModQ(BigInteger.ONE));
    assertThat(as_int).isEqualTo(as_elem);
  }

  @Test
  public void test_div_p() {
    ElementModP as_int = div_p(q.elem, BigInteger.ONE);
    ElementModP as_elem = div_p(q, new ElementModQ(BigInteger.ONE));
    assertThat(as_int).isEqualTo(as_elem);
  }

  @Test
  public void test_no_mult_inv_of_zero() {
    try {
      mult_inv_p(ZERO_MOD_P);
      fail();
    } catch (Exception e) {
      //correct
    }
  }

  @Test
  public void test_mult_inverses() {
    ElementModP inv = mult_inv_p(p_no_zero);
    assertThat(mult_p(p_no_zero, inv)).isEqualTo(ONE_MOD_P);
  }

  @Test
  public void test_mult_identity() {
    assertThat(p).isEqualTo(mult_p(p));
  }

  @Test
  public void test_mult_noargs() {
    assertThat(ONE_MOD_P).isEqualTo(mult_p());
  }

  @Test
  public void test_add_noargs() {
    assertThat(ZERO_MOD_Q).isEqualTo(add_q());
  }

  @Test
  public void test_properties_for_constants() {
    assertThat(G).isNotEqualTo(BigInteger.ONE);
    assertThat((R.multiply(Q).mod(P))).isEqualTo(P.subtract(BigInteger.ONE));
    assertThat(Q.compareTo(P) < 0).isTrue();
    assertThat(G.compareTo(P) < 0).isTrue();
    assertThat(R.compareTo(P) < 0).isTrue();
  }

  @Test
  public void test_simple_powers() {
    ElementModP gp = int_to_p(G).get();
    assertThat(gp).isEqualTo(g_pow_p(ONE_MOD_Q));
    assertThat(ONE_MOD_P).isEqualTo(g_pow_p(ZERO_MOD_Q));
  }

  @Test
  public void test_in_bounds_q() {
    assertThat(q.is_in_bounds()).isTrue();
    BigInteger too_big = q.getBigInt().add(Q);
    BigInteger too_small = q.getBigInt().subtract(Q);
    assertThat(int_to_q_unchecked(too_big).is_in_bounds()).isFalse();
    assertThat(int_to_q_unchecked(too_small).is_in_bounds()).isFalse();
    assertThat(int_to_q(too_big)).isEmpty();
    assertThat(int_to_q(too_small)).isEmpty();
  }

  @Test
  public void test_in_bounds_p() {
    assertThat(p.is_in_bounds()).isTrue();
    BigInteger too_big = p.getBigInt().add(P);
    BigInteger too_small = p.getBigInt().subtract(P);
    assertThat(int_to_p_unchecked(too_big).is_in_bounds()).isFalse();
    assertThat(int_to_p_unchecked(too_small).is_in_bounds()).isFalse();
    assertThat(int_to_p(too_big)).isEmpty();
    assertThat(int_to_p(too_small)).isEmpty();
  }

  @Test
  public void test_in_bounds_q_no_zero() {
    assertThat(is_in_bounds_no_zero(q_no_zero)).isTrue();
    assertThat(is_in_bounds_no_zero(ZERO_MOD_Q)).isFalse();
    assertThat(is_in_bounds_no_zero(int_to_q_unchecked(q_no_zero.getBigInt().add(Q)))).isFalse();
    assertThat(is_in_bounds_no_zero(int_to_q_unchecked(q_no_zero.getBigInt().subtract(Q)))).isFalse();
  }

  @Test
  public void test_in_bounds_p_no_zero() {
    assertThat(is_in_bounds_no_zero(p_no_zero)).isTrue();
    assertThat(is_in_bounds_no_zero(ZERO_MOD_P)).isFalse();
    assertThat(is_in_bounds_no_zero(int_to_p_unchecked(p_no_zero.getBigInt().add(P)))).isFalse();
    assertThat(is_in_bounds_no_zero(int_to_p_unchecked(p_no_zero.getBigInt().subtract(P)))).isFalse();
  }

  @Test
  public void test_large_values_rejected_by_int_to_q() {
    BigInteger oversize = q.elem.add(Q);
    assertThat(int_to_q(oversize)).isEmpty();
  }

  private boolean is_in_bounds_no_zero(ElementModP p) {
    return Group.between(BigInteger.ONE, p.elem, P);
  }

  private boolean is_in_bounds_no_zero(ElementModQ q) {
    return Group.between(BigInteger.ONE, q.elem, Q);
  }
}
