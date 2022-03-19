package com.sunya.electionguard.proto;

import com.sunya.electionguard.BallotBox;
import com.sunya.electionguard.ChaumPedersen;
import com.sunya.electionguard.SubmittedBallot;
import com.sunya.electionguard.CiphertextBallot;

import static com.sunya.electionguard.proto.CommonConvert.convertCiphertext;
import static com.sunya.electionguard.proto.CommonConvert.convertElementModQ;
import static com.sunya.electionguard.proto.CommonConvert.convertElementModP;
import static com.sunya.electionguard.proto.CommonConvert.convertUInt256;

import electionguard.protogen.CiphertextBallotProto;

public class CiphertextBallotToProto {

  public static CiphertextBallotProto.SubmittedBallot translateToProto(SubmittedBallot ballot) {
    CiphertextBallotProto.SubmittedBallot.Builder builder = CiphertextBallotProto.SubmittedBallot.newBuilder();
    builder.setBallotId(ballot.object_id());
    builder.setBallotStyleId(ballot.ballotStyleId);
    builder.setManifestHash(convertUInt256(ballot.manifestHash));
    builder.setCode(convertUInt256(ballot.code));
    builder.setCodeSeed(convertUInt256(ballot.code_seed));
    ballot.contests.forEach(value -> builder.addContests(convertContest(value)));
    builder.setTimestamp(ballot.timestamp);
    builder.setCryptoHash(convertUInt256(ballot.crypto_hash));
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
    builder.setContestHash(convertUInt256(contest.contestHash));
    contest.selections.forEach(value -> builder.addSelections(convertSelection(value)));
    builder.setCryptoHash(convertUInt256(contest.crypto_hash));
    builder.setCiphertextAccumulation(convertCiphertext(contest.ciphertextAccumulation));
    contest.proof.ifPresent(value -> builder.setProof(convertConstantProof(value)));
    return builder.build();
  }

  static CiphertextBallotProto.CiphertextBallotSelection convertSelection(CiphertextBallot.Selection selection) {
    CiphertextBallotProto.CiphertextBallotSelection.Builder builder = CiphertextBallotProto.CiphertextBallotSelection.newBuilder();
    builder.setSelectionId(selection.object_id());
    builder.setSequenceOrder(selection.sequence_order());
    builder.setSelectionHash(convertUInt256(selection.description_hash()));
    builder.setCiphertext(convertCiphertext(selection.ciphertext()));
    builder.setCryptoHash(convertUInt256(selection.crypto_hash));
    builder.setIsPlaceholderSelection(selection.is_placeholder_selection);
    selection.proof.ifPresent(value -> builder.setProof(convertDisjunctiveProof(value)));
    selection.extended_data.ifPresent(value -> builder.setExtendedData(convertCiphertext(value)));
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
    builder.setProofZeroPad(convertElementModP(proof.proof_zero_pad));
    builder.setProofZeroData(convertElementModP(proof.proof_zero_data));
    builder.setProofOnePad(convertElementModP(proof.proof_one_pad));
    builder.setProofOneData(convertElementModP(proof.proof_one_data));
    builder.setProofZeroChallenge(convertElementModQ(proof.proof_zero_challenge));
    builder.setProofOneChallenge(convertElementModQ(proof.proof_one_challenge));
    builder.setProofZeroResponse(convertElementModQ(proof.proof_zero_response));
    builder.setChallenge(convertElementModQ(proof.challenge));
    builder.setProofOneResponse(convertElementModQ(proof.proof_one_response));
    return builder.build();
  }
}
