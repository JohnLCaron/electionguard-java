package com.sunya.electionguard.proto;

import com.google.common.collect.ImmutableList;
import com.google.protobuf.ByteString;
import com.sunya.electionguard.ElGamal;
import com.sunya.electionguard.ElectionPolynomial;
import com.sunya.electionguard.Guardian;
import com.sunya.electionguard.GuardianBuilder;
import com.sunya.electionguard.KeyCeremony;
import com.sunya.electionguard.Rsa;
import com.sunya.electionguard.guardian.RemoteTrustee;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.List;

import static com.sunya.electionguard.proto.CommonConvert.convertElementModP;
import static com.sunya.electionguard.proto.CommonConvert.convertElementModQ;
import static com.sunya.electionguard.proto.CommonConvert.convertSchnorrProof;

public class TrusteeFromProto {

  public static ImmutableList<RemoteTrustee> readTrustees(String filename) throws IOException {
    TrusteeProto.Trustees proto;
    try (FileInputStream inp = new FileInputStream(filename)) {
      proto = TrusteeProto.Trustees.parseDelimitedFrom(inp);
    }
    return convertTrustees(proto);
  }

  private static ImmutableList<RemoteTrustee> convertTrustees(TrusteeProto.Trustees proto) {
    int quorum = proto.getQuorum();
    int count = proto.getTrusteesCount();
    ImmutableList.Builder<Guardian> builder = ImmutableList.builder();
    for (TrusteeProto.Trustee guardianProto : proto.getTrusteesList()) {
      builder.add(convertTrustee(guardianProto, nguardians, quorum));
    }
    return builder.build();
  }

  private static RemoteTrustee convertTrustee(TrusteeProto.Trustee proto, int nguardians, int quorum) {

    KeyCeremony.CoefficientSet coefficients = convertCoefficients(proto.getCoefficients());
    GuardianBuilder builder = new GuardianBuilder(coefficients, nguardians, quorum,
            convertAuxiliaryKeyPair(proto.getAuxiliaryKeys()),
            convertElectionKeys(proto.getElectionKeys()));

    proto.getOtherGuardianAuxiliaryKeysList()
            .forEach(k -> builder.save_auxiliary_public_key(convertAuxiliaryPublicKey(k)));
    proto.getOtherGuardianElectionKeysList()
            .forEach(k -> builder.save_election_public_key(convertElectionPublicKey(k)));
    proto.getOtherGuardianBackupsList()
            .forEach(b -> builder.save_election_partial_key_backup(convertElectionPartialKeyBackup(b)));

    // Generate partial key backups based on existing public keys
    builder.generate_election_partial_key_backups(null);
    return builder.build();
  }

  private static TrusteeProto.GuardianSecrets convertGuardianSecrets(RemoteTrustee.GuardianSecrets org) {
    TrusteeProto.GuardianSecrets.Builder builder = TrusteeProto.GuardianSecrets.newBuilder();
    builder.setPolynomial(convertElectionPolynomial(org.polynomial));
    builder.setKeyPair(convertElgamalKeypair(org.election_key_pair));
    builder.setProof(CommonConvert.convertSchnorrProof(org.proof));
    builder.setRsaKeypair(convertRsaKeypair(org.rsa_keypair));
    return builder.build();
  }

  private static TrusteeProto.PublicKeySet convertPublicKeySet(KeyCeremony.PublicKeySet org) {
    TrusteeProto.PublicKeySet.Builder builder = TrusteeProto.PublicKeySet.newBuilder();
    builder.setOwnerId(org.owner_id());
    builder.setSequenceOrder(org.sequence_order());
    builder.setRsaPublicKey(convertJavaPublicKey(org.auxiliary_public_key()));
    builder.setElectionPublicKey(convertElementModP(org.election_public_key()));
    builder.setElectionPublicKeyProof(CommonConvert.convertSchnorrProof(org.election_public_key_proof()));
    return builder.build();
  }

  private static KeyCeremonyProto.ElectionPartialKeyBackup convertElectionPartialKeyBackup(KeyCeremony.ElectionPartialKeyBackup org) {
    KeyCeremonyProto.ElectionPartialKeyBackup.Builder builder = KeyCeremonyProto.ElectionPartialKeyBackup.newBuilder();
    builder.setOwnerId(org.owner_id());
    builder.setDesignatedId(org.designated_id());
    builder.setDesignatedSequenceOrder(org.designated_sequence_order());
    builder.setEncryptedValue(ByteString.copyFrom(org.encrypted_value().getBytes()));
    org.coefficient_commitments().forEach(c -> builder.addCoefficientCommitments(convertElementModP(c)));
    org.coefficient_proofs().forEach(p -> builder.addCoefficientProofs(convertSchnorrProof(p)));
    return builder.build();
  }

  private static KeyCeremonyProto.ElectionPolynomial convertElectionPolynomial(ElectionPolynomial org) {
    KeyCeremonyProto.ElectionPolynomial.Builder builder = KeyCeremonyProto.ElectionPolynomial.newBuilder();
    org.coefficients.forEach(c -> builder.addCoefficients(convertElementModQ(c)));
    org.coefficient_commitments.forEach(c -> builder.addCoefficientCommitments(convertElementModP(c)));
    org.coefficient_proofs.forEach(p -> builder.addCoefficientProofs(convertSchnorrProof(p)));
    return builder.build();
  }

  private static KeyCeremonyProto.ElGamalKeyPair convertElgamalKeypair(ElGamal.KeyPair keypair) {
    KeyCeremonyProto.ElGamalKeyPair.Builder builder = KeyCeremonyProto.ElGamalKeyPair.newBuilder();
    builder.setSecretKey(CommonConvert.convertElementModQ(keypair.secret_key));
    builder.setPublicKey(CommonConvert.convertElementModP(keypair.public_key));
    return builder.build();
  }

  // LOOK there may be something better to do when serializing. Find out before use in production.
  private static TrusteeProto.RsaKeyPair convertRsaKeypair(java.security.KeyPair key) {
    TrusteeProto.RsaKeyPair.Builder builder = TrusteeProto.RsaKeyPair.newBuilder();
    builder.setPublicKey(convertJavaPublicKey(key.getPublic()));
    builder.setPrivateKey(convertJavaPrivateKey(key.getPrivate()));
    return builder.build();
  }

  // LOOK there may be something better to do when serializing. Find out before use in production.
  private static KeyCeremonyProto.RSAPublicKey convertJavaPublicKey(java.security.PublicKey key) {
    Rsa.KeyPieces pieces = Rsa.convertJavaPublicKey(key);
    KeyCeremonyProto.RSAPublicKey.Builder builder = KeyCeremonyProto.RSAPublicKey.newBuilder();
    builder.setModulus(ByteString.copyFrom(pieces.modulus.toByteArray()));
    builder.setPublicExponent(ByteString.copyFrom(pieces.exponent.toByteArray()));
    return builder.build();
  }

  // LOOK there may be something better to do when serializing. Find out before use in production.
  private static KeyCeremonyProto.RSAPrivateKey convertJavaPrivateKey(java.security.PrivateKey key) {
    Rsa.KeyPieces pieces = Rsa.convertJavaPrivateKey(key);
    KeyCeremonyProto.RSAPrivateKey.Builder builder = KeyCeremonyProto.RSAPrivateKey.newBuilder();
    builder.setModulus(ByteString.copyFrom(pieces.modulus.toByteArray()));
    builder.setPrivateExponent(ByteString.copyFrom(pieces.exponent.toByteArray()));
    return builder.build();
  }

}
