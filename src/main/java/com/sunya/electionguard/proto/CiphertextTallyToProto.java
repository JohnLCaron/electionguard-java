package com.sunya.electionguard.proto;

import com.sunya.electionguard.CiphertextTally;

import java.util.Map;

import static com.sunya.electionguard.proto.CommonConvert.convertCiphertext;
import static com.sunya.electionguard.proto.CommonConvert.convertElementModQ;

import com.sunya.electionguard.protogen.CiphertextTallyProto;

public class CiphertextTallyToProto {

  public static CiphertextTallyProto.CiphertextTally translateToProto(CiphertextTally tally) {
    CiphertextTallyProto.CiphertextTally.Builder builder = CiphertextTallyProto.CiphertextTally.newBuilder();
    builder.setObjectId(tally.object_id());
    for (Map.Entry<String, CiphertextTally.Contest> contest : tally.contests.entrySet()) {
      builder.putContests(contest.getKey(), convertContest(contest.getValue()));
    }
    return builder.build();
  }

  static CiphertextTallyProto.CiphertextTallyContest convertContest(CiphertextTally.Contest contest) {
    CiphertextTallyProto.CiphertextTallyContest.Builder builder = CiphertextTallyProto.CiphertextTallyContest.newBuilder();
    builder.setObjectId(contest.object_id());
    builder.setSequenceOrder(contest.sequence_order());
    builder.setDescriptionHash(convertElementModQ(contest.contestDescriptionHash));
    for (Map.Entry<String, CiphertextTally.Selection> selection : contest.selections.entrySet()) {
      builder.putTallySelections(selection.getKey(), convertSelection(selection.getValue()));
    }
    return builder.build();
  }

  static CiphertextTallyProto.CiphertextTallySelection convertSelection(CiphertextTally.Selection selection) {
    CiphertextTallyProto.CiphertextTallySelection.Builder builder = CiphertextTallyProto.CiphertextTallySelection.newBuilder();
    builder.setObjectId(selection.object_id());
    builder.setSequenceOrder(selection.sequence_order());
    builder.setDescriptionHash(convertElementModQ(selection.description_hash()));
    builder.setCiphertext(convertCiphertext(selection.ciphertext()));
    return builder.build();
  }
}
