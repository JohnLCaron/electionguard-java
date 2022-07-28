package com.sunya.electionguard.protoconvert;

import com.sunya.electionguard.ballot.EncryptedTally;

import java.util.Map;
import java.util.stream.Collectors;

import electionguard.protogen.EncryptedTallyProto;

import static com.sunya.electionguard.protoconvert.CommonConvert.publishCiphertext;


public class EncryptedTallyConvert {

  public static EncryptedTally importEncryptedTally(EncryptedTallyProto.EncryptedTally tally) {
    Map<String, EncryptedTally.Contest> contests = tally.getContestsList().stream()
            .collect(Collectors.toMap(t -> t.getContestId(), t -> convertContest(t)));

    return new EncryptedTally(
            tally.getTallyId(), contests);
  }

  static EncryptedTally.Contest convertContest(EncryptedTallyProto.EncryptedTallyContest proto) {
    Map<String, EncryptedTally.Selection> selections = proto.getSelectionsList().stream()
            .collect(Collectors.toMap(t -> t.getSelectionId(), t -> convertSelection(t)));

    return new EncryptedTally.Contest(
            proto.getContestId(),
            proto.getSequenceOrder(),
            CommonConvert.importUInt256toQ(proto.getContestDescriptionHash()),
            selections);
  }

  static EncryptedTally.Selection convertSelection(EncryptedTallyProto.EncryptedTallySelection proto) {

    return new EncryptedTally.Selection(
            proto.getSelectionId(),
            proto.getSequenceOrder(),
            CommonConvert.importUInt256toQ(proto.getSelectionDescriptionHash()),
            CommonConvert.importCiphertext(proto.getCiphertext()));
  }
  
  ////////////////////////////////////////////////////////////////////////////////////////////

  public static EncryptedTallyProto.EncryptedTally publishEncryptedTally(EncryptedTally tally) {
    EncryptedTallyProto.EncryptedTally.Builder builder = EncryptedTallyProto.EncryptedTally.newBuilder();
    builder.setTallyId(tally.object_id());
    for (Map.Entry<String, EncryptedTally.Contest> contest : tally.contests.entrySet()) {
      builder.addContests(publishContest(contest.getValue()));
    }
    return builder.build();
  }

  static EncryptedTallyProto.EncryptedTallyContest publishContest(EncryptedTally.Contest contest) {
    EncryptedTallyProto.EncryptedTallyContest.Builder builder = EncryptedTallyProto.EncryptedTallyContest.newBuilder();
    builder.setContestId(contest.object_id());
    builder.setSequenceOrder(contest.sequence_order());
    builder.setContestDescriptionHash(CommonConvert.publishUInt256fromQ(contest.contestDescriptionHash));
    for (Map.Entry<String, EncryptedTally.Selection> selection : contest.selections.entrySet()) {
      builder.addSelections(publishSelection(selection.getValue()));
    }
    return builder.build();
  }

  static EncryptedTallyProto.EncryptedTallySelection publishSelection(EncryptedTally.Selection selection) {
    EncryptedTallyProto.EncryptedTallySelection.Builder builder = EncryptedTallyProto.EncryptedTallySelection.newBuilder();
    builder.setSelectionId(selection.object_id());
    builder.setSequenceOrder(selection.sequence_order());
    builder.setSelectionDescriptionHash(CommonConvert.publishUInt256fromQ(selection.description_hash()));
    builder.setCiphertext(publishCiphertext(selection.ciphertext()));
    return builder.build();
  }
}
