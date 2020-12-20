package com.sunya.electionguard;

import net.jqwik.api.*;

import java.math.BigInteger;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth8.assertThat;
import static com.sunya.electionguard.Group.*;
import static org.junit.Assert.fail;

public class TestGroupProperties extends TestProperties {

  //// TestEquality
  @Property
  public void testPsNotEqualToQs(@ForAll("elements_mod_q") ElementModQ q, @ForAll("elements_mod_q") ElementModQ q2) {
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
  @Property
  public void test_add_q(@ForAll("elements_mod_q") ElementModQ q) {
    ElementModQ as_int = add_qi(q.elem, BigInteger.ONE);
    ElementModQ as_elem = add_q(q, new ElementModQ(BigInteger.ONE));
    assertThat(as_int).isEqualTo(as_elem);
  }

  @Property
  public void test_a_plus_bc_q(@ForAll("elements_mod_q") ElementModQ q) {
    ElementModQ as_int = a_plus_bc_q(q.elem, BigInteger.ONE, BigInteger.ONE);
    ElementModQ as_elem = a_plus_bc_q(q, new ElementModQ(BigInteger.ONE), new ElementModQ(BigInteger.ONE));
    assertThat(as_int).isEqualTo(as_elem);
  }

  @Property
  public void test_a_minus_b_q(@ForAll("elements_mod_q") ElementModQ q) {
    ElementModQ as_int = a_minus_b_q(q.elem, BigInteger.ONE);
    ElementModQ as_elem = a_minus_b_q(q, new ElementModQ(BigInteger.ONE));
    assertThat(as_int).isEqualTo(as_elem);
  }

  @Property
  public void test_div_q(@ForAll("elements_mod_q") ElementModQ q) {
    ElementModQ as_int = div_q(q.elem, BigInteger.ONE);
    ElementModQ as_elem = div_q(q, new ElementModQ(BigInteger.ONE));
    assertThat(as_int).isEqualTo(as_elem);
  }

  @Property
  public void test_div_p(@ForAll("elements_mod_p") ElementModP p) {
    ElementModP as_int = div_p(p.elem, BigInteger.ONE);
    ElementModP as_elem = div_p(p, new ElementModQ(BigInteger.ONE));
    assertThat(as_int).isEqualTo(as_elem);
  }

  @Property
  public void test_no_mult_inv_of_zero() {
    try {
      mult_inv_p(ZERO_MOD_P);
      fail();
    } catch (Exception e) {
      //correct
    }
  }

  @Property
  public void test_mult_inverses(@ForAll("elements_mod_p_no_zero") ElementModP p_no_zero) {
    ElementModP inv = mult_inv_p(p_no_zero);
    assertThat(mult_p(p_no_zero, inv)).isEqualTo(ONE_MOD_P);
  }

  @Property
  public void test_mult_identity(@ForAll("elements_mod_p") ElementModP p) {
    assertThat(p).isEqualTo(mult_p(p));
  }

  @Property
  public void test_mult_noargs() {
    assertThat(ONE_MOD_P).isEqualTo(mult_p());
  }

  @Property
  public void test_add_noargs() {
    assertThat(ZERO_MOD_Q).isEqualTo(add_q());
  }

  @Property
  public void test_properties_for_constants() {
    assertThat(G).isNotEqualTo(BigInteger.ONE);
    assertThat((R.multiply(Q).mod(P))).isEqualTo(P.subtract(BigInteger.ONE));
    assertThat(Q.compareTo(P) < 0).isTrue();
    assertThat(G.compareTo(P) < 0).isTrue();
    assertThat(R.compareTo(P) < 0).isTrue();
  }

  @Property
  public void test_simple_powers() {
    ElementModP gp = int_to_p(G).get();
    assertThat(gp).isEqualTo(g_pow_p(ONE_MOD_Q));
    assertThat(ONE_MOD_P).isEqualTo(g_pow_p(ZERO_MOD_Q));
  }

  @Property
  public void test_in_bounds_q(@ForAll("elements_mod_q") ElementModQ q) {
    assertThat(q.is_in_bounds()).isTrue();
    BigInteger too_big = q.getBigInt().add(Q);
    BigInteger too_small = q.getBigInt().subtract(Q);
    assertThat(int_to_q_unchecked(too_big).is_in_bounds()).isFalse();
    assertThat(int_to_q_unchecked(too_small).is_in_bounds()).isFalse();
    assertThat(int_to_q(too_big)).isEmpty();
    assertThat(int_to_q(too_small)).isEmpty();
  }

  @Property
  public void test_in_bounds_p(@ForAll("elements_mod_p") ElementModP p) {
    assertThat(p.is_in_bounds()).isTrue();
    BigInteger too_big = p.getBigInt().add(P);
    BigInteger too_small = p.getBigInt().subtract(P);
    assertThat(int_to_p_unchecked(too_big).is_in_bounds()).isFalse();
    assertThat(int_to_p_unchecked(too_small).is_in_bounds()).isFalse();
    assertThat(int_to_p(too_big)).isEmpty();
    assertThat(int_to_p(too_small)).isEmpty();
  }

  @Property
  public void test_in_bounds_q_no_zero(@ForAll("elements_mod_q_no_zero") ElementModQ q_no_zero) {
    assertThat(is_in_bounds_no_zero(q_no_zero)).isTrue();
    assertThat(is_in_bounds_no_zero(ZERO_MOD_Q)).isFalse();
    assertThat(is_in_bounds_no_zero(int_to_q_unchecked(q_no_zero.getBigInt().add(Q)))).isFalse();
    assertThat(is_in_bounds_no_zero(int_to_q_unchecked(q_no_zero.getBigInt().subtract(Q)))).isFalse();
  }

  @Property
  public void test_in_bounds_p_no_zero(@ForAll("elements_mod_p_no_zero") ElementModP p_no_zero) {
    assertThat(is_in_bounds_no_zero(p_no_zero)).isTrue();
    assertThat(is_in_bounds_no_zero(ZERO_MOD_P)).isFalse();
    assertThat(is_in_bounds_no_zero(int_to_p_unchecked(p_no_zero.getBigInt().add(P)))).isFalse();
    assertThat(is_in_bounds_no_zero(int_to_p_unchecked(p_no_zero.getBigInt().subtract(P)))).isFalse();
  }

  @Property
  public void test_large_values_rejected_by_int_to_q(@ForAll("elements_mod_q") ElementModQ q) {
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
