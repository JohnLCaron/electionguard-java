package com.sunya.electionguard.workflow;

import com.sunya.electionguard.PlaintextBallot;

/** An interface for providing the input ballots to electionguard. */
public interface BallotProvider {

  Iterable<PlaintextBallot> ballots();

}
