package com.sunya.electionguard.verifier;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.sunya.electionguard.Ballot;
import com.sunya.electionguard.Election;
import com.sunya.electionguard.Encrypt;
import com.sunya.electionguard.Group;
import com.sunya.electionguard.KeyCeremony;
import com.sunya.electionguard.PublishedCiphertextTally;
import com.sunya.electionguard.PlaintextTally;
import com.sunya.electionguard.publish.CloseableIterable;
import com.sunya.electionguard.publish.CloseableIterableAdapter;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;
import java.math.BigInteger;
import java.util.List;
import java.util.Objects;

@Immutable
public class ElectionRecord {
  public final Election.ElectionConstants constants;
  public final Election.CiphertextElectionContext context;
  public final Election.ElectionDescription election;
  public final ImmutableList<KeyCeremony.CoefficientValidationSet> guardianCoefficients;
  public final ImmutableList<Encrypt.EncryptionDevice> devices;
  public final CloseableIterable<Ballot.CiphertextAcceptedBallot> acceptedBallots; // LOOK all ballots, not just cast!
  @Nullable public final PublishedCiphertextTally ciphertextTally;
  @Nullable public final PlaintextTally decryptedTally;
  public final CloseableIterable<Ballot.PlaintextBallot> spoiledBallots;
  public final CloseableIterable<PlaintextTally> spoiledTallies;

  private final ImmutableMap<String, Integer> contest_vote_limits;

  public ElectionRecord(Election.ElectionConstants constants,
                        Election.CiphertextElectionContext context,
                        Election.ElectionDescription election,
                        List<KeyCeremony.CoefficientValidationSet> guardianCoefficients,
                        @Nullable List<Encrypt.EncryptionDevice> devices,
                        @Nullable PublishedCiphertextTally ciphertextTally,
                        @Nullable PlaintextTally decryptedTally,
                        @Nullable CloseableIterable<Ballot.CiphertextAcceptedBallot> castBallots,
                        @Nullable CloseableIterable<Ballot.PlaintextBallot> spoiledBallots,
                        @Nullable CloseableIterable<PlaintextTally> spoiledTallies) {
    this.constants = constants;
    this.context = context;
    this.election = election;
    this.guardianCoefficients = ImmutableList.copyOf(guardianCoefficients);
    this.devices = devices == null ? ImmutableList.of() : ImmutableList.copyOf(devices);
    this.acceptedBallots = castBallots == null ? CloseableIterableAdapter.empty() : castBallots;
    this.ciphertextTally = ciphertextTally;
    this.decryptedTally = decryptedTally;
    this.spoiledBallots = spoiledBallots == null ? CloseableIterableAdapter.empty() : spoiledBallots;
    this.spoiledTallies = spoiledTallies == null ? CloseableIterableAdapter.empty() : spoiledTallies;

    int num_guardians = context.number_of_guardians;
    if (num_guardians != this.guardianCoefficients.size()) {
      throw new IllegalStateException(String.format("Number of guardians (%d) does not match number of coefficients (%d)",
              num_guardians, this.guardianCoefficients.size()));
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

  public ElectionRecord setBallots(CloseableIterable<Ballot.CiphertextAcceptedBallot> acceptedBallots,
                                   CloseableIterable<Ballot.PlaintextBallot> spoiledBallots,
                                   CloseableIterable<PlaintextTally> spoiledBallotTallies
                                   ) {
    return new ElectionRecord(this.constants,
            this.context,
            this.election,
            this.guardianCoefficients,
            this.devices,
            this.ciphertextTally,
            this.decryptedTally,
            acceptedBallots,
            spoiledBallots,
            spoiledBallotTallies
            );
  }

  public BigInteger generator() {
    return this.constants.generator;
  }

  public Group.ElementModP generatorP() {
    return Group.int_to_p_unchecked(this.constants.generator);
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
    for (KeyCeremony.CoefficientValidationSet coeff : this.guardianCoefficients) {
      List<Group.ElementModP> cc = coeff.coefficient_commitments();
      Preconditions.checkArgument(!cc.isEmpty());
      result.put(coeff.owner_id(), cc.get(0));
    }
    return result.build();
  }

  public int quorum() {
    return this.context.quorum;
  }

  public Integer getVoteLimitForContest(String contest_id) {
    return contest_vote_limits.get(contest_id);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    ElectionRecord that = (ElectionRecord) o;
    return constants.equals(that.constants) &&
            context.equals(that.context) &&
            election.equals(that.election) &&
            devices.equals(that.devices) &&
            acceptedBallots.equals(that.acceptedBallots) &&
            guardianCoefficients.equals(that.guardianCoefficients) &&
            Objects.equals(ciphertextTally, that.ciphertextTally) &&
            Objects.equals(decryptedTally, that.decryptedTally) &&
            contest_vote_limits.equals(that.contest_vote_limits);
  }

  @Override
  public int hashCode() {
    return Objects.hash(constants, context, election, devices, acceptedBallots, guardianCoefficients, ciphertextTally,
            decryptedTally, contest_vote_limits);
  }
}
