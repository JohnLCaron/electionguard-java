package com.sunya.electionguard.proto;

import com.sunya.electionguard.CiphertextTally;

import java.util.Map;
import java.util.stream.Collectors;

import static com.sunya.electionguard.proto.CommonConvert.convertCiphertext;
import static com.sunya.electionguard.proto.CommonConvert.convertElementModQ;

import com.sunya.electionguard.protogen.CiphertextTallyProto;

public class CiphertextTallyFromProto {

  public static CiphertextTally translateFromProto(CiphertextTallyProto.CiphertextTally tally) {
    Map<String, CiphertextTally.Contest> contests = tally.getContestsMap().entrySet().stream()
            .collect(Collectors.toMap(Map.Entry::getKey,
                    e -> convertContest(e.getValue())));

    return new CiphertextTally(
            tally.getObjectId(), contests);
  }

  static CiphertextTally.Contest convertContest(CiphertextTallyProto.CiphertextTallyContest proto) {
    Map<String, CiphertextTally.Selection> selections = proto.getTallySelectionsMap().entrySet().stream()
            .collect(Collectors.toMap(Map.Entry::getKey,
                    e -> convertSelection(e.getValue())));

    return new CiphertextTally.Contest(
            proto.getObjectId(),
            convertElementModQ(proto.getDescriptionHash()),
            selections);
  }

  static CiphertextTally.Selection convertSelection(CiphertextTallyProto.CiphertextTallySelection proto) {

    return new CiphertextTally.Selection(
            proto.getObjectId(),
            convertElementModQ(proto.getDescriptionHash()),
            convertCiphertext(proto.getCiphertext()));
  }
}
