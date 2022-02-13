package com.sunya.electionguard.verifier;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.sunya.electionguard.AvailableGuardian;
import com.sunya.electionguard.BallotBox;
import com.sunya.electionguard.GuardianRecord;
import com.sunya.electionguard.Manifest;
import com.sunya.electionguard.SubmittedBallot;
import com.sunya.electionguard.CiphertextElectionContext;
import com.sunya.electionguard.ElectionConstants;
import com.sunya.electionguard.Encrypt;
import com.sunya.electionguard.Group;
import com.sunya.electionguard.CiphertextTally;
import com.sunya.electionguard.PlaintextTally;
import com.sunya.electionguard.publish.CloseableIterable;
import com.sunya.electionguard.publish.CloseableIterableAdapter;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;
import java.math.BigInteger;
import java.util.List;
import java.util.Objects;

/** The published election record for a collection of ballots, eg from a single encryption device. */
@Immutable
public class ElectionRecord {
  public static final String currentVersion = "1.2.2";

  public final String version;
  public final ElectionConstants constants;
  public final CiphertextElectionContext context;
  public final Manifest election;
  public final ImmutableList<GuardianRecord> guardianRecords;
  public final ImmutableList<Encrypt.EncryptionDevice> devices; // may be empty
  public final CloseableIterable<SubmittedBallot> acceptedBallots; // All ballots, not just cast! // may be empty
  @Nullable public final CiphertextTally encryptedTally;
  @Nullable public final PlaintextTally decryptedTally;
  public final CloseableIterable<PlaintextTally> spoiledBallots; // may be empty
  public final ImmutableList<AvailableGuardian> availableGuardians; // may be empty

  private final ImmutableMap<String, Integer> contestVoteLimits;

  public ElectionRecord(String version,
                        ElectionConstants constants,
                        CiphertextElectionContext context,
                        Manifest election,
                        List<GuardianRecord> guardianRecords,
                        @Nullable List<Encrypt.EncryptionDevice> devices,
                        @Nullable CiphertextTally encryptedTally,
                        @Nullable PlaintextTally decryptedTally,
                        @Nullable CloseableIterable<SubmittedBallot> acceptedBallots,
                        @Nullable CloseableIterable<PlaintextTally> spoiledBallots,
                        @Nullable List<AvailableGuardian> availableGuardians) {
    this.version = version;
    this.constants = constants;
    this.context = context;
    this.election = election;
    this.guardianRecords = ImmutableList.copyOf(guardianRecords);
    this.devices = devices == null ? ImmutableList.of() : ImmutableList.copyOf(devices);
    this.acceptedBallots = acceptedBallots == null ? CloseableIterableAdapter.empty() : acceptedBallots;
    this.encryptedTally = encryptedTally;
    this.decryptedTally = decryptedTally;
    this.spoiledBallots = spoiledBallots == null ? CloseableIterableAdapter.empty() : spoiledBallots;
    this.availableGuardians = availableGuardians == null ? ImmutableList.of() : ImmutableList.copyOf(availableGuardians);

    /* int num_guardians = context.number_of_guardians;
    if (num_guardians != this.guardianRecords.size()) {
      throw new IllegalStateException(String.format("Number of guardians (%d) does not match number of coefficients (%d)",
              num_guardians, this.guardianRecords.size()));
    } */

    ImmutableMap.Builder<String, Integer> builder = ImmutableMap.builder();
    for (Manifest.ContestDescription contest : election.contests()) {
      builder.put(contest.object_id(), contest.votes_allowed());
    }
    contestVoteLimits = builder.build();
  }

  public ElectionRecord setBallots(CloseableIterable<SubmittedBallot> acceptedBallots,
                                   CloseableIterable<PlaintextTally> spoiledBallots) {
    return new ElectionRecord(currentVersion,
            this.constants,
            this.context,
            this.election,
            this.guardianRecords,
            this.devices,
            this.encryptedTally,
            this.decryptedTally,
            acceptedBallots,
            spoiledBallots,
            this.availableGuardians);
  }

  /** The generator g in the spec. */
  public BigInteger generator() {
    return this.constants.generator;
  }

  /** The generator g in the spec as a ElementModP. */
  public Group.ElementModP generatorP() {
    return Group.int_to_p_unchecked(this.constants.generator);
  }

  /** Large prime p in the spec. */
  public BigInteger largePrime() {
    return this.constants.large_prime;
  }

  /** Small prime q in the spec. */
  public BigInteger smallPrime() {
    return this.constants.small_prime;
  }

  /** G in the spec. */
  public BigInteger cofactor() {
    return this.constants.cofactor;
  }

  /** Manifest description crypto hash */
  public Group.ElementModQ description_hash() {
    return this.context.manifest_hash;
  }

  /** The extended base hash, Qbar in the spec. */
  public Group.ElementModQ extendedHash() {
    return this.context.crypto_extended_base_hash;
  }

  /** The base hash, Q in the spec. */
  public Group.ElementModQ baseHash() {
    return this.context.crypto_base_hash;
  }

  /** Joint election public key, K in the spec. */
  public Group.ElementModP electionPublicKey() {
    return this.context.elgamal_public_key;
  }

  /** Make a map of guardian_id, guardian's public_key. */
  public ImmutableMap<String, Group.ElementModP> public_keys_of_all_guardians() {
    ImmutableMap.Builder<String, Group.ElementModP> result = ImmutableMap.builder();
    for (GuardianRecord guardianRecord : this.guardianRecords) {
      result.put(guardianRecord.guardian_id(), guardianRecord.election_public_key());
    }
    return result.build();
  }

  /** Make a map of guardian_id, guardian's public_key. */
  public Iterable<SubmittedBallot> spoiledBallots() {
    ImmutableList.Builder<SubmittedBallot> result = ImmutableList.builder();
    for (SubmittedBallot ballot : this.acceptedBallots) {
      if (ballot.state == BallotBox.State.SPOILED) {
        result.add(ballot);
      }
    }
    return result.build();
  }

  /** The quorum of guardians needed to decrypt. */
  public int quorum() {
    return this.context.quorum;
  }

  /** Votes allowed for the named contest. */
  public Integer getVoteLimitForContest(String contest_id) {
    return contestVoteLimits.get(contest_id);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    ElectionRecord that = (ElectionRecord) o;
    return constants.equals(that.constants) &&
            context.equals(that.context) &&
            election.equals(that.election) &&
            Objects.equals(devices, that.devices) &&
            Objects.equals(acceptedBallots, that.acceptedBallots) &&
            Objects.equals(encryptedTally, that.encryptedTally) &&
            Objects.equals(decryptedTally, that.decryptedTally) &&
            Objects.equals(guardianRecords, that.guardianRecords) &&
            Objects.equals(spoiledBallots, that.spoiledBallots) &&
            Objects.equals(contestVoteLimits, that.contestVoteLimits);
  }

  @Override
  public int hashCode() {
    return Objects.hash(constants, context, election, devices, acceptedBallots, encryptedTally, decryptedTally, guardianRecords, spoiledBallots, contestVoteLimits);
  }
}
