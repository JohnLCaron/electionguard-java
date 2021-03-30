package com.sunya.electionguard.proto;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.sunya.electionguard.Auxiliary;
import com.sunya.electionguard.ElGamal;
import com.sunya.electionguard.Group;
import com.sunya.electionguard.guardian.DecryptingTrustee;
import com.sunya.electionguard.guardian.KeyCeremony2;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

import static com.sunya.electionguard.proto.CommonConvert.convertElementModP;

public class TrusteeFromProto {

  public static ImmutableList<DecryptingTrustee> readTrustees(String filename) throws IOException {
    TrusteeProto.Trustees proto;
    try (FileInputStream inp = new FileInputStream(filename)) {
      proto = TrusteeProto.Trustees.parseDelimitedFrom(inp);
    }
    return convertTrustees(proto);
  }

  public static DecryptingTrustee readTrustee(String filename) throws IOException {
    TrusteeProto.Trustee proto;
    try (FileInputStream inp = new FileInputStream(filename)) {
      proto = TrusteeProto.Trustee.parseDelimitedFrom(inp);
    }
    return convertTrustee(proto, ImmutableMap.of());
  }

  private static ImmutableList<DecryptingTrustee> convertTrustees(TrusteeProto.Trustees proto) {
    Map<String, ImmutableList<Group.ElementModP>> commitments = new HashMap<>();
    for (TrusteeProto.Trustee guardianProto : proto.getTrusteesList()) {
      convertCommitments(guardianProto, commitments);
    }

    ImmutableList.Builder<DecryptingTrustee> builder = ImmutableList.builder();
    for (TrusteeProto.Trustee guardianProto : proto.getTrusteesList()) {
      builder.add(convertTrustee(guardianProto, commitments));
    }
    return builder.build();
  }

  private static void convertCommitments(TrusteeProto.Trustee proto,
                                         Map<String, ImmutableList<Group.ElementModP>> result) {
    ImmutableList.Builder<Group.ElementModP> builder = ImmutableList.builder();
    for (CommonProto.ElementModP commitment : proto.getCoefficientCommitmentsList()) {
      builder.add(convertElementModP(commitment));
    }
    result.put(proto.getGuardianId(), builder.build());
  }

  private static DecryptingTrustee convertTrustee(TrusteeProto.Trustee proto,
                                                  Map<String, ImmutableList<Group.ElementModP>> commitments) {

    String guardian_id = proto.getGuardianId();
    int sequence_order = proto.getGuardianXCoordinate();
    java.security.PrivateKey rsa_private_key = CommonConvert.convertJavaPrivateKey(proto.getRsaPrivateKey());
    ElGamal.KeyPair election_keypair = convertElgamalKeypair(proto.getElectionKeyPair());

    Map<String, KeyCeremony2.PartialKeyBackup> otherGuardianPartialKeyBackups =
            proto.getOtherGuardianBackupsList().stream()
                    .collect(Collectors.toMap(p -> p.getGeneratingGuardianId(), p -> convertElectionPartialKeyBackup(p)));

    return new DecryptingTrustee(guardian_id, sequence_order, rsa_private_key, election_keypair,
            otherGuardianPartialKeyBackups, commitments);
  }

  private static KeyCeremony2.PartialKeyBackup convertElectionPartialKeyBackup(TrusteeProto.ElectionPartialKeyBackup2 proto) {
    return KeyCeremony2.PartialKeyBackup.create(
            proto.getGeneratingGuardianId(),
            proto.getDesignatedGuardianId(),
            proto.getDesignatedGuardianXCoordinate(),
            new Auxiliary.ByteString(proto.getEncryptedCoordinate().toByteArray()));
  }

  private static ElGamal.KeyPair convertElgamalKeypair(KeyCeremonyProto.ElGamalKeyPair keypair) {
    return new ElGamal.KeyPair(
            CommonConvert.convertElementModQ(keypair.getSecretKey()),
            convertElementModP(keypair.getPublicKey()));
  }

}
