package com.sunya.electionguard;

/**
 *     Creates a sequence of random elements in [0,Q), seeded from an initial element in [0,Q).
 *     If you start with the same seed, you'll get exactly the same sequence. Optional string
 *     or ElementModPOrQ "headers" can be included alongside the seed both at construction time
 *     and when asking for the next nonce. This is useful when specifying what a nonce is
 *     being used for, to avoid various kinds of subtle cryptographic attacks.
 *
 *     The Nonces class is a Sequence. It can be iterated, or it can be treated as an array
 *     and indexed. Asking for a nonce is constant time, regardless of the index.
 */
public class Nonces {

  private final Group.ElementModQ seed;
  private final Object[] headers;

  Nonces(Group.ElementModQ seed, Object... headers) {
    this.seed = seed;
    this.headers = headers;
  }

  Group.ElementModQ get(int element) {
    return Hash.hash_elems(seed, element, headers);
  }
}
