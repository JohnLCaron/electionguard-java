package com.sunya.electionguard.proto;

import com.sunya.electionguard.DecryptionShare;
import com.sunya.electionguard.PlaintextTally;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static com.sunya.electionguard.proto.CommonConvert.convertElementModP;
import static com.sunya.electionguard.proto.CommonConvert.convertCiphertext;

import com.sunya.electionguard.protogen.PlaintextTallyProto;


public class PlaintextTallyFromProto {

  public static PlaintextTally translateFromProto(PlaintextTallyProto.PlaintextTally tally) {
    Map<String, PlaintextTally.Contest> contests = tally.getContestsMap().entrySet().stream()
            .collect(Collectors.toMap(Map.Entry::getKey, e -> convertContest(e.getValue())));

    return new PlaintextTally(
            tally.getObjectId(),
            contests);
  }

  static PlaintextTally.Contest convertContest(PlaintextTallyProto.PlaintextTallyContest proto) {
    Map<String, PlaintextTally.Selection> selections = proto.getSelectionsMap().entrySet().stream()
            .collect(Collectors.toMap(Map.Entry::getKey,
                    e -> convertSelection(e.getValue())));

    return PlaintextTally.Contest.create(
            proto.getObjectId(),
            selections);
  }

  static PlaintextTally.Selection convertSelection(PlaintextTallyProto.PlaintextTallySelection proto) {
    List<DecryptionShare.CiphertextDecryptionSelection> shares = proto.getSharesList().stream()
            .map(PlaintextTallyFromProto::convertShare)
            .collect(Collectors.toList());

    return PlaintextTally.Selection.create(
            proto.getObjectId(),
            proto.getSequenceOrder(),
            proto.getTally(),
            convertElementModP(proto.getValue()),
            convertCiphertext(proto.getMessage()),
            shares);
  }

  private static DecryptionShare.CiphertextDecryptionSelection convertShare(PlaintextTallyProto.CiphertextDecryptionSelection proto) {
    Map<String, DecryptionShare.CiphertextCompensatedDecryptionSelection> recovered = new HashMap<>();
    for (Map.Entry<String, PlaintextTallyProto.CiphertextCompensatedDecryptionSelection> entry : proto.getRecoveredPartsMap().entrySet()) {
      recovered.put(entry.getKey(), convertCompensatedShare(entry.getValue()));
    }

    return DecryptionShare.CiphertextDecryptionSelection.create(
            proto.getObjectId(),
            proto.getGuardianId(),
            convertElementModP(proto.getShare()),
            proto.hasProof() ? Optional.of(CommonConvert.convertChaumPedersenProof(proto.getProof())) : Optional.empty(),
            recovered.size() > 0 ? Optional.of(recovered) : Optional.empty());
  }

  private static DecryptionShare.CiphertextCompensatedDecryptionSelection convertCompensatedShare(
          PlaintextTallyProto.CiphertextCompensatedDecryptionSelection proto) {

    return DecryptionShare.CiphertextCompensatedDecryptionSelection.create(
            proto.getObjectId(),
            proto.getGuardianId(),
            proto.getMissingGuardianId(),
            convertElementModP(proto.getShare()),
            convertElementModP(proto.getRecoveryKey()),
            CommonConvert.convertChaumPedersenProof(proto.getProof()));
  }

}
