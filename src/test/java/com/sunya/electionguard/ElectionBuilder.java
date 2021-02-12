package com.sunya.electionguard;

import java.util.Optional;

import static com.sunya.electionguard.Election.*;
import static com.sunya.electionguard.Group.rand_q;

// LOOK this should go away, keeping it as convenience for now.
public class ElectionBuilder {

  public static class DescriptionAndContext {
    final ElectionWithPlaceholders metadata;
    final CiphertextElectionContext context;

    public DescriptionAndContext(ElectionDescription election, CiphertextElectionContext context) {
      this.metadata = new ElectionWithPlaceholders(election);
      this.context = context;
    }
  }

  int number_of_guardians; // The number of guardians necessary to generate the public key
  int quorum; // The quorum of guardians necessary to decrypt an election.  Must be less than `number_of_guardians`
  ElectionDescription description;
  Group.ElementModQ commitment_hash;
  Optional<Group.ElementModP> elgamal_public_key = Optional.empty();

  public ElectionBuilder(int number_of_guardians, int quorum, ElectionDescription description) {
    this.number_of_guardians = number_of_guardians;
    this.quorum = quorum;
    this.description = description;
  }

  ElectionBuilder set_public_key(Group.ElementModP elgamal_public_key) {
    this.elgamal_public_key = Optional.of(elgamal_public_key);
    return this;
  }

  ElectionBuilder set_commitment_hash(Group.ElementModQ commitment_hash) {
    this.commitment_hash = commitment_hash;
    return this;
  }

  Optional<DescriptionAndContext> build() {
    if (!this.description.is_valid()) {
      return Optional.empty();
    }

    if (this.elgamal_public_key.isEmpty()) {
      return Optional.empty();
    }

    return Optional.of(new DescriptionAndContext(this.description,
            CiphertextElectionContext.create(
              this.number_of_guardians,
              this.quorum,
              this.elgamal_public_key.get(),
              this.description,
              commitment_hash == null ? rand_q() : commitment_hash)));
  }

}
