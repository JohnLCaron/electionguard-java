package com.sunya.electionguard.proto;

import com.google.protobuf.ByteString;
import com.sunya.electionguard.Election;
import com.sunya.electionguard.PublishedCiphertextTally;
import com.sunya.electionguard.PlaintextTally;
import com.sunya.electionguard.verifier.ElectionRecord;
import com.sunya.electionguard.Encrypt;
import com.sunya.electionguard.Group;
import com.sunya.electionguard.KeyCeremony;
import com.sunya.electionguard.SchnorrProof;

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
    List<KeyCeremony.CoefficientValidationSet> guardianCoefficients =
            proto.getGuardianCoefficientsList().stream().map(ElectionRecordFromProto::convertValidationCoefficients).collect(Collectors.toList());
    List<Encrypt.EncryptionDevice> devices =
            proto.getDeviceList().stream().map(ElectionRecordFromProto::convertDevice).collect(Collectors.toList());

    PublishedCiphertextTally ciphertextTally = proto.hasCiphertextTally() ?
            CiphertextTallyFromProto.translateFromProto(proto.getCiphertextTally()) : null;
    PlaintextTally decryptedTally = proto.hasDecryptedTally() ?
            PlaintextTallyFromProto.translateFromProto(proto.getDecryptedTally()) : null;

    return new ElectionRecord(constants, context, description, guardianCoefficients,
            devices, null, ciphertextTally, decryptedTally);
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

  static Encrypt.EncryptionDevice convertDevice(ElectionRecordProto.EncryptionDevice device) {
    return new Encrypt.EncryptionDevice(device.getUuid(), device.getLocation());
  }

  static KeyCeremony.CoefficientValidationSet convertValidationCoefficients(KeyCeremonyProto.CoefficientValidationSet coeff) {
   List<Group.ElementModP> coefficient_commitments = coeff.getCoefficientCommitmentsList().stream()
            .map(CommonConvert::convertElementModP)
            .collect(Collectors.toList());

    List<SchnorrProof> coefficient_proofs = coeff.getCoefficientProofsList().stream()
            .map(CommonConvert::convertSchnorrProof)
            .collect(Collectors.toList());

    return KeyCeremony.CoefficientValidationSet.create(
            coeff.getOwnerId(),
            coefficient_commitments,
            coefficient_proofs
    );
  }

}
