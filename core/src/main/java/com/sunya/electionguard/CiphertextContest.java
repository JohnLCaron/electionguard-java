package com.sunya.electionguard;

import java.util.ArrayList;

/**
 * Base encrypted contest for both CiphertextTally and CiphertextBallot.
 */
public class CiphertextContest {
  public final String object_id;
  public final Group.ElementModQ description_hash;
  public final Iterable<CiphertextSelection> selections;

  public CiphertextContest(String object_id, Group.ElementModQ description_hash, Iterable<CiphertextSelection> selections) {
    this.object_id = object_id;
    this.description_hash = description_hash;
    this.selections = selections;
  }

  public static CiphertextContest createFrom(CiphertextTally.Contest tallyContest) {
    return new CiphertextContest(tallyContest.object_id(), tallyContest.contestDescriptionHash,
            new ArrayList<>(tallyContest.selections.values()));
  }

  public static CiphertextContest createFrom(CiphertextBallot.Contest ballotContest) {
    return new CiphertextContest(ballotContest.contestId, ballotContest.contestHash,
            new ArrayList<>(ballotContest.selections));
  }
}
