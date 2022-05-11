package com.sunya.electionguard.protoconvert;

import com.sunya.electionguard.BallotBox;
import com.sunya.electionguard.ChaumPedersen;
import com.sunya.electionguard.SubmittedBallot;
import com.sunya.electionguard.CiphertextBallot;

import javax.annotation.Nullable;
import java.util.Optional;

import static com.sunya.electionguard.protoconvert.CommonConvert.convertList;

import electionguard.protogen.CiphertextBallotProto;


public class SubmittedBallotFromProto {

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
            CommonConvert.importUInt256toQ(ballot.getManifestHash()),
            CommonConvert.importUInt256toQ(ballot.getCodeSeed()),
            convertList(ballot.getContestsList(), SubmittedBallotFromProto::convertContest),
            CommonConvert.importUInt256toQ(ballot.getCode()),
            ballot.getTimestamp(),
            CommonConvert.importUInt256toQ(ballot.getCryptoHash()),
            convertBallotState(ballot.getState()));
  }

  static BallotBox.State convertBallotState(CiphertextBallotProto.SubmittedBallot.BallotState type) {
    return BallotBox.State.valueOf(type.name());
  }

  static CiphertextBallot.Contest convertContest(CiphertextBallotProto.CiphertextBallotContest contest) {
    return new CiphertextBallot.Contest(
            contest.getContestId(),
            contest.getSequenceOrder(),
            CommonConvert.importUInt256toQ(contest.getContestHash()),
            convertList(contest.getSelectionsList(), SubmittedBallotFromProto::convertSelection),
            CommonConvert.importUInt256toQ(contest.getCryptoHash()),
            Optional.empty(),
            Optional.ofNullable(convertConstantProof(contest.getProof())));
  }

  static CiphertextBallot.Selection convertSelection(CiphertextBallotProto.CiphertextBallotSelection selection) {
    return new CiphertextBallot.Selection(
            selection.getSelectionId(),
            selection.getSequenceOrder(),
            CommonConvert.importUInt256toQ(selection.getSelectionHash()),
            CommonConvert.importCiphertext(selection.getCiphertext()),
            CommonConvert.importUInt256toQ(selection.getCryptoHash()),
            selection.getIsPlaceholderSelection(),
            Optional.empty(),
            Optional.ofNullable(convertDisjunctiveProof(selection.getProof())),
            Optional.ofNullable(CommonConvert.importHashedCiphertext(selection.getExtendedData())));
  }

  @Nullable
  static ChaumPedersen.ConstantChaumPedersenProof convertConstantProof(@Nullable CiphertextBallotProto.ConstantChaumPedersenProof proof) {
    if (proof == null) {
      return null;
    }
    if (proof.hasProof()) {
      electionguard.protogen.CommonProto.GenericChaumPedersenProof gproof = proof.getProof();
      return new ChaumPedersen.ConstantChaumPedersenProof(
              CommonConvert.importElementModQ(gproof.getChallenge()),
              CommonConvert.importElementModQ(gproof.getResponse()),
              proof.getConstant());

    }
    return new ChaumPedersen.ConstantChaumPedersenProof(
            CommonConvert.importElementModP(proof.getPad()),
            CommonConvert.importElementModP(proof.getData()),
            CommonConvert.importElementModQ(proof.getChallenge()),
            CommonConvert.importElementModQ(proof.getResponse()),
            proof.getConstant());
  }

  @Nullable
  static ChaumPedersen.DisjunctiveChaumPedersenProof convertDisjunctiveProof(@Nullable CiphertextBallotProto.DisjunctiveChaumPedersenProof proof) {
    if (proof == null || !proof.hasChallenge()) {
      return null;
    }
    // 2.0
    if (proof.hasProof0() && proof.hasProof1()) {
      return new ChaumPedersen.DisjunctiveChaumPedersenProof(
              CommonConvert.importChaumPedersenProof(proof.getProof0()),
              CommonConvert.importChaumPedersenProof(proof.getProof1()),
              CommonConvert.importElementModQ(proof.getChallenge())
      );
    }
    // 1.0
    return new ChaumPedersen.DisjunctiveChaumPedersenProof(
            CommonConvert.importElementModP(proof.getProofZeroPad()),
            CommonConvert.importElementModP(proof.getProofZeroData()),
            CommonConvert.importElementModP(proof.getProofOnePad()),
            CommonConvert.importElementModP(proof.getProofOneData()),
            CommonConvert.importElementModQ(proof.getProofZeroChallenge()),
            CommonConvert.importElementModQ(proof.getProofOneChallenge()),
            CommonConvert.importElementModQ(proof.getChallenge()),
            CommonConvert.importElementModQ(proof.getProofZeroResponse()),
            CommonConvert.importElementModQ(proof.getProofOneResponse()));
  }
}
