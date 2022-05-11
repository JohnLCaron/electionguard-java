package com.sunya.electionguard.protoconvert;

import com.google.protobuf.ByteString;
import electionguard.ballot.ElectionConstants;
import electionguard.ballot.ElectionConfig;
import electionguard.protogen.ElectionRecordProto;

import javax.annotation.concurrent.Immutable;
import java.io.FileInputStream;
import java.io.IOException;

import static com.sunya.electionguard.protoconvert.ManifestFromProto.translateFromProto;
import static com.sunya.electionguard.protoconvert.ManifestToProto.publishManifest;

@Immutable
public class ElectionConfigConvert {

  public static ElectionConfig read(String filename) throws IOException {
    ElectionRecordProto.ElectionConfig proto;
    try (FileInputStream inp = new FileInputStream(filename)) {
      proto = ElectionRecordProto.ElectionConfig.parseFrom(inp);
    }
    return importElectionConfig(proto);
  }

  static ElectionConfig importElectionConfig(ElectionRecordProto.ElectionConfig config) {
    if (config == null) {
      return null;
    }
    return new ElectionConfig(
            config.getProtoVersion(),
            importElectionConstants(config.getConstants()),
            translateFromProto(config.getManifest()),
            config.getNumberOfGuardians(),
            config.getQuorum(),
            config.getMetadataMap());
  }

  static ElectionConstants importElectionConstants(ElectionRecordProto.ElectionConstants constants) {
    return new ElectionConstants(
            constants.getName(),
            constants.getLargePrime().toByteArray(),
            constants.getSmallPrime().toByteArray(),
            constants.getCofactor().toByteArray(),
            constants.getGenerator().toByteArray());
  }

  //////////////////////////////////////////////////////////////////////////////////////////////////////////

  public static ElectionRecordProto.ElectionConfig publishElectionConfig(ElectionConfig config) {
    ElectionRecordProto.ElectionConfig.Builder builder = ElectionRecordProto.ElectionConfig.newBuilder();
    builder.setProtoVersion(config.getProtoVersion());
    builder.setConstants(publishElectionConstants(config.getConstants()));
    builder.setManifest(publishManifest(config.getManifest()));
    builder.setNumberOfGuardians(config.getNumberOfGuardians());
    builder.setQuorum(config.getQuorum());
    builder.putAllMetadata(config.getMetadata());

    return builder.build();
  }

  static ElectionRecordProto.ElectionConstants publishElectionConstants(ElectionConstants constants) {
    ElectionRecordProto.ElectionConstants.Builder builder = ElectionRecordProto.ElectionConstants.newBuilder();
    builder.setName(constants.getName());
    builder.setLargePrime(ByteString.copyFrom(constants.getLargePrime()));
    builder.setSmallPrime(ByteString.copyFrom(constants.getSmallPrime()));
    builder.setCofactor(ByteString.copyFrom(constants.getCofactor()));
    builder.setGenerator(ByteString.copyFrom(constants.getGenerator()));

    return builder.build();
  }
}
