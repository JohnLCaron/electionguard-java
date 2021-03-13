package com.sunya.electionguard.proto;

import com.google.protobuf.ByteString;
import com.sunya.electionguard.ElGamal;
import com.sunya.electionguard.KeyCeremony;
import com.sunya.electionguard.Rsa;
import com.sunya.electionguard.guardian.KeyCeremonyTrustee;

import java.util.List;

import static com.sunya.electionguard.proto.CommonConvert.convertElementModP;
import static com.sunya.electionguard.proto.CommonConvert.convertSchnorrProof;

public class TrusteeToProto {

  public static TrusteeProto.Trustees convertTrustees(List<KeyCeremonyTrustee> trustees) {
    TrusteeProto.Trustees.Builder builder = TrusteeProto.Trustees.newBuilder();
    trustees.forEach(t -> builder.addTrustees(convertTrustee(t)));
    return builder.build();
  }

  private static TrusteeProto.Trustee convertTrustee(KeyCeremonyTrustee trustee) {
    TrusteeProto.Trustee.Builder builder = TrusteeProto.Trustee.newBuilder();
    builder.setGuardianId(trustee.id);
    builder.setGuardianSequence(trustee.sequence_order);
    builder.setElectionKeyPair(convertElgamalKeypair(trustee.secrets().election_key_pair));
    builder.setRsaPrivateKey(convertJavaPrivateKey(trustee.secrets().rsa_keypair.getPrivate()));
    trustee.otherGuardianPartialKeyBackups.values().forEach(k -> builder.addOtherGuardianBackups(convertElectionPartialKeyBackup(k)));

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

  private static KeyCeremonyProto.ElGamalKeyPair convertElgamalKeypair(ElGamal.KeyPair keypair) {
    KeyCeremonyProto.ElGamalKeyPair.Builder builder = KeyCeremonyProto.ElGamalKeyPair.newBuilder();
    builder.setSecretKey(CommonConvert.convertElementModQ(keypair.secret_key));
    builder.setPublicKey(CommonConvert.convertElementModP(keypair.public_key));
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
