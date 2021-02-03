package com.sunya.electionguard.workflow;

import com.sunya.electionguard.Ballot;

public interface BallotProvider {

  Iterable<Ballot.PlaintextBallot> ballots();

}
