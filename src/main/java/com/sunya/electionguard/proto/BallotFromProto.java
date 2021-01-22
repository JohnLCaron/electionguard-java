package com.sunya.electionguard.proto;

import com.sunya.electionguard.Ballot;
import com.sunya.electionguard.ChaumPedersen;
import com.sunya.electionguard.ElGamal;
import com.sunya.electionguard.Group;

import javax.annotation.Nullable;
import java.math.BigInteger;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.sunya.electionguard.proto.BallotProto.CiphertextAcceptedBallot;
import static com.sunya.electionguard.proto.BallotProto.CiphertextBallot;
import static com.sunya.electionguard.proto.BallotProto.CiphertextBallotContest;
import static com.sunya.electionguard.proto.BallotProto.CiphertextBallotSelection;
import static com.sunya.electionguard.proto.BallotProto.ConstantChaumPedersenProof;
import static com.sunya.electionguard.proto.BallotProto.DisjunctiveChaumPedersenProof;
import static com.sunya.electionguard.proto.BallotProto.ElGamalCiphertext;

public class BallotFromProto {

  public static Ballot.CiphertextAcceptedBallot translate(CiphertextAcceptedBallot ballot) {
    return new Ballot.CiphertextAcceptedBallot(
            translateCiphertextBallot(ballot.getCiphertextBallot()),
            translateBallotBoxState(ballot.getState()));
  }

  @Nullable
  private static <T, U> List<U> convertList(@Nullable List<T> from, Function<T, U> converter) {
    return from == null ? null : from.stream().map(converter).collect(Collectors.toList());
  }

  static Ballot.BallotBoxState translateBallotBoxState(CiphertextAcceptedBallot.BallotBoxState type) {
    return Ballot.BallotBoxState.valueOf(type.name());
  }

  static Ballot.CiphertextBallot translateCiphertextBallot(CiphertextBallot ballot) {
    // String object_id, String ballot_style, ElementModQ description_hash,
    //                            ElementModQ previous_tracking_hash, List<CiphertextBallotContest> contests,
    //                            Optional<ElementModQ> tracking_hash, long timestamp, ElementModQ crypto_hash,
    //                            Optional<ElementModQ> nonce
    return new Ballot.CiphertextBallot(
            ballot.getObjectId(),
            ballot.getBallotStyleId(),
            translateElementModQ(ballot.getDescriptionHash()),
            translateElementModQ(ballot.getPreviousTrackingHash()),
            convertList(ballot.getContestsList(), BallotFromProto::translateContest),
            Optional.ofNullable(translateElementModQ(ballot.getTrackingHash())),
            ballot.getTimestamp(),
            translateElementModQ(ballot.getCryptoHash()),
            Optional.ofNullable(translateElementModQ(ballot.getNonce())));
  }

  @Nullable
  static Group.ElementModQ translateElementModQ(@Nullable CommonProto.ElementModQ modQ) {
    if (modQ == null || modQ.getValue().isEmpty()) {
      return null;
    }
    BigInteger elem = new BigInteger(modQ.getValue().toByteArray());
    return Group.int_to_q_unchecked(elem);
  }

  @Nullable
  static Group.ElementModP translateElementModP(@Nullable CommonProto.ElementModP modP) {
    if (modP == null || modP.getValue().isEmpty()) {
      return null;
    }
    BigInteger elem = new BigInteger(modP.getValue().toByteArray());
    return Group.int_to_p_unchecked(elem);
  }

  static Ballot.CiphertextBallotContest translateContest(CiphertextBallotContest contest) {
    return new Ballot.CiphertextBallotContest(
            contest.getObjectId(),
            translateElementModQ(contest.getDescriptionHash()),
            convertList(contest.getSelectionsList(), BallotFromProto::translateSelection),
            translateElementModQ(contest.getCryptoHash()),
            Optional.ofNullable(translateElementModQ(contest.getNonce())),
            Optional.ofNullable(translateConstantProof(contest.getProof())));
  }

  static Ballot.CiphertextBallotSelection translateSelection(CiphertextBallotSelection selection) {
    return new Ballot.CiphertextBallotSelection(
            selection.getObjectId(),
            translateElementModQ(selection.getDescriptionHash()),
            translateCiphertext(selection.getCiphertext()),
            translateElementModQ(selection.getCryptoHash()),
            selection.getIsPlaceholderSelection(),
            Optional.ofNullable(translateElementModQ(selection.getNonce())),
            Optional.ofNullable(translateDisjunctiveProof(selection.getProof())),
            Optional.ofNullable(translateCiphertext(selection.getExtendedData())));
  }

  @Nullable
  static ElGamal.Ciphertext translateCiphertext(@Nullable ElGamalCiphertext ciphertext) {
    if (ciphertext == null || !ciphertext.hasPad()) {
      return null;
    }
    return new ElGamal.Ciphertext(
            translateElementModP(ciphertext.getPad()),
            translateElementModP(ciphertext.getData())
    );
  }

  @Nullable
  static ChaumPedersen.ConstantChaumPedersenProof translateConstantProof(@Nullable ConstantChaumPedersenProof proof) {
    if (proof == null || !proof.hasPad()) {
      return null;
    }
    return new ChaumPedersen.ConstantChaumPedersenProof(
            translateElementModP(proof.getPad()),
            translateElementModP(proof.getData()),
            translateElementModQ(proof.getChallenge()),
            translateElementModQ(proof.getResponse()),
            proof.getConstant());
  }

  @Nullable
  static ChaumPedersen.DisjunctiveChaumPedersenProof translateDisjunctiveProof(@Nullable DisjunctiveChaumPedersenProof proof) {
    if (proof == null || proof.getProofZeroPad() == null) {
      return null;
    }
    return new ChaumPedersen.DisjunctiveChaumPedersenProof(
            translateElementModP(proof.getProofZeroPad()),
            translateElementModP(proof.getProofZeroData()),
            translateElementModP(proof.getProofOnePad()),
            translateElementModP(proof.getProofOneData()),
            translateElementModQ(proof.getProofZeroChallenge()),
            translateElementModQ(proof.getProofOneChallenge()),
            translateElementModQ(proof.getChallenge()),
            translateElementModQ(proof.getProofZeroResponse()),
            translateElementModQ(proof.getProofOneResponse()));
  }
}
