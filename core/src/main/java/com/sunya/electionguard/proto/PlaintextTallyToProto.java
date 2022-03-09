package com.sunya.electionguard.proto;

import com.sunya.electionguard.DecryptionShare;
import com.sunya.electionguard.PlaintextTally;

import java.util.List;
import java.util.Map;

import static com.sunya.electionguard.proto.CommonConvert.convertElementModP;
import static com.sunya.electionguard.proto.CommonConvert.convertCiphertext;

import electionguard.protogen.PlaintextTallyProto;


public class PlaintextTallyToProto {

  public static PlaintextTallyProto.PlaintextTally translateToProto(PlaintextTally tally) {
    PlaintextTallyProto.PlaintextTally.Builder builder = PlaintextTallyProto.PlaintextTally.newBuilder();
    builder.setTallyId(tally.tallyId);
    for (Map.Entry<String, PlaintextTally.Contest> entry : tally.contests.entrySet()) {
      builder.addContests(convertContest(entry.getValue()));
    }
    return builder.build();
  }

  static PlaintextTallyProto.PlaintextTallyContest convertContest(PlaintextTally.Contest contest) {
    PlaintextTallyProto.PlaintextTallyContest.Builder builder = PlaintextTallyProto.PlaintextTallyContest.newBuilder();
    builder.setContestId(contest.contestId());
    for (Map.Entry<String, PlaintextTally.Selection> selection : contest.selections().entrySet()) {
      builder.addSelections(convertSelection(selection.getValue()));
    }
    return builder.build();
  }

  static PlaintextTallyProto.PlaintextTallySelection convertSelection(PlaintextTally.Selection selection) {

    List<PlaintextTallyProto.CiphertextDecryptionSelection> shares = selection.shares().stream()
                    .map(PlaintextTallyToProto::convertShare)
                    .toList();

    PlaintextTallyProto.PlaintextTallySelection.Builder builder = PlaintextTallyProto.PlaintextTallySelection.newBuilder();
    builder.setSelectionId(selection.selectionId());
    builder.setTally(selection.tally());
    builder.setValue(convertElementModP(selection.value()));
    builder.setMessage(convertCiphertext(selection.message()));
    builder.addAllShares(shares);
    return builder.build();
  }

  private static PlaintextTallyProto.CiphertextDecryptionSelection convertShare(DecryptionShare.CiphertextDecryptionSelection org) {
    PlaintextTallyProto.CiphertextDecryptionSelection.Builder builder = PlaintextTallyProto.CiphertextDecryptionSelection.newBuilder();

    builder.setSelectionId(org.selectionId());
    builder.setGuardianId(org.guardianId());
    builder.setShare(convertElementModP(org.share()));
    // OneOf
    org.proof().ifPresent( proof -> builder.setProof(CommonConvert.convertChaumPedersenProof(proof)));
    org.recoveredParts().ifPresent(org_recoverd ->  {
      PlaintextTallyProto.RecoveredParts.Builder partsBuilder = PlaintextTallyProto.RecoveredParts.newBuilder();
      for (Map.Entry<String, DecryptionShare.CiphertextCompensatedDecryptionSelection> entry : org_recoverd.entrySet()) {
        partsBuilder.addFragments(convertCompensatedShare(entry.getValue()));
      }
      builder.setRecoveredParts(partsBuilder);
    });
    return builder.build();
  }

  private static PlaintextTallyProto.CiphertextCompensatedDecryptionSelection convertCompensatedShare(
          DecryptionShare.CiphertextCompensatedDecryptionSelection org) {

    PlaintextTallyProto.CiphertextCompensatedDecryptionSelection.Builder builder = PlaintextTallyProto.CiphertextCompensatedDecryptionSelection.newBuilder();
    builder.setSelectionId(org.selectionId());
    builder.setGuardianId(org.guardianId());
    builder.setMissingGuardianId(org.missing_guardian_id());
    builder.setShare(convertElementModP(org.share()));
    builder.setRecoveryKey(convertElementModP(org.recoveryKey()));
    builder.setProof(CommonConvert.convertChaumPedersenProof(org.proof()));
    return builder.build();
  }
}
