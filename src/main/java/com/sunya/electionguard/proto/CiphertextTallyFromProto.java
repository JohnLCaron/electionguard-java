package com.sunya.electionguard.proto;

import com.sunya.electionguard.Tally;

import java.util.Map;
import java.util.stream.Collectors;

import static com.sunya.electionguard.proto.CiphertextProto.PublishedCiphertextTally;
import static com.sunya.electionguard.proto.CommonConvert.convertCiphertext;
import static com.sunya.electionguard.proto.CommonConvert.convertElementModQ;

public class CiphertextTallyFromProto {

  public static Tally.PublishedCiphertextTally translateFromProto(PublishedCiphertextTally tally) {
    Map<String, Tally.CiphertextTallyContest> contests = tally.getCastMap().entrySet().stream()
            .collect(Collectors.toMap(Map.Entry::getKey,
                    e -> convertContest(e.getValue())));

    return Tally.PublishedCiphertextTally.create(
            tally.getObjectId(), contests);
  }

  static Tally.CiphertextTallyContest convertContest(CiphertextProto.CiphertextTallyContest proto) {
    Map<String, Tally.CiphertextTallySelection> selections = proto.getTallySelectionsMap().entrySet().stream()
            .collect(Collectors.toMap(Map.Entry::getKey,
                    e -> convertSelection(e.getValue())));

    return new Tally.CiphertextTallyContest(
            proto.getObjectId(),
            convertElementModQ(proto.getDescriptionHash()),
            selections);
  }

  static Tally.CiphertextTallySelection convertSelection(CiphertextProto.CiphertextTallySelection proto) {

    return new Tally.CiphertextTallySelection(
            proto.getObjectId(),
            convertElementModQ(proto.getDescriptionHash()),
            convertCiphertext(proto.getCiphertext()));
  }
}
