package com.sunya.electionguard.decrypting;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.flogger.FluentLogger;
import com.sunya.electionguard.BallotBox;
import com.sunya.electionguard.ElGamal;
import com.sunya.electionguard.Group;
import com.sunya.electionguard.proto.CommonConvert;
import com.sunya.electionguard.protogen.CommonProto;
import com.sunya.electionguard.protogen.DecryptingTrusteeProto;
import com.sunya.electionguard.protogen.DecryptingTrusteeServiceGrpc;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.StatusRuntimeException;

import javax.annotation.Nullable;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/** A Remote Trustee client proxy, communicating over gRpc. */
class DecryptingRemoteTrusteeProxy implements DecryptingTrusteeIF  {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  @Override
  public String id() {
    return trusteeId;
  }

  @Override
  public int xCoordinate() {
    return xCoordinate;
  }

  @Override
  public Group.ElementModP electionPublicKey() {
    return electionPublicKey;
  }

  @Override
  public List<DecryptionProofRecovery> compensatedDecrypt(
          String missing_guardian_id,
          List<ElGamal.Ciphertext> text,
          Group.ElementModQ extended_base_hash,
          @Nullable Group.ElementModQ nonce_seed) { // LOOK currently ignoring

    try {
      List<CommonProto.ElGamalCiphertext> texts = text.stream().map(CommonConvert::convertCiphertext).collect(Collectors.toList());

      DecryptingTrusteeProto.CompensatedDecryptionRequest.Builder request = DecryptingTrusteeProto.CompensatedDecryptionRequest.newBuilder()
              .setMissingGuardianId(missing_guardian_id)
              .addAllText(texts)
              .setExtendedBaseHash(CommonConvert.convertElementModQ(extended_base_hash));

      DecryptingTrusteeProto.CompensatedDecryptionResponse response = blockingStub.compensatedDecrypt(request.build());
      if (!response.getError().isEmpty()) {
        logger.atSevere().log("compensatedDecrypt failed: %s", response.getError());
        return ImmutableList.of();
      }
      return response.getResultsList().stream()
              .map(this::convertDecryptionProofRecovery).collect(Collectors.toList());

    } catch (StatusRuntimeException e) {
      logger.atSevere().withCause(e).log("compensatedDecrypt failed");
      return ImmutableList.of();
    }
  }

  private DecryptionProofRecovery convertDecryptionProofRecovery(DecryptingTrusteeProto.CompensatedDecryptionResult proto) {
    return new DecryptionProofRecovery(
            CommonConvert.convertElementModP(proto.getDecryption()),
            CommonConvert.convertChaumPedersenProof(proto.getProof()),
            CommonConvert.convertElementModP(proto.getRecoveryPublicKey()));
  }

  @Override
  public List<BallotBox.DecryptionProofTuple> partialDecrypt(
          List<ElGamal.Ciphertext> text,
          Group.ElementModQ extended_base_hash,
          @Nullable Group.ElementModQ nonce_seed) { // LOOK currently ignoring
    try {
      List<CommonProto.ElGamalCiphertext> texts = text.stream().map(CommonConvert::convertCiphertext).collect(Collectors.toList());

      DecryptingTrusteeProto.PartialDecryptionRequest.Builder request = DecryptingTrusteeProto.PartialDecryptionRequest.newBuilder()
              .addAllText(texts)
              .setExtendedBaseHash(CommonConvert.convertElementModQ(extended_base_hash));

      DecryptingTrusteeProto.PartialDecryptionResponse response = blockingStub.partialDecrypt(request.build());
      if (!response.getError().isEmpty()) {
        logger.atSevere().log("partialDecrypt failed: %s", response.getError());
        return ImmutableList.of();
      }
      return response.getResultsList().stream()
              .map(this::convertDecryptionProofTuple).collect(Collectors.toList());

    } catch (StatusRuntimeException e) {
      logger.atSevere().withCause(e).log("partialDecrypt failed: ");
      return ImmutableList.of();
    }
  }

  private BallotBox.DecryptionProofTuple convertDecryptionProofTuple(DecryptingTrusteeProto.PartialDecryptionResult proto) {
    return new BallotBox.DecryptionProofTuple(
            CommonConvert.convertElementModP(proto.getDecryption()),
            CommonConvert.convertChaumPedersenProof(proto.getProof()));
  }

  boolean finish(boolean allOk) {
    try {
      CommonProto.FinishRequest request = CommonProto.FinishRequest.newBuilder().setAllOk(allOk).build();
      CommonProto.ErrorResponse response = blockingStub.finish(request);
      if (!response.getError().isEmpty()) {
        logger.atSevere().log("commit failed: %s", response.getError());
        return false;
      }
      return true;

    } catch (StatusRuntimeException e) {
      logger.atSevere().withCause(e).log("commit failed: ");
      e.printStackTrace();
      return false;
    }
  }

  boolean shutdown() {
    try {
      channel.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
      return true;
    } catch (InterruptedException e) {
      e.printStackTrace();
      return false;
    }
  }

  ////////////////////////////////////////////
  private final String trusteeId;
  private final int xCoordinate;
  private final Group.ElementModP electionPublicKey;
  private final ManagedChannel channel;
  private final DecryptingTrusteeServiceGrpc.DecryptingTrusteeServiceBlockingStub blockingStub;

  static Builder builder() {
    return new Builder();
  }

  private DecryptingRemoteTrusteeProxy(String trusteeId, int xCoordinate, Group.ElementModP electionPublicKey, ManagedChannel channel) {
    this.trusteeId = Preconditions.checkNotNull(trusteeId);
    Preconditions.checkArgument(xCoordinate > 0);
    this.xCoordinate = xCoordinate;
    this.electionPublicKey = Preconditions.checkNotNull(electionPublicKey);
    this.channel = Preconditions.checkNotNull(channel);
    this.blockingStub = DecryptingTrusteeServiceGrpc.newBlockingStub(channel);
  }

  static class Builder {
    String trusteeId;
    String target;
    int xCoordinate;
    Group.ElementModP electionPublicKey;

    Builder setTrusteeId(String trusteeId) {
      this.trusteeId = trusteeId;
      return this;
    }

    Builder setUrl(String target) {
      this.target = target;
      return this;
    }

    Builder setXCoordinate(int xCoordinate) {
      this.xCoordinate = xCoordinate;
      return this;
    }

    Builder setElectionPublicKey(Group.ElementModP electionPublicKey) {
      this.electionPublicKey = electionPublicKey;
      return this;
    }

    DecryptingRemoteTrusteeProxy build() {
      ManagedChannel channel = ManagedChannelBuilder.forTarget(target)
              .usePlaintext()
              .keepAliveTime(1, TimeUnit.MINUTES)
              // .enableFullStreamDecompression()
              // .maxInboundMessageSize(MAX_MESSAGE)
              .build();
      return new DecryptingRemoteTrusteeProxy(trusteeId, xCoordinate, electionPublicKey, channel);
    }
  }
}
