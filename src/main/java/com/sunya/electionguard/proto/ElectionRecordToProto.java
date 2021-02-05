package com.sunya.electionguard.proto;

import com.google.protobuf.ByteString;
import com.sunya.electionguard.Ballot;
import com.sunya.electionguard.Election;
import com.sunya.electionguard.Encrypt;
import com.sunya.electionguard.Group;
import com.sunya.electionguard.KeyCeremony;
import com.sunya.electionguard.PublishedCiphertextTally;
import com.sunya.electionguard.PlaintextTally;
import com.sunya.electionguard.SchnorrProof;

import javax.annotation.Nullable;

import static com.sunya.electionguard.proto.ElectionRecordProto.ElectionRecord;
import static com.sunya.electionguard.proto.CommonConvert.convertElementModP;
import static com.sunya.electionguard.proto.CommonConvert.convertElementModQ;

public class ElectionRecordToProto {

  public static ElectionRecord buildElectionRecord(
          Election.ElectionDescription description,
          Election.CiphertextElectionContext context,
          Election.ElectionConstants constants,
          Iterable<KeyCeremony.CoefficientValidationSet> guardianCoefficients,
          @Nullable Iterable<Encrypt.EncryptionDevice> devices,
          @Nullable Iterable<Ballot.CiphertextAcceptedBallot> castBallots,
          @Nullable PublishedCiphertextTally ciphertext_tally,
          @Nullable PlaintextTally decryptedTally) {

    ElectionRecord.Builder builder = ElectionRecord.newBuilder();
    builder.setConstants( convertConstants(constants));
    builder.setElection( ElectionDescriptionToProto.translateToProto(description));
    builder.setContext( convertContext(context));

    for (KeyCeremony.CoefficientValidationSet coeff : guardianCoefficients) {
      builder.addGuardianCoefficients(convertValidationCoefficients(coeff));
    }

    if (devices != null) {
      for (Encrypt.EncryptionDevice device : devices) {
        builder.addDevice(convertDevice(device));
      }
    }
    if (castBallots != null) {
      for (Ballot.CiphertextAcceptedBallot ballot : castBallots) {
        builder.addCastBallots(CiphertextBallotToProto.translateToProto(ballot));
      }
    }
    if (ciphertext_tally != null) {
      builder.setCiphertextTally(CiphertextTallyToProto.translateToProto(ciphertext_tally));
    }
    if (decryptedTally != null) {
      builder.setDecryptedTally(PlaintextTallyToProto.translateToProto(decryptedTally));
    }
    return builder.build();
  }

  static ElectionRecordProto.Constants convertConstants(Election.ElectionConstants constants) {
    ElectionRecordProto.Constants.Builder builder = ElectionRecordProto.Constants.newBuilder();
    builder.setLargePrime(ByteString.copyFrom(constants.large_prime.toByteArray()));
    builder.setSmallPrime(ByteString.copyFrom(constants.small_prime.toByteArray()));
    builder.setCofactor(ByteString.copyFrom(constants.cofactor.toByteArray()));
    builder.setGenerator(ByteString.copyFrom(constants.generator.toByteArray()));
    return builder.build();
  }

  static ElectionRecordProto.ElectionContext convertContext(Election.CiphertextElectionContext context) {
    ElectionRecordProto.ElectionContext.Builder builder = ElectionRecordProto.ElectionContext.newBuilder();
    builder.setNumberOfGuardians(context.number_of_guardians);
    builder.setQuorum(context.quorum);
    builder.setElgamalPublicKey(convertElementModP(context.elgamal_public_key));
    builder.setDescriptionHash(convertElementModQ(context.description_hash));
    builder.setCryptoBaseHash(convertElementModQ(context.crypto_base_hash));
    builder.setCryptoExtendedBaseHash(convertElementModQ(context.crypto_extended_base_hash));
    return builder.build();
  }

  static ElectionRecordProto.EncryptionDevice convertDevice(Encrypt.EncryptionDevice device) {
    ElectionRecordProto.EncryptionDevice.Builder builder = ElectionRecordProto.EncryptionDevice.newBuilder();
    builder.setUuid(device.uuid);
    builder.setLocation(device.location);
    return builder.build();
  }

  static KeyCeremonyProto.CoefficientValidationSet convertValidationCoefficients(KeyCeremony.CoefficientValidationSet validationSet) {
    KeyCeremonyProto.CoefficientValidationSet.Builder builder = KeyCeremonyProto.CoefficientValidationSet.newBuilder();
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
