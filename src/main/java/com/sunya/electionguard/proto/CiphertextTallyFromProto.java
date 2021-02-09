package com.sunya.electionguard.proto;

import com.sunya.electionguard.PublishedCiphertextTally;

import java.util.Map;
import java.util.stream.Collectors;

import static com.sunya.electionguard.proto.CiphertextTallyProto.CiphertextTallyContest;
import static com.sunya.electionguard.proto.CiphertextTallyProto.CiphertextTallySelection;
import static com.sunya.electionguard.proto.CommonConvert.convertCiphertext;
import static com.sunya.electionguard.proto.CommonConvert.convertElementModQ;

public class CiphertextTallyFromProto {

  public static PublishedCiphertextTally translateFromProto(CiphertextTallyProto.PublishedCiphertextTally tally) {
    Map<String, PublishedCiphertextTally.CiphertextTallyContest> contests = tally.getContestsMap().entrySet().stream()
            .collect(Collectors.toMap(Map.Entry::getKey,
                    e -> convertContest(e.getValue())));

    return new PublishedCiphertextTally(
            tally.getObjectId(), contests);
  }

  static PublishedCiphertextTally.CiphertextTallyContest convertContest(CiphertextTallyContest proto) {
    Map<String, PublishedCiphertextTally.CiphertextTallySelection> selections = proto.getTallySelectionsMap().entrySet().stream()
            .collect(Collectors.toMap(Map.Entry::getKey,
                    e -> convertSelection(e.getValue())));

    return new PublishedCiphertextTally.CiphertextTallyContest(
            proto.getObjectId(),
            convertElementModQ(proto.getDescriptionHash()),
            selections);
  }

  static PublishedCiphertextTally.CiphertextTallySelection convertSelection(CiphertextTallySelection proto) {

    return new PublishedCiphertextTally.CiphertextTallySelection(
            proto.getObjectId(),
            convertElementModQ(proto.getDescriptionHash()),
            convertCiphertext(proto.getCiphertext()));
  }
}
