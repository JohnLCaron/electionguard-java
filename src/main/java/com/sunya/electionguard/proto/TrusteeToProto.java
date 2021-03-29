package com.sunya.electionguard.proto;

import com.google.protobuf.ByteString;
import com.sunya.electionguard.ElGamal;
import com.sunya.electionguard.guardian.KeyCeremony2;
import com.sunya.electionguard.guardian.KeyCeremonyTrustee;

import java.util.List;

import static com.sunya.electionguard.proto.CommonConvert.convertElementModP;

public class TrusteeToProto {

  public static TrusteeProto.Trustees convertTrustees(List<KeyCeremonyTrustee> trustees) {
    TrusteeProto.Trustees.Builder builder = TrusteeProto.Trustees.newBuilder();
    trustees.forEach(t -> builder.addTrustees(convertTrustee(t)));
    return builder.build();
  }

  private static TrusteeProto.Trustee convertTrustee(KeyCeremonyTrustee trustee) {
    TrusteeProto.Trustee.Builder builder = TrusteeProto.Trustee.newBuilder();
    builder.setGuardianId(trustee.id);
    builder.setGuardianXCoordinate(trustee.xCoordinate);
    builder.setElectionKeyPair(convertElgamalKeypair(trustee.secrets().election_key_pair));
    builder.setRsaPrivateKey(CommonConvert.convertJavaPrivateKey(trustee.secrets().rsa_keypair.getPrivate()));
    trustee.otherGuardianPartialKeyBackups.values().forEach(k -> builder.addOtherGuardianBackups(convertElectionPartialKeyBackup(k)));
    trustee.guardianSecrets.polynomial.coefficient_commitments.forEach(k -> builder.addCoefficientCommitments(convertElementModP(k)));

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

  private static KeyCeremonyProto.ElGamalKeyPair convertElgamalKeypair(ElGamal.KeyPair keypair) {
    KeyCeremonyProto.ElGamalKeyPair.Builder builder = KeyCeremonyProto.ElGamalKeyPair.newBuilder();
    builder.setSecretKey(CommonConvert.convertElementModQ(keypair.secret_key));
    builder.setPublicKey(CommonConvert.convertElementModP(keypair.public_key));
    return builder.build();
  }

}
