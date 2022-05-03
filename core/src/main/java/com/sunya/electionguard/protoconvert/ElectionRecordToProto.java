package com.sunya.electionguard.protoconvert;

import com.google.protobuf.ByteString;
import com.sunya.electionguard.AvailableGuardian;
import com.sunya.electionguard.ElectionCryptoContext;
import com.sunya.electionguard.ElectionConstants;
import com.sunya.electionguard.GuardianRecord;
import com.sunya.electionguard.Manifest;
import com.sunya.electionguard.Encrypt;
import com.sunya.electionguard.Group;
import com.sunya.electionguard.CiphertextTally;
import com.sunya.electionguard.PlaintextTally;
import com.sunya.electionguard.SchnorrProof;

import javax.annotation.Nullable;

import static com.sunya.electionguard.protoconvert.CommonConvert.publishElementModP;
import static com.sunya.electionguard.protoconvert.CommonConvert.publishElementModQ;

import electionguard.protogen.ElectionRecordProto;
import electionguard.protogen.ElectionRecordProto1;


public class ElectionRecordToProto {
  public static final String PROTO_VERSION = "1.0.0";

  public static ElectionRecordProto1.ElectionRecord buildElectionRecord(
          Manifest description,
          ElectionConstants constants,
          @Nullable ElectionCryptoContext context,
          @Nullable Iterable<GuardianRecord> guardianRecords,
          @Nullable Iterable<Encrypt.EncryptionDevice> devices,
          @Nullable CiphertextTally ciphertext_tally,
          @Nullable PlaintextTally decryptedTally,
          @Nullable Iterable<AvailableGuardian> availableGuardians) {

    ElectionRecordProto1.ElectionRecord.Builder builder = ElectionRecordProto1.ElectionRecord.newBuilder();
    builder.setProtoVersion(PROTO_VERSION);
    builder.setConstants( convertConstants(constants));
    builder.setManifest( ManifestToProto.publishManifest(description));
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
      builder.setCiphertextTally(CiphertextTallyToProto.publishCiphertextTally(ciphertext_tally));
    }
    if (decryptedTally != null) {
      builder.setDecryptedTally(PlaintextTallyToProto.publishPlaintextTally(decryptedTally));
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
    builder.setLagrangeCoefficient(publishElementModQ(guardian.lagrangeCoefficient()));
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

  static ElectionRecordProto1.ElectionContext convertContext(ElectionCryptoContext context) {
    ElectionRecordProto1.ElectionContext.Builder builder = ElectionRecordProto1.ElectionContext.newBuilder();
    builder.setNumberOfGuardians(context.numberOfGuardians);
    builder.setQuorum(context.quorum);
    builder.setJointPublicKey(publishElementModP(context.jointPublicKey));
    builder.setManifestHash(CommonConvert.publishUInt256fromQ(context.manifestHash));
    builder.setCryptoBaseHash(CommonConvert.publishUInt256fromQ(context.cryptoBaseHash));
    builder.setCryptoExtendedBaseHash(CommonConvert.publishUInt256fromQ(context.cryptoExtendedBaseHash));
    builder.setCommitmentHash(CommonConvert.publishUInt256fromQ(context.commitmentHash));
    if (context.extended_data != null && !context.extended_data.isEmpty()) {
      builder.putAllExtendedData(context.extended_data);
    }
    return builder.build();
  }

  static ElectionRecordProto1.EncryptionDevice convertDevice(Encrypt.EncryptionDevice device) {
    ElectionRecordProto1.EncryptionDevice.Builder builder = ElectionRecordProto1.EncryptionDevice.newBuilder();
    builder.setDeviceId(device.deviceId());
    builder.setSessionId(device.sessionId());
    builder.setLaunchCode(device.launchCode());
    builder.setLocation(device.location());
    return builder.build();
  }

  static ElectionRecordProto1.GuardianRecord convertGuardianRecord(GuardianRecord guardianRecord) {
    ElectionRecordProto1.GuardianRecord.Builder builder = ElectionRecordProto1.GuardianRecord.newBuilder();
    builder.setGuardianId(guardianRecord.guardianId());
    builder.setXCoordinate(guardianRecord.xCoordinate());
    builder.setGuardianPublicKey(publishElementModP(guardianRecord.guardianPublicKey()));
    for (Group.ElementModP commitment : guardianRecord.coefficientCommitments()) {
      builder.addCoefficientCommitments(publishElementModP(commitment));
    }
    for (SchnorrProof proof : guardianRecord.coefficientProofs()) {
      builder.addCoefficientProofs(CommonConvert.publishSchnorrProof(proof));
    }
    return builder.build();
  }

}
