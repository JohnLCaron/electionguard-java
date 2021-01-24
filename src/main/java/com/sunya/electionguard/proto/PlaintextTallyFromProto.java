package com.sunya.electionguard.proto;

import com.sunya.electionguard.ChaumPedersen;
import com.sunya.electionguard.DecryptionShare;
import com.sunya.electionguard.Tally;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static com.sunya.electionguard.proto.CommonConvert.convertElementModP;
import static com.sunya.electionguard.proto.PlaintextProto.PlaintextTally;
import static com.sunya.electionguard.proto.PlaintextProto.PlaintextTallyContest;
import static com.sunya.electionguard.proto.PlaintextProto.PlaintextTallyContestMap;
import static com.sunya.electionguard.proto.PlaintextProto.PlaintextTallySelection;
import static com.sunya.electionguard.proto.PlaintextProto.CiphertextDecryptionSelection;
import static com.sunya.electionguard.proto.PlaintextProto.CiphertextCompensatedDecryptionSelection;
import static com.sunya.electionguard.proto.CommonConvert.convertCiphertext;
import static com.sunya.electionguard.proto.CommonConvert.convertElementModQ;

public class PlaintextTallyFromProto {

  public static Tally.PlaintextTally translateFromProto(PlaintextTally tally) {
    Map<String, Tally.PlaintextTallyContest> contests = tally.getContestsMap().entrySet().stream()
            .collect(Collectors.toMap(Map.Entry::getKey,
                    e -> convertContest(e.getValue())));

    Map<String, Map<String, Tally.PlaintextTallyContest>> spoiled = tally.getSpoiledBallotsMap().entrySet().stream()
            .collect(Collectors.toMap(Map.Entry::getKey,
                    e -> convertSpoiled(e.getValue())));

    return Tally.PlaintextTally.create(
            tally.getObjectId(),
            contests,
            spoiled);
  }

  static Map<String, Tally.PlaintextTallyContest> convertSpoiled(PlaintextTallyContestMap spoiledMap) {
    Map<String, Tally.PlaintextTallyContest> result = new HashMap<>();
    for (Map.Entry<String, PlaintextTallyContest> selection : spoiledMap.getContestsMap().entrySet()) {
      result.put(selection.getKey(), convertContest(selection.getValue()));
    }
    return result;
  }

  static Tally.PlaintextTallyContest convertContest(PlaintextTallyContest proto) {
    Map<String, Tally.PlaintextTallySelection> selections = proto.getSelectionsMap().entrySet().stream()
            .collect(Collectors.toMap(Map.Entry::getKey,
                    e -> convertSelection(e.getValue())));

    return Tally.PlaintextTallyContest.create(
            proto.getObjectId(),
            selections);
  }

  static Tally.PlaintextTallySelection convertSelection(PlaintextTallySelection proto) {
    List<DecryptionShare.CiphertextDecryptionSelection> shares = proto.getSharesList().stream()
            .map(PlaintextTallyFromProto::convertShare)
            .collect(Collectors.toList());

    return Tally.PlaintextTallySelection.create(
            proto.getObjectId(),
            proto.getTally(),
            convertElementModP(proto.getValue()),
            convertCiphertext(proto.getMessage()),
            shares);
  }

  private static DecryptionShare.CiphertextDecryptionSelection convertShare(CiphertextDecryptionSelection proto) {
    Map<String, DecryptionShare.CiphertextCompensatedDecryptionSelection> recovered = new HashMap<>();
    for (Map.Entry<String, CiphertextCompensatedDecryptionSelection> entry : proto.getRecoveredPartsMap().entrySet()) {
      recovered.put(entry.getKey(), convertCompensatedShare(entry.getValue()));
    }

    return DecryptionShare.CiphertextDecryptionSelection.create(
            proto.getObjectId(),
            proto.getGuardianId(),
            convertElementModQ(proto.getDescriptionHash()),
            convertElementModP(proto.getShare()),
            proto.hasProof() ? Optional.of(convertProof(proto.getProof())) : Optional.empty(),
            recovered.size() > 0 ? Optional.of(recovered) : Optional.empty());
  }

  private static DecryptionShare.CiphertextCompensatedDecryptionSelection convertCompensatedShare(
          CiphertextCompensatedDecryptionSelection proto) {

    return DecryptionShare.CiphertextCompensatedDecryptionSelection.create(
            proto.getObjectId(),
            proto.getGuardianId(),
            proto.getMissingGuardianId(),
            convertElementModQ(proto.getDescriptionHash()),
            convertElementModP(proto.getShare()),
            convertElementModP(proto.getRecoveryKey()),
            convertProof(proto.getProof()));
  }

  private static ChaumPedersen.ChaumPedersenProof convertProof(PlaintextProto.ChaumPedersenProof proof) {
    return new ChaumPedersen.ChaumPedersenProof(
            convertElementModP(proof.getPad()),
            convertElementModP(proof.getData()),
            convertElementModQ(proof.getChallenge()),
            convertElementModQ(proof.getResponse()));
  }

}
