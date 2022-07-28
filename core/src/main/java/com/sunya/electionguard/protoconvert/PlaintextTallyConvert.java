package com.sunya.electionguard.protoconvert;

import com.sunya.electionguard.DecryptionShare;
import com.sunya.electionguard.PlaintextTally;
import electionguard.protogen.PlaintextTallyProto;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static com.sunya.electionguard.protoconvert.CommonConvert.publishCiphertext;
import static com.sunya.electionguard.protoconvert.CommonConvert.publishElementModP;

public class PlaintextTallyConvert {

  public static PlaintextTally importPlaintextTally(PlaintextTallyProto.PlaintextTally tally) {
    Map<String, PlaintextTally.Contest> contests = tally.getContestsList().stream()
            .collect(Collectors.toMap(t -> t.getContestId(), t -> importContest(t)));

    return new PlaintextTally(
            tally.getTallyId(),
            contests);
  }

  static PlaintextTally.Contest importContest(PlaintextTallyProto.PlaintextTallyContest proto) {
    Map<String, PlaintextTally.Selection> selections = proto.getSelectionsList().stream()
            .collect(Collectors.toMap(s -> s.getSelectionId(),
                    s -> importSelection(s)));

    return new PlaintextTally.Contest(
            proto.getContestId(),
            selections);
  }

  static PlaintextTally.Selection importSelection(PlaintextTallyProto.PlaintextTallySelection proto) {
    List<DecryptionShare.CiphertextDecryptionSelection> shares = proto.getPartialDecryptionsList().stream()
            .map(share -> importShare(proto.getSelectionId(), share))
            .toList();

    return new PlaintextTally.Selection(
            proto.getSelectionId(),
            proto.getTally(),
            CommonConvert.importElementModP(proto.getValue()),
            CommonConvert.importCiphertext(proto.getMessage()),
            shares);
  }

  private static DecryptionShare.CiphertextDecryptionSelection importShare(String selectionId, PlaintextTallyProto.PartialDecryption proto) {
    Map<String, DecryptionShare.CiphertextCompensatedDecryptionSelection> recovered = new HashMap<>();
    if (proto.hasRecoveredParts()) {
      for (PlaintextTallyProto.RecoveredPartialDecryption part : proto.getRecoveredParts().getFragmentsList()) {
        recovered.put(part.getDecryptingGuardianId(), importCompensatedShare(selectionId, part));
      }
    }

    return new DecryptionShare.CiphertextDecryptionSelection(
            proto.getSelectionId(),
            proto.getGuardianId(),
            CommonConvert.importElementModP(proto.getShare()),
            proto.hasProof() ? Optional.of(CommonConvert.importChaumPedersenProof(proto.getProof())) : Optional.empty(),
            proto.hasRecoveredParts() ? Optional.of(recovered) : Optional.empty());
  }

  private static DecryptionShare.CiphertextCompensatedDecryptionSelection importCompensatedShare(
          String selectionId, PlaintextTallyProto.RecoveredPartialDecryption proto) {

    return new DecryptionShare.CiphertextCompensatedDecryptionSelection(
            selectionId,
            proto.getDecryptingGuardianId(),
            proto.getMissingGuardianId(),
            CommonConvert.importElementModP(proto.getShare()),
            CommonConvert.importElementModP(proto.getRecoveryKey()),
            CommonConvert.importChaumPedersenProof(proto.getProof()));
  }

  /////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

  public static PlaintextTallyProto.PlaintextTally publishPlaintextTally(PlaintextTally tally) {
    PlaintextTallyProto.PlaintextTally.Builder builder = PlaintextTallyProto.PlaintextTally.newBuilder();
    builder.setTallyId(tally.tallyId);
    for (Map.Entry<String, PlaintextTally.Contest> entry : tally.contests.entrySet()) {
      builder.addContests(publishContest(entry.getValue()));
    }
    return builder.build();
  }

  static PlaintextTallyProto.PlaintextTallyContest publishContest(PlaintextTally.Contest contest) {
    PlaintextTallyProto.PlaintextTallyContest.Builder builder = PlaintextTallyProto.PlaintextTallyContest.newBuilder();
    builder.setContestId(contest.contestId());
    for (Map.Entry<String, PlaintextTally.Selection> selection : contest.selections().entrySet()) {
      builder.addSelections(publishSelection(selection.getValue()));
    }
    return builder.build();
  }

  static PlaintextTallyProto.PlaintextTallySelection publishSelection(PlaintextTally.Selection selection) {

    List<PlaintextTallyProto.PartialDecryption> shares = selection.shares().stream()
            .map(PlaintextTallyConvert::publishShare)
            .toList();

    PlaintextTallyProto.PlaintextTallySelection.Builder builder = PlaintextTallyProto.PlaintextTallySelection.newBuilder();
    builder.setSelectionId(selection.selectionId());
    builder.setTally(selection.tally());
    builder.setValue(publishElementModP(selection.value()));
    builder.setMessage(publishCiphertext(selection.message()));
    builder.addAllPartialDecryptions(shares);
    return builder.build();
  }

  private static PlaintextTallyProto.PartialDecryption publishShare(DecryptionShare.CiphertextDecryptionSelection org) {
    PlaintextTallyProto.PartialDecryption.Builder builder = PlaintextTallyProto.PartialDecryption.newBuilder();

    builder.setSelectionId(org.selectionId());
    builder.setGuardianId(org.guardianId());
    builder.setShare(publishElementModP(org.share()));
    // OneOf
    org.proof().ifPresent( proof -> builder.setProof(CommonConvert.publishChaumPedersenProof(proof)));
    org.recoveredParts().ifPresent(org_recoverd ->  {
      PlaintextTallyProto.RecoveredParts.Builder partsBuilder = PlaintextTallyProto.RecoveredParts.newBuilder();
      for (Map.Entry<String, DecryptionShare.CiphertextCompensatedDecryptionSelection> entry : org_recoverd.entrySet()) {
        partsBuilder.addFragments(publishCompensatedShare(entry.getValue()));
      }
      builder.setRecoveredParts(partsBuilder);
    });
    return builder.build();
  }

  private static PlaintextTallyProto.RecoveredPartialDecryption publishCompensatedShare(
          DecryptionShare.CiphertextCompensatedDecryptionSelection org) {

    PlaintextTallyProto.RecoveredPartialDecryption.Builder builder = PlaintextTallyProto.RecoveredPartialDecryption.newBuilder();
    builder.setDecryptingGuardianId(org.guardianId());
    builder.setMissingGuardianId(org.missing_guardian_id());
    builder.setShare(publishElementModP(org.share()));
    builder.setRecoveryKey(publishElementModP(org.recoveryKey()));
    builder.setProof(CommonConvert.publishChaumPedersenProof(org.proof()));
    return builder.build();
  }

}
