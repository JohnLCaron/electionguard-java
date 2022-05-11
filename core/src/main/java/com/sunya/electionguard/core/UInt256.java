package com.sunya.electionguard.core;

import com.google.common.base.Preconditions;
import com.sunya.electionguard.Group;
import com.sunya.electionguard.Hash;
import at.favre.lib.bytes.Bytes;

import java.math.BigInteger;
import java.util.Arrays;

public record UInt256(byte[] val) implements Hash.CryptoHashableString {

  public UInt256 {
    Preconditions.checkArgument(val.length == 32);
  }

  public static UInt256 fromModQ(Group.ElementModQ elem) {
    return new UInt256(elem.bytes().array());
  }

  public Group.ElementModQ toModQ() {
    return Group.int_to_q_unchecked( new BigInteger(val));
  }

  public String toString() {
    return String.format("UInt256(0x%s)", cryptoHashString());
  }

  @Override
  public String cryptoHashString() {
    return Bytes.wrap(val).encodeHex(true);
  }

  public Bytes toBytes() {
    return Bytes.wrap(val);
  }

  // TODO why does default record equals() fail?
  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    UInt256 uInt256 = (UInt256) o;
    return Arrays.equals(val, uInt256.val);
  }

  @Override
  public int hashCode() {
    return Arrays.hashCode(val);
  }
}
