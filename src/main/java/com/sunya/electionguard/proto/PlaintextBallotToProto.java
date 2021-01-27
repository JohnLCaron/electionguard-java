package com.sunya.electionguard.proto;

import com.sunya.electionguard.Ballot;

import static com.sunya.electionguard.proto.PlaintextBallotProto.PlaintextBallot;
import static com.sunya.electionguard.proto.PlaintextBallotProto.PlaintextBallotContest;
import static com.sunya.electionguard.proto.PlaintextBallotProto.PlaintextBallotSelection;
import static com.sunya.electionguard.proto.PlaintextBallotProto.ExtendedData;

public class PlaintextBallotToProto {

  public static PlaintextBallot translateToProto(Ballot.PlaintextBallot ballot) {
    PlaintextBallot.Builder builder = PlaintextBallot.newBuilder();
    builder.setObjectId(ballot.object_id);
    builder.setBallotStyle(ballot.ballot_style);
    ballot.contests.forEach(value -> builder.addContests(convertContest(value)));
    return builder.build();
  }

  static PlaintextBallotContest convertContest(Ballot.PlaintextBallotContest contest) {
    PlaintextBallotContest.Builder builder = PlaintextBallotContest.newBuilder();
    builder.setContestId(contest.contest_id);
    contest.ballot_selections.forEach(value -> builder.addBallotSelections(convertSelection(value)));
    return builder.build();
  }

  static PlaintextBallotSelection convertSelection(Ballot.PlaintextBallotSelection selection) {
    PlaintextBallotSelection.Builder builder = PlaintextBallotSelection.newBuilder();
    builder.setSelectionId(selection.selection_id);
    builder.setVote(selection.vote);
    builder.setIsPlaceholderSelection(selection.is_placeholder_selection);
    selection.extended_data.ifPresent(value -> builder.setExtendedData(convertExtendedData(value)));
    return builder.build();
  }

  static ExtendedData convertExtendedData(Ballot.ExtendedData data) {
    ExtendedData.Builder builder = ExtendedData.newBuilder();
    builder.setValue(data.value);
    builder.setLength(data.length);
    return builder.build();

  }
}
