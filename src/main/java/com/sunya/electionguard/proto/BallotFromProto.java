package com.sunya.electionguard.proto;

import com.sunya.electionguard.Ballot;
import com.sunya.electionguard.ChaumPedersen;

import javax.annotation.Nullable;
import java.util.Optional;

import static com.sunya.electionguard.proto.BallotProto.CiphertextAcceptedBallot;
import static com.sunya.electionguard.proto.BallotProto.CiphertextBallot;
import static com.sunya.electionguard.proto.BallotProto.CiphertextBallotContest;
import static com.sunya.electionguard.proto.BallotProto.CiphertextBallotSelection;
import static com.sunya.electionguard.proto.BallotProto.ConstantChaumPedersenProof;
import static com.sunya.electionguard.proto.BallotProto.DisjunctiveChaumPedersenProof;
import static com.sunya.electionguard.proto.CommonConvert.convertCiphertext;
import static com.sunya.electionguard.proto.CommonConvert.convertElementModQ;
import static com.sunya.electionguard.proto.CommonConvert.convertElementModP;
import static com.sunya.electionguard.proto.CommonConvert.convertList;

public class BallotFromProto {

  public static Ballot.CiphertextAcceptedBallot translateFromProto(CiphertextAcceptedBallot ballot) {
    return new Ballot.CiphertextAcceptedBallot(
            convertCiphertextBallot(ballot.getCiphertextBallot()),
            convertBallotBoxState(ballot.getState()));
  }

  static Ballot.BallotBoxState convertBallotBoxState(CiphertextAcceptedBallot.BallotBoxState type) {
    return Ballot.BallotBoxState.valueOf(type.name());
  }

  static Ballot.CiphertextBallot convertCiphertextBallot(CiphertextBallot ballot) {
    // String object_id, String ballot_style, ElementModQ description_hash,
    //                            ElementModQ previous_tracking_hash, List<CiphertextBallotContest> contests,
    //                            Optional<ElementModQ> tracking_hash, long timestamp, ElementModQ crypto_hash,
    //                            Optional<ElementModQ> nonce
    return new Ballot.CiphertextBallot(
            ballot.getObjectId(),
            ballot.getBallotStyleId(),
            convertElementModQ(ballot.getDescriptionHash()),
            convertElementModQ(ballot.getPreviousTrackingHash()),
            convertList(ballot.getContestsList(), BallotFromProto::convertContest),
            convertElementModQ(ballot.getTrackingHash()),
            ballot.getTimestamp(),
            convertElementModQ(ballot.getCryptoHash()),
            Optional.ofNullable(convertElementModQ(ballot.getNonce())));
  }

  static Ballot.CiphertextBallotContest convertContest(CiphertextBallotContest contest) {
    return new Ballot.CiphertextBallotContest(
            contest.getObjectId(),
            convertElementModQ(contest.getDescriptionHash()),
            convertList(contest.getSelectionsList(), BallotFromProto::convertSelection),
            convertElementModQ(contest.getCryptoHash()),
            Optional.ofNullable(convertElementModQ(contest.getNonce())),
            Optional.ofNullable(convertConstantProof(contest.getProof())));
  }

  static Ballot.CiphertextBallotSelection convertSelection(CiphertextBallotSelection selection) {
    return new Ballot.CiphertextBallotSelection(
            selection.getObjectId(),
            convertElementModQ(selection.getDescriptionHash()),
            convertCiphertext(selection.getCiphertext()),
            convertElementModQ(selection.getCryptoHash()),
            selection.getIsPlaceholderSelection(),
            Optional.ofNullable(convertElementModQ(selection.getNonce())),
            Optional.ofNullable(convertDisjunctiveProof(selection.getProof())),
            Optional.ofNullable(convertCiphertext(selection.getExtendedData())));
  }

  @Nullable
  static ChaumPedersen.ConstantChaumPedersenProof convertConstantProof(@Nullable ConstantChaumPedersenProof proof) {
    if (proof == null || !proof.hasPad()) {
      return null;
    }
    return new ChaumPedersen.ConstantChaumPedersenProof(
            convertElementModP(proof.getPad()),
            convertElementModP(proof.getData()),
            convertElementModQ(proof.getChallenge()),
            convertElementModQ(proof.getResponse()),
            proof.getConstant());
  }

  @Nullable
  static ChaumPedersen.DisjunctiveChaumPedersenProof convertDisjunctiveProof(@Nullable DisjunctiveChaumPedersenProof proof) {
    if (proof == null || proof.getProofZeroPad() == null) {
      return null;
    }
    return new ChaumPedersen.DisjunctiveChaumPedersenProof(
            convertElementModP(proof.getProofZeroPad()),
            convertElementModP(proof.getProofZeroData()),
            convertElementModP(proof.getProofOnePad()),
            convertElementModP(proof.getProofOneData()),
            convertElementModQ(proof.getProofZeroChallenge()),
            convertElementModQ(proof.getProofOneChallenge()),
            convertElementModQ(proof.getChallenge()),
            convertElementModQ(proof.getProofZeroResponse()),
            convertElementModQ(proof.getProofOneResponse()));
  }
}
