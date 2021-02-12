package com.sunya.electionguard.workflow;

import com.sunya.electionguard.Ballot;

/** An interface for providing the input ballots to electionguard. */
public interface BallotProvider {

  Iterable<Ballot.PlaintextBallot> ballots();

}
