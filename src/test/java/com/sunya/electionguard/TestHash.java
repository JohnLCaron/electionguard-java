package com.sunya.electionguard;

import org.junit.Before;
import org.junit.Test;

import static com.google.common.truth.Truth.assertThat;
import static com.sunya.electionguard.Group.P;
import static com.sunya.electionguard.Group.Q;

public class TestHash {
  Group.ElementModQ q;
  Group.ElementModQ q2;
  Group.ElementModP p;

  @Before
  public void setup() {
    q = new Group.ElementModQ(Secrets.randbelow(Q));
    q2 = new Group.ElementModQ(Secrets.randbelow(Q));
    p = new Group.ElementModP(Secrets.randbelow(P));
  }

  @Test
  public void test_same_answer_twice_in_a_row() {
    // if this doesn't work, then our hash function isn't a function
    Group.ElementModQ  h1 = Hash.hash_elems(q, p);
    Group.ElementModQ  h2 = Hash.hash_elems(q, p);
    assertThat(h1).isEqualTo(h2);
  }

  @Test
  public void test_basic_hash_properties() {
    Group.ElementModQ h1 = Hash.hash_elems(q);
    Group.ElementModQ h2 = Hash.hash_elems(q2);

    assertThat(h1.equals(h2)).isEqualTo(q.equals(q2));
  }

}
