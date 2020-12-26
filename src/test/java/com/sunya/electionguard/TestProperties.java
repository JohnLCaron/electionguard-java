package com.sunya.electionguard;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Provide;

import java.util.Random;

public abstract class TestProperties {
  static Random random = new Random(System.currentTimeMillis());

  @Provide
  Arbitrary<ElectionTestHelper.EverythingTuple> elections_and_ballots() {
    ElectionTestHelper helper = new ElectionTestHelper(random);
    return Arbitraries.of(helper.elections_and_ballots(1 + random.nextInt(2)));
  }

  @Provide
  Arbitrary<Election.ElectionDescription> election_description() {
    ElectionTestHelper helper = new ElectionTestHelper(random);
    return Arbitraries.of(helper.election_descriptions(
            1 + random.nextInt(2), 1 + random.nextInt(2)));
  }

  @Provide
  Arbitrary<Ballot.PlaintextBallotSelection> get_selection_poorly_formed() {
    return Arbitraries.of(BallotFactory.get_selection_poorly_formed());
  }

  @Provide
  Arbitrary<Ballot.PlaintextBallotSelection> get_selection_well_formed() {
    return Arbitraries.of(BallotFactory.get_selection_well_formed());
  }

  @Provide
  Arbitrary<Election.SelectionDescription> selection_description() {
    return Arbitraries.of(ElectionFactory.get_selection_description_well_formed().selection_description);
  }

  @Provide
  Arbitrary<Election.ContestDescriptionWithPlaceholders> contest_description_well_formed() {
    return Arbitraries.of(ElectionFactory.get_contest_description_well_formed());
  }

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
