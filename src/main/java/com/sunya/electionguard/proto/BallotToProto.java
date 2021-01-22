package com.sunya.electionguard.proto;

import com.google.protobuf.ByteString;
import com.sunya.electionguard.Ballot;
import com.sunya.electionguard.ChaumPedersen;
import com.sunya.electionguard.ElGamal;
import com.sunya.electionguard.Group;

import static com.sunya.electionguard.proto.BallotProto.*;

public class BallotToProto {

  public static CiphertextAcceptedBallot translate(Ballot.CiphertextAcceptedBallot ballot) {
    CiphertextAcceptedBallot.Builder builder = BallotProto.CiphertextAcceptedBallot.newBuilder();
    builder.setCiphertextBallot(translateCiphertextBallot(ballot));
    builder.setState(translateBallotBoxState(ballot.state));
    return builder.build();
  }

  static CiphertextAcceptedBallot.BallotBoxState translateBallotBoxState(Ballot.BallotBoxState type) {
    return CiphertextAcceptedBallot.BallotBoxState.valueOf(type.name());
  }

  static CiphertextBallot translateCiphertextBallot(Ballot.CiphertextBallot ballot) {
    CiphertextBallot.Builder builder = CiphertextBallot.newBuilder();
    builder.setObjectId(ballot.object_id);
    builder.setBallotStyleId(ballot.ballot_style);
    builder.setDescriptionHash(translateElementModQ(ballot.description_hash));
    ballot.tracking_hash.ifPresent(value -> builder.setTrackingHash(translateElementModQ(value)));
    builder.setPreviousTrackingHash(translateElementModQ(ballot.previous_tracking_hash));
    ballot.contests.forEach(value -> builder.addContests(translateContest(value)));
    builder.setTimestamp(ballot.timestamp);
    builder.setCryptoHash(translateElementModQ(ballot.crypto_hash));
    ballot.nonce.ifPresent(value -> builder.setNonce(translateElementModQ(value)));
    return builder.build();
  }

  static CommonProto.ElementModQ translateElementModQ(Group.ElementModQ modQ) {
    CommonProto.ElementModQ.Builder builder = CommonProto.ElementModQ.newBuilder();
    byte[] ba = modQ.getBigInt().toByteArray();
    if (ba.length == 0)
      System.out.printf("HEY");
    builder.setValue(ByteString.copyFrom(modQ.getBigInt().toByteArray()));
    return builder.build();
  }

  static CommonProto.ElementModP translateElementModP(Group.ElementModP modP) {
    CommonProto.ElementModP.Builder builder = CommonProto.ElementModP.newBuilder();
    byte[] ba = modP.getBigInt().toByteArray();
    if (ba.length == 0)
      System.out.printf("HEY");
    builder.setValue(ByteString.copyFrom(modP.getBigInt().toByteArray()));
    return builder.build();
  }

  static CiphertextBallotContest translateContest(Ballot.CiphertextBallotContest contest) {
    CiphertextBallotContest.Builder builder = CiphertextBallotContest.newBuilder();
    builder.setObjectId(contest.object_id);
    builder.setDescriptionHash(translateElementModQ(contest.description_hash));
    contest.ballot_selections.forEach(value -> builder.addSelections(translateSelection(value)));
    builder.setCryptoHash(translateElementModQ(contest.crypto_hash));
    contest.nonce.ifPresent(value -> builder.setNonce(translateElementModQ(value)));
    contest.proof.ifPresent(value -> builder.setProof(translateConstantProof(value)));
    return builder.build();
  }

  static CiphertextBallotSelection translateSelection(Ballot.CiphertextBallotSelection selection) {
    CiphertextBallotSelection.Builder builder = CiphertextBallotSelection.newBuilder();
    builder.setObjectId(selection.object_id);
    builder.setDescriptionHash(translateElementModQ(selection.description_hash));
    builder.setCiphertext(translateCiphertext(selection.ciphertext()));
    builder.setCryptoHash(translateElementModQ(selection.crypto_hash));
    builder.setIsPlaceholderSelection(selection.is_placeholder_selection);
    selection.nonce.ifPresent(value -> builder.setNonce(translateElementModQ(value)));
    selection.proof.ifPresent(value -> builder.setProof(translateDisjunctiveProof(value)));
    selection.extended_data.ifPresent(value -> builder.setExtendedData(translateCiphertext(value)));
    return builder.build();
  }

  static ElGamalCiphertext translateCiphertext(ElGamal.Ciphertext ciphertext) {
    ElGamalCiphertext.Builder builder = ElGamalCiphertext.newBuilder();
    builder.setPad(translateElementModP(ciphertext.pad));
    builder.setData(translateElementModP(ciphertext.data));
    return builder.build();
  }

  static ConstantChaumPedersenProof translateConstantProof(ChaumPedersen.ConstantChaumPedersenProof proof) {
    ConstantChaumPedersenProof.Builder builder = ConstantChaumPedersenProof.newBuilder();
    builder.setPad(translateElementModP(proof.pad));
    builder.setData(translateElementModP(proof.data));
    builder.setChallenge(translateElementModQ(proof.challenge));
    builder.setResponse(translateElementModQ(proof.response));
    builder.setConstant(proof.constant);
    return builder.build();
  }

  static DisjunctiveChaumPedersenProof translateDisjunctiveProof(ChaumPedersen.DisjunctiveChaumPedersenProof proof) {
    DisjunctiveChaumPedersenProof.Builder builder = DisjunctiveChaumPedersenProof.newBuilder();
    builder.setProofZeroPad(translateElementModP(proof.proof_zero_pad));
    builder.setProofZeroData(translateElementModP(proof.proof_zero_data));
    builder.setProofOnePad(translateElementModP(proof.proof_one_pad));
    builder.setProofOneData(translateElementModP(proof.proof_one_data));
    builder.setProofZeroChallenge(translateElementModQ(proof.proof_zero_challenge));
    builder.setProofOneChallenge(translateElementModQ(proof.proof_one_challenge));
    builder.setProofZeroResponse(translateElementModQ(proof.proof_zero_response));
    builder.setChallenge(translateElementModQ(proof.challenge));
    builder.setProofOneResponse(translateElementModQ(proof.proof_one_response));
    return builder.build();
  }
}
