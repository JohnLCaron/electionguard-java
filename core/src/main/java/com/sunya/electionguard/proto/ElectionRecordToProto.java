package com.sunya.electionguard.proto;

import com.google.protobuf.ByteString;
import com.sunya.electionguard.AvailableGuardian;
import com.sunya.electionguard.ElectionContext;
import com.sunya.electionguard.ElectionConstants;
import com.sunya.electionguard.GuardianRecord;
import com.sunya.electionguard.Manifest;
import com.sunya.electionguard.Encrypt;
import com.sunya.electionguard.Group;
import com.sunya.electionguard.CiphertextTally;
import com.sunya.electionguard.PlaintextTally;
import com.sunya.electionguard.SchnorrProof;

import javax.annotation.Nullable;

import static com.sunya.electionguard.proto.CommonConvert.convertElementModP;
import static com.sunya.electionguard.proto.CommonConvert.convertElementModQ;

import electionguard.protogen.ElectionRecordProto;


public class ElectionRecordToProto {
  public static final String PROTO_VERSION = "1.0.0";

  public static ElectionRecordProto.ElectionRecord buildElectionRecord(
          Manifest description,
          ElectionConstants constants,
          @Nullable ElectionContext context,
          @Nullable Iterable<GuardianRecord> guardianRecords,
          @Nullable Iterable<Encrypt.EncryptionDevice> devices,
          @Nullable CiphertextTally ciphertext_tally,
          @Nullable PlaintextTally decryptedTally,
          @Nullable Iterable<AvailableGuardian> availableGuardians) {

    ElectionRecordProto.ElectionRecord.Builder builder = ElectionRecordProto.ElectionRecord.newBuilder();
    builder.setProtoVersion(PROTO_VERSION);
    builder.setConstants( convertConstants(constants));
    builder.setManifest( ManifestToProto.translateToProto(description));
    if (context != null) {
      builder.setContext(convertContext(context));
    }

    if (guardianRecords != null) {
      for (GuardianRecord guardianRecord : guardianRecords) {
        builder.addGuardianRecords(convertGuardianRecord(guardianRecord));
      }
    }

    if (devices != null) {
      for (Encrypt.EncryptionDevice device : devices) {
        builder.addDevices(convertDevice(device));
      }
    }
    if (ciphertext_tally != null) {
      builder.setCiphertextTally(CiphertextTallyToProto.translateToProto(ciphertext_tally));
    }
    if (decryptedTally != null) {
      builder.setDecryptedTally(PlaintextTallyToProto.translateToProto(decryptedTally));
    }
    if (availableGuardians != null) {
      for (AvailableGuardian guardian : availableGuardians) {
        builder.addAvailableGuardians(convertAvailableGuardian(guardian));
      }
    }
    return builder.build();
  }

  static ElectionRecordProto.AvailableGuardian convertAvailableGuardian(AvailableGuardian guardian) {
    ElectionRecordProto.AvailableGuardian.Builder builder = ElectionRecordProto.AvailableGuardian.newBuilder();
    builder.setGuardianId(guardian.guardianId());
    builder.setXCoordinate(guardian.xCoordinate());
    builder.setLagrangeCoordinate(convertElementModQ(guardian.lagrangeCoordinate()));
    return builder.build();
  }

  static ElectionRecordProto.ElectionConstants convertConstants(ElectionConstants constants) {
    ElectionRecordProto.ElectionConstants.Builder builder = ElectionRecordProto.ElectionConstants.newBuilder();
    if (constants.name != null) {
      builder.setName(constants.name);
    }
    builder.setLargePrime(ByteString.copyFrom(constants.largePrime.toByteArray()));
    builder.setSmallPrime(ByteString.copyFrom(constants.smallPrime.toByteArray()));
    builder.setCofactor(ByteString.copyFrom(constants.cofactor.toByteArray()));
    builder.setGenerator(ByteString.copyFrom(constants.generator.toByteArray()));
    return builder.build();
  }

  static ElectionRecordProto.ElectionContext convertContext(ElectionContext context) {
    ElectionRecordProto.ElectionContext.Builder builder = ElectionRecordProto.ElectionContext.newBuilder();
    builder.setNumberOfGuardians(context.numberOfGuardians);
    builder.setQuorum(context.quorum);
    builder.setJointPublicKey(convertElementModP(context.jointPublicKey));
    builder.setManifestHash(convertElementModQ(context.manifestHash));
    builder.setCryptoBaseHash(convertElementModQ(context.cryptoBaseHash));
    builder.setCryptoExtendedBaseHash(convertElementModQ(context.cryptoExtendedBaseHash));
    builder.setCommitmentHash(convertElementModQ(context.commitmentHash));
    if (context.extended_data != null && !context.extended_data.isEmpty()) {
      builder.putAllExtendedData(context.extended_data);
    }
    return builder.build();
  }

  static ElectionRecordProto.EncryptionDevice convertDevice(Encrypt.EncryptionDevice device) {
    ElectionRecordProto.EncryptionDevice.Builder builder = ElectionRecordProto.EncryptionDevice.newBuilder();
    builder.setDeviceId(device.deviceId());
    builder.setSessionId(device.sessionId());
    builder.setLaunchCode(device.launchCode());
    builder.setLocation(device.location());
    return builder.build();
  }

  static ElectionRecordProto.GuardianRecord convertGuardianRecord(GuardianRecord guardianRecord) {
    ElectionRecordProto.GuardianRecord.Builder builder = ElectionRecordProto.GuardianRecord.newBuilder();
    builder.setGuardianId(guardianRecord.guardianId());
    builder.setXCoordinate(guardianRecord.xCoordinate());
    builder.setGuardianPublicKey(convertElementModP(guardianRecord.guardianPublicKey()));
    for (Group.ElementModP commitment : guardianRecord.coefficientCommitments()) {
      builder.addCoefficientCommitments(convertElementModP(commitment));
    }
    for (SchnorrProof proof : guardianRecord.coefficientProofs()) {
      builder.addCoefficientProofs(CommonConvert.convertSchnorrProof(proof));
    }
    return builder.build();
  }

}
