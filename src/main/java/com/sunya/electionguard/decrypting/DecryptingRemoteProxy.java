package com.sunya.electionguard.decrypting;

import com.google.common.flogger.FluentLogger;
import com.sunya.electionguard.proto.DecryptingProto;
import com.sunya.electionguard.proto.DecryptingServiceGrpc;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.StatusRuntimeException;

import javax.annotation.Nullable;
import java.util.concurrent.TimeUnit;

class DecryptingRemoteProxy {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final ManagedChannel channel;
  private final DecryptingServiceGrpc.DecryptingServiceBlockingStub blockingStub;

  DecryptingRemoteProxy(String url) {
    this.channel = ManagedChannelBuilder.forTarget(url)
            .usePlaintext()
            .enableFullStreamDecompression()
            .usePlaintext()
            .maxInboundMessageSize(2000)
            .build();

    blockingStub = DecryptingServiceGrpc.newBlockingStub(channel);
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
  DecryptingProto.RegisterDecryptingTrusteeResponse registerTrustee(String guardianId, String remoteUrl) {
    try {
      DecryptingProto.RegisterDecryptingTrusteeRequest request = DecryptingProto.RegisterDecryptingTrusteeRequest.newBuilder()
              .setGuardianId(guardianId)
              .setRemoteUrl(remoteUrl)
              .build();
      DecryptingProto.RegisterDecryptingTrusteeResponse response = blockingStub.registerTrustee(request);
      if (response.hasError()) {
        logger.atSevere().log("registerTrustee failed: %s", response.getError().getMessage());
        return null;
      }
      return response;

    } catch (StatusRuntimeException e) {
      logger.atSevere().withCause(e).log("registerTrustee failed: ");
      e.printStackTrace();
      return null;
    }
  }

}