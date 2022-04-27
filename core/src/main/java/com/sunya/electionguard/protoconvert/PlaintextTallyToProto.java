package com.sunya.electionguard.protoconvert;

import com.sunya.electionguard.DecryptionShare;
import com.sunya.electionguard.PlaintextTally;

import java.util.List;
import java.util.Map;

import static com.sunya.electionguard.protoconvert.CommonConvert.publishElementModP;
import static com.sunya.electionguard.protoconvert.CommonConvert.publishCiphertext;

import electionguard.protogen.PlaintextTallyProto;


public class PlaintextTallyToProto {

  public static PlaintextTallyProto.PlaintextTally publishPlaintextTally(PlaintextTally tally) {
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

    List<PlaintextTallyProto.PartialDecryption> shares = selection.shares().stream()
                    .map(PlaintextTallyToProto::convertShare)
                    .toList();

    PlaintextTallyProto.PlaintextTallySelection.Builder builder = PlaintextTallyProto.PlaintextTallySelection.newBuilder();
    builder.setSelectionId(selection.selectionId());
    builder.setTally(selection.tally());
    builder.setValue(publishElementModP(selection.value()));
    builder.setMessage(publishCiphertext(selection.message()));
    builder.addAllPartialDecryptions(shares);
    return builder.build();
  }

  private static PlaintextTallyProto.PartialDecryption convertShare(DecryptionShare.CiphertextDecryptionSelection org) {
    PlaintextTallyProto.PartialDecryption.Builder builder = PlaintextTallyProto.PartialDecryption.newBuilder();

    builder.setSelectionId(org.selectionId());
    builder.setGuardianId(org.guardianId());
    builder.setShare(publishElementModP(org.share()));
    // OneOf
    org.proof().ifPresent( proof -> builder.setProof(CommonConvert.publishChaumPedersenProof(proof)));
    org.recoveredParts().ifPresent(org_recoverd ->  {
      PlaintextTallyProto.RecoveredParts.Builder partsBuilder = PlaintextTallyProto.RecoveredParts.newBuilder();
      for (Map.Entry<String, DecryptionShare.CiphertextCompensatedDecryptionSelection> entry : org_recoverd.entrySet()) {
        partsBuilder.addFragments(convertCompensatedShare(entry.getValue()));
      }
      builder.setRecoveredParts(partsBuilder);
    });
    return builder.build();
  }

  private static PlaintextTallyProto.RecoveredPartialDecryption convertCompensatedShare(
          DecryptionShare.CiphertextCompensatedDecryptionSelection org) {

    PlaintextTallyProto.RecoveredPartialDecryption.Builder builder = PlaintextTallyProto.RecoveredPartialDecryption.newBuilder();
    builder.setSelectionId(org.selectionId());
    builder.setGuardianId(org.guardianId());
    builder.setMissingGuardianId(org.missing_guardian_id());
    builder.setShare(publishElementModP(org.share()));
    builder.setRecoveryKey(publishElementModP(org.recoveryKey()));
    builder.setProof(CommonConvert.publishChaumPedersenProof(org.proof()));
    return builder.build();
  }
}
