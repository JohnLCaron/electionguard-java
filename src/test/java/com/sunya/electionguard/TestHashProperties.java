package com.sunya.electionguard;

import com.google.common.collect.ImmutableList;
import net.jqwik.api.Example;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import org.junit.Before;
import org.junit.Test;

import java.util.List;

import static com.google.common.truth.Truth.assertThat;
import static com.sunya.electionguard.Group.P;
import static com.sunya.electionguard.Group.Q;

public class TestHashProperties extends TestProperties {

  @Property
  public void test_same_answer_twice_in_a_row(@ForAll("elements_mod_q") Group.ElementModQ q, @ForAll("elements_mod_p") Group.ElementModP p) {
    // if this doesn't work, then our hash function isn't a function
    Group.ElementModQ  h1 = Hash.hash_elems(q, p);
    Group.ElementModQ  h2 = Hash.hash_elems(q, p);
    assertThat(h1).isEqualTo(h2);
  }

  @Property
  public void test_basic_hash_properties(@ForAll("elements_mod_q") Group.ElementModQ q, @ForAll("elements_mod_q") Group.ElementModQ q2) {
    Group.ElementModQ h1 = Hash.hash_elems(q);
    Group.ElementModQ h2 = Hash.hash_elems(q2);
    assertThat(h1.equals(h2)).isEqualTo(q.equals(q2));
  }

  @Example
  public void test_object_arrays() {
    String s = "test";
    Group.ElementModQ q1 = TestUtils.elements_mod_q();
    Group.ElementModQ q2 = TestUtils.elements_mod_q();
    Group.ElementModQ q3 = TestUtils.elements_mod_q();

    Group.ElementModQ h1 = Hash.hash_elems(s, q1, q2, q3);
    assertThat(h1).isEqualTo(Hash.hash_elems(s, q1, q2, q3));

    List<Group.ElementModQ> list = ImmutableList.of(q2, q3);
    Group.ElementModQ h2 = Hash.hash_elems(s, q1, list);
    assertThat(h2).isEqualTo(Hash.hash_elems(s, q1, list));
    assertThat(h1).isNotEqualTo(h2);
  }

}
