package com.sunya.electionguard;

import com.google.common.base.Preconditions;

import java.util.Arrays;

/**
 * A sequence of random elements in [0,Q), seeded from an initial element in [0,Q).
 * If you start with the same seed, you'll get exactly the same sequence. Optional string
 * or ElementModPOrQ "headers" can be included alongside the seed at construction time.
 * This is useful when specifying what a nonce is being used for, to avoid various kinds of subtle cryptographic attacks.
 */
public class Nonces {
  private final Group.ElementModQ seed;

  Nonces(Group.ElementModQ seed, Object... headers) {
    this.seed = (headers.length > 0) ? Hash.hash_elems(seed, Arrays.asList(headers)) : seed;
  }

  Group.ElementModQ get(int index) {
    Preconditions.checkArgument(index >= 0, "Nonces do not support negative indices.");
    return Hash.hash_elems(seed, index);
  }
}
