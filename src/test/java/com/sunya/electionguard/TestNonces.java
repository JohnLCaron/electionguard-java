package com.sunya.electionguard;

import org.junit.Before;
import org.junit.Test;

import static com.google.common.truth.Truth.assertThat;
import static com.sunya.electionguard.Group.Q;
import static org.junit.Assert.fail;

public class TestNonces {
  Group.ElementModQ seed;
  Nonces nonces;

  @Before
  public void setup() {
    seed = new Group.ElementModQ(Secrets.randbelow(Q));
    nonces = new Nonces(seed);
  }

  @Test
  public void test_nonces_distinct() {
    Group.ElementModQ n0 = nonces.get(0);
    Group.ElementModQ n1 = nonces.get(1);
    assertThat(n0).isNotEqualTo(n1);
  }

  @Test
  public void test_nonces_deterministic() {
    for (int i = 0; i < 1000000; i += 1000) {
      assertThat(nonces.get(i)).isEqualTo(nonces.get(i));
    }
  }

  @Test
  public void test_nonces_seed_matters() {
    Group.ElementModQ seed2 = new Group.ElementModQ(Secrets.randbelow(Q));
    Nonces nonces2 = new Nonces(seed2);
    for (int i = 0; i < 1000000; i += 1000) {
      assertThat(nonces.get(i)).isNotEqualTo(nonces2.get(i));
    }
  }

  @Test
  public void test_nonces_type_errors() {
    try {
      nonces.get(-1);
      fail();
    } catch (Exception e) {
      // correct
    }
  }

}
