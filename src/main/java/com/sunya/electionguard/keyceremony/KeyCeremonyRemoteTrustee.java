package com.sunya.electionguard.keyceremony;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.beust.jcommander.ParameterException;
import com.google.common.flogger.FluentLogger;
import com.sunya.electionguard.SchnorrProof;
import com.sunya.electionguard.proto.CommonConvert;
import com.sunya.electionguard.protogen.CommonProto;
import com.sunya.electionguard.protogen.RemoteKeyCeremonyProto;
import com.sunya.electionguard.protogen.RemoteKeyCeremonyTrusteeProto;
import com.sunya.electionguard.protogen.RemoteKeyCeremonyTrusteeServiceGrpc;
import com.sunya.electionguard.protogen.TrusteeProto;
import com.sunya.electionguard.proto.TrusteeToProto;
import com.sunya.electionguard.publish.PrivateData;
import com.sunya.electionguard.publish.Publisher;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.stub.StreamObserver;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.ServerSocket;
import java.util.Formatter;
import java.util.List;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static com.sunya.electionguard.Group.ElementModP;

/** A Remote Trustee with a KeyCeremonyTrustee delegate, communicating over gRpc. */
class KeyCeremonyRemoteTrustee extends RemoteKeyCeremonyTrusteeServiceGrpc.RemoteKeyCeremonyTrusteeServiceImplBase {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();
  private static final Random random = new Random();

  private static class CommandLine {
    @Parameter(names = {"-name"}, order = 0, description = "Guardian name", required = true)
    String name;

    @Parameter(names = {"-port"}, order = 1, description = "This KeyCeremonyRemoteTrustee port")
    int port = 0;

    @Parameter(names = {"-serverPort"}, order = 2, description = "The KeyCeremonyRemote server port")
    int serverPort = 17111;

    @Parameter(names = {"-out"}, order = 3, description = "Directory where the Guardian state is written", required = true)
    String outputDir;

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
    String progName = KeyCeremonyRemoteTrustee.class.getName();
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
    System.out.printf("*** KeyCeremonyRemote %s with args %s %s%n", serverUrl, cmdLine.name, url);

    // first contact the KeyCeremonyRemote "server" to get parameters
    KeyCeremonyRemoteProxy proxy = new KeyCeremonyRemoteProxy(serverUrl);
    RemoteKeyCeremonyProto.RegisterKeyCeremonyTrusteeResponse response = proxy.registerTrustee(cmdLine.name, url);
    proxy.shutdown();
    if (!response.getError().isEmpty()) {
      System.out.printf("    registerTrustee error %s%n", response.getError());
      throw new RuntimeException(response.getError());
    }
    System.out.printf("    response %s %d %d %n", response.getGuardianId(),
            response.getGuardianXCoordinate(),
            response.getQuorum());

    // Now start up our own 'RemoteTrustee' Service
    try {
      KeyCeremonyRemoteTrustee keyCeremony = new KeyCeremonyRemoteTrustee(
              response.getGuardianId(),
              response.getGuardianXCoordinate(),
              response.getQuorum(),
              cmdLine.outputDir);

      keyCeremony.start(port);
      keyCeremony.blockUntilShutdown();
      System.exit(0);

    } catch (Throwable t) {
      System.out.printf("*** KeyCeremonyRemote FAILURE%n");
      t.printStackTrace();
      System.exit(3);
    }
  }

  private static boolean isLocalPortFree(int port) {
    System.out.printf("Try %d port ", port);
    try {
      new ServerSocket(port).close();
      System.out.printf("free%n");
      return true;
    } catch (IOException e) {
      System.out.printf("taken%n");
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

    System.out.printf("---- KeyCeremonyRemoteService started, listening on %d ----%n", port);
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
  final KeyCeremonyTrustee delegate;
  final String outputDir;
  final PrivateData publisher;

  KeyCeremonyRemoteTrustee(String id,
                            int xCoordinate,
                            int quorum,
                            String outputDir) throws IOException {
    this.delegate = new KeyCeremonyTrustee(id, xCoordinate, quorum, null);
    this.outputDir = outputDir;

    // fail fast on bad output directory
    publisher = new PrivateData(outputDir, false, true);
    Formatter errors = new Formatter();
    if (!publisher.validateOutputDir(errors)) {
      System.out.printf("*** Publisher validateOutputDir FAILED on %s%n%s", outputDir, errors);
      throw new FileNotFoundException(errors.toString());
    }
  }

  @Override
  public void sendPublicKeys(RemoteKeyCeremonyTrusteeProto.PublicKeySetRequest request,
                             StreamObserver<RemoteKeyCeremonyTrusteeProto.PublicKeySet> responseObserver) {

    RemoteKeyCeremonyTrusteeProto.PublicKeySet.Builder response = RemoteKeyCeremonyTrusteeProto.PublicKeySet.newBuilder();
    try {
      KeyCeremony2.PublicKeySet keyset = delegate.sharePublicKeys();
      response.setOwnerId(keyset.ownerId())
              .setGuardianXCoordinate(keyset.guardianXCoordinate())
              .setAuxiliaryPublicKey(CommonConvert.convertJavaPublicKey(keyset.auxiliaryPublicKey()));
      keyset.coefficientProofs().forEach(p -> response.addCoefficientProofs(CommonConvert.convertSchnorrProof(p)));
      logger.atInfo().log("KeyCeremonyRemoteTrustee %s sendPublicKeys", delegate.id);
    } catch (Throwable t) {
      logger.atSevere().withCause(t).log("KeyCeremonyRemoteTrustee %s sendPublicKeys failed", delegate.id);
      t.printStackTrace();
      response.setError("KeyCeremonyRemoteTrustee sendPublicKeys failed:" + t.getMessage());
    }

    responseObserver.onNext(response.build());
    responseObserver.onCompleted();
  }

  @Override
  public void receivePublicKeys(RemoteKeyCeremonyTrusteeProto.PublicKeySet proto,
                                StreamObserver<CommonProto.ErrorResponse> responseObserver) {

    CommonProto.ErrorResponse.Builder response = CommonProto.ErrorResponse.newBuilder();
    try {
      List<SchnorrProof> proofs = proto.getCoefficientProofsList().stream().map(CommonConvert::convertSchnorrProof).collect(Collectors.toList());
      KeyCeremony2.PublicKeySet keyset = KeyCeremony2.PublicKeySet.create(
              proto.getOwnerId(),
              proto.getGuardianXCoordinate(),
              CommonConvert.convertJavaPublicKey(proto.getAuxiliaryPublicKey()),
              proofs);
      String error = delegate.receivePublicKeys(keyset);
      response.setError(error);
      logger.atInfo().log("KeyCeremonyRemoteTrustee receivePublicKeys from %s", proto.getOwnerId());

    } catch (Throwable t) {
      logger.atSevere().withCause(t).log("KeyCeremonyRemoteTrustee receivePublicKeys from %s failed", proto.getOwnerId());
      t.printStackTrace();
      response.setError(t.getMessage());
    }

    responseObserver.onNext(response.build());
    responseObserver.onCompleted();
  }

  @Override
  public void sendPartialKeyBackup(RemoteKeyCeremonyTrusteeProto.PartialKeyBackupRequest request,
                                   StreamObserver<RemoteKeyCeremonyTrusteeProto.PartialKeyBackup> responseObserver) {

    RemoteKeyCeremonyTrusteeProto.PartialKeyBackup.Builder response = RemoteKeyCeremonyTrusteeProto.PartialKeyBackup.newBuilder();
    try {
      KeyCeremony2.PartialKeyBackup backup = delegate.sendPartialKeyBackup(request.getGuardianId());

      response.setGeneratingGuardianId(backup.generatingGuardianId())
              .setDesignatedGuardianId(backup.designatedGuardianId())
              .setDesignatedGuardianXCoordinate(backup.designatedGuardianXCoordinate())
              .setError(backup.error());
      if (backup.coordinate() != null) {
        response.setCoordinate(CommonConvert.convertElementModQ(backup.coordinate()));
      }
      logger.atInfo().log("KeyCeremonyRemoteTrustee sendPartialKeyBackup %s", request.getGuardianId());

    } catch (Throwable t) {
      logger.atSevere().withCause(t).log("KeyCeremonyRemoteTrustee sendPartialKeyBackup failed");
      t.printStackTrace();
      response.setError(t.getMessage());
    }

    responseObserver.onNext(response.build());
    responseObserver.onCompleted();
  }

  @Override
  public void verifyPartialKeyBackup(RemoteKeyCeremonyTrusteeProto.PartialKeyBackup proto,
                                     StreamObserver<RemoteKeyCeremonyTrusteeProto.PartialKeyVerification> responseObserver) {

    RemoteKeyCeremonyTrusteeProto.PartialKeyVerification.Builder response = RemoteKeyCeremonyTrusteeProto.PartialKeyVerification.newBuilder();
    try {
      KeyCeremony2.PartialKeyBackup backup = KeyCeremony2.PartialKeyBackup.create(
              proto.getGeneratingGuardianId(),
              proto.getDesignatedGuardianId(),
              proto.getDesignatedGuardianXCoordinate(),
              CommonConvert.convertElementModQ(proto.getCoordinate()),
              null);

      KeyCeremony2.PartialKeyVerification verify = delegate.verifyPartialKeyBackup(backup);

      response.setGeneratingGuardianId(verify.generatingGuardianId())
              .setDesignatedGuardianId(verify.designatedGuardianId())
              .setDesignatedGuardianXCoordinate(backup.designatedGuardianXCoordinate())
              .setError(verify.error());
      logger.atInfo().log("KeyCeremonyRemoteTrustee verifyPartialKeyBackup %s", proto.getGeneratingGuardianId());

    } catch (Throwable t) {
      logger.atSevere().withCause(t).log("KeyCeremonyRemoteTrustee verifyPartialKeyBackup failed");
      t.printStackTrace();
      response.setError(t.getMessage());
    }

    responseObserver.onNext(response.build());
    responseObserver.onCompleted();
  }

  @Override
  public void sendBackupChallenge(RemoteKeyCeremonyTrusteeProto.PartialKeyChallenge request,
                                  StreamObserver<RemoteKeyCeremonyTrusteeProto.PartialKeyChallengeResponse> responseObserver) {
    RemoteKeyCeremonyTrusteeProto.PartialKeyChallengeResponse.Builder response = RemoteKeyCeremonyTrusteeProto.PartialKeyChallengeResponse.newBuilder();
    try {
      KeyCeremony2.PartialKeyChallengeResponse backup = delegate.sendBackupChallenge(request.getGuardianId());
      response.setGeneratingGuardianId(backup.generatingGuardianId())
              .setDesignatedGuardianId(backup.designatedGuardianId())
              .setDesignatedGuardianXCoordinate(backup.designatedGuardianXCoordinate())
              .setError(backup.error());
      if (backup.coordinate() != null) {
        response.setCoordinate(CommonConvert.convertElementModQ(backup.coordinate()));
      }

      logger.atInfo().log("KeyCeremonyRemoteTrustee sendBackupChallenge %s", backup.designatedGuardianId());

    } catch (Throwable t) {
      logger.atSevere().withCause(t).log("KeyCeremonyRemoteTrustee sendBackupChallenge failed");
      t.printStackTrace();
      response.setError(t.getMessage());
    }

    responseObserver.onNext(response.build());
    responseObserver.onCompleted();
  }

  @Override
  public void sendJointPublicKey(RemoteKeyCeremonyTrusteeProto.JointPublicKeyRequest request,
                                 StreamObserver<RemoteKeyCeremonyTrusteeProto.JointPublicKeyResponse> responseObserver) {
    RemoteKeyCeremonyTrusteeProto.JointPublicKeyResponse.Builder response = RemoteKeyCeremonyTrusteeProto.JointPublicKeyResponse.newBuilder();
    try {
      ElementModP jointKey = delegate.publishJointKey();
      response.setJointPublicKey(CommonConvert.convertElementModP(jointKey));
      logger.atInfo().log("KeyCeremonyRemoteTrustee sendJointPublicKey %s", delegate.id);

    } catch (Throwable t) {
      logger.atSevere().withCause(t).log("KeyCeremonyRemoteTrustee sendJointPublicKey failed");
      t.printStackTrace();
      response.setError("KeyCeremonyRemoteTrustee sendJointPublicKey failed:" + t.getMessage());
    }

    responseObserver.onNext(response.build());
    responseObserver.onCompleted();
  }

  @Override
  public void saveState(com.google.protobuf.Empty request,
                        StreamObserver<CommonProto.ErrorResponse> responseObserver) {
    CommonProto.ErrorResponse.Builder response = CommonProto.ErrorResponse.newBuilder();
    try {
      TrusteeProto.DecryptingTrustee trusteeProto = TrusteeToProto.convertTrustee(this.delegate);
      publisher.overwriteTrusteeProto(trusteeProto);
      logger.atInfo().log("KeyCeremonyRemoteTrustee saveState %s", delegate.id);

    } catch (Throwable t) {
      logger.atSevere().withCause(t).log("KeyCeremonyRemoteTrustee saveState failed");
      t.printStackTrace();
      response.setError(t.getMessage());
    }

    responseObserver.onNext(response.build());
    responseObserver.onCompleted();
  }

  @Override
  public void finish(CommonProto.FinishRequest request,
                     StreamObserver<CommonProto.ErrorResponse> responseObserver) {
    CommonProto.ErrorResponse.Builder response = CommonProto.ErrorResponse.newBuilder();
    boolean ok = true;
    try {
      logger.atInfo().log("KeyCeremonyRemoteTrustee finish ok = %s", request.getAllOk());

    } catch (Throwable t) {
      logger.atSevere().withCause(t).log("KeyCeremonyRemoteTrustee finish failed");
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
