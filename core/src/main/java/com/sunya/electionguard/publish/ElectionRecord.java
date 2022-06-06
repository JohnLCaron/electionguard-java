package com.sunya.electionguard.publish;

import com.sunya.electionguard.AvailableGuardian;
import com.sunya.electionguard.Manifest;
import com.sunya.electionguard.SubmittedBallot;
import com.sunya.electionguard.Group;
import com.sunya.electionguard.CiphertextTally;
import com.sunya.electionguard.PlaintextTally;
import com.sunya.electionguard.ElectionConstants;

import javax.annotation.Nullable;
import java.math.BigInteger;
import java.util.List;

/** The published election record for a collection of ballots, eg from a single encryption device. */
public interface ElectionRecord extends ElectionContext {
  String protoVersion();
  Manifest manifest();
  List<electionguard.ballot.Guardian> guardians();
  Iterable<SubmittedBallot> submittedBallots(); // All ballots, not just cast! // may be empty
  @Nullable CiphertextTally ciphertextTally();
  @Nullable PlaintextTally decryptedTally();
  Iterable<PlaintextTally> spoiledBallotTallies(); // may be empty
  List<AvailableGuardian> availableGuardians(); // may be empty

  /** The extended base hash, Qbar in the spec. */
  Group.ElementModQ extendedHash();

  /** The base hash, Q in the spec. */
  Group.ElementModQ baseHash();

  /** Joint election key, K in the spec. */
  Group.ElementModP electionPublicKey();

  /** The number of guardians in the key ceremony. */
  int numberOfGuardians();

  /** The quorum of guardians needed to decrypt. */
  int quorum();

  /** The generator g in the spec. */
  BigInteger generator();

  /** The generator g in the spec as a ElementModP. */
  Group.ElementModP generatorP();

  /** Large prime p in the spec. */
  BigInteger largePrime();

  /** Small prime q in the spec. */
  BigInteger smallPrime();

  /** G in the spec. */
  BigInteger cofactor();

  ElectionConstants constants();
}
