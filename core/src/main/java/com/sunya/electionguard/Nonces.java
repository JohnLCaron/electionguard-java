package com.sunya.electionguard;

import com.google.common.base.Preconditions;

import java.util.Arrays;

/**
 * A sequence of random elements in [0,Q). This is created from an initial "seed" ElementModQ.
 * If you start with the same seed, you'll get exactly the same sequence. Optional
 * "headers" can be included in the seed at construction time.
 * This is useful to avoid various kinds of subtle cryptographic attacks.
 */
public class Nonces {
  final Group.ElementModQ internalSeed;

  public Nonces(Group.ElementModQ seed, Object... headers) {
    if (headers.length > 0) {
      Object[] allArgs = new Object[headers.length + 1];
      allArgs[0] = seed;
      System.arraycopy(headers, 0, allArgs, 1, headers.length);
      this.internalSeed = Hash.hash_elems(allArgs);
    } else {
      this.internalSeed = seed;
    }
  }

  public Group.ElementModQ get(int index) {
    Preconditions.checkArgument(index >= 0, "Nonces do not support negative indices.");
    return Hash.hash_elems(this.internalSeed, index);
  }
}
