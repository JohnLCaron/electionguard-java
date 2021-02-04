package com.sunya.electionguard.proto;

import com.google.protobuf.ByteString;
import com.sunya.electionguard.Auxiliary;
import com.sunya.electionguard.Group;
import com.sunya.electionguard.Guardian;
import com.sunya.electionguard.KeyCeremony;

import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.List;

import static com.sunya.electionguard.proto.CommonConvert.convertElementModP;
import static com.sunya.electionguard.proto.CommonConvert.convertElementModQ;
import static com.sunya.electionguard.proto.CommonConvert.convertSchnorrProof;

public class KeyCeremonyToProto {

  public static KeyCeremonyProto.Guardians convertGuardians(List<Guardian> guardians, int quorum, Group.ElementModQ crypto_base_hash) {
    KeyCeremonyProto.Guardians.Builder builder = KeyCeremonyProto.Guardians.newBuilder();
    builder.setQuorum(quorum);
    builder.setCryptoBaseHash(convertElementModQ(crypto_base_hash));
    guardians.forEach(g -> builder.addGuardians(convertGuardian(g)));
    return builder.build();
  }

  private static KeyCeremonyProto.Guardian convertGuardian(Guardian guardian) {
    KeyCeremonyProto.Guardian.Builder builder = KeyCeremonyProto.Guardian.newBuilder();
    builder.setCoefficients(convertCoefficients(guardian.coefficients()));
    builder.setAuxiliaryKeys(convertAuxiliaryKeyPair(guardian.auxiliary_keys()));

    guardian.auxiliary_public_keys().forEach(k -> builder.addOtherGuardianAuxiliaryKeys(convertAuxiliaryPublicKey(k)));
    guardian.election_public_keys().forEach(k -> builder.addOtherGuardianElectionKeys(convertElectionPublicKey(k)));
    guardian.election_partial_key_backups().forEach(k -> builder.addOtherGuardianBackups(convertElectionPartialKeyBackup(k)));

    return builder.build();
  }

  private static KeyCeremonyProto.AuxiliaryKeyPair convertAuxiliaryKeyPair(Auxiliary.KeyPair org) {
    KeyCeremonyProto.AuxiliaryKeyPair.Builder builder = KeyCeremonyProto.AuxiliaryKeyPair.newBuilder();
    builder.setSecretKey(convertJavaPrivateKey(org.secret_key));
    builder.setPublicKey(convertJavaPublicKey(org.public_key));
    return builder.build();
  }

  private static KeyCeremonyProto.AuxiliaryPublicKey convertAuxiliaryPublicKey( Auxiliary.PublicKey org) {
    KeyCeremonyProto.AuxiliaryPublicKey.Builder builder = KeyCeremonyProto.AuxiliaryPublicKey.newBuilder();
    builder.setOwnerId(org.owner_id);
    builder.setSequenceOrder(org.sequence_order);
    builder.setKey(convertJavaPublicKey(org.key));
    return builder.build();
  }

  private static KeyCeremonyProto.ElectionPublicKey convertElectionPublicKey(KeyCeremony.ElectionPublicKey org) {
    KeyCeremonyProto.ElectionPublicKey.Builder builder = KeyCeremonyProto.ElectionPublicKey.newBuilder();
    builder.setOwnerId(org.owner_id());
    builder.setSequenceOrder(org.sequence_order());
    builder.setProof(convertSchnorrProof(org.proof()));
    builder.setKey(convertElementModP(org.key()));
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

  // LOOK there may be something better to do when serializing. Find out before use in production.
  private static KeyCeremonyProto.RSAPublicKey convertJavaPublicKey(java.security.PublicKey key) {
    RSAPublicKey publicKey = (RSAPublicKey) key;
    KeyCeremonyProto.RSAPublicKey.Builder builder = KeyCeremonyProto.RSAPublicKey.newBuilder();
    builder.setModulus(ByteString.copyFrom(publicKey.getModulus().toByteArray()));
    builder.setPublicExponent(ByteString.copyFrom(publicKey.getPublicExponent().toByteArray()));
    return builder.build();
  }

  private static KeyCeremonyProto.RSAPrivateKey convertJavaPrivateKey(java.security.PrivateKey key) {
    RSAPrivateKey privateKey = (RSAPrivateKey) key;
    KeyCeremonyProto.RSAPrivateKey.Builder builder = KeyCeremonyProto.RSAPrivateKey.newBuilder();
    builder.setModulus(ByteString.copyFrom(privateKey.getModulus().toByteArray()));
    builder.setPrivateExponent(ByteString.copyFrom(privateKey.getPrivateExponent().toByteArray()));
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
