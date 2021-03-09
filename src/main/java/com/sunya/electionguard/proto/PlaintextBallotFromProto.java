package com.sunya.electionguard.proto;

import com.sunya.electionguard.PlaintextBallot;

import static com.sunya.electionguard.proto.CommonConvert.convertList;
import static com.sunya.electionguard.proto.PlaintextBallotProto.ExtendedData;
import static com.sunya.electionguard.proto.PlaintextBallotProto.PlaintextBallotContest;
import static com.sunya.electionguard.proto.PlaintextBallotProto.PlaintextBallotSelection;

public class PlaintextBallotFromProto {

  public static PlaintextBallot translateFromProto(PlaintextBallotProto.PlaintextBallot ballot) {
    return new PlaintextBallot(
            ballot.getObjectId(),
            ballot.getStyleId(),
            convertList(ballot.getContestsList(), PlaintextBallotFromProto::convertContest));
  }

  static PlaintextBallot.Contest convertContest(PlaintextBallotContest contest) {
    return new PlaintextBallot.Contest(
            contest.getContestId(),
            convertList(contest.getBallotSelectionsList(), PlaintextBallotFromProto::convertSelection));
  }

  static PlaintextBallot.Selection convertSelection(PlaintextBallotSelection selection) {
    return new PlaintextBallot.Selection(
            selection.getSelectionId(),
            selection.getVote(),
            selection.getIsPlaceholderSelection(),
            selection.hasExtendedData() ? convertExtendedData(selection.getExtendedData()) : null);
  }

  static PlaintextBallot.ExtendedData convertExtendedData(ExtendedData data) {
    return new PlaintextBallot.ExtendedData(
            data.getValue(),
            data.getLength());
  }
}
