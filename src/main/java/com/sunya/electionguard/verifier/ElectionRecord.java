package com.sunya.electionguard.verifier;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.sunya.electionguard.CiphertextAcceptedBallot;
import com.sunya.electionguard.CiphertextElectionContext;
import com.sunya.electionguard.ElectionConstants;
import com.sunya.electionguard.Election;
import com.sunya.electionguard.Encrypt;
import com.sunya.electionguard.Group;
import com.sunya.electionguard.KeyCeremony;
import com.sunya.electionguard.CiphertextTally;
import com.sunya.electionguard.PlaintextBallot;
import com.sunya.electionguard.PlaintextTally;
import com.sunya.electionguard.publish.CloseableIterable;
import com.sunya.electionguard.publish.CloseableIterableAdapter;
import com.sunya.electionguard.publish.CloseableIterator;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;
import java.io.IOException;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** The published election record for a collection of ballots, eg from a single encryption device. */
@Immutable
public class ElectionRecord {
  public final ElectionConstants constants;
  public final CiphertextElectionContext context;
  public final Election election;
  public final ImmutableList<KeyCeremony.CoefficientValidationSet> guardianCoefficients;
  public final ImmutableList<Encrypt.EncryptionDevice> devices;
  public final CloseableIterable<CiphertextAcceptedBallot> acceptedBallots; // All ballots, not just cast!
  @Nullable public final CiphertextTally ciphertextTally;
  @Nullable public final PlaintextTally decryptedTally;
  public final CloseableIterable<PlaintextBallot> spoiledBallots;
  public final CloseableIterable<PlaintextTally> spoiledTallies;

  private final ImmutableMap<String, Integer> contest_vote_limits;

  public ElectionRecord(ElectionConstants constants,
                        CiphertextElectionContext context,
                        Election election,
                        List<KeyCeremony.CoefficientValidationSet> guardianCoefficients,
                        @Nullable List<Encrypt.EncryptionDevice> devices,
                        @Nullable CiphertextTally ciphertextTally,
                        @Nullable PlaintextTally decryptedTally,
                        @Nullable CloseableIterable<CiphertextAcceptedBallot> acceptedBallots,
                        @Nullable CloseableIterable<PlaintextBallot> spoiledBallots,
                        @Nullable CloseableIterable<PlaintextTally> spoiledTallies) {
    this.constants = constants;
    this.context = context;
    this.election = election;
    this.guardianCoefficients = ImmutableList.copyOf(guardianCoefficients);
    this.devices = devices == null ? ImmutableList.of() : ImmutableList.copyOf(devices);
    this.acceptedBallots = acceptedBallots == null ? CloseableIterableAdapter.empty() : acceptedBallots;
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

  public ElectionRecord setBallots(CloseableIterable<CiphertextAcceptedBallot> acceptedBallots,
                                   CloseableIterable<PlaintextBallot> spoiledBallots,
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

  public List<PlaintextTally> spoiledTalliesAsList() {
    List<PlaintextTally> result = new ArrayList<>();
    try (CloseableIterator<PlaintextTally> iter = spoiledTallies.iterator()) {
      while(iter.hasNext()) {
        result.add(iter.next());
      }
    } catch (IOException e) {
      throw new RuntimeException();
    }
    return result;
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
