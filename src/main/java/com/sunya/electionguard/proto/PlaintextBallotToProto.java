package com.sunya.electionguard.proto;

import com.sunya.electionguard.PlaintextBallot;

import static com.sunya.electionguard.proto.PlaintextBallotProto.PlaintextBallotContest;
import static com.sunya.electionguard.proto.PlaintextBallotProto.PlaintextBallotSelection;
import static com.sunya.electionguard.proto.PlaintextBallotProto.ExtendedData;

public class PlaintextBallotToProto {

  public static PlaintextBallotProto.PlaintextBallot translateToProto(PlaintextBallot ballot) {
    PlaintextBallotProto.PlaintextBallot.Builder builder = PlaintextBallotProto.PlaintextBallot.newBuilder();
    builder.setObjectId(ballot.object_id);
    builder.setStyleId(ballot.style_id);
    ballot.contests.forEach(value -> builder.addContests(convertContest(value)));
    return builder.build();
  }

  static PlaintextBallotContest convertContest(PlaintextBallot.Contest contest) {
    PlaintextBallotContest.Builder builder = PlaintextBallotContest.newBuilder();
    builder.setContestId(contest.contest_id);
    contest.ballot_selections.forEach(value -> builder.addBallotSelections(convertSelection(value)));
    return builder.build();
  }

  static PlaintextBallotSelection convertSelection(PlaintextBallot.Selection selection) {
    PlaintextBallotSelection.Builder builder = PlaintextBallotSelection.newBuilder();
    builder.setSelectionId(selection.selection_id);
    builder.setVote(selection.vote);
    builder.setIsPlaceholderSelection(selection.is_placeholder_selection);
    selection.extended_data.ifPresent(value -> builder.setExtendedData(convertExtendedData(value)));
    return builder.build();
  }

  static ExtendedData convertExtendedData(PlaintextBallot.ExtendedData data) {
    ExtendedData.Builder builder = ExtendedData.newBuilder();
    builder.setValue(data.value);
    builder.setLength(data.length);
    return builder.build();

  }
}
