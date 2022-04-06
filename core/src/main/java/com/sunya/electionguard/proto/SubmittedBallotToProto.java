package com.sunya.electionguard.proto;

import com.sunya.electionguard.BallotBox;
import com.sunya.electionguard.ChaumPedersen;
import com.sunya.electionguard.SubmittedBallot;
import com.sunya.electionguard.CiphertextBallot;

import static com.sunya.electionguard.proto.CommonConvert.convertChaumPedersenProof;
import static com.sunya.electionguard.proto.CommonConvert.convertCiphertext;
import static com.sunya.electionguard.proto.CommonConvert.convertElementModQ;
import static com.sunya.electionguard.proto.CommonConvert.convertElementModP;
import static com.sunya.electionguard.proto.CommonConvert.convertHashedCiphertext;
import static com.sunya.electionguard.proto.CommonConvert.convertUInt256fromQ;

import electionguard.protogen.CiphertextBallotProto;

public class SubmittedBallotToProto {

  public static CiphertextBallotProto.SubmittedBallot translateToProto(SubmittedBallot ballot) {
    CiphertextBallotProto.SubmittedBallot.Builder builder = CiphertextBallotProto.SubmittedBallot.newBuilder();
    builder.setBallotId(ballot.object_id());
    builder.setBallotStyleId(ballot.ballotStyleId);
    builder.setManifestHash(CommonConvert.convertUInt256fromQ(ballot.manifestHash));
    builder.setCode(CommonConvert.convertUInt256fromQ(ballot.code));
    builder.setCodeSeed(CommonConvert.convertUInt256fromQ(ballot.code_seed));
    ballot.contests.forEach(value -> builder.addContests(convertContest(value)));
    builder.setTimestamp(ballot.timestamp);
    builder.setCryptoHash(CommonConvert.convertUInt256fromQ(ballot.crypto_hash));
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
    builder.setContestHash(CommonConvert.convertUInt256fromQ(contest.contestHash));
    contest.selections.forEach(value -> builder.addSelections(convertSelection(value)));
    builder.setCryptoHash(CommonConvert.convertUInt256fromQ(contest.crypto_hash));
    builder.setCiphertextAccumulation(convertCiphertext(contest.ciphertextAccumulation));
    contest.proof.ifPresent(value -> builder.setProof(convertConstantProof(value)));
    return builder.build();
  }

  static CiphertextBallotProto.CiphertextBallotSelection convertSelection(CiphertextBallot.Selection selection) {
    CiphertextBallotProto.CiphertextBallotSelection.Builder builder = CiphertextBallotProto.CiphertextBallotSelection.newBuilder();
    builder.setSelectionId(selection.object_id());
    builder.setSequenceOrder(selection.sequence_order());
    builder.setSelectionHash(CommonConvert.convertUInt256fromQ(selection.description_hash()));
    builder.setCiphertext(convertCiphertext(selection.ciphertext()));
    builder.setCryptoHash(CommonConvert.convertUInt256fromQ(selection.crypto_hash));
    builder.setIsPlaceholderSelection(selection.is_placeholder_selection);
    selection.proof.ifPresent(value -> builder.setProof(convertDisjunctiveProof(value)));
    selection.extended_data.ifPresent(value -> builder.setExtendedData(convertHashedCiphertext(value)));
    return builder.build();
  }

  static CiphertextBallotProto.ConstantChaumPedersenProof convertConstantProof(ChaumPedersen.ConstantChaumPedersenProof proof) {
    CiphertextBallotProto.ConstantChaumPedersenProof.Builder builder = CiphertextBallotProto.ConstantChaumPedersenProof.newBuilder();
    builder.setPad(convertElementModP(proof.pad));
    builder.setData(convertElementModP(proof.data));
    builder.setChallenge(convertElementModQ(proof.challenge));
    builder.setResponse(convertElementModQ(proof.response));
    builder.setConstant(proof.constant);
    return builder.build();
  }

  static CiphertextBallotProto.DisjunctiveChaumPedersenProof convertDisjunctiveProof(ChaumPedersen.DisjunctiveChaumPedersenProof proof) {
    CiphertextBallotProto.DisjunctiveChaumPedersenProof.Builder builder = CiphertextBallotProto.DisjunctiveChaumPedersenProof.newBuilder();
    builder.setProof0(convertChaumPedersenProof(proof.proof0));
    builder.setProof1(convertChaumPedersenProof(proof.proof1));
    builder.setChallenge(convertElementModQ(proof.challenge));
    return builder.build();
  }
}
