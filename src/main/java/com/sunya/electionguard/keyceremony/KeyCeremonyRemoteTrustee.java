package com.sunya.electionguard.keyceremony;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.beust.jcommander.ParameterException;
import com.google.common.flogger.FluentLogger;
import com.google.protobuf.ByteString;
import com.sunya.electionguard.Auxiliary;
import com.sunya.electionguard.SchnorrProof;
import com.sunya.electionguard.guardian.KeyCeremony2;
import com.sunya.electionguard.guardian.KeyCeremonyTrustee;
import com.sunya.electionguard.proto.CommonConvert;
import com.sunya.electionguard.proto.RemoteKeyCeremonyProto;
import com.sunya.electionguard.proto.RemoteTrusteeProto;
import com.sunya.electionguard.proto.RemoteTrusteeServiceGrpc;
import com.sunya.electionguard.proto.TrusteeProto;
import com.sunya.electionguard.proto.TrusteeToProto;
import com.sunya.electionguard.publish.Publisher;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.stub.StreamObserver;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.List;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static com.sunya.electionguard.Group.ElementModP;

/** A Remote Trustee with a KeyCeremonyTrustee delegate, communicating over gRpc. */
class KeyCeremonyRemoteTrustee extends RemoteTrusteeServiceGrpc.RemoteTrusteeServiceImplBase {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();
  private static final Random random = new Random(System.currentTimeMillis());

  private static class CommandLine {
    @Parameter(names = {"-name"}, order = 0, description = "Trustee name", required = true)
    String name;

    @Parameter(names = {"-port"}, order = 1, description = "This KeyCeremonyRemoteTrustee port")
    int port = 17111;

    @Parameter(names = {"-serverPort"}, order = 2, description = "The KeyCeremonyRemote server port")
    int serverPort = 17111;

    @Parameter(names = {"-out"}, order = 1, description = "Directory where Guardians is written for decryption", required = true)
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
    if (port == cmdLine.serverPort) {
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
    RemoteKeyCeremonyProto.RegisterTrusteeResponse response = proxy.registerTrustee(cmdLine.name, url);
    proxy.shutdown();
    if (response.hasError()) {
      System.out.printf("    registerTrustee error %s%n", response.getError().getMessage());
      throw new RuntimeException(response.getError().getMessage());
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
  private final KeyCeremonyTrustee delegate;
  private final String outputDir;

  KeyCeremonyRemoteTrustee(String id,
                            int sequence_order,
                            int quorum,
                            String outputDir) {
    this.delegate = new KeyCeremonyTrustee(id, sequence_order, quorum, null);
    this.outputDir = outputDir;
  }

  @Override
  public void sendPublicKeys(RemoteTrusteeProto.PublicKeySetRequest request,
                             StreamObserver<RemoteTrusteeProto.PublicKeySet> responseObserver) {

    RemoteTrusteeProto.PublicKeySet.Builder response = RemoteTrusteeProto.PublicKeySet.newBuilder();
    try {
      KeyCeremony2.PublicKeySet keyset = delegate.sharePublicKeys();
      response.setOwnerId(keyset.ownerId())
              .setGuardianXCoordinate(keyset.guardianXCoordinate())
              .setAuxiliaryPublicKey(CommonConvert.convertJavaPublicKey(keyset.auxiliaryPublicKey()));
      keyset.coefficientProofs().forEach(p -> response.addCoefficientProofs(CommonConvert.convertSchnorrProof(p)));
      logger.atInfo().log("KeyCeremonyRemoteTrustee sendPublicKeys %s", delegate.id);
    } catch (Throwable t) {
      logger.atSevere().withCause(t).log("KeyCeremonyRemoteTrustee sendPublicKeys failed");
      t.printStackTrace();
      response.setError(RemoteTrusteeProto.RemoteTrusteeError.newBuilder().setMessage(t.getMessage()).build());
    }

    responseObserver.onNext(response.build());
    responseObserver.onCompleted();
  }

  @Override
  public void receivePublicKeys(RemoteTrusteeProto.PublicKeySet proto,
                                StreamObserver<RemoteTrusteeProto.BooleanResponse> responseObserver) {

    RemoteTrusteeProto.BooleanResponse.Builder response = RemoteTrusteeProto.BooleanResponse.newBuilder();
    try {
      List<SchnorrProof> proofs = proto.getCoefficientProofsList().stream().map(CommonConvert::convertSchnorrProof).collect(Collectors.toList());
      KeyCeremony2.PublicKeySet keyset = KeyCeremony2.PublicKeySet.create(
              proto.getOwnerId(),
              proto.getGuardianXCoordinate(),
              CommonConvert.convertJavaPublicKey(proto.getAuxiliaryPublicKey()),
              proofs);
      boolean valid = delegate.receivePublicKeys(keyset);
      response.setOk(valid);
      logger.atInfo().log("KeyCeremonyRemoteTrustee receivePublicKeys %s", delegate.id);

    } catch (Throwable t) {
      logger.atSevere().withCause(t).log("KeyCeremonyRemoteTrustee receivePublicKeys failed");
      t.printStackTrace();
      response.setError(RemoteTrusteeProto.RemoteTrusteeError.newBuilder().setMessage(t.getMessage()).build());
    }

    responseObserver.onNext(response.build());
    responseObserver.onCompleted();
  }

  @Override
  public void sendPartialKeyBackup(RemoteTrusteeProto.PartialKeyBackupRequest request,
                                   StreamObserver<RemoteTrusteeProto.PartialKeyBackup> responseObserver) {

    RemoteTrusteeProto.PartialKeyBackup.Builder response = RemoteTrusteeProto.PartialKeyBackup.newBuilder();
    try {
      KeyCeremony2.PartialKeyBackup backup = delegate.sendPartialKeyBackup(request.getGuardianId());
      if (backup == null) {
        logger.atSevere().log("KeyCeremonyRemoteTrustee sendPartialKeyBackup failed");
        response.setError(RemoteTrusteeProto.RemoteTrusteeError.newBuilder().setMessage("why?").build());

      } else {
        response.setGeneratingGuardianId(backup.generatingGuardianId())
                .setDesignatedGuardianId(backup.designatedGuardianId())
                .setDesignatedGuardianXCoordinate(backup.designatedGuardianXCoordinate())
                .setEncryptedCoordinate(ByteString.copyFrom(backup.encryptedCoordinate().getBytes()));
        logger.atInfo().log("KeyCeremonyRemoteTrustee sendPartialKeyBackup %s", delegate.id);
      }

    } catch (Throwable t) {
      logger.atSevere().withCause(t).log("KeyCeremonyRemoteTrustee sendPartialKeyBackup failed");
      t.printStackTrace();
      response.setError(RemoteTrusteeProto.RemoteTrusteeError.newBuilder().setMessage(t.getMessage()).build());
    }

    responseObserver.onNext(response.build());
    responseObserver.onCompleted();
  }

  @Override
  public void verifyPartialKeyBackup(RemoteTrusteeProto.PartialKeyBackup proto,
                                     StreamObserver<RemoteTrusteeProto.PartialKeyVerification> responseObserver) {

    RemoteTrusteeProto.PartialKeyVerification.Builder response = RemoteTrusteeProto.PartialKeyVerification.newBuilder();
    try {
      KeyCeremony2.PartialKeyBackup backup = KeyCeremony2.PartialKeyBackup.create(
              proto.getGeneratingGuardianId(),
              proto.getDesignatedGuardianId(),
              proto.getDesignatedGuardianXCoordinate(),
              new Auxiliary.ByteString(proto.getEncryptedCoordinate().toByteArray()));

      KeyCeremony2.PartialKeyVerification verify = delegate.verifyPartialKeyBackup(backup);
      response.setGeneratingGuardianId(verify.generatingGuardianId())
              .setDesignatedGuardianId(verify.designatedGuardianId())
              .setDesignatedGuardianXCoordinate(backup.designatedGuardianXCoordinate())
              .setVerify(verify.verified());
      logger.atInfo().log("KeyCeremonyRemoteTrustee verifyPartialKeyBackup %s", delegate.id);

    } catch (Throwable t) {
      logger.atSevere().withCause(t).log("KeyCeremonyRemoteTrustee verifyPartialKeyBackup failed");
      t.printStackTrace();
      response.setError(RemoteTrusteeProto.RemoteTrusteeError.newBuilder().setMessage(t.getMessage()).build());
    }

    responseObserver.onNext(response.build());
    responseObserver.onCompleted();
  }

  @Override
  public void sendBackupChallenge(RemoteTrusteeProto.PartialKeyChallenge request,
                                  StreamObserver<RemoteTrusteeProto.PartialKeyChallengeResponse> responseObserver) {
    RemoteTrusteeProto.PartialKeyChallengeResponse.Builder response = RemoteTrusteeProto.PartialKeyChallengeResponse.newBuilder();
    try {
      KeyCeremony2.PartialKeyChallengeResponse backup = delegate.sendBackupChallenge(request.getGuardianId());
      if (backup == null) {
        logger.atSevere().log("KeyCeremonyRemoteTrustee sendBackupChallenge failed");
        response.setError(RemoteTrusteeProto.RemoteTrusteeError.newBuilder().setMessage("why?").build());
      } else {
        response.setGeneratingGuardianId(backup.generatingGuardianId())
                .setDesignatedGuardianId(backup.designatedGuardianId())
                .setDesignatedGuardianXCoordinate(backup.designatedGuardianXCoordinate())
                .setCoordinate(CommonConvert.convertElementModQ(backup.coordinate()));
        logger.atInfo().log("KeyCeremonyRemoteTrustee sendBackupChallenge %s", delegate.id);
      }

    } catch (Throwable t) {
      logger.atSevere().withCause(t).log("KeyCeremonyRemoteTrustee sendBackupChallenge failed");
      t.printStackTrace();
      response.setError(RemoteTrusteeProto.RemoteTrusteeError.newBuilder().setMessage(t.getMessage()).build());
    }

    responseObserver.onNext(response.build());
    responseObserver.onCompleted();
  }

  @Override
  public void sendJointPublicKey(RemoteTrusteeProto.JointPublicKeyRequest request,
                                 StreamObserver<RemoteTrusteeProto.JointPublicKeyResponse> responseObserver) {
    RemoteTrusteeProto.JointPublicKeyResponse.Builder response = RemoteTrusteeProto.JointPublicKeyResponse.newBuilder();
    try {
      ElementModP jointKey = delegate.publishJointKey();
      response.setJointPublicKey(CommonConvert.convertElementModP(jointKey));
      logger.atInfo().log("KeyCeremonyRemoteTrustee sendJointPublicKey %s", delegate.id);

    } catch (Throwable t) {
      logger.atSevere().withCause(t).log("KeyCeremonyRemoteTrustee sendJointPublicKey failed");
      t.printStackTrace();
      response.setError(RemoteTrusteeProto.RemoteTrusteeError.newBuilder().setMessage(t.getMessage()).build());
    }

    responseObserver.onNext(response.build());
    responseObserver.onCompleted();
  }

  @Override
  public void saveState(com.google.protobuf.Empty request,
                        StreamObserver<RemoteTrusteeProto.BooleanResponse> responseObserver) {
    RemoteTrusteeProto.BooleanResponse.Builder response = RemoteTrusteeProto.BooleanResponse.newBuilder();
    boolean ok = true;
    try {
      TrusteeProto.Trustee trusteeProto = TrusteeToProto.convertTrustee(this.delegate);
      Publisher.overwriteTrusteeProto(this.outputDir, trusteeProto);
      logger.atInfo().log("KeyCeremonyRemoteTrustee saveState %s", delegate.id);

    } catch (Throwable t) {
      logger.atSevere().withCause(t).log("KeyCeremonyRemoteTrustee saveState failed");
      t.printStackTrace();
      response.setError(RemoteTrusteeProto.RemoteTrusteeError.newBuilder().setMessage(t.getMessage()).build());
      ok = false;
    }

    response.setOk(ok);
    responseObserver.onNext(response.build());
    responseObserver.onCompleted();
  }

  // LOOK what to do?
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
  }

}
