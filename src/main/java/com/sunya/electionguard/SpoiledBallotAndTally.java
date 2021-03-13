package com.sunya.electionguard;

/**
 * A tuple of a decrypted spoiled ballot and its tally.
 */
public class SpoiledBallotAndTally {
  public final PlaintextTally tally;
  public final PlaintextBallot ballot;

  SpoiledBallotAndTally(PlaintextTally tally, PlaintextBallot ballot) {
    this.tally = tally;
    this.ballot = ballot;
  }
}
