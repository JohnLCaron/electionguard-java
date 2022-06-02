package com.sunya.electionguard.protoconvert;

import com.sunya.electionguard.PlaintextBallot;

import static com.sunya.electionguard.protoconvert.CommonConvert.convertList;

import electionguard.protogen.PlaintextBallotProto;

public class PlaintextBallotFromProto {

  public static PlaintextBallot translateFromProto(PlaintextBallotProto.PlaintextBallot ballot) {
    return new PlaintextBallot(
            ballot.getBallotId(),
            ballot.getBallotStyleId(),
            convertList(ballot.getContestsList(), PlaintextBallotFromProto::convertContest),
            ballot.getErrors().isEmpty() ? null : ballot.getErrors()
            );
  }

  static PlaintextBallot.Contest convertContest(PlaintextBallotProto.PlaintextBallotContest contest) {
    return new PlaintextBallot.Contest(
            contest.getContestId(),
            contest.getSequenceOrder(),
            convertList(contest.getSelectionsList(), PlaintextBallotFromProto::convertSelection));
  }

  static PlaintextBallot.Selection convertSelection(PlaintextBallotProto.PlaintextBallotSelection selection) {
    return new PlaintextBallot.Selection(
            selection.getSelectionId(),
            selection.getSequenceOrder(),
            selection.getVote(),
            selection.getExtendedData());
  }

}