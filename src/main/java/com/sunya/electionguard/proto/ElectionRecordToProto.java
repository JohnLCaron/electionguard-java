package com.sunya.electionguard.proto;

import com.google.protobuf.ByteString;
import com.sunya.electionguard.AvailableGuardian;
import com.sunya.electionguard.CiphertextElectionContext;
import com.sunya.electionguard.ElectionConstants;
import com.sunya.electionguard.Manifest;
import com.sunya.electionguard.Encrypt;
import com.sunya.electionguard.Group;
import com.sunya.electionguard.KeyCeremony;
import com.sunya.electionguard.CiphertextTally;
import com.sunya.electionguard.PlaintextTally;
import com.sunya.electionguard.SchnorrProof;

import javax.annotation.Nullable;

import static com.sunya.electionguard.proto.ElectionRecordProto.ElectionRecord;
import static com.sunya.electionguard.proto.CommonConvert.convertElementModP;
import static com.sunya.electionguard.proto.CommonConvert.convertElementModQ;

public class ElectionRecordToProto {

  public static ElectionRecord buildElectionRecord(
          Manifest description,
          CiphertextElectionContext context,
          ElectionConstants constants,
          Iterable<KeyCeremony.CoefficientValidationSet> guardianCoefficients,
          @Nullable Iterable<Encrypt.EncryptionDevice> devices,
          @Nullable CiphertextTally ciphertext_tally,
          @Nullable PlaintextTally decryptedTally,
          @Nullable Iterable<AvailableGuardian> availableGuardians) {

    ElectionRecord.Builder builder = ElectionRecord.newBuilder();
    builder.setConstants( convertConstants(constants));
    builder.setManifest( ManifestToProto.translateToProto(description));
    builder.setContext( convertContext(context));

    for (KeyCeremony.CoefficientValidationSet coeff : guardianCoefficients) {
      builder.addGuardianCoefficients(convertValidationCoefficients(coeff));
    }

    if (devices != null) {
      for (Encrypt.EncryptionDevice device : devices) {
        builder.addDevice(convertDevice(device));
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
    builder.setGuardianId(guardian.guardian_id);
    builder.setSequence(guardian.sequence);
    builder.setLagrangeCoordinate(convertElementModQ(guardian.lagrangeCoordinate));
    return builder.build();
  }

  static ElectionRecordProto.Constants convertConstants(ElectionConstants constants) {
    ElectionRecordProto.Constants.Builder builder = ElectionRecordProto.Constants.newBuilder();
    builder.setLargePrime(ByteString.copyFrom(constants.large_prime.toByteArray()));
    builder.setSmallPrime(ByteString.copyFrom(constants.small_prime.toByteArray()));
    builder.setCofactor(ByteString.copyFrom(constants.cofactor.toByteArray()));
    builder.setGenerator(ByteString.copyFrom(constants.generator.toByteArray()));
    return builder.build();
  }

  static ElectionRecordProto.ElectionContext convertContext(CiphertextElectionContext context) {
    ElectionRecordProto.ElectionContext.Builder builder = ElectionRecordProto.ElectionContext.newBuilder();
    builder.setNumberOfGuardians(context.number_of_guardians);
    builder.setQuorum(context.quorum);
    builder.setJointPublicKey(convertElementModP(context.elgamal_public_key));
    builder.setDescriptionHash(convertElementModQ(context.description_hash));
    builder.setCryptoBaseHash(convertElementModQ(context.crypto_base_hash));
    builder.setCryptoExtendedBaseHash(convertElementModQ(context.crypto_extended_base_hash));
    return builder.build();
  }

  static ElectionRecordProto.EncryptionDevice convertDevice(Encrypt.EncryptionDevice device) {
    ElectionRecordProto.EncryptionDevice.Builder builder = ElectionRecordProto.EncryptionDevice.newBuilder();
    builder.setUuid(device.uuid);
    builder.setSessionId(device.session_id);
    builder.setLaunchCode(device.launch_code);
    builder.setLocation(device.location);
    return builder.build();
  }

  static ElectionRecordProto.CoefficientValidationSet convertValidationCoefficients(KeyCeremony.CoefficientValidationSet validationSet) {
    ElectionRecordProto.CoefficientValidationSet.Builder builder = ElectionRecordProto.CoefficientValidationSet.newBuilder();
    builder.setOwnerId(validationSet.owner_id());
    for (Group.ElementModP commitment : validationSet.coefficient_commitments()) {
      builder.addCoefficientCommitments(convertElementModP(commitment));
    }
    for (SchnorrProof proof : validationSet.coefficient_proofs()) {
      builder.addCoefficientProofs(CommonConvert.convertSchnorrProof(proof));
    }
    return builder.build();
  }

}
