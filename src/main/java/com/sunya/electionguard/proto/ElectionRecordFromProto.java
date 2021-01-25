package com.sunya.electionguard.proto;

import com.google.protobuf.ByteString;
import com.sunya.electionguard.Ballot;
import com.sunya.electionguard.Election;
import com.sunya.electionguard.verifier.ElectionRecord;
import com.sunya.electionguard.Encrypt;
import com.sunya.electionguard.Group;
import com.sunya.electionguard.KeyCeremony;
import com.sunya.electionguard.SchnorrProof;
import com.sunya.electionguard.Tally;

import javax.annotation.concurrent.Immutable;
import java.io.FileInputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.util.List;
import java.util.stream.Collectors;

import static com.sunya.electionguard.proto.CommonConvert.convertElementModP;
import static com.sunya.electionguard.proto.CommonConvert.convertElementModQ;

@Immutable
public class ElectionRecordFromProto {

  public static ElectionRecord read(String filename) throws IOException {
    ElectionRecordProto.ElectionRecord proto;
    try (FileInputStream inp = new FileInputStream(filename)) {
      proto = ElectionRecordProto.ElectionRecord.parseDelimitedFrom(inp);
    }
    return translateFromProto(proto);
  }

  public static ElectionRecord translateFromProto(ElectionRecordProto.ElectionRecord proto) {
    Election.ElectionConstants constants = convertConstants(proto.getConstants());
    Election.CiphertextElectionContext context = convertContext(proto.getContext());
    Election.ElectionDescription description = ElectionDescriptionFromProto.translateFromProto(proto.getElection());
    List<Encrypt.EncryptionDevice> devices =
            proto.getDeviceList().stream().map(ElectionRecordFromProto::convertDevice).collect(Collectors.toList());
    List<Ballot.CiphertextAcceptedBallot> castBallots =
            proto.getCastBallotsList().stream().map(BallotFromProto::translateFromProto).collect(Collectors.toList());
    List<Ballot.CiphertextAcceptedBallot> spoiledBallots =
            proto.getSpoiledBallotsList().stream().map(BallotFromProto::translateFromProto).collect(Collectors.toList());
    List<KeyCeremony.CoefficientValidationSet> guardianCoefficients =
            proto.getGuardianCoefficientsList().stream().map(ElectionRecordFromProto::convertCoefficients).collect(Collectors.toList());
    Tally.PublishedCiphertextTally ciphertextTally = CiphertextTallyFromProto.translateFromProto(proto.getCiphertextTally());
    Tally.PlaintextTally decryptedTally = PlaintextTallyFromProto.translateFromProto(proto.getDecryptedTally());

    return new ElectionRecord(constants, context, description, devices, castBallots, spoiledBallots,
            guardianCoefficients, ciphertextTally, decryptedTally);
  }

  static Election.ElectionConstants convertConstants(ElectionRecordProto.Constants constants) {
    return new Election.ElectionConstants(
            convertBigInteger(constants.getLargePrime()),
            convertBigInteger(constants.getSmallPrime()),
            convertBigInteger(constants.getCofactor()),
            convertBigInteger(constants.getGenerator()));
  }

  static BigInteger convertBigInteger(ByteString bs) {
    return new BigInteger(bs.toByteArray());
  }


  static Election.CiphertextElectionContext convertContext(ElectionRecordProto.ElectionContext context) {
    return new Election.CiphertextElectionContext(
            context.getNumberOfGuardians(),
            context.getQuorum(),
            convertElementModP(context.getElgamalPublicKey()),
            convertElementModQ(context.getDescriptionHash()),
            convertElementModQ(context.getCryptoBaseHash()),
            convertElementModQ(context.getCryptoExtendedBaseHash()));
  }

  static Encrypt.EncryptionDevice convertDevice(ElectionRecordProto.Device device) {
    return new Encrypt.EncryptionDevice(device.getUuid(), device.getLocation());
  }

  static KeyCeremony.CoefficientValidationSet convertCoefficients(KeyCeremonyProto.CoefficientValidationSet coeff) {
   List<Group.ElementModP> coefficient_commitments = coeff.getCoefficientCommitmentsList().stream()
            .map(CommonConvert::convertElementModP)
            .collect(Collectors.toList());

    List<SchnorrProof> coefficient_proofs = coeff.getCoefficientProofsList().stream()
            .map(ElectionRecordFromProto::convertSchnorrProof)
            .collect(Collectors.toList());

    return KeyCeremony.CoefficientValidationSet.create(
            coeff.getOwnerId(),
            coefficient_commitments,
            coefficient_proofs
    );
  }

  static SchnorrProof convertSchnorrProof(KeyCeremonyProto.SchnorrProof proof) {
    return new SchnorrProof(
            convertElementModP(proof.getPublicKey()),
            convertElementModP(proof.getCommitment()),
            convertElementModQ(proof.getChallenge()),
            convertElementModQ(proof.getResponse()));
  }
}