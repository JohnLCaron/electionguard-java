package com.sunya.electionguard.protoconvert;

import com.google.protobuf.ByteString;
import com.sunya.electionguard.AvailableGuardian;
import com.sunya.electionguard.ElectionCryptoContext;
import com.sunya.electionguard.ElectionConstants;
import com.sunya.electionguard.CiphertextTally;
import com.sunya.electionguard.GuardianRecord;
import com.sunya.electionguard.Manifest;
import com.sunya.electionguard.PlaintextTally;
import com.sunya.electionguard.publish.ElectionRecord;
import com.sunya.electionguard.Encrypt;
import com.sunya.electionguard.Group;
import com.sunya.electionguard.SchnorrProof;

import javax.annotation.concurrent.Immutable;
import java.io.FileInputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.util.List;

import electionguard.protogen.ElectionRecordProto;
import electionguard.protogen.ElectionRecordProto1;

@Immutable
public class ElectionRecordFromProto {

  public static ElectionRecord read(String filename) throws IOException {
    ElectionRecordProto1.ElectionRecord proto;
    try (FileInputStream inp = new FileInputStream(filename)) {
      proto = ElectionRecordProto1.ElectionRecord.parseFrom(inp);
    }
    return translateFromProto(proto);
  }

  public static ElectionRecord translateFromProto(ElectionRecordProto1.ElectionRecord proto) {
    String version = proto.getProtoVersion();
    Manifest manifest = ManifestFromProto.translateFromProto(proto.getManifest());
    ElectionConstants constants = convertConstants(proto.getConstants());
    ElectionCryptoContext context = proto.hasContext() ? convertContext(proto.getContext()) : null;

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

    return null; // new ElectionRecordFromProto1(version, manifest, constants, context, guardianRecords,
            // devices, ciphertextTally, decryptedTally, null, null, guardians);
  }

  static AvailableGuardian convertAvailableGuardian(ElectionRecordProto.AvailableGuardian proto) {
    return new AvailableGuardian(
            proto.getGuardianId(),
            proto.getXCoordinate(),
            CommonConvert.importElementModQ(proto.getLagrangeCoefficient())
    );
  }

  static ElectionConstants convertConstants(ElectionRecordProto.ElectionConstants constants) {
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

  static ElectionCryptoContext convertContext(ElectionRecordProto1.ElectionContext context) {
    if (context == null) {
      return null;
    }
    return new ElectionCryptoContext(
            context.getNumberOfGuardians(),
            context.getQuorum(),
            CommonConvert.importElementModP(context.getJointPublicKey()),
            CommonConvert.importUInt256toQ(context.getManifestHash()),
            CommonConvert.importUInt256toQ(context.getCryptoBaseHash()),
            CommonConvert.importUInt256toQ(context.getCryptoExtendedBaseHash()),
            CommonConvert.importUInt256toQ(context.getCommitmentHash()),
            context.getExtendedDataMap());
  }

  static Encrypt.EncryptionDevice convertDevice(ElectionRecordProto1.EncryptionDevice device) {
    return new Encrypt.EncryptionDevice(device.getDeviceId(), device.getSessionId(), device.getLaunchCode(), device.getLocation());
  }

  static GuardianRecord convertGuardianRecord(ElectionRecordProto1.GuardianRecord guardianRecord) {
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
