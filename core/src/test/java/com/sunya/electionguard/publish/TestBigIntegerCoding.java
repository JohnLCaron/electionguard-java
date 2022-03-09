package com.sunya.electionguard.publish;

import com.sunya.electionguard.Group;
import net.jqwik.api.Example;

import java.math.BigInteger;

import static com.google.common.truth.Truth.assertThat;

public class TestBigIntegerCoding {

  @Example
  public void testBigIntegerEncoding() {
    BigInteger bi = Group.getPrimes().largePrime;
    String s = bi.toString();
    System.out.printf("BigInteger.toString() = (%d) %s%n", s.length(), s);

    s = bi.toString(16);
    System.out.printf("BigInteger.toString(16) = (%d) %s%n", s.length(), s);

    Group.ElementModP modp = Group.int_to_p_unchecked(Group.getPrimes().largePrime);
    s = modp.toString();
    System.out.printf("ElementModP.toString() = (%d) %s%n", s.length(), s);

    s = modp.to_hex();
    System.out.printf("ElementModP.to_hex() = (%d) %s%n", s.length(), s);
  }

  @Example
  public void testBigIntegerDecoding() {
    BigInteger bi = Group.getPrimes().largePrime;
    String s = bi.toString();
    BigInteger rtrip = new BigInteger(s);
    assertThat(rtrip).isEqualTo(bi);

    s = bi.toString(16);
    rtrip = new BigInteger(s, 16);
    assertThat(rtrip).isEqualTo(bi);

    Group.ElementModP modp = Group.int_to_p_unchecked(Group.getPrimes().largePrime);
    s = modp.to_hex();
    rtrip = new BigInteger(s, 16);
    assertThat(rtrip).isEqualTo(bi);
  }
}
