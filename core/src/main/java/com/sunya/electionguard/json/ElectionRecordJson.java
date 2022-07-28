package com.sunya.electionguard.json;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.sunya.electionguard.ballot.DecryptingGuardian;
import com.sunya.electionguard.BallotBox;
import com.sunya.electionguard.ballot.EncryptedTally;
import com.sunya.electionguard.ElectionConstants;
import com.sunya.electionguard.ElectionCryptoContext;
import com.sunya.electionguard.Encrypt;
import com.sunya.electionguard.Group;
import com.sunya.electionguard.GuardianRecord;
import com.sunya.electionguard.Manifest;
import com.sunya.electionguard.PlaintextTally;
import com.sunya.electionguard.ballot.EncryptedBallot;
import com.sunya.electionguard.publish.CloseableIterable;
import com.sunya.electionguard.publish.CloseableIterableAdapter;
import com.sunya.electionguard.publish.ElectionRecord;
import electionguard.ballot.Guardian;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;
import java.math.BigInteger;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** The published election record for a collection of ballots, eg from a single encryption device. */
@Immutable
public class ElectionRecordJson implements ElectionRecord {
  public static final String currentVersion = "1.2.2";

  public final String protoVersion;
  public final Manifest manifest;
  public final ElectionConstants constants;
  public final ElectionCryptoContext context;
  public final ImmutableList<GuardianRecord> guardianRecords; // may be empty
  public final ImmutableList<Encrypt.EncryptionDevice> devices; // may be empty
  public final CloseableIterable<EncryptedBallot> acceptedBallots; // All ballots, not just cast! // may be empty
  @Nullable public final EncryptedTally ciphertextTally;
  @Nullable public final PlaintextTally decryptedTally;
  public final ImmutableList<DecryptingGuardian> availableGuardians; // may be empty
  public final ImmutableMap<String, Integer> contestVoteLimits;
  public CloseableIterable<PlaintextTally> spoiledBallotTallies;

  public ElectionRecordJson(String version,
                            Manifest manifest,
                            ElectionConstants constants,
                            ElectionCryptoContext context,
                            @Nullable List<GuardianRecord> guardianRecords,
                            @Nullable List<Encrypt.EncryptionDevice> devices,
                            @Nullable EncryptedTally encryptedTally,
                            @Nullable PlaintextTally decryptedTally,
                            @Nullable CloseableIterable<EncryptedBallot> acceptedBallots,
                            @Nullable CloseableIterable<PlaintextTally> spoiledBallotTallies,
                            @Nullable List<DecryptingGuardian> availableGuardians) {
    this.protoVersion = version;
    this.manifest = manifest;
    this.constants = constants;
    this.context = context;
    this.guardianRecords = guardianRecords == null ? ImmutableList.of() : ImmutableList.copyOf(guardianRecords);
    this.devices = devices == null ? ImmutableList.of() : ImmutableList.copyOf(devices);
    this.acceptedBallots = acceptedBallots == null ? CloseableIterableAdapter.empty() : acceptedBallots;
    this.ciphertextTally = encryptedTally;
    this.decryptedTally = decryptedTally;
    this.spoiledBallotTallies = spoiledBallotTallies == null ? CloseableIterableAdapter.empty() : spoiledBallotTallies;
    this.availableGuardians = availableGuardians == null ? ImmutableList.of() : ImmutableList.copyOf(availableGuardians);

    ImmutableMap.Builder<String, Integer> builder = ImmutableMap.builder();
    for (Manifest.ContestDescription contest : manifest.contests()) {
      builder.put(contest.contestId(), contest.votesAllowed());
    }
    contestVoteLimits = builder.build();
  }

  public ElectionRecordJson setBallots(CloseableIterable<EncryptedBallot> acceptedBallots,
                                       CloseableIterable<PlaintextTally> spoiledBallots) {
    return new ElectionRecordJson(currentVersion,
            this.manifest,
            this.constants,
            this.context,
            this.guardianRecords,
            this.devices,
            this.ciphertextTally,
            this.decryptedTally,
            acceptedBallots,
            spoiledBallots,
            this.availableGuardians);
  }

  @Override
  public String protoVersion() {
    return protoVersion;
  }

  @Override
  public Manifest manifest() {
    return manifest;
  }

  @Override
  public List<Guardian> guardians() {
    return guardianRecords.stream().map(gr -> new Guardian(gr)).toList();
  }

  @Override
  public Iterable<EncryptedBallot> submittedBallots() {
    return acceptedBallots;
  }

  /** The generator g in the spec. */
  @Override
  public  BigInteger generator() {
    return this.constants.generator;
  }

  /** The generator g in the spec as a ElementModP. */
  @Override
  public  Group.ElementModP generatorP() {
    return Group.int_to_p_unchecked(this.constants.generator);
  }

  /** Large prime p in the spec. */
  @Override
  public  BigInteger largePrime() {
    return this.constants.largePrime;
  }

  /** Small prime q in the spec. */
  @Override
  public  BigInteger smallPrime() {
    return this.constants.smallPrime;
  }

  /** G in the spec. */
  @Override
  public  BigInteger cofactor() {
    return this.constants.cofactor;
  }

  @Override
  public ElectionConstants constants() {
    return this.constants;
  }

  @org.jetbrains.annotations.Nullable
  @Override
  public EncryptedTally ciphertextTally() {
    return ciphertextTally;
  }

  @org.jetbrains.annotations.Nullable
  @Override
  public PlaintextTally decryptedTally() {
    return decryptedTally;
  }

  @Override
  public Iterable<PlaintextTally> spoiledBallotTallies() {
    return spoiledBallotTallies;
  }

  @Override
  public List<DecryptingGuardian> availableGuardians() {
    return availableGuardians;
  }

  @Override
  public Group.ElementModQ extendedHash() {
    return this.context.cryptoExtendedBaseHash;
  }

  @Override
  public Group.ElementModQ baseHash() {
    return this.context.cryptoBaseHash;
  }

  @Override
  public Group.ElementModP electionPublicKey() {
    return this.context.jointPublicKey;
  }

  /** Make a map of guardian_id, guardian's public_key. */
  public ImmutableMap<String, Group.ElementModP> publicKeysOfAllGuardians() {
    ImmutableMap.Builder<String, Group.ElementModP> result = ImmutableMap.builder();
    for (GuardianRecord guardianRecord : this.guardianRecords) {
      result.put(guardianRecord.guardianId(), guardianRecord.guardianPublicKey());
    }
    return result.build();
  }

  public Optional<GuardianRecord> findGuardian(String id) {
    return this.guardianRecords.stream().filter(g -> g.guardianId().equals(id)).findFirst();
  }

  /** Make a map of guardian_id, guardian's public_key. */
  public Iterable<EncryptedBallot> spoiledBallots() {
    ImmutableList.Builder<EncryptedBallot> result = ImmutableList.builder();
    for (EncryptedBallot ballot : this.acceptedBallots) {
      if (ballot.state == BallotBox.State.SPOILED) {
        result.add(ballot);
      }
    }
    return result.build();
  }

  @Override
  public int quorum() {
    return this.context.quorum;
  }

  @Override
  public int numberOfGuardians() {
    return this.context.numberOfGuardians;
  }

  /** Votes allowed for the named contest. */
  public Integer getVoteLimitForContest(String contest_id) {
    return contestVoteLimits.get(contest_id);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    ElectionRecordJson that = (ElectionRecordJson) o;
    return protoVersion.equals(that.protoVersion) && manifest.equals(that.manifest) && constants.equals(that.constants) && context.equals(that.context) && Objects.equals(guardianRecords, that.guardianRecords) && Objects.equals(devices, that.devices) && Objects.equals(acceptedBallots, that.acceptedBallots) && Objects.equals(ciphertextTally, that.ciphertextTally) && Objects.equals(decryptedTally, that.decryptedTally) && Objects.equals(availableGuardians, that.availableGuardians) && Objects.equals(contestVoteLimits, that.contestVoteLimits) && Objects.equals(spoiledBallotTallies, that.spoiledBallotTallies);
  }

  @Override
  public int hashCode() {
    return Objects.hash(protoVersion, manifest, constants, context, guardianRecords, devices, acceptedBallots, ciphertextTally, decryptedTally, availableGuardians, contestVoteLimits, spoiledBallotTallies);
  }
}
