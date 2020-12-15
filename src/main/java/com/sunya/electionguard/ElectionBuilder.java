package com.sunya.electionguard;

import java.util.Optional;

import static com.sunya.electionguard.Election.*;


/**
 *     `ElectionBuilder` is a stateful builder object that constructs `CiphertextElectionContext` objects
 *     following the initialization process that ElectionGuard Expects.
 */
public class ElectionBuilder {
  int number_of_guardians; // The number of guardians necessary to generate the public key
  int quorum; //  The quorum of guardians necessary to decrypt an election.  Must be less than `number_of_guardians`
  ElectionDescription description;
  InternalElectionDescription internal_description;
  Optional<Group.ElementModP> elgamal_public_key = Optional.empty();

  /**
   *         Set election public key
   *         :param elgamal_public_key: elgamal public key for election
   *         :return: election builder
   */
  ElectionBuilder set_public_key(Group.ElementModP elgamal_public_key) {
    this.elgamal_public_key = Optional.of(elgamal_public_key);
    return this;
  }
}
