package com.sunya.electionguard.decrypting;

import com.google.common.flogger.FluentLogger;
import com.sunya.electionguard.Group;
import com.sunya.electionguard.protoconvert.CommonConvert;
import electionguard.protogen.DecryptingProto;
import electionguard.protogen.DecryptingServiceGrpc;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.StatusRuntimeException;

import javax.annotation.Nullable;
import java.util.concurrent.TimeUnit;

class DecryptingMediatorRunnerProxy {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final ManagedChannel channel;
  private final DecryptingServiceGrpc.DecryptingServiceBlockingStub blockingStub;

  DecryptingMediatorRunnerProxy(String url) {
    this.channel = ManagedChannelBuilder.forTarget(url)
            .usePlaintext()
            // .enableFullStreamDecompression()
            // .maxInboundMessageSize(2000)
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
  DecryptingProto.RegisterDecryptingTrusteeResponse registerTrustee(String guardianId, String remoteUrl, int coordinate,
                                               Group.ElementModP publicKey) {
    try {
      DecryptingProto.RegisterDecryptingTrusteeRequest request = DecryptingProto.RegisterDecryptingTrusteeRequest.newBuilder()
              .setGuardianId(guardianId)
              .setRemoteUrl(remoteUrl)
              .setGuardianXCoordinate(coordinate)
              .setPublicKey(CommonConvert.publishElementModP(publicKey))
              .build();

      DecryptingProto.RegisterDecryptingTrusteeResponse response = blockingStub.registerTrustee(request);
      if (!response.getError().isEmpty()) {
        logger.atSevere().log("registerTrustee failed: %s", response.getError());
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