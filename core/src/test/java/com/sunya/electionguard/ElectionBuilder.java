package com.sunya.electionguard;

import java.util.Optional;

import static com.sunya.electionguard.Group.rand_q;

// LOOK this should go away, keeping it as convenience for now.
public class ElectionBuilder {

  public static class DescriptionAndContext {
    public final InternalManifest internalManifest;
    public final CiphertextElectionContext context;

    public DescriptionAndContext(Manifest election, CiphertextElectionContext context) {
      this.internalManifest = new InternalManifest(election);
      this.context = context;
    }
  }

  int number_of_guardians; // The number of guardians necessary to generate the public key
  int quorum; // The quorum of guardians necessary to decrypt an election.  Must be less than `number_of_guardians`
  Manifest description;
  Group.ElementModQ commitment_hash;
  Optional<Group.ElementModP> election_joint_public_key = Optional.empty();

  public ElectionBuilder(int number_of_guardians, int quorum, Manifest description) {
    this.number_of_guardians = number_of_guardians;
    this.quorum = quorum;
    this.description = description;
  }

  public ElectionBuilder set_public_key(Group.ElementModP election_joint_public_key) {
    this.election_joint_public_key = Optional.of(election_joint_public_key);
    return this;
  }

  public ElectionBuilder set_commitment_hash(Group.ElementModQ commitment_hash) {
    this.commitment_hash = commitment_hash;
    return this;
  }

  public Optional<DescriptionAndContext> build() {
    if (!this.description.is_valid()) {
      return Optional.empty();
    }

    if (this.election_joint_public_key.isEmpty()) {
      return Optional.empty();
    }

    return Optional.of(new DescriptionAndContext(this.description,
            CiphertextElectionContext.create(
              this.number_of_guardians,
              this.quorum,
              this.election_joint_public_key.get(),
              this.description,
              commitment_hash == null ? rand_q() : commitment_hash,
                    null)));
  }

}
