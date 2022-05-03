package com.sunya.electionguard.publish;

import com.sunya.electionguard.AvailableGuardian;
import com.sunya.electionguard.CiphertextTally;
import com.sunya.electionguard.ElectionConstants;
import com.sunya.electionguard.Group;
import com.sunya.electionguard.Manifest;
import com.sunya.electionguard.PlaintextTally;
import com.sunya.electionguard.SubmittedBallot;
import electionguard.ballot.DecryptionResult;
import electionguard.ballot.ElectionConfig;
import electionguard.ballot.ElectionInitialized;
import electionguard.ballot.Guardian;
import electionguard.ballot.TallyResult;

import javax.annotation.concurrent.Immutable;
import java.math.BigInteger;
import java.util.List;

import static java.util.Collections.emptyList;

/** The published election record for a collection of ballots, eg from a single encryption device. */
@Immutable
public class ElectionRecordFromProto implements ElectionRecord {
  private Consumer consumer;
  private final ElectionConfig config;
  private ElectionInitialized init;
  private TallyResult tally;
  private DecryptionResult decryption;

  public ElectionRecordFromProto(ElectionConfig config) {
    this.config = config;
  }

  public ElectionRecordFromProto(ElectionInitialized init, Consumer consumer) {
    this.init = init;
    this.config =  init.getConfig();
    this.consumer = consumer;
  }

  public ElectionRecordFromProto(TallyResult tally, Consumer consumer) {
    this.tally = tally;
    this.init = tally.getElectionIntialized();
    this.config =  init.getConfig();
    this.consumer = consumer;
  }

  public ElectionRecordFromProto(DecryptionResult decryption, Consumer consumer) {
    this.decryption = decryption;
    this.tally = decryption.getTallyResult();
    this.init = tally.getElectionIntialized();
    this.config =  init.getConfig();
    this.consumer = consumer;
  }

  /** The generator g in the spec. */
  @Override
  public  BigInteger generator() {
    return new BigInteger(this.config.getConstants().getGenerator());
  }

  /** The generator g in the spec as a ElementModP. */
  @Override
  public  Group.ElementModP generatorP() {
    return Group.int_to_p_unchecked(generator());
  }

  /** Large prime p in the spec. */
  @Override
  public  BigInteger largePrime() {
    return new BigInteger(this.config.getConstants().getLargePrime());
  }

  /** Small prime q in the spec. */
  @Override
  public  BigInteger smallPrime() {
    return new BigInteger(this.config.getConstants().getSmallPrime());
  }

  /** G in the spec. */
  @Override
  public  BigInteger cofactor() {
    return new BigInteger(this.config.getConstants().getCofactor());
  }

  @Override
  public ElectionConstants constants() {
    electionguard.ballot.ElectionConstants c = this.config.getConstants();
    return new ElectionConstants(
            c.getName(),
            new BigInteger(c.getLargePrime()),
            new BigInteger(c.getSmallPrime()),
            new BigInteger(c.getCofactor()),
            new BigInteger(c.getGenerator()));
  }

  @Override
  public String protoVersion() {
    return config.getProtoVersion();
  }

  @Override
  public Manifest manifest() {
    return config.getManifest();
  }

  @Override
  public int numberOfGuardians() {
    return this.config.getNumberOfGuardians();
  }

  @Override
  public int quorum() {
    return this.config.getQuorum();
  }

  @Override
  public List<Guardian> guardians() {
    return this.init == null ? emptyList() : this.init.getGuardians();
  }

  @Override
  public Group.ElementModQ extendedHash() {
    return this.init == null ? null : this.init.getCryptoExtendedBaseHash().toModQ();
  }

  @Override
  public Group.ElementModQ baseHash() { // LOOK wrong
    return this.init == null ? null : this.init.getCryptoExtendedBaseHash().toModQ();
  }

  /** Joint election public key, K in the spec. */
  @Override
  public Group.ElementModP electionPublicKey() {
    return this.init == null ? null : this.init.getJointPublicKey();
  }

  @org.jetbrains.annotations.Nullable
  @Override
  public CiphertextTally ciphertextTally() {
    return this.tally == null ? null : this.tally.getCiphertextTally();
  }

  @org.jetbrains.annotations.Nullable
  @Override
  public PlaintextTally decryptedTally() {
    return this.decryption == null ? null : this.decryption.getDecryptedTally();
  }

  @Override
  public List<AvailableGuardian> availableGuardians() {
    return this.decryption == null ? null : this.decryption.getAvailableGuardians();
  }

  @Override
  public Iterable<SubmittedBallot> submittedBallots() {
    return this.consumer == null ? emptyList() : consumer.iterateSubmittedBallots();
  }

  @Override
  public Iterable<PlaintextTally> spoiledBallotTallies() {
    return this.consumer == null ? emptyList() : consumer.iterateSpoiledBallotTallies();
  }
}
