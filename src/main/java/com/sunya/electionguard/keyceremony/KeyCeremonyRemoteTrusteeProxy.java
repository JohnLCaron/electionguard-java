package com.sunya.electionguard.keyceremony;

import com.google.common.flogger.FluentLogger;
import com.google.protobuf.ByteString;
import com.sunya.electionguard.Auxiliary;
import com.sunya.electionguard.Group;
import com.sunya.electionguard.SchnorrProof;
import com.sunya.electionguard.guardian.KeyCeremony2;
import com.sunya.electionguard.proto.RemoteTrusteeServiceGrpc;
import com.sunya.electionguard.proto.CommonConvert;
import com.sunya.electionguard.proto.RemoteTrusteeProto;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.StatusRuntimeException;

import javax.annotation.Nullable;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/** A Remote Trustee client proxy, communicating over gRpc. */
public class KeyCeremonyRemoteTrusteeProxy {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();
  private static final int MAX_MESSAGE = 51 * 1000 * 1000; // 51 Mb

  public String id() {
    return trusteeId;
  }

  @Nullable
  public KeyCeremony2.PublicKeySet sendPublicKeys() {
    try {
      RemoteTrusteeProto.PublicKeySetRequest request = RemoteTrusteeProto.PublicKeySetRequest.getDefaultInstance();
      RemoteTrusteeProto.PublicKeySet response = blockingStub.sendPublicKeys(request);
      if (response.hasError()) {
        logger.atSevere().log("sendPublicKeys failed: %s", response.getError().getMessage());
        return null;
      }
      List<SchnorrProof> proofs = response.getCoefficientProofsList().stream().map(CommonConvert::convertSchnorrProof).collect(Collectors.toList());
      return KeyCeremony2.PublicKeySet.create(
              response.getOwnerId(),
              response.getGuardianXCoordinate(),
              CommonConvert.convertJavaPublicKey(response.getAuxiliaryPublicKey()),
              proofs);

    } catch (StatusRuntimeException e) {
      logger.atSevere().withCause(e).log("sendPublicKeys failed: ");
      e.printStackTrace();
      return null;
    }
  }

  public boolean receivePublicKeys(KeyCeremony2.PublicKeySet keyset) {
    try {
      RemoteTrusteeProto.PublicKeySet.Builder request = RemoteTrusteeProto.PublicKeySet.newBuilder();
      request.setOwnerId(keyset.ownerId())
              .setGuardianXCoordinate(keyset.guardianXCoordinate())
              .setAuxiliaryPublicKey(CommonConvert.convertJavaPublicKey(keyset.auxiliaryPublicKey()));
      keyset.coefficientProofs().forEach(p -> request.addCoefficientProofs(CommonConvert.convertSchnorrProof(p)));

      RemoteTrusteeProto.BooleanResponse response = blockingStub.receivePublicKeys(request.build());
      if (response.hasError()) {
        logger.atSevere().log("receivePublicKeys failed: %s", response.getError().getMessage());
        return false;
      }
      return response.getOk();

    } catch (StatusRuntimeException e) {
      logger.atSevere().withCause(e).log("receivePublicKeys failed: ");
      e.printStackTrace();
      return false;
    }
  }

  @Nullable
  public KeyCeremony2.PartialKeyBackup sendPartialKeyBackup(String guardianId) {
    try {
      RemoteTrusteeProto.PartialKeyBackupRequest request = RemoteTrusteeProto.PartialKeyBackupRequest.newBuilder().setGuardianId(guardianId).build();
      RemoteTrusteeProto.PartialKeyBackup response = blockingStub.sendPartialKeyBackup(request);
      if (response.hasError()) {
        logger.atSevere().log("sendPartialKeyBackup failed: %s", response.getError().getMessage());
        return null;
      }
      return KeyCeremony2.PartialKeyBackup.create(
              response.getGeneratingGuardianId(),
              response.getDesignatedGuardianId(),
              response.getDesignatedGuardianXCoordinate(),
              new Auxiliary.ByteString(response.getEncryptedCoordinate().toByteArray()));


    } catch (StatusRuntimeException e) {
      logger.atSevere().withCause(e).log("sendPartialKeyBackup failed: ");
      e.printStackTrace();
      return null;
    }
  }

  @Nullable
  public KeyCeremony2.PartialKeyVerification verifyPartialKeyBackup(KeyCeremony2.PartialKeyBackup backup) {
    try {
      RemoteTrusteeProto.PartialKeyBackup.Builder request = RemoteTrusteeProto.PartialKeyBackup.newBuilder();
      request.setGeneratingGuardianId(backup.generatingGuardianId())
              .setDesignatedGuardianId(backup.designatedGuardianId())
              .setDesignatedGuardianXCoordinate(backup.designatedGuardianXCoordinate())
              .setEncryptedCoordinate(ByteString.copyFrom(backup.encryptedCoordinate().getBytes()));

      RemoteTrusteeProto.PartialKeyVerification response = blockingStub.verifyPartialKeyBackup(request.build());
      if (response.hasError()) {
        logger.atSevere().log("verifyPartialKeyBackup failed: %s", response.getError().getMessage());
        return null;
      }

      return KeyCeremony2.PartialKeyVerification.create(
              response.getGeneratingGuardianId(),
              response.getDesignatedGuardianId(),
              response.getVerify());

    } catch (StatusRuntimeException e) {
      logger.atSevere().withCause(e).log("verifyPartialKeyBackup failed: ");
      e.printStackTrace();
      return null;
    }
  }

  @Nullable
  public KeyCeremony2.PartialKeyChallengeResponse sendBackupChallenge(String guardianId) {
    try {
      RemoteTrusteeProto.PartialKeyChallenge request = RemoteTrusteeProto.PartialKeyChallenge.newBuilder().setGuardianId(guardianId).build();
      RemoteTrusteeProto.PartialKeyChallengeResponse response = blockingStub.sendBackupChallenge(request);
      if (response.hasError()) {
        logger.atSevere().log("sendBackupChallenge failed: %s", response.getError().getMessage());
        return null;
      }

      return KeyCeremony2.PartialKeyChallengeResponse.create(
              response.getGeneratingGuardianId(),
              response.getDesignatedGuardianId(),
              response.getDesignatedGuardianXCoordinate(),
              CommonConvert.convertElementModQ(response.getCoordinate()));

    } catch (StatusRuntimeException e) {
      logger.atSevere().withCause(e).log("sendBackupChallenge failed: ");
      e.printStackTrace();
      return null;
    }
  }

  @Nullable
  public Group.ElementModP sendJointPublicKey() {
    try {
      RemoteTrusteeProto.JointPublicKeyRequest request = RemoteTrusteeProto.JointPublicKeyRequest.getDefaultInstance();
      RemoteTrusteeProto.JointPublicKeyResponse response = blockingStub.sendJointPublicKey(request);
      if (response.hasError()) {
        logger.atSevere().log("sendJointPublicKey failed: %s", response.getError().getMessage());
        return null;
      }
      return CommonConvert.convertElementModP(response.getJointPublicKey());

    } catch (StatusRuntimeException e) {
      logger.atSevere().withCause(e).log("sendJointPublicKey failed: ");
      e.printStackTrace();
      return null;
    }
  }

  public boolean saveState() {
    try {
      RemoteTrusteeProto.BooleanResponse response = blockingStub.saveState(com.google.protobuf.Empty.getDefaultInstance());
      if (response.hasError()) {
        logger.atSevere().log("saveState failed: %s", response.getError().getMessage());
        return false;
      }
      return response.getOk();

    } catch (StatusRuntimeException e) {
      logger.atSevere().withCause(e).log("saveState failed: ");
      e.printStackTrace();
      return false;
    }
  }

  //    public com.sunya.electionguard.proto.RemoteTrusteeProto.BooleanResponse finish(
  //    com.sunya.electionguard.proto.RemoteTrusteeProto.FinishRequest request) {
  public boolean finish(boolean allOk) {
    try {
      RemoteTrusteeProto.FinishRequest request = RemoteTrusteeProto.FinishRequest.newBuilder().setAllOk(allOk).build();
      RemoteTrusteeProto.BooleanResponse response = blockingStub.finish(request);
      if (response.hasError()) {
        logger.atSevere().log("commit failed: %s", response.getError().getMessage());
        return false;
      }
      return response.getOk();

    } catch (StatusRuntimeException e) {
      logger.atSevere().withCause(e).log("commit failed: ");
      e.printStackTrace();
      return false;
    }
  }

  public boolean shutdown() {
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
  private final RemoteTrusteeServiceGrpc.RemoteTrusteeServiceBlockingStub blockingStub;

  public int coordinate() {
    return coordinate;
  }

  public int quorum() {
    return quorum;
  }

  public static Builder builder() {
    return new Builder();
  }

  /** Construct client for accessing HelloWorld server using the existing channel. */
  private KeyCeremonyRemoteTrusteeProxy(String trusteeId, int coordinate, int quorum, ManagedChannel channel) {
    this.trusteeId = trusteeId;
    this.coordinate = coordinate;
    this.quorum = quorum;
    this.channel = channel;
    blockingStub = RemoteTrusteeServiceGrpc.newBlockingStub(channel);
  }

  public static class Builder {
    String trusteeId;
    String target;
    int coordinate;
    int quorum;

    public Builder setTrusteeId(String trusteeId) {
      this.trusteeId = trusteeId;
      return this;
    }

    public Builder setUrl(String target) {
      this.target = target;
      return this;
    }

    public Builder setCoordinate(int coordinate) {
      this.coordinate = coordinate;
      return this;
    }

    public Builder setQuorum(int quorum) {
      this.quorum = quorum;
      return this;
    }

    public KeyCeremonyRemoteTrusteeProxy build() {
      ManagedChannel channel = ManagedChannelBuilder.forTarget(target)
              .usePlaintext()
              .enableFullStreamDecompression()
              .maxInboundMessageSize(MAX_MESSAGE).usePlaintext().build();
      return new KeyCeremonyRemoteTrusteeProxy(trusteeId, coordinate, quorum, channel);
    }
  }
}
