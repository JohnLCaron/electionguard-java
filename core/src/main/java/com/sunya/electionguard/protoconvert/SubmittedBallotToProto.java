package com.sunya.electionguard.protoconvert;

import com.sunya.electionguard.BallotBox;
import com.sunya.electionguard.ChaumPedersen;
import com.sunya.electionguard.SubmittedBallot;
import com.sunya.electionguard.CiphertextBallot;

import static com.sunya.electionguard.protoconvert.CommonConvert.publishChaumPedersenProof;
import static com.sunya.electionguard.protoconvert.CommonConvert.publishCiphertext;
import static com.sunya.electionguard.protoconvert.CommonConvert.publishElementModQ;
import static com.sunya.electionguard.protoconvert.CommonConvert.publishElementModP;
import static com.sunya.electionguard.protoconvert.CommonConvert.publishHashedCiphertext;

import electionguard.protogen.CiphertextBallotProto;

public class SubmittedBallotToProto {

  public static CiphertextBallotProto.SubmittedBallot translateToProto(SubmittedBallot ballot) {
    CiphertextBallotProto.SubmittedBallot.Builder builder = CiphertextBallotProto.SubmittedBallot.newBuilder();
    builder.setBallotId(ballot.object_id());
    builder.setBallotStyleId(ballot.ballotStyleId);
    builder.setManifestHash(CommonConvert.publishUInt256fromQ(ballot.manifestHash));
    builder.setCode(CommonConvert.publishUInt256fromQ(ballot.code));
    builder.setCodeSeed(CommonConvert.publishUInt256fromQ(ballot.code_seed));
    ballot.contests.forEach(value -> builder.addContests(convertContest(value)));
    builder.setTimestamp(ballot.timestamp);
    builder.setCryptoHash(CommonConvert.publishUInt256fromQ(ballot.crypto_hash));
    builder.setState(convertBallotState(ballot.state));
    return builder.build();
  }

  static CiphertextBallotProto.SubmittedBallot.BallotState convertBallotState(BallotBox.State type) {
    return CiphertextBallotProto.SubmittedBallot.BallotState.valueOf(type.name());
  }

  static CiphertextBallotProto.CiphertextBallotContest convertContest(CiphertextBallot.Contest contest) {
    CiphertextBallotProto.CiphertextBallotContest.Builder builder = CiphertextBallotProto.CiphertextBallotContest.newBuilder();
    builder.setContestId(contest.contestId);
    builder.setSequenceOrder(contest.sequence_order());
    builder.setContestHash(CommonConvert.publishUInt256fromQ(contest.contestHash));
    contest.selections.forEach(value -> builder.addSelections(convertSelection(value)));
    builder.setCryptoHash(CommonConvert.publishUInt256fromQ(contest.crypto_hash));
    contest.proof.ifPresent(value -> builder.setProof(convertConstantProof(value)));
    return builder.build();
  }

  static CiphertextBallotProto.CiphertextBallotSelection convertSelection(CiphertextBallot.Selection selection) {
    CiphertextBallotProto.CiphertextBallotSelection.Builder builder = CiphertextBallotProto.CiphertextBallotSelection.newBuilder();
    builder.setSelectionId(selection.object_id());
    builder.setSequenceOrder(selection.sequence_order());
    builder.setSelectionHash(CommonConvert.publishUInt256fromQ(selection.description_hash()));
    builder.setCiphertext(publishCiphertext(selection.ciphertext()));
    builder.setCryptoHash(CommonConvert.publishUInt256fromQ(selection.crypto_hash));
    builder.setIsPlaceholderSelection(selection.is_placeholder_selection);
    selection.proof.ifPresent(value -> builder.setProof(convertDisjunctiveProof(value)));
    selection.extended_data.ifPresent(value -> builder.setExtendedData(publishHashedCiphertext(value)));
    return builder.build();
  }

  static CiphertextBallotProto.ConstantChaumPedersenProof convertConstantProof(ChaumPedersen.ConstantChaumPedersenProof proof) {
    CiphertextBallotProto.ConstantChaumPedersenProof.Builder builder = CiphertextBallotProto.ConstantChaumPedersenProof.newBuilder();
    builder.setPad(publishElementModP(proof.pad));
    builder.setData(publishElementModP(proof.data));
    builder.setChallenge(publishElementModQ(proof.challenge));
    builder.setResponse(publishElementModQ(proof.response));
    builder.setConstant(proof.constant);
    return builder.build();
  }

  static CiphertextBallotProto.DisjunctiveChaumPedersenProof convertDisjunctiveProof(ChaumPedersen.DisjunctiveChaumPedersenProof proof) {
    CiphertextBallotProto.DisjunctiveChaumPedersenProof.Builder builder = CiphertextBallotProto.DisjunctiveChaumPedersenProof.newBuilder();
    builder.setProof0(publishChaumPedersenProof(proof.proof0));
    builder.setProof1(publishChaumPedersenProof(proof.proof1));
    builder.setChallenge(publishElementModQ(proof.challenge));
    return builder.build();
  }
}
