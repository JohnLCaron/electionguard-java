package com.sunya.electionguard.proto;

import com.google.common.collect.ImmutableList;
import com.sunya.electionguard.Auxiliary;
import com.sunya.electionguard.ElGamal;
import com.sunya.electionguard.KeyCeremony;
import com.sunya.electionguard.Rsa;
import com.sunya.electionguard.guardian.DecryptingTrustee;

import java.io.FileInputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.util.Map;
import java.util.stream.Collectors;

public class TrusteeFromProto {

  public static ImmutableList<DecryptingTrustee> readTrustees(String filename) throws IOException {
    TrusteeProto.Trustees proto;
    try (FileInputStream inp = new FileInputStream(filename)) {
      proto = TrusteeProto.Trustees.parseDelimitedFrom(inp);
    }
    return convertTrustees(proto);
  }

  private static ImmutableList<DecryptingTrustee> convertTrustees(TrusteeProto.Trustees proto) {
    ImmutableList.Builder<DecryptingTrustee> builder = ImmutableList.builder();
    for (TrusteeProto.Trustee guardianProto : proto.getTrusteesList()) {
      builder.add(convertTrustee(guardianProto));
    }
    return builder.build();
  }

  private static DecryptingTrustee convertTrustee(TrusteeProto.Trustee proto) {

    String guardian_id = proto.getGuardianId();
    int sequence_order = proto.getGuardianSequence();
    java.security.PrivateKey rsa_private_key = convertJavaPrivateKey(proto.getRsaPrivateKey());
    ElGamal.KeyPair election_keypair = convertElgamalKeypair(proto.getElectionKeyPair());

    Map<String, KeyCeremony.ElectionPartialKeyBackup> otherGuardianPartialKeyBackups =
            proto.getOtherGuardianBackupsList().stream()
                    .collect(Collectors.toMap(p -> p.getOwnerId(), p -> convertElectionPartialKeyBackup(p)));

    return new DecryptingTrustee(guardian_id, sequence_order, rsa_private_key, election_keypair, otherGuardianPartialKeyBackups);
  }

  private static KeyCeremony.ElectionPartialKeyBackup convertElectionPartialKeyBackup(KeyCeremonyProto.ElectionPartialKeyBackup proto) {
    return KeyCeremony.ElectionPartialKeyBackup.create(
            proto.getOwnerId(),
            proto.getDesignatedId(),
            proto.getDesignatedSequenceOrder(),
            new Auxiliary.ByteString(proto.getEncryptedValue().toByteArray()),
            CommonConvert.convertList(proto.getCoefficientCommitmentsList(), CommonConvert::convertElementModP),
            CommonConvert.convertList(proto.getCoefficientProofsList(), CommonConvert::convertSchnorrProof));
  }

  private static ElGamal.KeyPair convertElgamalKeypair(KeyCeremonyProto.ElGamalKeyPair keypair) {
    return new ElGamal.KeyPair(
            CommonConvert.convertElementModQ(keypair.getSecretKey()),
            CommonConvert.convertElementModP(keypair.getPublicKey()));
  }

  // LOOK there may be something better to do when serializing. Find out before use in production.
  private static java.security.PrivateKey convertJavaPrivateKey(KeyCeremonyProto.RSAPrivateKey proto) {
    BigInteger privateExponent = new BigInteger(proto.getPrivateExponent().toByteArray());
    BigInteger modulus = new BigInteger(proto.getModulus().toByteArray());
    return Rsa.convertJavaPrivateKey(modulus, privateExponent);
  }

}
