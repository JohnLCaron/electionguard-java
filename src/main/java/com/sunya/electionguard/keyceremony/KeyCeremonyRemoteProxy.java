package com.sunya.electionguard.keyceremony;

import com.google.common.flogger.FluentLogger;
import com.sunya.electionguard.protogen.RemoteKeyCeremonyServiceGrpc;
import com.sunya.electionguard.protogen.RemoteKeyCeremonyProto;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.StatusRuntimeException;

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

  RemoteKeyCeremonyProto.RegisterKeyCeremonyTrusteeResponse registerTrustee(String guardianId, String remoteUrl) {
    try {
      RemoteKeyCeremonyProto.RegisterKeyCeremonyTrusteeRequest request = RemoteKeyCeremonyProto.RegisterKeyCeremonyTrusteeRequest.newBuilder()
              .setGuardianId(guardianId)
              .setRemoteUrl(remoteUrl)
              .build();
      return blockingStub.registerTrustee(request);

    } catch (StatusRuntimeException e) {
      logger.atSevere().withCause(e).log("sendPublicKeys failed: ");
      e.printStackTrace();
      return RemoteKeyCeremonyProto.RegisterKeyCeremonyTrusteeResponse.newBuilder()
              .setError("sendPublicKeys failed: " + e.getMessage())
              .build();
    }
  }

}