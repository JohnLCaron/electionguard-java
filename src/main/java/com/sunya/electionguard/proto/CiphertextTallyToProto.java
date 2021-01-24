package com.sunya.electionguard.proto;

import com.sunya.electionguard.Tally;

import java.util.Map;

import static com.sunya.electionguard.proto.CiphertextProto.PublishedCiphertextTally;
import static com.sunya.electionguard.proto.CommonConvert.convertCiphertext;
import static com.sunya.electionguard.proto.CommonConvert.convertElementModQ;

public class CiphertextTallyToProto {

  public static PublishedCiphertextTally translateToProto(Tally.PublishedCiphertextTally tally) {
    PublishedCiphertextTally.Builder builder = PublishedCiphertextTally.newBuilder();
    builder.setObjectId(tally.object_id());
    for (Map.Entry<String, Tally.CiphertextTallyContest> contest : tally.cast().entrySet()) {
      builder.putCast(contest.getKey(), convertContest(contest.getValue()));
    }
    return builder.build();
  }

  static CiphertextProto.CiphertextTallyContest convertContest(Tally.CiphertextTallyContest contest) {
    CiphertextProto.CiphertextTallyContest.Builder builder = CiphertextProto.CiphertextTallyContest.newBuilder();
    builder.setObjectId(contest.object_id);
    builder.setDescriptionHash(convertElementModQ(contest.description_hash()));
    for (Map.Entry<String, Tally.CiphertextTallySelection> selection : contest.tally_selections().entrySet()) {
      builder.putTallySelections(selection.getKey(), convertSelection(selection.getValue()));
    }
    return builder.build();
  }

  static CiphertextProto.CiphertextTallySelection convertSelection(Tally.CiphertextTallySelection selection) {
    CiphertextProto.CiphertextTallySelection.Builder builder = CiphertextProto.CiphertextTallySelection.newBuilder();
    builder.setObjectId(selection.object_id);
    builder.setDescriptionHash(convertElementModQ(selection.description_hash));
    builder.setCiphertext(convertCiphertext(selection.ciphertext()));
    return builder.build();
  }
}
