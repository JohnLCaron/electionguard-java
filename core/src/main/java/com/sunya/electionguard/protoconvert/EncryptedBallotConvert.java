package com.sunya.electionguard.protoconvert;

import com.sunya.electionguard.BallotBox;
import com.sunya.electionguard.ChaumPedersen;
import com.sunya.electionguard.ballot.EncryptedBallot;

import javax.annotation.Nullable;
import java.util.Optional;

import electionguard.protogen.EncryptedBallotProto;


import static com.sunya.electionguard.protoconvert.CommonConvert.convertList;
import static com.sunya.electionguard.protoconvert.CommonConvert.publishChaumPedersenProof;
import static com.sunya.electionguard.protoconvert.CommonConvert.publishCiphertext;
import static com.sunya.electionguard.protoconvert.CommonConvert.publishElementModQ;
import static com.sunya.electionguard.protoconvert.CommonConvert.publishHashedCiphertext;

public class EncryptedBallotConvert {

  public static EncryptedBallot importEncryptedBallot(EncryptedBallotProto.EncryptedBallot ballot) {
    return new EncryptedBallot(
            ballot.getBallotId(),
            ballot.getBallotStyleId(),
            CommonConvert.importUInt256toQ(ballot.getManifestHash()),
            CommonConvert.importUInt256toQ(ballot.getCodeSeed()),
            convertList(ballot.getContestsList(), EncryptedBallotConvert::importContest),
            CommonConvert.importUInt256toQ(ballot.getCode()),
            ballot.getTimestamp(),
            CommonConvert.importUInt256toQ(ballot.getCryptoHash()),
            importBallotState(ballot.getState()));
  }

  static BallotBox.State importBallotState(EncryptedBallotProto.EncryptedBallot.BallotState type) {
    return BallotBox.State.valueOf(type.name());
  }

  static EncryptedBallot.Contest importContest(EncryptedBallotProto.EncryptedBallotContest contest) {
    return new EncryptedBallot.Contest(
            contest.getContestId(),
            contest.getSequenceOrder(),
            CommonConvert.importUInt256toQ(contest.getContestHash()),
            convertList(contest.getSelectionsList(), EncryptedBallotConvert::importSelection),
            CommonConvert.importUInt256toQ(contest.getCryptoHash()),
            Optional.empty(),
            Optional.ofNullable(importConstantProof(contest.getProof())));
  }

  static EncryptedBallot.Selection importSelection(EncryptedBallotProto.EncryptedBallotSelection selection) {
    return new EncryptedBallot.Selection(
            selection.getSelectionId(),
            selection.getSequenceOrder(),
            CommonConvert.importUInt256toQ(selection.getSelectionHash()),
            CommonConvert.importCiphertext(selection.getCiphertext()),
            CommonConvert.importUInt256toQ(selection.getCryptoHash()),
            selection.getIsPlaceholderSelection(),
            Optional.empty(),
            Optional.ofNullable(importDisjunctiveProof(selection.getProof())),
            Optional.ofNullable(CommonConvert.importHashedCiphertext(selection.getExtendedData())));
  }

  @Nullable
  static ChaumPedersen.ConstantChaumPedersenProof importConstantProof(@Nullable EncryptedBallotProto.ConstantChaumPedersenProof proof) {
    if (proof == null) {
      return null;
    }
      electionguard.protogen.CommonProto.GenericChaumPedersenProof gproof = proof.getProof();
      return new ChaumPedersen.ConstantChaumPedersenProof(
              CommonConvert.importElementModQ(gproof.getChallenge()),
              CommonConvert.importElementModQ(gproof.getResponse()),
              proof.getConstant());
  }

  @Nullable
  static ChaumPedersen.DisjunctiveChaumPedersenProof importDisjunctiveProof(@Nullable EncryptedBallotProto.DisjunctiveChaumPedersenProof proof) {
    if (proof == null || !proof.hasChallenge()) {
      return null;
    }
      return new ChaumPedersen.DisjunctiveChaumPedersenProof(
              CommonConvert.importChaumPedersenProof(proof.getProof0()),
              CommonConvert.importChaumPedersenProof(proof.getProof1()),
              CommonConvert.importElementModQ(proof.getChallenge())
      );
    }
  
  //////////////////////////////////////////////////////////////////////////////

  public static EncryptedBallotProto.EncryptedBallot publishEncryptedBallot(EncryptedBallot ballot) {
    EncryptedBallotProto.EncryptedBallot.Builder builder = EncryptedBallotProto.EncryptedBallot.newBuilder();
    builder.setBallotId(ballot.object_id());
    builder.setBallotStyleId(ballot.ballotStyleId);
    builder.setManifestHash(CommonConvert.publishUInt256fromQ(ballot.manifestHash));
    builder.setCode(CommonConvert.publishUInt256fromQ(ballot.code));
    builder.setCodeSeed(CommonConvert.publishUInt256fromQ(ballot.code_seed));
    ballot.contests.forEach(value -> builder.addContests(publishContest(value)));
    builder.setTimestamp(ballot.timestamp);
    builder.setCryptoHash(CommonConvert.publishUInt256fromQ(ballot.crypto_hash));
    builder.setState(publishBallotState(ballot.state));
    return builder.build();
  }

  static EncryptedBallotProto.EncryptedBallot.BallotState publishBallotState(BallotBox.State type) {
    return EncryptedBallotProto.EncryptedBallot.BallotState.valueOf(type.name());
  }

  static EncryptedBallotProto.EncryptedBallotContest publishContest(EncryptedBallot.Contest contest) {
    EncryptedBallotProto.EncryptedBallotContest.Builder builder = EncryptedBallotProto.EncryptedBallotContest.newBuilder();
    builder.setContestId(contest.contestId);
    builder.setSequenceOrder(contest.sequence_order());
    builder.setContestHash(CommonConvert.publishUInt256fromQ(contest.contestHash));
    contest.selections.forEach(value -> builder.addSelections(publishSelection(value)));
    builder.setCryptoHash(CommonConvert.publishUInt256fromQ(contest.crypto_hash));
    contest.proof.ifPresent(value -> builder.setProof(publishConstantProof(value)));
    return builder.build();
  }

  static EncryptedBallotProto.EncryptedBallotSelection publishSelection(EncryptedBallot.Selection selection) {
    EncryptedBallotProto.EncryptedBallotSelection.Builder builder = EncryptedBallotProto.EncryptedBallotSelection.newBuilder();
    builder.setSelectionId(selection.object_id());
    builder.setSequenceOrder(selection.sequence_order());
    builder.setSelectionHash(CommonConvert.publishUInt256fromQ(selection.description_hash()));
    builder.setCiphertext(publishCiphertext(selection.ciphertext()));
    builder.setCryptoHash(CommonConvert.publishUInt256fromQ(selection.crypto_hash));
    builder.setIsPlaceholderSelection(selection.is_placeholder_selection);
    selection.proof.ifPresent(value -> builder.setProof(publishDisjunctiveProof(value)));
    selection.extended_data.ifPresent(value -> builder.setExtendedData(publishHashedCiphertext(value)));
    return builder.build();
  }

  static EncryptedBallotProto.ConstantChaumPedersenProof publishConstantProof(ChaumPedersen.ConstantChaumPedersenProof proof) {
    EncryptedBallotProto.ConstantChaumPedersenProof.Builder builder = EncryptedBallotProto.ConstantChaumPedersenProof.newBuilder();
    builder.setProof(publishChaumPedersenProof(proof.getProof()));
    builder.setConstant(proof.constant);
    return builder.build();
  }

  static EncryptedBallotProto.DisjunctiveChaumPedersenProof publishDisjunctiveProof(ChaumPedersen.DisjunctiveChaumPedersenProof proof) {
    EncryptedBallotProto.DisjunctiveChaumPedersenProof.Builder builder = EncryptedBallotProto.DisjunctiveChaumPedersenProof.newBuilder();
    builder.setProof0(publishChaumPedersenProof(proof.proof0));
    builder.setProof1(publishChaumPedersenProof(proof.proof1));
    builder.setChallenge(publishElementModQ(proof.challenge));
    return builder.build();
  }
}
