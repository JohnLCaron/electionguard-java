package com.sunya.electionguard.proto;

import com.sunya.electionguard.ChaumPedersen;
import com.sunya.electionguard.DecryptionShare;
import com.sunya.electionguard.Tally;
import com.sunya.electionguard.publish.PlaintextTallyPojo;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.sunya.electionguard.proto.CommonConvert.convertElementModP;
import static com.sunya.electionguard.proto.CommonConvert.convertElementModQ;
import static com.sunya.electionguard.proto.CommonConvert.convertCiphertext;
import static com.sunya.electionguard.proto.PlaintextProto.PlaintextTally;
import static com.sunya.electionguard.proto.PlaintextProto.PlaintextTallyContest;
import static com.sunya.electionguard.proto.PlaintextProto.PlaintextTallyContestMap;
import static com.sunya.electionguard.proto.PlaintextProto.PlaintextTallySelection;
import static com.sunya.electionguard.proto.PlaintextProto.CiphertextDecryptionSelection;
import static com.sunya.electionguard.proto.PlaintextProto.CiphertextCompensatedDecryptionSelection;
import static com.sunya.electionguard.proto.PlaintextProto.ChaumPedersenProof;

public class PlaintextTallyToProto {

  public static PlaintextTally translateToProto(Tally.PlaintextTally tally) {
    PlaintextTally.Builder builder = PlaintextTally.newBuilder();
    builder.setObjectId(tally.object_id());
    for (Map.Entry<String, Tally.PlaintextTallyContest> contest : tally.contests().entrySet()) {
      builder.putContests(contest.getKey(), convertContest(contest.getValue()));
    }
    for (Map.Entry<String, Map<String, Tally.PlaintextTallyContest>> spoiled : tally.spoiled_ballots().entrySet()) {
      builder.putSpoiledBallots(spoiled.getKey(), convertSpoiled(spoiled.getValue()));
    }
    return builder.build();
  }

  static PlaintextTallyContestMap convertSpoiled(Map<String, Tally.PlaintextTallyContest> spoiledMap) {
    PlaintextTallyContestMap.Builder builder = PlaintextTallyContestMap.newBuilder();
    for (Map.Entry<String, Tally.PlaintextTallyContest> selection : spoiledMap.entrySet()) {
      builder.putContests(selection.getKey(), convertContest(selection.getValue()));
    }
    return builder.build();
  }

  static PlaintextTallyContest convertContest(Tally.PlaintextTallyContest contest) {
    PlaintextTallyContest.Builder builder = PlaintextTallyContest.newBuilder();
    builder.setObjectId(contest.object_id());
    for (Map.Entry<String, Tally.PlaintextTallySelection> selection : contest.selections().entrySet()) {
      builder.putSelections(selection.getKey(), convertSelection(selection.getValue()));
    }
    return builder.build();
  }

  static PlaintextTallySelection convertSelection(Tally.PlaintextTallySelection selection) {

    List<PlaintextProto.CiphertextDecryptionSelection> shares =
            selection.shares().stream().map(PlaintextTallyToProto::convertShare).collect(Collectors.toList());

    PlaintextTallySelection.Builder builder = PlaintextTallySelection.newBuilder();
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
    builder.setDescriptionHash(convertElementModQ(org.description_hash()));
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
    builder.setDescriptionHash(convertElementModQ(org.description_hash()));
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
}
