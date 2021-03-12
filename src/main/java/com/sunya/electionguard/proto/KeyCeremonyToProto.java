package com.sunya.electionguard.proto;

import com.google.protobuf.ByteString;
import com.sunya.electionguard.Auxiliary;
import com.sunya.electionguard.ElGamal;
import com.sunya.electionguard.ElectionPolynomial;
import com.sunya.electionguard.Group;
import com.sunya.electionguard.Guardian;
import com.sunya.electionguard.KeyCeremony;
import com.sunya.electionguard.Rsa;

import java.util.List;

import static com.sunya.electionguard.proto.CommonConvert.convertElementModP;
import static com.sunya.electionguard.proto.CommonConvert.convertElementModQ;
import static com.sunya.electionguard.proto.CommonConvert.convertSchnorrProof;

public class KeyCeremonyToProto {

  public static KeyCeremonyProto.Guardians convertGuardians(List<Guardian> guardians, int quorum) {
    KeyCeremonyProto.Guardians.Builder builder = KeyCeremonyProto.Guardians.newBuilder();
    builder.setQuorum(quorum);
    guardians.forEach(g -> builder.addGuardians(convertGuardian(g)));
    return builder.build();
  }

  private static KeyCeremonyProto.Guardian convertGuardian(Guardian guardian) {
    KeyCeremonyProto.Guardian.Builder builder = KeyCeremonyProto.Guardian.newBuilder();
    builder.setCoefficients(convertCoefficients(guardian.coefficients()));
    builder.setAuxiliaryKeys(convertAuxiliaryKeyPair(guardian.auxiliary_keys()));
    builder.setElectionKeys(convertElectionKeys(guardian.election_keys()));

    guardian.auxiliary_public_keys().forEach(k -> builder.addOtherGuardianAuxiliaryKeys(convertAuxiliaryPublicKey(k)));
    guardian.election_public_keys().forEach(k -> builder.addOtherGuardianElectionKeys(convertElectionPublicKey(k)));
    guardian.election_partial_key_backups().forEach(k -> builder.addOtherGuardianBackups(convertElectionPartialKeyBackup(k)));

    return builder.build();
  }

  private static KeyCeremonyProto.AuxiliaryKeyPair convertAuxiliaryKeyPair(Auxiliary.KeyPair org) {
    KeyCeremonyProto.AuxiliaryKeyPair.Builder builder = KeyCeremonyProto.AuxiliaryKeyPair.newBuilder();
    builder.setSecretKey(convertJavaPrivateKey(org.secret_key)); // LOOK do we really need the private key ??
    builder.setPublicKey(convertJavaPublicKey(org.public_key));
    return builder.build();
  }

  private static KeyCeremonyProto.AuxiliaryPublicKey convertAuxiliaryPublicKey(Auxiliary.PublicKey org) {
    KeyCeremonyProto.AuxiliaryPublicKey.Builder builder = KeyCeremonyProto.AuxiliaryPublicKey.newBuilder();
    builder.setOwnerId(org.owner_id);
    builder.setSequenceOrder(org.sequence_order);
    builder.setKey(convertJavaPublicKey(org.key));
    return builder.build();
  }

  private static KeyCeremonyProto.ElectionKeyPair convertElectionKeys(KeyCeremony.ElectionKeyPair org) {
    KeyCeremonyProto.ElectionKeyPair.Builder builder = KeyCeremonyProto.ElectionKeyPair.newBuilder();
    builder.setKeyPair(convertElgamalKeypair(org.key_pair()));
    builder.setProof(CommonConvert.convertSchnorrProof(org.proof()));
    builder.setPolynomial(convertElectionPolynomial(org.polynomial()));
    return builder.build();
  }

  private static KeyCeremonyProto.ElectionPublicKey convertElectionPublicKey(KeyCeremony.ElectionPublicKey org) {
    KeyCeremonyProto.ElectionPublicKey.Builder builder = KeyCeremonyProto.ElectionPublicKey.newBuilder();
    builder.setOwnerId(org.owner_id());
    builder.setSequenceOrder(org.sequence_order());
    builder.setProof(convertSchnorrProof(org.proof()));
    builder.setKey(convertElementModP(org.publicKey()));
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

  /////////////////////////////////////////////////////////////////////////////////////////////////////////

  public static KeyCeremonyProto.CoefficientSets convertCoefficientSet(List<KeyCeremony.CoefficientSet> coeffSets) {
    KeyCeremonyProto.CoefficientSets.Builder builder = KeyCeremonyProto.CoefficientSets.newBuilder();
    for (KeyCeremony.CoefficientSet coeffSet : coeffSets) {
      builder.addGuardianSets(convertCoefficients(coeffSet));
    }
    return builder.build();
  }

  private static KeyCeremonyProto.CoefficientSet convertCoefficients(KeyCeremony.CoefficientSet coeffSet) {
    KeyCeremonyProto.CoefficientSet.Builder builder = KeyCeremonyProto.CoefficientSet.newBuilder();
    builder.setGuardianId(coeffSet.guardianId());
    builder.setGuardianSequence(coeffSet.guardianSequence());
    for (Group.ElementModQ coeff : coeffSet.coefficients()) {
      builder.addCoefficients(convertElementModQ(coeff));
    }
    return builder.build();
  }

}
