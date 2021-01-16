package com.sunya.electionguard.verifier;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.sunya.electionguard.Election;
import com.sunya.electionguard.Group;
import com.sunya.electionguard.KeyCeremony;
import com.sunya.electionguard.publish.Consumer;

import javax.annotation.concurrent.Immutable;
import java.io.IOException;
import java.math.BigInteger;
import java.util.List;

/** Global election parameters, contained in constants, context, description, and coefficients files. */
@Immutable
public class ElectionParameters {
  private final Election.ElectionConstants constants;
  private final Election.CiphertextElectionContext context;
  private final Election.ElectionDescription election;
  private final ImmutableList<KeyCeremony.CoefficientValidationSet> coefficients;
  private final ImmutableMap<String, Integer> contest_vote_limits;

  ElectionParameters(Consumer consumer) throws IOException {
    this.constants = consumer.constants();
    this.context = consumer.context();
    this.election = consumer.election();
    this.coefficients = ImmutableList.copyOf(consumer.coefficients());

    int num_guardians = context.number_of_guardians;
    if (num_guardians != this.coefficients.size()) {
      throw new IllegalStateException(String.format("Number of guardians (%d) does not match number of coefficients (%d)",
              num_guardians, this.coefficients.size()));
    }

    ImmutableMap.Builder<String, Integer> builder = ImmutableMap.builder();
      for (Election.ContestDescription contest : election.contests) {
        builder.put(contest.object_id,
                // LOOK why optional? comment says "In n-of-m elections, this will be None."
                contest.votes_allowed.orElseThrow(() -> new IllegalStateException(
                String.format("Contest description %s does not have number of allowed votes", contest.object_id))));
      }
    contest_vote_limits = builder.build();
  }

  public BigInteger generator() {
    return this.constants.generator;
  }

  public BigInteger large_prime() {
    return this.constants.large_prime;
  }

  public BigInteger small_prime() {
    return this.constants.small_prime;
  }

  public BigInteger cofactor() {
    return this.constants.cofactor;
  }

  public Group.ElementModQ description_hash() {
    return this.context.description_hash;
  }

  public Group.ElementModQ extended_hash() {
    return this.context.crypto_extended_base_hash;
  }

  public Group.ElementModQ base_hash() {
    return this.context.crypto_base_hash;
  }

  public Group.ElementModP elgamal_key() {
    return this.context.elgamal_public_key;
  }

  /** return map of guardian_id, public_key. */
  public ImmutableMap<String, Group.ElementModP> public_keys_of_all_guardians() {
    ImmutableMap.Builder<String, Group.ElementModP> result = ImmutableMap.builder();
    for (KeyCeremony.CoefficientValidationSet coeff : this.coefficients) {
      List<Group.ElementModP> cc = coeff.coefficient_commitments();
      Preconditions.checkArgument(!cc.isEmpty());
      result.put(coeff.owner_id(), cc.get(0));
    }
    return result.build();
  }

  public Election.ElectionDescription description() {
    return this.election;
  }

  public int quorum() {
    return this.context.quorum;
  }

  public ImmutableList<KeyCeremony.CoefficientValidationSet> coefficients() {
    return coefficients;
  }

  public Integer getVoteLimitForContest(String contest_id) {
    return contest_vote_limits.get(contest_id);
  }
}
