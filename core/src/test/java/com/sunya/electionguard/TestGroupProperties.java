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

    // same value should imply they're equal
    assertThat(p).isEqualTo(q);
    assertThat(q).isEqualTo(p);

    if (!q.getBigInt().equals(q2.getBigInt())) {
      // these are genuinely different numbers
      assertThat(q).isNotEqualTo(q2);
      assertThat(p).isNotEqualTo(p2);
      assertThat(q).isNotEqualTo(p2);
      assertThat(p).isNotEqualTo(q2);

      // of course, we're going to make sure that a number is equal to itself
      assertThat(p).isEqualTo(p);
      assertThat(q).isEqualTo(q);
    }
  }

  //// TestModularArithmetic
  @Property
  public void test_add_q(@ForAll("elements_mod_q") ElementModQ q) {
    BigInteger as_int = q.elem.add(BigInteger.ONE);
    ElementModQ as_elem = add_q(q, ONE_MOD_Q);
    assertThat(as_int).isEqualTo(as_elem.elem);
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
  public void test_simple_powers() {
    ElementModP gp = int_to_p_unchecked(getPrimes().generator);
    assertThat(gp).isEqualTo(g_pow_p(ONE_MOD_Q));
    assertThat(ONE_MOD_P).isEqualTo(g_pow_p(ZERO_MOD_Q));
  }

  @Property
  public void test_in_bounds_q(@ForAll("elements_mod_q") ElementModQ q) {
    assertThat(q.is_in_bounds()).isTrue();
    BigInteger too_big = q.getBigInt().add(Group.getPrimes().smallPrime);
    BigInteger too_small = q.getBigInt().subtract(Group.getPrimes().smallPrime);
    assertThat(int_to_q_unchecked(too_big).is_in_bounds()).isFalse();
    assertThat(int_to_q_unchecked(too_small).is_in_bounds()).isFalse();
    assertThat(int_to_q(too_big)).isEmpty();
    assertThat(int_to_q(too_small)).isEmpty();
  }

  @Property
  public void test_in_bounds_p(@ForAll("elements_mod_p") ElementModP p) {
    assertThat(p.is_in_bounds()).isTrue();
    BigInteger too_big = p.getBigInt().add(Group.getPrimes().largePrime);
    BigInteger too_small = p.getBigInt().subtract(Group.getPrimes().largePrime);
    assertThat(int_to_p_unchecked(too_big).is_in_bounds()).isFalse();
    assertThat(int_to_p_unchecked(too_small).is_in_bounds()).isFalse();
    assertThat(int_to_p(too_big)).isEmpty();
    assertThat(int_to_p(too_small)).isEmpty();
  }

  @Property
  public void test_in_bounds_q_no_zero(@ForAll("elements_mod_q_no_zero") ElementModQ q_no_zero) {
    assertThat(is_in_bounds_no_zero(q_no_zero)).isTrue();
    assertThat(is_in_bounds_no_zero(ZERO_MOD_Q)).isFalse();
    assertThat(is_in_bounds_no_zero(int_to_q_unchecked(q_no_zero.getBigInt().add(Group.getPrimes().smallPrime)))).isFalse();
    assertThat(is_in_bounds_no_zero(int_to_q_unchecked(q_no_zero.getBigInt().subtract(Group.getPrimes().smallPrime)))).isFalse();
  }

  @Property
  public void test_in_bounds_p_no_zero(@ForAll("elements_mod_p_no_zero") ElementModP p_no_zero) {
    assertThat(is_in_bounds_no_zero(p_no_zero)).isTrue();
    assertThat(is_in_bounds_no_zero(ZERO_MOD_P)).isFalse();
    assertThat(is_in_bounds_no_zero(int_to_p_unchecked(p_no_zero.getBigInt().add(Group.getPrimes().largePrime)))).isFalse();
    assertThat(is_in_bounds_no_zero(int_to_p_unchecked(p_no_zero.getBigInt().subtract(Group.getPrimes().largePrime)))).isFalse();
  }

  @Property
  public void test_large_values_rejected_by_int_to_q(@ForAll("elements_mod_q") ElementModQ q) {
    BigInteger oversize = q.elem.add(Group.getPrimes().smallPrime);
    assertThat(int_to_q(oversize)).isEmpty();
  }

  private boolean is_in_bounds_no_zero(ElementModP p) {
    return Group.between(BigInteger.ONE, p.elem, Group.getPrimes().largePrime);
  }

  private boolean is_in_bounds_no_zero(ElementModQ q) {
    return Group.between(BigInteger.ONE, q.elem, Group.getPrimes().smallPrime);
  }

  @Example
  public void testP() {
    String data = "9A46B10B62BA7738CBD527CFE3E5A9B95E7D2311333C470ADCCC528F5657346B6FA4823D5FDDC57675DAE8330BA7BE180BAE0511A2C9307B1F6F9FDFD180D1A61C641E79249F72CDFFEB84EAD226CDCB36C1CFFB1D58ACBEFE12A7B09493ED0BE20B4A8B643808A0052C5CFB81996B8B44343C4E12A1C629BFC53DA33E85AFACE8B8A7A0FAFB3A61B99F69D07C28D11B45CC4FBB5F198061FE7606CEFB7DE64A94B61CEDC3F87FCBE3DA9F4430126ED54EB93CD6C6188395CC9F2402F87DBBF3A87F5C526F7B24CA4AE8E8A052AF1CDCD802D84F7F9C333BD81FBD9A1D599636AB48ED69E492DA615B34D88178199A00731D9F190AB1B5A5E3EAB9A66F39DAE0EDE750797FD408B41F5C1CF0C2254B6854D75CD6DA03006856E5DA96CE862AB80EEFFCF6F4FAA33300C2ECF81F03226BC81911437A5157F5A0E98F0B5F38193019D6DB48DFB155132296B5F3C6A3D9E3121051F7187AB3B12CF1734B88EC3B2A18EC5B7AB9B97CA136094F9F9CADB5083484DBC267B02CB103A1BD31E641514866611C82B7851D93E3B71CD7B450393418E9327B5DC23F41CCA5146A258EE3B8AF8C0FE7FFB3DB3DA81A02C4395F77FFF1AE4623556BB49327C8C4C276C2A8D2030BAC9A7A74E841F48DBDCB2FDABD835A66AD9ED08F31CB7F5D71EF2EBD40C75483F547D980F853EDEC5043AF1D8BA216A12A9E98D9CC928C439FAEA5F436D5";
    assertThat(Group.int_to_p(new BigInteger(data, 16))).isPresent();
  }

}
