package com.sunya.electionguard.proto;

import com.google.common.collect.ImmutableList;
import com.google.protobuf.ByteString;
import com.sunya.electionguard.Ballot;
import com.sunya.electionguard.Election;
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

  public static ElectionRecordFromProto read(String filename) throws IOException {
    ElectionRecordProto.ElectionRecord proto;
    try (FileInputStream inp = new FileInputStream(filename)) {
      proto = ElectionRecordProto.ElectionRecord.parseDelimitedFrom(inp);
    }
    return translateFromProto(proto);
  }

  public static ElectionRecordFromProto translateFromProto(ElectionRecordProto.ElectionRecord proto) {
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

    return new ElectionRecordFromProto(constants, context, description, devices, castBallots, spoiledBallots,
            guardianCoefficients, ciphertextTally, decryptedTally);
  }

  public final Election.ElectionConstants constants;
  public final Election.CiphertextElectionContext context;
  public final Election.ElectionDescription election;
  public final ImmutableList<Encrypt.EncryptionDevice> devices;
  public final ImmutableList<Ballot.CiphertextAcceptedBallot> castBallots;
  public final ImmutableList<Ballot.CiphertextAcceptedBallot> spoiledBallots;
  public final ImmutableList<KeyCeremony.CoefficientValidationSet> guardianCoefficients;
  public final Tally.PublishedCiphertextTally ciphertextTally;
  public final Tally.PlaintextTally decryptedTally;

  private ElectionRecordFromProto(Election.ElectionConstants constants,
                                  Election.CiphertextElectionContext context,
                                  Election.ElectionDescription description,
                                  Iterable<Encrypt.EncryptionDevice> devices,
                                  Iterable<Ballot.CiphertextAcceptedBallot> castBallots,
                                  Iterable<Ballot.CiphertextAcceptedBallot> spoiledBallots,
                                  Iterable<KeyCeremony.CoefficientValidationSet> guardianCoefficients,
                                  Tally.PublishedCiphertextTally ciphertextTally,
                                  Tally.PlaintextTally decryptedTally) {
    this.constants = constants;
    this.context = context;
    this.election = description;
    this.devices = ImmutableList.copyOf(devices);
    this.castBallots = ImmutableList.copyOf(castBallots);
    this.spoiledBallots = ImmutableList.copyOf(spoiledBallots);
    this.guardianCoefficients = ImmutableList.copyOf(guardianCoefficients);
    this.ciphertextTally = ciphertextTally;
    this.decryptedTally = decryptedTally;
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
