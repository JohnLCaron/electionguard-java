package com.sunya.electionguard.workflow;

import com.sunya.electionguard.Manifest;
import com.sunya.electionguard.PlaintextBallot;

import java.util.ArrayList;

public class TestBallotProvider {

  public static PlaintextBallot makeBallot(Manifest election, String ballotStyle, int contestIdx, int selectionIdx) {
    ArrayList<PlaintextBallot.Contest> contests = new ArrayList<>();
    contests.add(makeContest(election.contests().get(contestIdx), selectionIdx));
    return new PlaintextBallot("id", ballotStyle, contests, null);
  }

  public static PlaintextBallot.Contest makeContest(Manifest.ContestDescription contest, int selectionIdx) {
    ArrayList<PlaintextBallot.Selection> selections = new ArrayList<>();
    selections.add(makeSelection(contest.selections().get(selectionIdx)));

    return new PlaintextBallot.Contest(
            contest.contestId(),
            contest.sequenceOrder(),
            selections);
  }

  public static PlaintextBallot.Selection makeSelection(Manifest.SelectionDescription selection) {
    return new PlaintextBallot.Selection(
            selection.selectionId(), selection.sequenceOrder(),
            1, null);
  }
}
