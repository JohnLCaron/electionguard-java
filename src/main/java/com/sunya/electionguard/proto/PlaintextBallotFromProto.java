package com.sunya.electionguard.proto;

import com.sunya.electionguard.Ballot;

import static com.sunya.electionguard.proto.CommonConvert.convertList;
import static com.sunya.electionguard.proto.PlaintextBallotProto.ExtendedData;
import static com.sunya.electionguard.proto.PlaintextBallotProto.PlaintextBallot;
import static com.sunya.electionguard.proto.PlaintextBallotProto.PlaintextBallotContest;
import static com.sunya.electionguard.proto.PlaintextBallotProto.PlaintextBallotSelection;

public class PlaintextBallotFromProto {

  public static Ballot.PlaintextBallot translateFromProto(PlaintextBallot ballot) {
    return new Ballot.PlaintextBallot(
            ballot.getObjectId(),
            ballot.getBallotStyle(),
            convertList(ballot.getContestsList(), PlaintextBallotFromProto::convertContest));
  }

  static Ballot.PlaintextBallotContest convertContest(PlaintextBallotContest contest) {
    return new Ballot.PlaintextBallotContest(
            contest.getContestId(),
            convertList(contest.getBallotSelectionsList(), PlaintextBallotFromProto::convertSelection));
  }

  static Ballot.PlaintextBallotSelection convertSelection(PlaintextBallotSelection selection) {
    return new Ballot.PlaintextBallotSelection(
            selection.getSelectionId(),
            selection.getVote(),
            selection.getIsPlaceholderSelection(),
            selection.hasExtendedData() ? convertExtendedData(selection.getExtendedData()) : null);
  }

  static Ballot.ExtendedData convertExtendedData(ExtendedData data) {
    return new Ballot.ExtendedData(
            data.getValue(),
            data.getLength());
  }
}
