package com.sunya.electionguard.keyceremony;

import com.google.common.flogger.FluentLogger;
import com.sunya.electionguard.Group;
import com.sunya.electionguard.SchnorrProof;
import com.sunya.electionguard.protoconvert.CommonConvert;
import electionguard.protogen.CommonRpcProto;
import electionguard.protogen.RemoteKeyCeremonyTrusteeProto;
import electionguard.protogen.RemoteKeyCeremonyTrusteeServiceGrpc;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.StatusRuntimeException;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static electionguard.protogen.RemoteKeyCeremonyTrusteeServiceGrpc.RemoteKeyCeremonyTrusteeServiceBlockingStub;

/** A Remote Trustee client proxy, communicating over gRpc. */
class KeyCeremonyRemoteTrusteeProxy implements KeyCeremonyTrusteeIF {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();
  private static final int MAX_MESSAGE = 51 * 1000 * 1000; // 51 Mb

  @Override
  public String id() {
    return trusteeId;
  }

  @Override
  public int xCoordinate() {
    return coordinate;
  }

  @Override
  public Optional<KeyCeremony2.PublicKeySet> sendPublicKeys() {
    try {
      logger.atInfo().log("%s sendPublicKeys", id());
      RemoteKeyCeremonyTrusteeProto.PublicKeySetRequest request = RemoteKeyCeremonyTrusteeProto.PublicKeySetRequest.getDefaultInstance();
      RemoteKeyCeremonyTrusteeProto.PublicKeySet response = blockingStub.sendPublicKeys(request);
      if (!response.getError().isEmpty()) {
        logger.atSevere().log("sendPublicKeys failed: %s", response.getError());
        return Optional.empty();
      }
      List<SchnorrProof> proofs = response.getCoefficientProofsList().stream()
              .map(CommonConvert::importSchnorrProof)
              .toList();
      return Optional.of(new KeyCeremony2.PublicKeySet(
              response.getOwnerId(),
              response.getGuardianXCoordinate(),
              proofs));

    } catch (StatusRuntimeException e) {
      logger.atSevere().withCause(e).log("sendPublicKeys failed: ");
      return Optional.empty();
    }
  }

  @Override
  public String receivePublicKeys(KeyCeremony2.PublicKeySet keyset) {
    try {
      logger.atInfo().log("%s receivePublicKeys from %s", id(), keyset.ownerId());
      RemoteKeyCeremonyTrusteeProto.PublicKeySet.Builder request = RemoteKeyCeremonyTrusteeProto.PublicKeySet.newBuilder();
      request.setOwnerId(keyset.ownerId())
              .setGuardianXCoordinate(keyset.guardianXCoordinate());
      keyset.coefficientProofs().forEach(p -> request.addCoefficientProofs(CommonConvert.publishSchnorrProof(p)));

      CommonRpcProto.ErrorResponse response = blockingStub.receivePublicKeys(request.build());
      if (!response.getError().isEmpty()) {
        logger.atSevere().log("receivePublicKeys failed: '%s'", response.getError());
      }
      return response.getError();

    } catch (StatusRuntimeException e) {
      logger.atSevere().withCause(e).log("receivePublicKeys StatusRuntimeException: ");
      return "receivePublicKeys StatusRuntimeException: " + e.getMessage();
    }
  }

  @Override
  public Optional<KeyCeremony2.PartialKeyBackup> sendPartialKeyBackup(String guardianId) {
    try {
      RemoteKeyCeremonyTrusteeProto.PartialKeyBackupRequest request = RemoteKeyCeremonyTrusteeProto.PartialKeyBackupRequest.newBuilder().setGuardianId(guardianId).build();
      RemoteKeyCeremonyTrusteeProto.PartialKeyBackup response = blockingStub.sendPartialKeyBackup(request);
      return Optional.of(new KeyCeremony2.PartialKeyBackup(
              response.getGeneratingGuardianId(),
              response.getDesignatedGuardianId(),
              response.getDesignatedGuardianXCoordinate(),
              CommonConvert.importElementModQ(response.getCoordinate()),
              response.getError()));

    } catch (StatusRuntimeException e) {
      logger.atSevere().withCause(e).log("sendPartialKeyBackup failed: ");
      return Optional.empty();
    }
  }

  @Override
  public Optional<KeyCeremony2.PartialKeyVerification> verifyPartialKeyBackup(KeyCeremony2.PartialKeyBackup backup) {
    try {
      RemoteKeyCeremonyTrusteeProto.PartialKeyBackup.Builder request = RemoteKeyCeremonyTrusteeProto.PartialKeyBackup.newBuilder();
      request.setGeneratingGuardianId(backup.generatingGuardianId())
              .setDesignatedGuardianId(backup.designatedGuardianId())
              .setDesignatedGuardianXCoordinate(backup.designatedGuardianXCoordinate())
              .setCoordinate(CommonConvert.publishElementModQ(backup.coordinate()));

      RemoteKeyCeremonyTrusteeProto.PartialKeyVerification response = blockingStub.verifyPartialKeyBackup(request.build());
      return Optional.of(new KeyCeremony2.PartialKeyVerification(
              response.getGeneratingGuardianId(),
              response.getDesignatedGuardianId(),
              response.getError()));

    } catch (StatusRuntimeException e) {
      logger.atSevere().withCause(e).log("verifyPartialKeyBackup failed: ");
      return Optional.empty();
    }
  }

  @Override
  public Optional<KeyCeremony2.PartialKeyChallengeResponse> sendBackupChallenge(String guardianId) {
    try {
      RemoteKeyCeremonyTrusteeProto.PartialKeyChallenge request = RemoteKeyCeremonyTrusteeProto.PartialKeyChallenge.newBuilder().setGuardianId(guardianId).build();
      RemoteKeyCeremonyTrusteeProto.PartialKeyChallengeResponse response = blockingStub.sendBackupChallenge(request);
       return Optional.of(new KeyCeremony2.PartialKeyChallengeResponse(
              response.getGeneratingGuardianId(),
              response.getDesignatedGuardianId(),
              response.getDesignatedGuardianXCoordinate(),
              CommonConvert.importElementModQ(response.getCoordinate()),
              response.getError()));


    } catch (StatusRuntimeException e) {
      logger.atSevere().withCause(e).log("sendBackupChallenge failed: ");
      return Optional.empty();
    }
  }

  @Override
  public Optional<Group.ElementModP> sendJointPublicKey() {
    try {
      RemoteKeyCeremonyTrusteeProto.JointPublicKeyRequest request = RemoteKeyCeremonyTrusteeProto.JointPublicKeyRequest.getDefaultInstance();
      RemoteKeyCeremonyTrusteeProto.JointPublicKeyResponse response = blockingStub.sendJointPublicKey(request);
      if (!response.getError().isEmpty()) {
        logger.atSevere().log("sendJointPublicKey failed: %s", response.getError());
        return Optional.empty();
      }
      return Optional.ofNullable(CommonConvert.importElementModP(response.getJointPublicKey()));

    } catch (StatusRuntimeException e) {
      logger.atSevere().withCause(e).log("sendJointPublicKey failed: ");
      return Optional.empty();
    }
  }

  boolean saveState() {
    try {
      CommonRpcProto.ErrorResponse response = blockingStub.saveState(com.google.protobuf.Empty.getDefaultInstance());
      if (!response.getError().isEmpty()) {
        logger.atSevere().log("saveState failed: %s", response.getError());
        return false;
      }
      return true;

    } catch (StatusRuntimeException e) {
      logger.atSevere().withCause(e).log("saveState failed: ");
      e.printStackTrace();
      return false;
    }
  }

  boolean finish(boolean allOk) {
    try {
      CommonRpcProto.FinishRequest request = CommonRpcProto.FinishRequest.newBuilder().setAllOk(allOk).build();
      CommonRpcProto.ErrorResponse response = blockingStub.finish(request);
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
  private final int coordinate;
  private final int quorum;
  private final ManagedChannel channel;
  private final RemoteKeyCeremonyTrusteeServiceBlockingStub blockingStub;

  public int quorum() {
    return quorum;
  }

  static Builder builder() {
    return new Builder();
  }

  /** Construct client for accessing HelloWorld server using the existing channel. */
  private KeyCeremonyRemoteTrusteeProxy(String trusteeId, int coordinate, int quorum, ManagedChannel channel) {
    this.trusteeId = trusteeId;
    this.coordinate = coordinate;
    this.quorum = quorum;
    this.channel = channel;
    blockingStub = RemoteKeyCeremonyTrusteeServiceGrpc.newBlockingStub(channel);
  }

  static class Builder {
    String trusteeId;
    String target;
    int coordinate;
    int quorum;

    Builder setTrusteeId(String trusteeId) {
      this.trusteeId = trusteeId;
      return this;
    }

    Builder setUrl(String target) {
      this.target = target;
      return this;
    }

    Builder setCoordinate(int coordinate) {
      this.coordinate = coordinate;
      return this;
    }

    Builder setQuorum(int quorum) {
      this.quorum = quorum;
      return this;
    }

    KeyCeremonyRemoteTrusteeProxy build() {
      ManagedChannel channel = ManagedChannelBuilder.forTarget(target)
              .usePlaintext()
              .enableFullStreamDecompression()
              .maxInboundMessageSize(MAX_MESSAGE).usePlaintext().build();
      return new KeyCeremonyRemoteTrusteeProxy(trusteeId, coordinate, quorum, channel);
    }
  }
}
