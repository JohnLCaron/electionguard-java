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

import electionguard.protogen.PlaintextTallyProto;


public class PlaintextTallyFromProto {

  public static PlaintextTally translateFromProto(PlaintextTallyProto.PlaintextTally tally) {
    Map<String, PlaintextTally.Contest> contests = tally.getContestsList().stream()
            .collect(Collectors.toMap(t -> t.getContestId(), t -> convertContest(t)));

    return new PlaintextTally(
            tally.getTallyId(),
            contests);
  }

  static PlaintextTally.Contest convertContest(PlaintextTallyProto.PlaintextTallyContest proto) {
    Map<String, PlaintextTally.Selection> selections = proto.getSelectionsList().stream()
            .collect(Collectors.toMap(s -> s.getSelectionId(),
                    s -> convertSelection(s)));

    return new PlaintextTally.Contest(
            proto.getContestId(),
            selections);
  }

  static PlaintextTally.Selection convertSelection(PlaintextTallyProto.PlaintextTallySelection proto) {
    List<DecryptionShare.CiphertextDecryptionSelection> shares = proto.getSharesList().stream()
            .map(PlaintextTallyFromProto::convertShare)
            .toList();

    return new PlaintextTally.Selection(
            proto.getSelectionId(),
            proto.getTally(),
            convertElementModP(proto.getValue()),
            convertCiphertext(proto.getMessage()),
            shares);
  }

  private static DecryptionShare.CiphertextDecryptionSelection convertShare(PlaintextTallyProto.CiphertextDecryptionSelection proto) {
    Map<String, DecryptionShare.CiphertextCompensatedDecryptionSelection> recovered = new HashMap<>();
    if (proto.hasRecoveredParts()) {
      for (PlaintextTallyProto.CiphertextCompensatedDecryptionSelection part : proto.getRecoveredParts().getFragmentsList()) {
        recovered.put(part.getGuardianId(), convertCompensatedShare(part));
      }
    }

    return new DecryptionShare.CiphertextDecryptionSelection(
            proto.getSelectionId(),
            proto.getGuardianId(),
            convertElementModP(proto.getShare()),
            proto.hasProof() ? Optional.of(CommonConvert.convertChaumPedersenProof(proto.getProof())) : Optional.empty(),
            proto.hasRecoveredParts() ? Optional.of(recovered) : Optional.empty());
  }

  private static DecryptionShare.CiphertextCompensatedDecryptionSelection convertCompensatedShare(
          PlaintextTallyProto.CiphertextCompensatedDecryptionSelection proto) {

    return new DecryptionShare.CiphertextCompensatedDecryptionSelection(
            proto.getSelectionId(),
            proto.getGuardianId(),
            proto.getMissingGuardianId(),
            convertElementModP(proto.getShare()),
            convertElementModP(proto.getRecoveryKey()),
            CommonConvert.convertChaumPedersenProof(proto.getProof()));
  }

}
