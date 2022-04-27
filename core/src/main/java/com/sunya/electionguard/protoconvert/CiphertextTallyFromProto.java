package com.sunya.electionguard.protoconvert;

import com.sunya.electionguard.CiphertextTally;

import java.util.Map;
import java.util.stream.Collectors;

import electionguard.protogen.CiphertextTallyProto;

public class CiphertextTallyFromProto {

  public static CiphertextTally importCiphertextTally(CiphertextTallyProto.CiphertextTally tally) {
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
            CommonConvert.importUInt256toQ(proto.getContestDescriptionHash()),
            selections);
  }

  static CiphertextTally.Selection convertSelection(CiphertextTallyProto.CiphertextTallySelection proto) {

    return new CiphertextTally.Selection(
            proto.getSelectionId(),
            proto.getSequenceOrder(),
            CommonConvert.importUInt256toQ(proto.getSelectionDescriptionHash()),
            CommonConvert.importCiphertext(proto.getCiphertext()));
  }
}
