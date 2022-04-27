package com.sunya.electionguard.protoconvert;

import com.google.protobuf.ByteString;
import electionguard.ballot.ElectionConstants;
import electionguard.ballot.ElectionConfig;
import electionguard.protogen.ElectionRecordProto2;

import javax.annotation.concurrent.Immutable;
import java.io.FileInputStream;
import java.io.IOException;

import static com.sunya.electionguard.protoconvert.ManifestFromProto.translateFromProto;
import static com.sunya.electionguard.protoconvert.ManifestToProto.publishManifest;

@Immutable
public class ElectionConfigConvert {

  public static ElectionConfig read(String filename) throws IOException {
    ElectionRecordProto2.ElectionConfig proto;
    try (FileInputStream inp = new FileInputStream(filename)) {
      proto = ElectionRecordProto2.ElectionConfig.parseFrom(inp);
    }
    return importElectionConfig(proto);
  }

  static ElectionConfig importElectionConfig(ElectionRecordProto2.ElectionConfig config) {
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

  static ElectionConstants importElectionConstants(ElectionRecordProto2.ElectionConstants constants) {
    return new ElectionConstants(
            constants.getName(),
            constants.getLargePrime().toByteArray(),
            constants.getSmallPrime().toByteArray(),
            constants.getCofactor().toByteArray(),
            constants.getGenerator().toByteArray());
  }

  //////////////////////////////////////////////////////////////////////////////////////////////////////////

  static ElectionRecordProto2.ElectionConfig publishElectionConfig(ElectionConfig config) {
    ElectionRecordProto2.ElectionConfig.Builder builder = ElectionRecordProto2.ElectionConfig.newBuilder();
    builder.setProtoVersion(config.getProtoVersion());
    builder.setConstants(publishElectionConstants(config.getConstants()));
    builder.setManifest(publishManifest(config.getManifest()));
    builder.setNumberOfGuardians(config.getNumberOfGuardians());
    builder.setQuorum(config.getQuorum());
    builder.putAllMetadata(config.getMetadata());

    return builder.build();
  }

  static ElectionRecordProto2.ElectionConstants publishElectionConstants(ElectionConstants constants) {
    ElectionRecordProto2.ElectionConstants.Builder builder = ElectionRecordProto2.ElectionConstants.newBuilder();
    builder.setName(constants.getName());
    builder.setLargePrime(ByteString.copyFrom(constants.getLargePrime()));
    builder.setSmallPrime(ByteString.copyFrom(constants.getSmallPrime()));
    builder.setCofactor(ByteString.copyFrom(constants.getCofactor()));
    builder.setGenerator(ByteString.copyFrom(constants.getGenerator()));

    return builder.build();
  }
}
