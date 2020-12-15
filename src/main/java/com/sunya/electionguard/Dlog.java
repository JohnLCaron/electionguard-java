package com.sunya.electionguard;

import java.math.BigInteger;

import static com.sunya.electionguard.Group.mult_p;

/** Support for computing discrete logs, with a cache so they're never recomputed. */
public class Dlog {

  static BigInteger discrete_log(Group.ElementModP elem) {
    // TODO add cache
    return discrete_log_internal(elem);
  }

  private static BigInteger discrete_log_internal(Group.ElementModP elem) {
    return null; // mult_p(ImmutableList.of(G, elem));
  }

}
