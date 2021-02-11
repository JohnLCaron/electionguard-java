package com.sunya.electionguard.proto;

import com.sunya.electionguard.ChaumPedersen;
import com.sunya.electionguard.DecryptionShare;
import com.sunya.electionguard.Group;
import com.sunya.electionguard.PlaintextTally;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.sunya.electionguard.proto.CommonConvert.convertElementModP;
import static com.sunya.electionguard.proto.CommonConvert.convertElementModQ;
import static com.sunya.electionguard.proto.CommonConvert.convertCiphertext;
import static com.sunya.electionguard.proto.CommonConvert.convertList;
import static com.sunya.electionguard.proto.PlaintextTallyProto.CiphertextDecryptionSelection;
import static com.sunya.electionguard.proto.PlaintextTallyProto.CiphertextCompensatedDecryptionSelection;
import static com.sunya.electionguard.proto.PlaintextTallyProto.ChaumPedersenProof;

public class PlaintextTallyToProto {

  public static PlaintextTallyProto.PlaintextTally translateToProto(PlaintextTally tally) {
    PlaintextTallyProto.PlaintextTally.Builder builder = PlaintextTallyProto.PlaintextTally.newBuilder();
    builder.setObjectId(tally.object_id);
    for (Map.Entry<String, PlaintextTally.PlaintextTallyContest> entry : tally.contests.entrySet()) {
      builder.putContests(entry.getKey(), convertContest(entry.getValue()));
    }
    /* for (Map.Entry<String, ImmutableMap<String, PlaintextTally.PlaintextTallyContest>> spoiled : tally.spoiledBallotTally.entrySet()) {
      builder.putSpoiledBallots(spoiled.getKey(), convertSpoiled(spoiled.getValue()));
    } */
    for (Map.Entry<String, Group.ElementModQ> coeff : tally.lagrange_coefficients.entrySet()) {
      builder.putLagrangeCoefficients(coeff.getKey(), convertElementModQ(coeff.getValue()));
    }
    builder.addAllGuardianStates(convertList(tally.guardianStates, PlaintextTallyToProto::convertState));
    return builder.build();
  }

  static PlaintextTallyProto.PlaintextTallyContestMap convertSpoiled(Map<String, PlaintextTally.PlaintextTallyContest> spoiledMap) {
    PlaintextTallyProto.PlaintextTallyContestMap.Builder builder = PlaintextTallyProto.PlaintextTallyContestMap.newBuilder();
    for (Map.Entry<String, PlaintextTally.PlaintextTallyContest> selection : spoiledMap.entrySet()) {
      builder.putContests(selection.getKey(), convertContest(selection.getValue()));
    }
    return builder.build();
  }

  static PlaintextTallyProto.PlaintextTallyContest convertContest(PlaintextTally.PlaintextTallyContest contest) {
    PlaintextTallyProto.PlaintextTallyContest.Builder builder = PlaintextTallyProto.PlaintextTallyContest.newBuilder();
    builder.setObjectId(contest.object_id());
    for (Map.Entry<String, PlaintextTally.PlaintextTallySelection> selection : contest.selections().entrySet()) {
      builder.putSelections(selection.getKey(), convertSelection(selection.getValue()));
    }
    return builder.build();
  }

  static PlaintextTallyProto.PlaintextTallySelection convertSelection(PlaintextTally.PlaintextTallySelection selection) {

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
    org.proof().ifPresent( proof -> builder.setProof(convertProof(proof)));
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
    builder.setProof(convertProof(org.proof()));
    return builder.build();
  }

  private static ChaumPedersenProof convertProof(ChaumPedersen.ChaumPedersenProof proof) {
    ChaumPedersenProof.Builder builder = ChaumPedersenProof.newBuilder();
    builder.setPad(convertElementModP(proof.pad));
    builder.setData(convertElementModP(proof.data));
    builder.setChallenge(convertElementModQ(proof.challenge));
    builder.setResponse(convertElementModQ(proof.response));
    return builder.build();
  }

  private static PlaintextTallyProto.GuardianState convertState(PlaintextTally.GuardianState state) {
    PlaintextTallyProto.GuardianState.Builder builder = PlaintextTallyProto.GuardianState.newBuilder();
    builder.setGuardianId(state.guardian_id());
    builder.setSequence(state.sequence());
    builder.setMissing(state.is_missing());
    return builder.build();
  }
}
