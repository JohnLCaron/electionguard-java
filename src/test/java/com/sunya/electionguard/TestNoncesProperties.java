package com.sunya.electionguard;

import net.jqwik.api.Example;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.Positive;

import java.math.BigInteger;

import static com.google.common.truth.Truth.assertThat;
import static com.sunya.electionguard.Group.Q;
import static org.junit.Assert.fail;

public class TestNoncesProperties extends TestProperties {
  Group.ElementModQ seed;
  Nonces nonces;

  public TestNoncesProperties() {
    seed = Group.int_to_q_unchecked(Utils.randbelow(Q));
    nonces = new Nonces(seed);
  }

  @Example
  public void test_nonces_distinct() {
    Group.ElementModQ n0 = nonces.get(0);
    Group.ElementModQ n1 = nonces.get(1);
    assertThat(n0).isNotEqualTo(n1);
  }

  @Property
  public void test_nonces_deterministic(@ForAll @Positive int i) {
      assertThat(nonces.get(i)).isEqualTo(nonces.get(i));
  }

  @Example
  public void test_nonces_seed_matters(@ForAll("elements_mod_q") Group.ElementModQ seed2, @ForAll @Positive int i) {
    Nonces nonces2 = new Nonces(seed2);
    assertThat(nonces.get(i)).isNotEqualTo(nonces2.get(i));
  }

  @Example
  public void test_nonces_type_errors() {
    try {
      nonces.get(-1);
      fail();
    } catch (Exception e) {
      // correct
    }
  }

  @Example
  public void test_arraysAsList() {
    Group.ElementModQ contest_description_hash = Group.int_to_q_unchecked(
            new BigInteger("23323337622785956586624352149596102740126789838719230463210351477786424874750"));
    Group.ElementModQ nonce_seed = Group.int_to_q_unchecked(
            new BigInteger("43908801192069419615255659718413032744597964462237265386133198243091690982198"));
    int seq = 7;
    Nonces nonce_sequence = new Nonces(contest_description_hash, nonce_seed);
    Group.ElementModQ contest_nonce  = nonce_sequence.get(seq);

    Nonces nonce_sequence2 = new Nonces(contest_description_hash, nonce_seed);
    Group.ElementModQ contest_nonce2  = nonce_sequence2.get(seq);
    assertThat(contest_nonce).isEqualTo(contest_nonce2);
  }

}
