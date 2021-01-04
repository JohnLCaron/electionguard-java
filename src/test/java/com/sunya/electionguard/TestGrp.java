package com.sunya.electionguard;

import com.google.common.truth.Truth;
import net.jqwik.api.Example;

public class TestGrp {

  @Example
  public void testPrime() {
    Truth.assertThat(Grp.is_prime(Group.P, 5)).isTrue();
    Truth.assertThat(Grp.is_prime(Group.Q, 5)).isTrue();
  }
}
