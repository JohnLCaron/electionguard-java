package com.sunya.electionguard.proto;

import com.sunya.electionguard.BallotBox;
import com.sunya.electionguard.ChaumPedersen;
import com.sunya.electionguard.SubmittedBallot;
import com.sunya.electionguard.CiphertextBallot;

import javax.annotation.Nullable;
import java.util.Optional;

import static com.sunya.electionguard.proto.CommonConvert.convertCiphertext;
import static com.sunya.electionguard.proto.CommonConvert.convertElementModQ;
import static com.sunya.electionguard.proto.CommonConvert.convertElementModP;
import static com.sunya.electionguard.proto.CommonConvert.convertList;

import electionguard.protogen.CiphertextBallotProto;


public class CiphertextBallotFromProto {

  //           String object_id,
  //                         String style_id,
  //                         Group.ElementModQ manifest_hash,
  //                         Group.ElementModQ code_seed,
  //                         List<Contest> contests,
  //                         Group.ElementModQ code,
  //                         long timestamp,
  //                         Group.ElementModQ crypto_hash,
  //                         BallotBox.State state
  public static SubmittedBallot translateFromProto(CiphertextBallotProto.SubmittedBallot ballot) {
    return new SubmittedBallot(
            ballot.getBallotId(),
            ballot.getBallotStyleId(),
            convertElementModQ(ballot.getManifestHash()),
            convertElementModQ(ballot.getPreviousTrackingHash()),
            convertList(ballot.getContestsList(), CiphertextBallotFromProto::convertContest),
            convertElementModQ(ballot.getTrackingHash()),
            ballot.getTimestamp(),
            convertElementModQ(ballot.getCryptoHash()),
            convertBallotState(ballot.getState()));
  }

  static BallotBox.State convertBallotState(CiphertextBallotProto.SubmittedBallot.BallotState type) {
    return BallotBox.State.valueOf(type.name());
  }

  static CiphertextBallot.Contest convertContest(CiphertextBallotProto.CiphertextBallotContest contest) {
    return new CiphertextBallot.Contest(
            contest.getContestId(),
            contest.getSequenceOrder(),
            convertElementModQ(contest.getContestHash()),
            convertList(contest.getSelectionsList(), CiphertextBallotFromProto::convertSelection),
            convertElementModQ(contest.getCryptoHash()),
            convertCiphertext(contest.getCiphertextAccumulation()),
            Optional.empty(),
            Optional.ofNullable(convertConstantProof(contest.getProof())));
  }

  static CiphertextBallot.Selection convertSelection(CiphertextBallotProto.CiphertextBallotSelection selection) {
    return new CiphertextBallot.Selection(
            selection.getSelectionId(),
            selection.getSequenceOrder(),
            convertElementModQ(selection.getSelectionHash()),
            convertCiphertext(selection.getCiphertext()),
            convertElementModQ(selection.getCryptoHash()),
            selection.getIsPlaceholderSelection(),
            Optional.empty(),
            Optional.ofNullable(convertDisjunctiveProof(selection.getProof())),
            Optional.ofNullable(convertCiphertext(selection.getExtendedData())));
  }

  @Nullable
  static ChaumPedersen.ConstantChaumPedersenProof convertConstantProof(@Nullable CiphertextBallotProto.ConstantChaumPedersenProof proof) {
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
  static ChaumPedersen.DisjunctiveChaumPedersenProof convertDisjunctiveProof(@Nullable CiphertextBallotProto.DisjunctiveChaumPedersenProof proof) {
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
