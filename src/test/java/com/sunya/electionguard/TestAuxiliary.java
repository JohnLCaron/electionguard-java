package com.sunya.electionguard;

import org.junit.Test;

import static com.google.common.truth.Truth.assertThat;
import static com.sunya.electionguard.Auxiliary.*;


public class TestAuxiliary {

  @Test
  public void testByteString() {
    ByteString one = new ByteString("one".getBytes());
    ByteString same = new ByteString(new String("one").getBytes());

    assertThat(one).isEqualTo(same);
    assertThat(one.hashCode()).isEqualTo(same.hashCode());

    ByteString two = new ByteString("two".getBytes());
    assertThat(one).isNotEqualTo(two);
    assertThat(one.hashCode()).isNotEqualTo(two.hashCode());
  }
}
