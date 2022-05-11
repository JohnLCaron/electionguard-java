package com.sunya.electionguard;

import at.favre.lib.bytes.Bytes;
import com.sunya.electionguard.json.ConvertFromJson;
import com.sunya.electionguard.json.ConvertToJson;
import com.sunya.electionguard.json.LagrangeCoefficientsPojo;
import net.jqwik.api.*;

import java.io.File;
import java.io.IOException;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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

    // p and q are not equal because they are normalized
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

  @Example
  public void testNormalized() {
    String hexValNormal = "0089940FEA8014812318EA706F2E6CC89088969A79E8477439849C729BD5EB03";
    BigInteger normal =   new BigInteger(hexValNormal, 16);
    String hexValNotNormal = "FF89940FEA8014812318EA706F2E6CC89088969A79E8477439849C729BD5EB03";
    BigInteger notNormal = new BigInteger(hexValNotNormal, 16);

    Group.ElementModQ qnot = Group.int_to_q(notNormal).orElseThrow();
    Group.ElementModQ qnormal = Group.int_to_q(normal).orElseThrow();

    assertThat(qnormal.isNormal()).isTrue();
    assertThat(qnot.isNormal()).isFalse();

    assertThat(qnot).isNotEqualTo(qnormal);
    assertThat(Group.int_to_q_normalized(notNormal)).isEqualTo(qnormal);

    assertThat(qnormal.to_hex()).isEqualTo(hexValNormal);
    assertThat(qnot.to_hex()).isEqualTo(hexValNotNormal);

    // assertThat((Iterable<?>) qnot.normalize(32)).isEqualTo(qnormal.normalize(32));
  }

  @Example
  public void testNormalizedConstants() {
    assertThat(TWO_MOD_Q).isEqualTo(Group.int_to_q_normalized(TWO_MOD_Q.getBigInt()));
    assertThat(TWO_MOD_P).isEqualTo(Group.int_to_p_normalized(TWO_MOD_P.getBigInt()));

    ElementModQ q2 = TWO_MOD_Q;
    assertThat(q2.isNormal()).isFalse();
    ElementModQ normalq2 = Group.int_to_q_normalized(q2.getBigInt());
    assertThat(q2).isEqualTo(normalq2);
    assertThat(q2.getBigInt()).isEqualTo(normalq2.getBigInt());
    assertThat(normalq2.isNormal()).isTrue();

    ElementModP p2 = TWO_MOD_P;
    ElementModP normalp2 = Group.int_to_p_normalized(p2.getBigInt());
    assertThat(p2.isNormal()).isFalse();
    assertThat(normalp2.isNormal()).isTrue();
  }

  @Example
  public void testNormalizedBigIntegers() {
    ElementModQ q2 = TWO_MOD_Q;
    BigInteger q2b = q2.getBigInt();
    assertThat(q2b.toByteArray().length).isNotEqualTo(32);

    Bytes b = q2.normalize(32);
    assertThat(b.length()).isEqualTo(32);
    BigInteger q2normal = new BigInteger(b.array());

    // BigInteger does not use the internal byte array
    assertThat(q2normal.toByteArray().length).isNotEqualTo(32);
  }

  @Example
  public void testNormalizedBigInteger2() {
    BigInteger big10 = new BigInteger("-985885428196823793871112996193692117877338077477509313481176490080043572707575411869476082314071793964095333398744714332767945309577734923855959666952312525705983935794224254844921191506542799980986277785730101641969998008950665607552674779780255443794340755631453736511195965846294137817959149387975188426988363938995046173983376227628568878079936975107253562158738016217863695915516514543524467023351623372147699587151823964788878931625535092111399233104855398144355716461292664016469938048164770562262915176131141149306974339848374124423701816561022798560781880074347370789098569941039608169169423225225700784888687065727520873473846824836442424518979566061683638437879690405121721580003679584987758993406983880687066931891663303142191385688382810398036456017524079326674174907842832390598151860062729968684600437956686678947144020988482631829670396480266255416655992443923337019632004174957302256795150925344684757543832824644645093832263068544636807355769443006223707566815533908185857489241864957258108438753697424938535863201731916633015843613633242770037047355829251893057397651915198708984643944247423365612497135095192340593592328347935363489421152889044708789452933119622244096831912473291882094524612876636835909701347");
    BigInteger big16 = new BigInteger("-3ddd686fd551ed14a405be11987688a4750100ef48f3ff5088877298277d5a5fe29d6a7e544c4cf68226578fce361f184c4c1e9a3b445869f2233114fd836e1b1cb689fe3b558b3c0258199c09e04788c616039c38c1cc60629b2142a19d3b12f9a48a8b232b9e9081221a0eadecbf4d76f9655100237e8587b220b19ed33ca73c980ce0883f70dbe36c244ba8b6738a5cca6d90f3d78ec6e5eae1d6c6f0878fd28f629fde2dcb4b9869364d21b0f0c7f484721a9daf3d4d193023f8ff220b384faf19531dea8301f68c5a7142e455af7a969f486c9ca106b54281d5c3c3e0d61ef2badffae3d8c2d77a96c82f2fceff809f64df23646ee3d2d1962475a89aacd7bc1eb12e3eafd828b8a29912fa4340b481b9f571f11aa3df73d96ffc49e6ed41ea32fd9523854476568eb20fbbfc2cd0ac7c7036cf684fb77a43bce4ad7f542ac4ea636bfd56442d92639eaf742be0ad2b8b19c5fa59f1a4cd3c844d434e33fda40569c9b34d18cb16d5f3446ab506ddc735d081d0c1cf1b37060da37b187dd7d3b9dc2f5890c084dcaeeae4d358118f5e414b2e2d2d01200c85ff18bc3e7b78be756fb8602eda65069f767d0ae459d36ae96f5d3b5191ed7b1b7f74de4146e364b7f5873fd366013a7fe2c1ad416628b78e23942996a4bac282713d5b82c3d05fe71f3c5809cc7aaffc3cdbb48367398b5e888f3dcde8ba1167c9fb8ee3", 16);
    assertThat(big10).isEqualTo(big16);

    ElementModP p = Group.int_to_p_unchecked(big10);
    ElementModP normalp = Group.int_to_p_normalized(big10);
    System.out.printf("p.to_hex = %s%n", p.to_hex());
    System.out.printf("normalp.to_hex = %s%n", normalp.to_hex());

    assertThat(p.is_in_bounds()).isFalse();
    assertThat(normalp.is_in_bounds()).isTrue();
  }

}
