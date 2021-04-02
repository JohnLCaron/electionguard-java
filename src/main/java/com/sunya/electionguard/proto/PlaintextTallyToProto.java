package com.sunya.electionguard.proto;

import com.sunya.electionguard.DecryptionShare;
import com.sunya.electionguard.PlaintextTally;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.sunya.electionguard.proto.CommonConvert.convertElementModP;
import static com.sunya.electionguard.proto.CommonConvert.convertCiphertext;
import static com.sunya.electionguard.proto.PlaintextTallyProto.CiphertextDecryptionSelection;
import static com.sunya.electionguard.proto.PlaintextTallyProto.CiphertextCompensatedDecryptionSelection;

public class PlaintextTallyToProto {

  public static PlaintextTallyProto.PlaintextTally translateToProto(PlaintextTally tally) {
    PlaintextTallyProto.PlaintextTally.Builder builder = PlaintextTallyProto.PlaintextTally.newBuilder();
    builder.setObjectId(tally.object_id);
    for (Map.Entry<String, PlaintextTally.Contest> entry : tally.contests.entrySet()) {
      builder.putContests(entry.getKey(), convertContest(entry.getValue()));
    }
    return builder.build();
  }

  static PlaintextTallyProto.PlaintextTallyContest convertContest(PlaintextTally.Contest contest) {
    PlaintextTallyProto.PlaintextTallyContest.Builder builder = PlaintextTallyProto.PlaintextTallyContest.newBuilder();
    builder.setObjectId(contest.object_id());
    for (Map.Entry<String, PlaintextTally.Selection> selection : contest.selections().entrySet()) {
      builder.putSelections(selection.getKey(), convertSelection(selection.getValue()));
    }
    return builder.build();
  }

  static PlaintextTallyProto.PlaintextTallySelection convertSelection(PlaintextTally.Selection selection) {

    List<CiphertextDecryptionSelection> shares =
            selection.shares().stream().map(PlaintextTallyToProto::convertShare).collect(Collectors.toList());

    PlaintextTallyProto.PlaintextTallySelection.Builder builder = PlaintextTallyProto.PlaintextTallySelection.newBuilder();
    builder.setObjectId(selection.object_id());
    builder.setTally(selection.tally());
    builder.setValue(convertElementModP(selection.value()));
    builder.setMessage(convertCiphertext(selection.message()));
    builder.addAllShares(shares);
    return builder.build();
  }

  private static CiphertextDecryptionSelection convertShare(DecryptionShare.CiphertextDecryptionSelection org) {
    CiphertextDecryptionSelection.Builder builder = CiphertextDecryptionSelection.newBuilder();

    builder.setObjectId(org.object_id());
    builder.setGuardianId(org.guardian_id());
    builder.setShare(convertElementModP(org.share()));
    // Optional
    org.proof().ifPresent( proof -> builder.setProof(CommonConvert.convertChaumPedersenProof(proof)));
    // Optional
    org.recovered_parts().ifPresent(org_recoverd ->  {
      for (Map.Entry<String, DecryptionShare.CiphertextCompensatedDecryptionSelection> entry : org_recoverd.entrySet()) {
        builder.putRecoveredParts(entry.getKey(), convertCompensatedShare(entry.getValue()));
      }
    });
    return builder.build();
  }

  private static CiphertextCompensatedDecryptionSelection convertCompensatedShare(
          DecryptionShare.CiphertextCompensatedDecryptionSelection org) {

    CiphertextCompensatedDecryptionSelection.Builder builder = CiphertextCompensatedDecryptionSelection.newBuilder();
    builder.setObjectId(org.object_id());
    builder.setGuardianId(org.guardian_id());
    builder.setMissingGuardianId(org.missing_guardian_id());
    builder.setShare(convertElementModP(org.share()));
    builder.setRecoveryKey(convertElementModP(org.recovery_key()));
    builder.setProof(CommonConvert.convertChaumPedersenProof(org.proof()));
    return builder.build();
  }
}
