package com.sunya.electionguard.proto;

import com.sunya.electionguard.CiphertextTally;

import java.util.Map;
import java.util.stream.Collectors;

import static com.sunya.electionguard.proto.CommonConvert.convertCiphertext;
import static com.sunya.electionguard.proto.CommonConvert.convertUInt256;

import electionguard.protogen.CiphertextTallyProto;

public class CiphertextTallyFromProto {

  public static CiphertextTally translateFromProto(CiphertextTallyProto.CiphertextTally tally) {
    Map<String, CiphertextTally.Contest> contests = tally.getContestsList().stream()
            .collect(Collectors.toMap(t -> t.getContestId(), t -> convertContest(t)));

    return new CiphertextTally(
            tally.getTallyId(), contests);
  }

  static CiphertextTally.Contest convertContest(CiphertextTallyProto.CiphertextTallyContest proto) {
    Map<String, CiphertextTally.Selection> selections = proto.getSelectionsList().stream()
            .collect(Collectors.toMap(t -> t.getSelectionId(), t -> convertSelection(t)));

    return new CiphertextTally.Contest(
            proto.getContestId(),
            proto.getSequenceOrder(),
            convertUInt256(proto.getContestDescriptionHash()),
            selections);
  }

  static CiphertextTally.Selection convertSelection(CiphertextTallyProto.CiphertextTallySelection proto) {

    return new CiphertextTally.Selection(
            proto.getSelectionId(),
            proto.getSequenceOrder(),
            convertUInt256(proto.getSelectionDescriptionHash()),
            convertCiphertext(proto.getCiphertext()));
  }
}
