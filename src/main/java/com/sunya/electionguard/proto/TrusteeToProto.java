package com.sunya.electionguard.proto;

import com.google.protobuf.ByteString;
import com.sunya.electionguard.ElGamal;
import com.sunya.electionguard.SchnorrProof;
import com.sunya.electionguard.keyceremony.KeyCeremony2;
import com.sunya.electionguard.keyceremony.KeyCeremonyTrustee;

import java.util.List;

import static com.sunya.electionguard.proto.CommonConvert.convertElementModP;

public class TrusteeToProto {

  public static TrusteeProto.DecryptingTrustees convertTrustees(List<KeyCeremonyTrustee> trustees) {
    TrusteeProto.DecryptingTrustees.Builder builder = TrusteeProto.DecryptingTrustees.newBuilder();
    trustees.forEach(t -> builder.addTrustees(convertTrustee(t)));
    return builder.build();
  }

  public static TrusteeProto.DecryptingTrustee convertTrustee(KeyCeremonyTrustee trustee) {
    TrusteeProto.DecryptingTrustee.Builder builder = TrusteeProto.DecryptingTrustee.newBuilder();
    builder.setGuardianId(trustee.id);
    builder.setGuardianXCoordinate(trustee.xCoordinate);
    builder.setElectionKeyPair(convertElgamalKeypair(trustee.secrets().election_key_pair));
    builder.setRsaPrivateKey(CommonConvert.convertJavaPrivateKey(trustee.secrets().rsa_keypair.getPrivate()));
    trustee.otherGuardianPartialKeyBackups.values().forEach(k -> builder.addOtherGuardianBackups(convertElectionPartialKeyBackup(k)));
    trustee.allGuardianPublicKeys.values().forEach(k -> builder.addGuardianCommitments(convertCoefficients(k)));
    return builder.build();
  }

  private static TrusteeProto.ElectionPartialKeyBackup2 convertElectionPartialKeyBackup(KeyCeremony2.PartialKeyBackup org) {
    TrusteeProto.ElectionPartialKeyBackup2.Builder builder = TrusteeProto.ElectionPartialKeyBackup2.newBuilder();
    builder.setGeneratingGuardianId(org.generatingGuardianId());
    builder.setDesignatedGuardianId(org.designatedGuardianId());
    builder.setDesignatedGuardianXCoordinate(org.designatedGuardianXCoordinate());
    builder.setEncryptedCoordinate(ByteString.copyFrom(org.encryptedCoordinate().getBytes()));
    return builder.build();
  }

  private static CommonProto.ElGamalKeyPair convertElgamalKeypair(ElGamal.KeyPair keypair) {
    CommonProto.ElGamalKeyPair.Builder builder = CommonProto.ElGamalKeyPair.newBuilder();
    builder.setSecretKey(CommonConvert.convertElementModQ(keypair.secret_key));
    builder.setPublicKey(CommonConvert.convertElementModP(keypair.public_key));
    return builder.build();
  }

  private static TrusteeProto.CommitmentSet convertCoefficients(KeyCeremony2.PublicKeySet publicKetSey) {
    TrusteeProto.CommitmentSet.Builder builder = TrusteeProto.CommitmentSet.newBuilder();
    builder.setGuardianId(publicKetSey.ownerId());
    for (SchnorrProof proof : publicKetSey.coefficientProofs()) {
      builder.addCommitments(convertElementModP(proof.public_key));
    }
    return builder.build();
  }

}
