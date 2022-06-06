package com.sunya.electionguard.core;

import com.sunya.electionguard.Group;
import net.jqwik.api.Example;

import java.math.BigInteger;

import static com.google.common.truth.Truth.assertThat;

public class TestUInt256 {

  @Example
  public void testUInt256() {
    String val10 = "89378920920032937196531702992192972263302712977973574040976517358784464109329";
    assertThat(val10.length()).isEqualTo(77);
    BigInteger bi = new BigInteger(val10);
    Group.ElementModQ biq = Group.int_to_q_unchecked(bi);
    assertThat(biq.bytes().length()).isEqualTo(32);
    UInt256 biu = UInt256.fromModQ(biq);
    assertThat(biu.toString()).isEqualTo("UInt256(0xC59AAD302F149A018F925AEC7B819C6F890441F0954C36C198FD0066C5A93F11)");

    BigInteger bi2 = new BigInteger("-26413168317283258227039282016494935589967271687666989998481066649128665530607");
    Group.ElementModQ biq2 = Group.int_to_q_unchecked(bi2);
    assertThat(biq2.bytes().length()).isEqualTo(32);
    UInt256 biu2 = UInt256.fromModQ(biq2);
    assertThat(biu2.toString()).isEqualTo("UInt256(0xC59AAD302F149A018F925AEC7B819C6F890441F0954C36C198FD0066C5A93F11)");

    assertThat(biu2).isEqualTo(biu);
    assertThat(biq2.toString()).isEqualTo(biq.toString());
    assertThat(biq2.base16()).isEqualTo(biq.base16());

    assertThat(biq2).isEqualTo(biq);
    assertThat(bi2).isNotEqualTo(bi);
  }

}
