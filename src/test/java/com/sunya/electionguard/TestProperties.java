package com.sunya.electionguard;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Provide;

public abstract class TestProperties {

  @Provide
  Arbitrary<Group.ElementModQ> elements_mod_q() {
    return Arbitraries.of(TestUtils.elements_mod_q());
  }

  @Provide
  Arbitrary<Group.ElementModP> elements_mod_p() {
    return Arbitraries.of(TestUtils.elements_mod_p());
  }

  @Provide
  Arbitrary<Group.ElementModQ> elements_mod_q_no_zero() {
    return Arbitraries.of(TestUtils.elements_mod_q_no_zero());
  }

  @Provide
  Arbitrary<Group.ElementModP> elements_mod_p_no_zero() {
    return Arbitraries.of(TestUtils.elements_mod_p_no_zero());
  }

  @Provide
  Arbitrary<ElGamal.KeyPair> elgamal_keypairs() {
    return Arbitraries.of(TestUtils.elgamal_keypairs());
  }

}
