package com.sunya.electionguard.proto;

import com.google.protobuf.ByteString;
import com.sunya.electionguard.AvailableGuardian;
import com.sunya.electionguard.CiphertextElectionContext;
import com.sunya.electionguard.ElectionConstants;
import com.sunya.electionguard.CiphertextTally;
import com.sunya.electionguard.GuardianRecord;
import com.sunya.electionguard.Manifest;
import com.sunya.electionguard.PlaintextTally;
import com.sunya.electionguard.verifier.ElectionRecord;
import com.sunya.electionguard.Encrypt;
import com.sunya.electionguard.Group;
import com.sunya.electionguard.SchnorrProof;

import javax.annotation.concurrent.Immutable;
import java.io.FileInputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.util.List;
import java.util.stream.Collectors;

import static com.sunya.electionguard.proto.CommonConvert.convertElementModP;
import static com.sunya.electionguard.proto.CommonConvert.convertElementModQ;

import com.sunya.electionguard.protogen.ElectionRecordProto;


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
    String version = proto.getVersion();
    ElectionConstants constants = convertConstants(proto.getConstants());
    CiphertextElectionContext context = convertContext(proto.getContext());
    Manifest description = ManifestFromProto.translateFromProto(proto.getManifest());
    List<GuardianRecord> guardianRecords =
            proto.getGuardianRecordsList().stream().map(ElectionRecordFromProto::convertGuardianRecord).collect(Collectors.toList());
    List<Encrypt.EncryptionDevice> devices =
            proto.getDeviceList().stream().map(ElectionRecordFromProto::convertDevice).collect(Collectors.toList());

    CiphertextTally ciphertextTally = proto.hasCiphertextTally() ?
            CiphertextTallyFromProto.translateFromProto(proto.getCiphertextTally()) : null;
    PlaintextTally decryptedTally = proto.hasDecryptedTally() ?
            PlaintextTallyFromProto.translateFromProto(proto.getDecryptedTally()) : null;

    List<AvailableGuardian> guardians =
            proto.getAvailableGuardiansList().stream().map(ElectionRecordFromProto::convertAvailableGuardian).collect(Collectors.toList());


    return new ElectionRecord(version, constants, context, description, guardianRecords,
            devices, ciphertextTally, decryptedTally, null, null, guardians);
  }

  static AvailableGuardian convertAvailableGuardian(ElectionRecordProto.AvailableGuardian proto) {
    return new AvailableGuardian(
            proto.getGuardianId(),
            proto.getSequence(),
            convertElementModQ(proto.getLagrangeCoordinate()));
  }

  static ElectionConstants convertConstants(ElectionRecordProto.Constants constants) {
    return new ElectionConstants(
            convertBigInteger(constants.getLargePrime()),
            convertBigInteger(constants.getSmallPrime()),
            convertBigInteger(constants.getCofactor()),
            convertBigInteger(constants.getGenerator()));
  }

  static BigInteger convertBigInteger(ByteString bs) {
    return new BigInteger(bs.toByteArray());
  }

  static CiphertextElectionContext convertContext(ElectionRecordProto.ElectionContext context) {
    return new CiphertextElectionContext(
            context.getNumberOfGuardians(),
            context.getQuorum(),
            convertElementModP(context.getJointPublicKey()),
            convertElementModQ(context.getDescriptionHash()),
            convertElementModQ(context.getCryptoBaseHash()),
            convertElementModQ(context.getCryptoExtendedBaseHash()),
            convertElementModQ(context.getCommitmentHash()),
            context.getExtendedDataMap());
  }

  static Encrypt.EncryptionDevice convertDevice(ElectionRecordProto.EncryptionDevice device) {
    return new Encrypt.EncryptionDevice(device.getDeviceId(), device.getSessionId(), device.getLaunchCode(), device.getLocation());
  }

  static GuardianRecord convertGuardianRecord(ElectionRecordProto.GuardianRecord guardianRecord) {
   List<Group.ElementModP> coefficient_commitments = guardianRecord.getCoefficientCommitmentsList().stream()
            .map(CommonConvert::convertElementModP)
            .collect(Collectors.toList());

    List<SchnorrProof> coefficient_proofs = guardianRecord.getCoefficientProofsList().stream()
            .map(CommonConvert::convertSchnorrProof)
            .collect(Collectors.toList());

    return new GuardianRecord(
            guardianRecord.getGuardianId(),
            guardianRecord.getSequence(),
            CommonConvert.convertElementModP(guardianRecord.getElectionPublicKey()),
            coefficient_commitments,
            coefficient_proofs
    );
  }

}
