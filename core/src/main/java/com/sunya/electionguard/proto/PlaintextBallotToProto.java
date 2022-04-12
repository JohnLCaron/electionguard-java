package com.sunya.electionguard.proto;

import com.sunya.electionguard.PlaintextBallot;

import electionguard.protogen.PlaintextBallotProto;

public class PlaintextBallotToProto {

  public static PlaintextBallotProto.PlaintextBallot translateToProto(PlaintextBallot ballot) {
    PlaintextBallotProto.PlaintextBallot.Builder builder = PlaintextBallotProto.PlaintextBallot.newBuilder();
    builder.setBallotId(ballot.object_id());
    builder.setBallotStyleId(ballot.ballotStyleId);
    ballot.contests.forEach(value -> builder.addContests(convertContest(value)));
    if (ballot.errors != null) {
      builder.setErrors(ballot.errors);
    }
    return builder.build();
  }

  static PlaintextBallotProto.PlaintextBallotContest convertContest(PlaintextBallot.Contest contest) {
    PlaintextBallotProto.PlaintextBallotContest.Builder builder = PlaintextBallotProto.PlaintextBallotContest.newBuilder();
    builder.setContestId(contest.contestId);
    contest.selections.forEach(value -> builder.addSelections(convertSelection(value)));
    return builder.build();
  }

  static PlaintextBallotProto.PlaintextBallotSelection convertSelection(PlaintextBallot.Selection selection) {
    PlaintextBallotProto.PlaintextBallotSelection.Builder builder = PlaintextBallotProto.PlaintextBallotSelection.newBuilder();
    builder.setSelectionId(selection.selectionId);
    builder.setSequenceOrder(selection.sequenceOrder);
    builder.setVote(selection.vote);
    builder.setIsPlaceholderSelection(selection.isPlaceholderSelection);
    selection.extendedData.ifPresent(value -> builder.setExtendedData(convertExtendedData(value)));
    return builder.build();
  }

  static PlaintextBallotProto.ExtendedData convertExtendedData(PlaintextBallot.ExtendedData data) {
    PlaintextBallotProto.ExtendedData.Builder builder = PlaintextBallotProto.ExtendedData.newBuilder();
    builder.setValue(data.value);
    builder.setLength(data.length);
    return builder.build();

  }
}
