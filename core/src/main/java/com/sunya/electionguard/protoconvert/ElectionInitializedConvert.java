package com.sunya.electionguard.protoconvert;

import com.sunya.electionguard.Group;
import com.sunya.electionguard.SchnorrProof;
import electionguard.ballot.ElectionConfig;
import electionguard.ballot.ElectionInitialized;
import electionguard.ballot.Guardian;
import electionguard.protogen.ElectionRecordProto;

import javax.annotation.concurrent.Immutable;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.List;

import static com.sunya.electionguard.protoconvert.CommonConvert.publishElementModP;
import static com.sunya.electionguard.protoconvert.CommonConvert.publishSchnorrProof;
import static com.sunya.electionguard.protoconvert.ElectionConfigConvert.importElectionConfig;
import static com.sunya.electionguard.protoconvert.ElectionConfigConvert.publishElectionConfig;
import static com.sunya.electionguard.protoconvert.ManifestToProto.publishManifest;

@Immutable
public class ElectionInitializedConvert {

  public static ElectionInitialized read(String filename) throws IOException {
    ElectionRecordProto.ElectionInitialized proto;
    try (FileInputStream inp = new FileInputStream(filename)) {
      proto = ElectionRecordProto.ElectionInitialized.parseFrom(inp);
    }
    return importElectionInitialized(proto);
  }

  public static ElectionInitialized importElectionInitialized(ElectionRecordProto.ElectionInitialized proto) {
    ElectionConfig config = importElectionConfig(proto.getConfig());

    List<Guardian> guardians =
            proto.getGuardiansList().stream()
                    .map(ElectionInitializedConvert::importGuardian)
                    .toList();

    return new ElectionInitialized(
            config,
            CommonConvert.importElementModP(proto.getJointPublicKey()),
            CommonConvert.importUInt256(proto.getManifestHash()),
            CommonConvert.importUInt256(proto.getCryptoExtendedBaseHash()),
            guardians,
            proto.getMetadataMap());
  }

  static Guardian importGuardian(ElectionRecordProto.Guardian guardianRecord) {
   List<Group.ElementModP> coefficient_commitments = guardianRecord.getCoefficientCommitmentsList().stream()
            .map(CommonConvert::importElementModP)
           .toList();
    List<SchnorrProof> coefficient_proofs = guardianRecord.getCoefficientProofsList().stream()
            .map(CommonConvert::importSchnorrProof)
            .toList();
    return new Guardian(
            guardianRecord.getGuardianId(),
            guardianRecord.getXCoordinate(),
            coefficient_commitments,
            coefficient_proofs
    );
  }

  //////////////////////////////////////////////////////////////////////////////////////////////////////////

  public static ElectionRecordProto.ElectionInitialized publishElectionInitialized(ElectionInitialized init) {
    ElectionRecordProto.ElectionInitialized.Builder builder = ElectionRecordProto.ElectionInitialized.newBuilder();
    builder.setConfig(publishElectionConfig(init.getConfig()));
    builder.setJointPublicKey(publishElementModP(init.getJointPublicKey()));
    builder.setManifestHash(CommonConvert.publishUInt256(init.getManifestHash()));
    builder.setCryptoExtendedBaseHash(CommonConvert.publishUInt256(init.getCryptoExtendedBaseHash()));
    builder.addAllGuardians(init.getGuardians().stream().map(it -> publishGuardian(it)).toList());
    builder.putAllMetadata(init.getMetadata());

    return builder.build();
  }

  static ElectionRecordProto.Guardian publishGuardian(Guardian guardian) {
    ElectionRecordProto.Guardian.Builder builder = ElectionRecordProto.Guardian.newBuilder();
    builder.setGuardianId(guardian.getGuardianId());
    builder.setXCoordinate(guardian.getXCoordinate());
    builder.addAllCoefficientCommitments(guardian.getCoefficientCommitments().stream().map(it -> publishElementModP(it)).toList());
    builder.addAllCoefficientProofs(guardian.getCoefficientProofs().stream().map(it -> publishSchnorrProof(it)).toList());

    return builder.build();

  }

}
