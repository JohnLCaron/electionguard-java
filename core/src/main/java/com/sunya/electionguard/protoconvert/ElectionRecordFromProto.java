package com.sunya.electionguard.protoconvert;

import com.google.protobuf.ByteString;
import com.sunya.electionguard.AvailableGuardian;
import com.sunya.electionguard.ElectionContext;
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

import electionguard.protogen.ElectionRecordProto;
import electionguard.protogen.ElectionRecordProto2;

@Immutable
public class ElectionRecordFromProto {

  public static ElectionRecord read(String filename) throws IOException {
    ElectionRecordProto.ElectionRecord proto;
    try (FileInputStream inp = new FileInputStream(filename)) {
      proto = ElectionRecordProto.ElectionRecord.parseFrom(inp);
    }
    return translateFromProto(proto);
  }

  public static ElectionRecord translateFromProto(ElectionRecordProto.ElectionRecord proto) {
    String version = proto.getProtoVersion();
    Manifest manifest = ManifestFromProto.translateFromProto(proto.getManifest());
    ElectionConstants constants = convertConstants(proto.getConstants());
    ElectionContext context = proto.hasContext() ? convertContext(proto.getContext()) : null;

    List<GuardianRecord> guardianRecords =
            proto.getGuardianRecordsList().stream()
                    .map(ElectionRecordFromProto::convertGuardianRecord)
                    .toList();
    List<Encrypt.EncryptionDevice> devices =
            proto.getDevicesList().stream()
                    .map(ElectionRecordFromProto::convertDevice)
                    .toList();

    CiphertextTally ciphertextTally = proto.hasCiphertextTally() ?
            CiphertextTallyFromProto.importCiphertextTally(proto.getCiphertextTally()) : null;
    PlaintextTally decryptedTally = proto.hasDecryptedTally() ?
            PlaintextTallyFromProto.importPlaintextTally(proto.getDecryptedTally()) : null;

    List<AvailableGuardian> guardians = proto.getAvailableGuardiansList().stream()
                    .map(ElectionRecordFromProto::convertAvailableGuardian)
                    .toList();

    return new ElectionRecord(version, manifest, constants, context, guardianRecords,
            devices, ciphertextTally, decryptedTally, null, null, guardians);
  }

  static AvailableGuardian convertAvailableGuardian(ElectionRecordProto2.AvailableGuardian proto) {
    return new AvailableGuardian(
            proto.getGuardianId(),
            proto.getXCoordinate(),
            CommonConvert.importElementModQ(proto.getLagrangeCoefficient())
    );
  }

  static ElectionConstants convertConstants(ElectionRecordProto2.ElectionConstants constants) {
    return new ElectionConstants(
            constants.getName(),
            convertBigInteger(constants.getLargePrime()),
            convertBigInteger(constants.getSmallPrime()),
            convertBigInteger(constants.getCofactor()),
            convertBigInteger(constants.getGenerator()));
  }

  static BigInteger convertBigInteger(ByteString bs) {
    return new BigInteger(1, bs.toByteArray());
  }

  static ElectionContext convertContext(ElectionRecordProto.ElectionContext context) {
    if (context == null) {
      return null;
    }
    return new ElectionContext(
            context.getNumberOfGuardians(),
            context.getQuorum(),
            CommonConvert.importElementModP(context.getJointPublicKey()),
            CommonConvert.importUInt256toQ(context.getManifestHash()),
            CommonConvert.importUInt256toQ(context.getCryptoBaseHash()),
            CommonConvert.importUInt256toQ(context.getCryptoExtendedBaseHash()),
            CommonConvert.importUInt256toQ(context.getCommitmentHash()),
            context.getExtendedDataMap());
  }

  static Encrypt.EncryptionDevice convertDevice(ElectionRecordProto.EncryptionDevice device) {
    return new Encrypt.EncryptionDevice(device.getDeviceId(), device.getSessionId(), device.getLaunchCode(), device.getLocation());
  }

  static GuardianRecord convertGuardianRecord(ElectionRecordProto.GuardianRecord guardianRecord) {
   List<Group.ElementModP> coefficient_commitments = guardianRecord.getCoefficientCommitmentsList().stream()
            .map(CommonConvert::importElementModP)
           .toList();
    List<SchnorrProof> coefficient_proofs = guardianRecord.getCoefficientProofsList().stream()
            .map(CommonConvert::importSchnorrProof)
            .toList();
    return new GuardianRecord(
            guardianRecord.getGuardianId(),
            guardianRecord.getXCoordinate(),
            CommonConvert.importElementModP(guardianRecord.getGuardianPublicKey()),
            coefficient_commitments,
            coefficient_proofs
    );
  }

}
