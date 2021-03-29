package com.sunya.electionguard.keyceremony;

import com.google.common.flogger.FluentLogger;
import com.sunya.electionguard.proto.RemoteKeyCeremonyServiceGrpc;
import com.sunya.electionguard.proto.RemoteKeyCeremonyProto;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.StatusRuntimeException;

import javax.annotation.Nullable;
import java.util.concurrent.TimeUnit;

class KeyCeremonyRemoteProxy {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final ManagedChannel channel;
  private final RemoteKeyCeremonyServiceGrpc.RemoteKeyCeremonyServiceBlockingStub blockingStub;

  KeyCeremonyRemoteProxy(String url) {
    this.channel = ManagedChannelBuilder.forTarget(url)
            .usePlaintext()
            .enableFullStreamDecompression()
            .usePlaintext()
            .maxInboundMessageSize(2000)
            .build();

    blockingStub = RemoteKeyCeremonyServiceGrpc.newBlockingStub(channel);
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

  @Nullable
  RemoteKeyCeremonyProto.RegisterTrusteeResponse registerTrustee(String guardianId, String remoteUrl) {
    try {
      RemoteKeyCeremonyProto.RegisterTrusteeRequest request = RemoteKeyCeremonyProto.RegisterTrusteeRequest.newBuilder()
              .setGuardianId(guardianId)
              .setRemoteUrl(remoteUrl)
              .build();
      RemoteKeyCeremonyProto.RegisterTrusteeResponse response = blockingStub.registerTrustee(request);
      if (response.hasError()) {
        logger.atSevere().log("sendPublicKeys failed: %s", response.getError().getMessage());
        return null;
      }
      return response;

    } catch (StatusRuntimeException e) {
      logger.atSevere().withCause(e).log("sendPublicKeys failed: ");
      e.printStackTrace();
      return null;
    }
  }

}