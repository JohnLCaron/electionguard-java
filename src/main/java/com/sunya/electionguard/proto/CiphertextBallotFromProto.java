package com.sunya.electionguard.proto;

import com.sunya.electionguard.BallotBox;
import com.sunya.electionguard.ChaumPedersen;
import com.sunya.electionguard.CiphertextAcceptedBallot;
import com.sunya.electionguard.CiphertextBallot;

import javax.annotation.Nullable;
import java.util.Optional;

import static com.sunya.electionguard.proto.CiphertextBallotProto.CiphertextBallotContest;
import static com.sunya.electionguard.proto.CiphertextBallotProto.CiphertextBallotSelection;
import static com.sunya.electionguard.proto.CiphertextBallotProto.ConstantChaumPedersenProof;
import static com.sunya.electionguard.proto.CiphertextBallotProto.DisjunctiveChaumPedersenProof;
import static com.sunya.electionguard.proto.CommonConvert.convertCiphertext;
import static com.sunya.electionguard.proto.CommonConvert.convertElementModQ;
import static com.sunya.electionguard.proto.CommonConvert.convertElementModP;
import static com.sunya.electionguard.proto.CommonConvert.convertList;

public class CiphertextBallotFromProto {

  public static CiphertextAcceptedBallot translateFromProto(CiphertextBallotProto.CiphertextAcceptedBallot ballot) {
    return new CiphertextAcceptedBallot(
            convertCiphertextBallot(ballot.getCiphertextBallot()),
            convertBallotBoxState(ballot.getState()));
  }

  static BallotBox.State convertBallotBoxState(CiphertextBallotProto.CiphertextAcceptedBallot.BallotBoxState type) {
    return BallotBox.State.valueOf(type.name());
  }

  static CiphertextBallot convertCiphertextBallot(CiphertextBallotProto.CiphertextBallot ballot) {
    return new CiphertextBallot(
            ballot.getObjectId(),
            ballot.getBallotStyleId(),
            convertElementModQ(ballot.getDescriptionHash()),
            convertElementModQ(ballot.getPreviousTrackingHash()),
            convertList(ballot.getContestsList(), CiphertextBallotFromProto::convertContest),
            convertElementModQ(ballot.getTrackingHash()),
            ballot.getTimestamp(),
            convertElementModQ(ballot.getCryptoHash()),
            Optional.ofNullable(convertElementModQ(ballot.getNonce())));
  }

  static CiphertextBallot.Contest convertContest(CiphertextBallotContest contest) {
    return new CiphertextBallot.Contest(
            contest.getObjectId(),
            convertElementModQ(contest.getDescriptionHash()),
            convertList(contest.getSelectionsList(), CiphertextBallotFromProto::convertSelection),
            convertElementModQ(contest.getCryptoHash()),
            convertCiphertext(contest.getEncryptedTotal()),
            Optional.ofNullable(convertElementModQ(contest.getNonce())),
            Optional.ofNullable(convertConstantProof(contest.getProof())));
  }

  static CiphertextBallot.Selection convertSelection(CiphertextBallotSelection selection) {
    return new CiphertextBallot.Selection(
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
