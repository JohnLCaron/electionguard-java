package com.sunya.electionguard.decrypting;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.beust.jcommander.ParameterException;
import com.google.common.flogger.FluentLogger;
import com.sunya.electionguard.DecryptionProofRecovery;
import com.sunya.electionguard.DecryptionProofTuple;
import com.sunya.electionguard.Group;
import com.sunya.electionguard.guardian.DecryptingTrustee;
import com.sunya.electionguard.proto.CommonConvert;
import com.sunya.electionguard.proto.CommonProto;
import com.sunya.electionguard.proto.DecryptingProto;
import com.sunya.electionguard.proto.DecryptingTrusteeProto;
import com.sunya.electionguard.proto.DecryptingTrusteeServiceGrpc;
import com.sunya.electionguard.proto.TrusteeFromProto;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.stub.StreamObserver;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.Random;
import java.util.concurrent.TimeUnit;

/** A Remote Trustee with a DecryptingTrustee delegate, communicating over gRpc. */
class DecryptingRemoteTrustee extends DecryptingTrusteeServiceGrpc.DecryptingTrusteeServiceImplBase {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();
  private static final Random random = new Random(System.currentTimeMillis());

  private static class CommandLine {
    @Parameter(names = {"-guardianFile"}, order = 1, description = "location of serialized guardian file", required = true)
    String guardianFile;

    @Parameter(names = {"-port"}, order = 1, description = "This DecryptingRemoteTrustee port")
    int port = 17711;

    @Parameter(names = {"-serverPort"}, order = 2, description = "The DecryptingRemote server port")
    int serverPort = 17711;

    @Parameter(names = {"-h", "--help"}, order = 9, description = "Display this help and exit", help = true)
    boolean help = false;

    private final JCommander jc;

    CommandLine(String progName, String[] args) throws ParameterException {
      this.jc = new JCommander(this);
      this.jc.parse(args);
      jc.setProgramName(String.format("java -classpath electionguard-java-all.jar %s", progName));
    }

    void printUsage() {
      jc.usage();
    }
  }

  public static void main(String[] args) {
    String progName = DecryptingRemoteTrustee.class.getName();
    CommandLine cmdLine = null;

    try {
      cmdLine = new CommandLine(progName, args);
      if (cmdLine.help) {
        cmdLine.printUsage();
        return;
      }
    } catch (ParameterException e) {
      System.err.println(e.getMessage());
      System.err.printf("Try '%s --help' for more information.%n", progName);
      System.exit(1);
    }

    // which port? if not assigned, pick one at random
    int port = cmdLine.port;
    if (port == cmdLine.serverPort) {
      port = cmdLine.serverPort + 1 + random.nextInt(10000);
      while (!isLocalPortFree(port)) {
        port = cmdLine.serverPort + 1 + random.nextInt(10000);
      }
    }
    String url = "localhost:"+port;
    String serverUrl = "localhost:" + cmdLine.serverPort;
    System.out.printf("*** DecryptingRemoteTrustee from file %s url %s server %s%n", cmdLine.guardianFile, url, serverUrl);

    // Now start up our own 'DecryptingRemoteTrustee' Service
    try {
      DecryptingRemoteTrustee trustee = new DecryptingRemoteTrustee(cmdLine.guardianFile);

      if (cmdLine.serverPort != 0) {
        // register with the DecryptingRemote "server".
        DecryptingRemoteProxy proxy = new DecryptingRemoteProxy(serverUrl);
        DecryptingProto.RegisterDecryptingTrusteeResponse response = proxy.registerTrustee(trustee.id(), url,
                trustee.delegate.xCoordinate, trustee.delegate.publicKey());
        proxy.shutdown();

        if (response == null) {
          System.out.printf("    registerTrustee returns null response%n");
          throw new RuntimeException("registerTrustee returns null response");
        }
        if (response.hasError()) {
          System.out.printf("    registerTrustee error %s%n", response.getError().getMessage());
          throw new RuntimeException(response.getError().getMessage());
        }
        if (!response.getOk()) {
          System.out.printf("    registerTrustee not ok%n");
          throw new RuntimeException("registerTrustee not ok");
        }
        System.out.printf("    registered with DecryptingRemote %n");
      }

      trustee.start(port);
      trustee.blockUntilShutdown();
      System.exit(0);

    } catch (Throwable t) {
      System.out.printf("*** DecryptingRemoteTrustee FAILURE%n");
      t.printStackTrace();
      System.exit(3);
    }
  }

  private static boolean isLocalPortFree(int port) {
    try {
      new ServerSocket(port).close();
      return true;
    } catch (IOException e) {
      return false;
    }
  }

  ///////////////////////////////////////////////////////////////////////////
  private Server server;

  private void start(int port) throws IOException {
    server = ServerBuilder.forPort(port) //
            .addService(this) //
            // .intercept(new MyServerInterceptor())
            .build().start();

    Runtime.getRuntime().addShutdownHook(new Thread() {
      @Override
      public void run() {
        // Use stderr here since the logger may have been reset by its JVM shutdown hook.
        System.err.println("*** shutting down gRPC server since JVM is shutting down");
        try {
          stopit();
        } catch (InterruptedException e) {
          e.printStackTrace(System.err);
        }
        System.err.println("*** server shut down");
      }
    });

    System.out.printf("---- DecryptingRemoteTrustee started, listening on %d ----%n", port);
  }

  private void stopit() throws InterruptedException {
    if (server != null) {
      server.shutdown().awaitTermination(30, TimeUnit.SECONDS);
    }
  }

  /** Await termination on the main thread since the grpc library uses daemon threads. */
  private void blockUntilShutdown() throws InterruptedException {
    if (server != null) {
      server.awaitTermination();
    }
  }

  ////////////////////////////////////////////////////////////////////////////////
  private final DecryptingTrustee delegate;

  DecryptingRemoteTrustee(String guardianFile) throws IOException {
    this.delegate = TrusteeFromProto.readTrustee(guardianFile);
  }

  String id() {
    return delegate.id;
  }

  @Override
  public void compensatedDecrypt(DecryptingTrusteeProto.CompensatedDecryptionRequest request,
                                 StreamObserver<DecryptingTrusteeProto.CompensatedDecryptionResponse> responseObserver) {

    DecryptingTrusteeProto.CompensatedDecryptionResponse.Builder response = DecryptingTrusteeProto.CompensatedDecryptionResponse.newBuilder();
    try {
      DecryptionProofRecovery tuple = delegate.compensatedDecrypt(
              request.getMissingGuardianId(),
              CommonConvert.convertCiphertext(request.getText()),
              CommonConvert.convertElementModQ(request.getExtendedBaseHash()),
              null);

      response.setDecryption(CommonConvert.convertElementModP(tuple.decryption))
              .setProof(CommonConvert.convertChaumPedersenProof(tuple.proof))
              .setRecoveryPublicKey(CommonConvert.convertElementModP(tuple.recoveryPublicKey));
      logger.atInfo().log("DecryptingRemoteTrustee compensatedDecrypt %s", delegate.id);
    } catch (Throwable t) {
      logger.atSevere().withCause(t).log("DecryptingRemoteTrustee compensatedDecrypt failed");
      String mess = t.getMessage() != null ? t.getMessage() : "Unknown";
      response.setError(CommonProto.RemoteError.newBuilder().setMessage(mess).build());
    }

    responseObserver.onNext(response.build());
    responseObserver.onCompleted();
  }

  @Override
  public void partialDecrypt(DecryptingTrusteeProto.DecryptionRequest request,
                                 StreamObserver<DecryptingTrusteeProto.DecryptionResponse> responseObserver) {

    DecryptingTrusteeProto.DecryptionResponse.Builder response = DecryptingTrusteeProto.DecryptionResponse.newBuilder();
    try {
      DecryptionProofTuple tuple = delegate.partialDecrypt(
              CommonConvert.convertCiphertext(request.getText()),
              CommonConvert.convertElementModQ(request.getExtendedBaseHash()),
              null);

      response.setDecryption(CommonConvert.convertElementModP(tuple.decryption))
              .setProof(CommonConvert.convertChaumPedersenProof(tuple.proof));
      logger.atInfo().log("DecryptingRemoteTrustee partialDecrypt %s", delegate.id);
    } catch (Throwable t) {
      logger.atSevere().withCause(t).log("DecryptingRemoteTrustee partialDecrypt failed");
      String mess = t.getMessage() != null ? t.getMessage() : "Unknown";
      response.setError(CommonProto.RemoteError.newBuilder().setMessage(mess).build());
    }

    responseObserver.onNext(response.build());
    responseObserver.onCompleted();
  }

  @Override
  public void recoverPublicKey(DecryptingTrusteeProto.RecoverPublicKeyRequest request,
                                StreamObserver<DecryptingTrusteeProto.RecoverPublicKeyResponse> responseObserver) {

    DecryptingTrusteeProto.RecoverPublicKeyResponse.Builder response = DecryptingTrusteeProto.RecoverPublicKeyResponse.newBuilder();
    try {
      Group.ElementModP key = delegate.recoverPublicKey(request.getGuardianId());

      response.setRecoveredKey(CommonConvert.convertElementModP(key));
      logger.atInfo().log("DecryptingRemoteTrustee partialDecrypt %s", delegate.id);
    } catch (Throwable t) {
      logger.atSevere().withCause(t).log("DecryptingRemoteTrustee partialDecrypt failed");
      t.printStackTrace();
      response.setError(CommonProto.RemoteError.newBuilder().setMessage(t.getMessage()).build());
    }

    responseObserver.onNext(response.build());
    responseObserver.onCompleted();
  }

  @Override
  public void ping(com.google.protobuf.Empty request,
                   StreamObserver<DecryptingTrusteeProto.PingResponse> responseObserver) {
    DecryptingTrusteeProto.PingResponse.Builder response = DecryptingTrusteeProto.PingResponse.newBuilder();
    responseObserver.onNext(response.setOk(true).build());
    responseObserver.onCompleted();
  }

  /*
  @Override
  public void finish(RemoteTrusteeProto.FinishRequest request,
                     StreamObserver<RemoteTrusteeProto.BooleanResponse> responseObserver) {
    RemoteTrusteeProto.BooleanResponse.Builder response = RemoteTrusteeProto.BooleanResponse.newBuilder();
    boolean ok = true;
    try {
      logger.atInfo().log("KeyCeremonyRemoteTrustee finish ok = %s", request.getAllOk());

    } catch (Throwable t) {
      logger.atSevere().withCause(t).log("KeyCeremonyRemoteTrustee finish failed");
      t.printStackTrace();
      response.setError(RemoteTrusteeProto.RemoteTrusteeError.newBuilder().setMessage(t.getMessage()).build());
      ok = false;
    }

    response.setOk(ok);
    responseObserver.onNext(response.build());
    responseObserver.onCompleted();
    System.exit(ok ? 0 : 1);
  } */

}
