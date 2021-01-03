package com.sunya.electionguard;

import java.util.Optional;

import static com.sunya.electionguard.Election.*;

/**
 * `ElectionBuilder` is a stateful builder object that constructs `CiphertextElectionContext` objects
 * following the initialization process that ElectionGuard Expects.
 */
public class ElectionBuilder {

  public static class DescriptionAndContext {
    final InternalElectionDescription description;
    final CiphertextElectionContext context;

    public DescriptionAndContext(InternalElectionDescription description, CiphertextElectionContext context) {
      this.description = description;
      this.context = context;
    }
  }

  int number_of_guardians; // The number of guardians necessary to generate the public key
  int quorum; // The quorum of guardians necessary to decrypt an election.  Must be less than `number_of_guardians`
  ElectionDescription description;
  InternalElectionDescription internal_description;
  Optional<Group.ElementModP> elgamal_public_key = Optional.empty();

  public ElectionBuilder(int number_of_guardians, int quorum, ElectionDescription description) {
    this.number_of_guardians = number_of_guardians;
    this.quorum = quorum;
    this.description = description;
    this.internal_description = new InternalElectionDescription(description);
  }

  ElectionBuilder set_public_key(Group.ElementModP elgamal_public_key) {
    this.elgamal_public_key = Optional.of(elgamal_public_key);
    return this;
  }

  Optional<DescriptionAndContext> build() {
    if (!this.description.is_valid()) {
      return Optional.empty();
    }

    if (this.elgamal_public_key.isEmpty()) {
      return Optional.empty();
    }

    return Optional.of(new DescriptionAndContext(this.internal_description,
      make_ciphertext_election_context(
              this.number_of_guardians,
              this.quorum,
              this.elgamal_public_key.get(),
              this.description.crypto_hash())));
  }

}
