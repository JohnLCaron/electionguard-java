package com.sunya.electionguard.decrypting;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.beust.jcommander.ParameterException;
import com.google.common.flogger.FluentLogger;
import com.sunya.electionguard.DecryptionProofTuple;
import com.sunya.electionguard.ElGamal;
import com.sunya.electionguard.proto.CommonConvert;
import com.sunya.electionguard.protogen.CommonProto;
import com.sunya.electionguard.protogen.DecryptingTrusteeProto;
import com.sunya.electionguard.protogen.DecryptingTrusteeServiceGrpc;
import com.sunya.electionguard.proto.TrusteeFromProto;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.stub.StreamObserver;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.List;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/** A Remote Trustee with a DecryptingTrustee delegate, communicating over gRpc. */
class DecryptingRemoteTrustee extends DecryptingTrusteeServiceGrpc.DecryptingTrusteeServiceImplBase {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();
  private static final Random random = new Random();

  private static class CommandLine {
    @Parameter(names = {"-guardianFile"}, order = 1, description = "location of serialized guardian file", required = true)
    String guardianFile;

    @Parameter(names = {"-port"}, order = 2, description = "This DecryptingRemoteTrustee port")
    int port = 0;

    @Parameter(names = {"-serverPort"}, order = 3, description = "The DecryptingRemote server port")
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
    if (port == 0) {
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
        DecryptingMediatorRunnerProxy proxy = new DecryptingMediatorRunnerProxy(serverUrl);
        CommonProto.ErrorResponse response = proxy.registerTrustee(trustee.id(), url,
                trustee.delegate.xCoordinate(), trustee.delegate.electionPublicKey());
        proxy.shutdown();

        if (response == null) {
          System.out.printf("    registerTrustee returns null response%n");
          throw new RuntimeException("registerTrustee returns null response");
        }
        if (!response.getError().isEmpty()) {
          System.out.printf("    registerTrustee error %s%n", response.getError());
          throw new RuntimeException(response.getError());
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

    Runtime.getRuntime().addShutdownHook(new Thread(() -> {
      // Use stderr here since the logger may have been reset by its JVM shutdown hook.
      System.err.println("*** shutting down gRPC server since JVM is shutting down");
      try {
        stopit();
      } catch (InterruptedException e) {
        e.printStackTrace(System.err);
      }
      System.err.println("*** server shut down");
    }));

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
    return delegate.id();
  }

  @Override
  public void compensatedDecrypt(DecryptingTrusteeProto.CompensatedDecryptionRequest request,
                                 StreamObserver<DecryptingTrusteeProto.CompensatedDecryptionResponse> responseObserver) {

    DecryptingTrusteeProto.CompensatedDecryptionResponse.Builder response = DecryptingTrusteeProto.CompensatedDecryptionResponse.newBuilder();
    try {
      List<ElGamal.Ciphertext > texts = request.getTextList().stream().map(CommonConvert::convertCiphertext).collect(Collectors.toList());

      List<DecryptionProofRecovery> tuples = delegate.compensatedDecrypt(
              request.getMissingGuardianId(),
              texts,
              CommonConvert.convertElementModQ(request.getExtendedBaseHash()),
              null);

      List<DecryptingTrusteeProto.CompensatedDecryptionResult> protos = tuples.stream().map(this::convertDecryptionProofRecovery).collect(Collectors.toList());
      response.addAllResults(protos);
      logger.atInfo().log("DecryptingRemoteTrustee compensatedDecrypt %s", request.getMissingGuardianId());
    } catch (Throwable t) {
      logger.atSevere().withCause(t).log("DecryptingRemoteTrustee compensatedDecrypt failed");
      String mess = t.getMessage() != null ? t.getMessage() : "Unknown";
      response.setError(mess);
    }

    responseObserver.onNext(response.build());
    responseObserver.onCompleted();
  }

  private DecryptingTrusteeProto.CompensatedDecryptionResult convertDecryptionProofRecovery(DecryptionProofRecovery tuple) {
    return DecryptingTrusteeProto.CompensatedDecryptionResult.newBuilder()
            .setDecryption(CommonConvert.convertElementModP(tuple.decryption))
            .setProof(CommonConvert.convertChaumPedersenProof(tuple.proof))
            .setRecoveryPublicKey(CommonConvert.convertElementModP(tuple.recoveryPublicKey))
            .build();
  }

  @Override
  public void partialDecrypt(DecryptingTrusteeProto.PartialDecryptionRequest request,
                                 StreamObserver<DecryptingTrusteeProto.PartialDecryptionResponse> responseObserver) {

    DecryptingTrusteeProto.PartialDecryptionResponse.Builder response = DecryptingTrusteeProto.PartialDecryptionResponse.newBuilder();
    try {
      List<ElGamal.Ciphertext > texts = request.getTextList().stream().map(CommonConvert::convertCiphertext).collect(Collectors.toList());
      List<DecryptionProofTuple> tuples = delegate.partialDecrypt(
              texts,
              CommonConvert.convertElementModQ(request.getExtendedBaseHash()),
              null);

      List<DecryptingTrusteeProto.PartialDecryptionResult> protos = tuples.stream().map(this::convertDecryptionProofTuple).collect(Collectors.toList());
      response.addAllResults(protos);
      logger.atInfo().log("DecryptingRemoteTrustee partialDecrypt %s", delegate.id());
    } catch (Throwable t) {
      logger.atSevere().withCause(t).log("DecryptingRemoteTrustee partialDecrypt failed");
      String mess = t.getMessage() != null ? t.getMessage() : "Unknown";
      response.setError(mess);
    }

    responseObserver.onNext(response.build());
    responseObserver.onCompleted();
  }

  private DecryptingTrusteeProto.PartialDecryptionResult convertDecryptionProofTuple(DecryptionProofTuple tuple) {
    return DecryptingTrusteeProto.PartialDecryptionResult.newBuilder()
            .setDecryption(CommonConvert.convertElementModP(tuple.decryption))
            .setProof(CommonConvert.convertChaumPedersenProof(tuple.proof))
          .build();
  }

  @Override
  public void finish(CommonProto.FinishRequest request,
                     StreamObserver<CommonProto.ErrorResponse> responseObserver) {
    CommonProto.ErrorResponse.Builder response = CommonProto.ErrorResponse.newBuilder();
    boolean ok = true;
    try {
      logger.atInfo().log("DecryptingTrusteeProto finish ok = %s", request.getAllOk());

    } catch (Throwable t) {
      logger.atSevere().withCause(t).log("DecryptingTrusteeProto finish failed");
      t.printStackTrace();
      response.setError(t.getMessage());
      ok = false;
    }

    responseObserver.onNext(response.build());
    responseObserver.onCompleted();
    if (server != null) {
      System.exit(request.getAllOk() && ok ? 0 : 1);
    }
  }

}
