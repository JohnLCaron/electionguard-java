package com.sunya.electionguard.proto;

import com.google.common.collect.ImmutableList;
import com.sunya.electionguard.Auxiliary;
import com.sunya.electionguard.ElGamal;
import com.sunya.electionguard.ElectionPolynomial;
import com.sunya.electionguard.Group;
import com.sunya.electionguard.Guardian;
import com.sunya.electionguard.GuardianBuilder;
import com.sunya.electionguard.KeyCeremony;

import java.io.FileInputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.RSAPrivateKeySpec;
import java.security.spec.RSAPublicKeySpec;
import java.util.List;
import java.util.stream.Collectors;

public class KeyCeremonyFromProto {

  public static ImmutableList<Guardian> readGuardians(String filename) throws IOException {
    KeyCeremonyProto.Guardians proto;
    try (FileInputStream inp = new FileInputStream(filename)) {
      proto = KeyCeremonyProto.Guardians.parseDelimitedFrom(inp);
    }
    return convertGuardians(proto);
  }

  private static ImmutableList<Guardian> convertGuardians(KeyCeremonyProto.Guardians proto) {
    int quorum = proto.getQuorum();
    int nguardians = proto.getGuardiansCount();
    ImmutableList.Builder<Guardian> builder = ImmutableList.builder();
    for (KeyCeremonyProto.Guardian guardianProto : proto.getGuardiansList()) {
      builder.add(convertGuardian(guardianProto, nguardians, quorum));
    }
    return builder.build();
  }

  private static Guardian convertGuardian(KeyCeremonyProto.Guardian proto, int nguardians, int quorum) {

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

  private static Auxiliary.KeyPair convertAuxiliaryKeyPair(KeyCeremonyProto.AuxiliaryKeyPair proto) {
    return new Auxiliary.KeyPair(
            convertJavaPrivateKey(proto.getSecretKey()),
            convertJavaPublicKey(proto.getPublicKey()));
  }

  private static Auxiliary.PublicKey convertAuxiliaryPublicKey(KeyCeremonyProto.AuxiliaryPublicKey proto) {
    return new Auxiliary.PublicKey(
            proto.getOwnerId(),
            proto.getSequenceOrder(),
            convertJavaPublicKey(proto.getKey()));
  }

  private static KeyCeremony.ElectionKeyPair convertElectionKeys(KeyCeremonyProto.ElectionKeyPair proto) {
    return KeyCeremony.ElectionKeyPair.create(
            convertElgamalKeypair(proto.getKeyPair()),
            CommonConvert.convertSchnorrProof(proto.getProof()),
            convertElectionPolynomial(proto.getPolynomial()));
  }

  private static KeyCeremony.ElectionPublicKey convertElectionPublicKey(KeyCeremonyProto.ElectionPublicKey proto) {
    return KeyCeremony.ElectionPublicKey.create(
            proto.getOwnerId(),
            proto.getSequenceOrder(),
            CommonConvert.convertSchnorrProof(proto.getProof()),
            CommonConvert.convertElementModP(proto.getKey()));
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

  private static ElectionPolynomial convertElectionPolynomial(KeyCeremonyProto.ElectionPolynomial proto) {
    return new ElectionPolynomial(
            CommonConvert.convertList(proto.getCoefficientsList(), CommonConvert::convertElementModQ),
            CommonConvert.convertList(proto.getCoefficientCommitmentsList(), CommonConvert::convertElementModP),
            CommonConvert.convertList(proto.getCoefficientProofsList(), CommonConvert::convertSchnorrProof));
  }

  private static ElGamal.KeyPair convertElgamalKeypair(KeyCeremonyProto.ElGamalKeyPair keypair) {
    return new ElGamal.KeyPair(
            CommonConvert.convertElementModQ(keypair.getSecretKey()),
            CommonConvert.convertElementModP(keypair.getPublicKey()));
  }

  // LOOK there may be something better to do when serializing. Find out before use in production.
  private static java.security.PublicKey convertJavaPublicKey(KeyCeremonyProto.RSAPublicKey proto) {
    BigInteger publicExponent = new BigInteger(proto.getPublicExponent().toByteArray());
    BigInteger modulus = new BigInteger(proto.getModulus().toByteArray());
    RSAPublicKeySpec Spec = new RSAPublicKeySpec(modulus, publicExponent);
    try {
      return KeyFactory.getInstance("RSA").generatePublic(Spec);
    } catch (InvalidKeySpecException | NoSuchAlgorithmException e ) {
      throw new RuntimeException(e);
    }
  }

  private static java.security.PrivateKey convertJavaPrivateKey(KeyCeremonyProto.RSAPrivateKey proto) {
    BigInteger privateExponent = new BigInteger(proto.getPrivateExponent().toByteArray());
    BigInteger modulus = new BigInteger(proto.getModulus().toByteArray());
    RSAPrivateKeySpec spec = new RSAPrivateKeySpec(modulus, privateExponent); // or RSAPrivateCrtKeySpec ?
    try {
      return KeyFactory.getInstance("RSA").generatePrivate(spec);
    } catch (InvalidKeySpecException | NoSuchAlgorithmException e ) {
      throw new RuntimeException(e);
    }
  }

  /////////////////////////////////////////////////////////////////////////////////////////////////////////

  public static ImmutableList<KeyCeremony.CoefficientSet> readCoefficientSet(String filename) throws IOException {
    KeyCeremonyProto.CoefficientSets proto;
    try (FileInputStream inp = new FileInputStream(filename)) {
      proto = KeyCeremonyProto.CoefficientSets.parseDelimitedFrom(inp);
    }
    return convertCoefficientSet(proto);
  }

  private static ImmutableList<KeyCeremony.CoefficientSet> convertCoefficientSet(KeyCeremonyProto.CoefficientSets coeffSets) {
    ImmutableList.Builder<KeyCeremony.CoefficientSet> builder = ImmutableList.builder();
    for (KeyCeremonyProto.CoefficientSet coeffSet : coeffSets.getGuardianSetsList()) {
      builder.add(convertCoefficients(coeffSet));
    }
    return builder.build();
  }

  private static KeyCeremony.CoefficientSet convertCoefficients(KeyCeremonyProto.CoefficientSet coeff) {
    List<Group.ElementModQ> coefficients = coeff.getCoefficientsList().stream()
            .map(CommonConvert::convertElementModQ)
            .collect(Collectors.toList());

    return KeyCeremony.CoefficientSet.create(
            coeff.getGuardianId(),
            coeff.getGuardianSequence(),
            coefficients);
  }
}
