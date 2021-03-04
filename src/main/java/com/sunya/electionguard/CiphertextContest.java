package com.sunya.electionguard;

import java.util.ArrayList;

/**
 * Base encrypted contest for both CiphertextTally and CiphertextBallot.
 */
class CiphertextContest {
  final String object_id;
  final Group.ElementModQ description_hash;
  final Iterable<CiphertextSelection> selections;

  public CiphertextContest(String object_id, Group.ElementModQ description_hash, Iterable<CiphertextSelection> selections) {
    this.object_id = object_id;
    this.description_hash = description_hash;
    this.selections = selections;
  }

  static CiphertextContest createFrom(CiphertextTally.Contest tallyContest) {
    return new CiphertextContest(tallyContest.object_id, tallyContest.contestDescriptionHash,
            new ArrayList<>(tallyContest.selections.values()));
  }

  static CiphertextContest createFrom(CiphertextBallot.Contest ballotContest) {
    return new CiphertextContest(ballotContest.object_id, ballotContest.contest_hash,
            new ArrayList<>(ballotContest.ballot_selections));
  }
}
