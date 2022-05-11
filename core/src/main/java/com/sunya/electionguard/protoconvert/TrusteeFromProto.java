package com.sunya.electionguard.protoconvert;

import com.google.common.collect.ImmutableList;
import com.sunya.electionguard.ElGamal;
import com.sunya.electionguard.Group;
import com.sunya.electionguard.decrypting.DecryptingTrustee;
import com.sunya.electionguard.keyceremony.KeyCeremony2;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import electionguard.protogen.CommonProto;
import electionguard.protogen.ElectionRecordProto;
import electionguard.protogen.TrusteeProto;

public class TrusteeFromProto {

  public static DecryptingTrustee readTrustee(String filename) throws IOException {
    TrusteeProto.DecryptingTrustee proto;
    try (FileInputStream inp = new FileInputStream(filename)) {
      proto = TrusteeProto.DecryptingTrustee.parseFrom(inp);
    }
    return importDecryptingTrustee(proto);
  }

  public static DecryptingTrustee importDecryptingTrustee(TrusteeProto.DecryptingTrustee proto) {
    String guardian_id = proto.getGuardianId();
    int sequence_order = proto.getGuardianXCoordinate();
    ElGamal.KeyPair election_keypair = convertElgamalKeypair(proto.getElectionKeypair());

    Map<String, KeyCeremony2.PartialKeyBackup> otherGuardianPartialKeyBackups =
            proto.getSecretKeySharesList().stream()
                    .collect(Collectors.toMap(p -> p.getGeneratingGuardianId(), p -> convertElectionPartialKeyBackup(p)));

    Map<String, List<Group.ElementModP>> commitments =
            proto.getCoefficientCommitmentsList().stream()
                    .collect(Collectors.toMap(p -> p.getGuardianId(), p -> convertCoefficients(p)));

    return new DecryptingTrustee(guardian_id, sequence_order, election_keypair,
            otherGuardianPartialKeyBackups, commitments);
  }

  private static KeyCeremony2.PartialKeyBackup convertElectionPartialKeyBackup(TrusteeProto.SecretKeyShare proto) {
    return new KeyCeremony2.PartialKeyBackup(
            proto.getGeneratingGuardianId(),
            proto.getDesignatedGuardianId(),
            proto.getDesignatedGuardianXCoordinate(),
            CommonConvert.importElementModQ(proto.getGeneratingGuardianValue()),
            proto.getError());
  }

  private static ElGamal.KeyPair convertElgamalKeypair(TrusteeProto.ElGamalKeypair keypair) {
    return new ElGamal.KeyPair(
            CommonConvert.importElementModQ(keypair.getSecretKey()),
            CommonConvert.importElementModP(keypair.getPublicKey()));
  }

  private static ImmutableList<Group.ElementModP> convertCoefficients(TrusteeProto.CommitmentSet proto) {
    ImmutableList.Builder<Group.ElementModP> builder = ImmutableList.builder();
    for (CommonProto.ElementModP commit : proto.getCommitmentsList()) {
      builder.add(CommonConvert.importElementModP(commit));
    }
    return builder.build();
  }

}
