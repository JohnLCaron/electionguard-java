package com.sunya.electionguard.keyceremony;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.beust.jcommander.ParameterException;
import com.google.common.flogger.FluentLogger;
import com.sunya.electionguard.Manifest;
import com.sunya.electionguard.input.ElectionInputValidation;
import com.sunya.electionguard.proto.RemoteKeyCeremonyProto;
import com.sunya.electionguard.proto.RemoteKeyCeremonyServiceGrpc;
import com.sunya.electionguard.publish.Consumer;
import com.sunya.electionguard.publish.Publisher;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.stub.StreamObserver;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Formatter;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A command line program that performs the key ceremony with remote Guardians.
 * It opens up a channel to allow guardians to register with it.
 * It waits until it nguardians register, then starts the key ceremony.
 * <p>
 * For command line help:
 * <strong>
 * <pre>
 *  java -classpath electionguard-java-all.jar com.sunya.electionguard.keyceremony.KeyCeremonyRemote --help
 * </pre>
 * </strong>
 */
class KeyCeremonyRemote {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private static class CommandLine {
    @Parameter(names = {"-in"}, order = 0,
            description = "Directory containing election manifest", required = true)
    String inputDir;

    @Parameter(names = {"-out"}, order = 1,
            description = "Directory where election record is written")
    String outputDir;

    @Parameter(names = {"-nguardians"}, order = 2, description = "Number of Guardians that will be used", required = true)
    int nguardians;

    @Parameter(names = {"-quorum"}, order = 3, description = "Number of Guardians that make a quorum", required = true)
    int quorum;

    @Parameter(names = {"-port"}, order = 4, description = "The port to run the server on")
    int port = 17111;

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
    String progName = KeyCeremonyRemote.class.getName();
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

    try {
      // all we need from election record is the ElectionDescription.
      Consumer consumer = new Consumer(cmdLine.inputDir);
      Manifest election = consumer.readManifest();
      ElectionInputValidation validator = new ElectionInputValidation(election);
      Formatter errors = new Formatter();
      if (!validator.validateElection(errors)) {
        System.out.printf("*** ElectionInputValidation FAILED on %s%n%s", cmdLine.inputDir, errors);
        System.exit(1);
      }

      // LOOK check that outputDir exists and can be written to
      Publisher publisher = new Publisher(cmdLine.outputDir, false, false);
      if (!publisher.validateOutputDir(errors)) {
        System.out.printf("*** Publisher validateOutputDir FAILED on %s%n%s", cmdLine.outputDir, errors);
        System.exit(1);
      }
      KeyCeremonyRemote keyCeremony = new KeyCeremonyRemote(election, cmdLine.nguardians, cmdLine.quorum, publisher);

      keyCeremony.start(cmdLine.port);

      try {
        Thread.sleep(5000);
      } catch (InterruptedException e) {
        e.printStackTrace();
      }
      keyCeremony.runKeyCeremony();
      // keyCeremony.blockUntilShutdown();
      System.exit(0);

    } catch (Throwable t) {
      System.out.printf("*** KeyCeremonyRemote FAILURE%n");
      t.printStackTrace();
      System.exit(3);
    }
  }

  ///////////////////////////////////////////////////////////////////////////
  private Server server;

  private void start(int port) throws IOException {
    server = ServerBuilder.forPort(port) //
            .addService(new KeyCeremonyRemoteService()) //
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

  ///////////////////////////////////////////////////////////////////////////
  final Manifest manifest;
  final int nguardians;
  final int quorum;
  final Publisher publisher;
  final List<KeyCeremonyRemoteTrusteeProxy> trusteeProxies = Collections.synchronizedList(new ArrayList<>());
  boolean startedKeyCeremony = false;

  KeyCeremonyRemote(Manifest manifest, int nguardians, int quorum, Publisher publisher) {
    this.manifest = manifest;
    this.nguardians = nguardians;
    this.quorum = quorum;
    this.publisher = publisher;
  }

  synchronized void checkAllGuardiansAreRegistered() {
    System.out.printf(" Number of Guardians registered = %d, need = %d%n", this.trusteeProxies.size(), this.nguardians);
    if (!this.startedKeyCeremony && this.trusteeProxies.size() == this.nguardians) {
      this.startedKeyCeremony = true;
      System.out.printf("Begin Key Ceremony%n");
      try {
        Thread.sleep(1000);
      } catch (InterruptedException e) {
        e.printStackTrace();
      }
      runKeyCeremony();
    }
  }

  private void runKeyCeremony() {
    // This runs the key ceremony
    List<KeyCeremonyTrusteeIF> trusteeIfs = new ArrayList<>(trusteeProxies);
    KeyCeremonyTrusteeMediator mediator = new KeyCeremonyTrusteeMediator(manifest, quorum, trusteeIfs);

    // tell the remote trustees to save their state
    boolean allOk = true;
    for (KeyCeremonyRemoteTrusteeProxy trustee : trusteeProxies) {
      if (!trustee.saveState()) {
        allOk = false;
      }
    }
    System.out.printf("%nKey Ceremony Trustees save state was success = %s%n", allOk);

    if (allOk) {
      allOk = mediator.publishElectionRecord(publisher);
    }

    // tell the remote trustees if its a take
    boolean finishOk = true;
    for (KeyCeremonyRemoteTrusteeProxy trustee : trusteeProxies) {
      if (!trustee.finish(allOk)) {
        finishOk = false;
      }
    }
    System.out.printf("Key Ceremony Trustees finish was success = %s%n", finishOk);

    // tell the remote trustees to shutdown
    boolean shutdownOk = true;
    for (KeyCeremonyRemoteTrusteeProxy trustee : trusteeProxies) {
      if (!trustee.shutdown()) {
        shutdownOk = false;
      }
    }
    System.out.printf("Key Ceremony Trustees shutdown was success = %s%n", shutdownOk && finishOk);
    System.exit(shutdownOk && finishOk ? 0 : 1);
  }

  AtomicInteger nextCoordinate = new AtomicInteger(0);
  private synchronized KeyCeremonyRemoteTrusteeProxy registerTrustee(String guardianId, String url) {
    KeyCeremonyRemoteTrusteeProxy.Builder builder = KeyCeremonyRemoteTrusteeProxy.builder();
    int coordinate = nextCoordinate.incrementAndGet();
    String trusteeId = guardianId + "-" + coordinate;
    builder.setTrusteeId(trusteeId);
    builder.setUrl(url);
    builder.setCoordinate(coordinate);
    builder.setQuorum(this.quorum);
    KeyCeremonyRemoteTrusteeProxy trustee = builder.build();
    trusteeProxies.add(trustee);
    return trustee;
  }

  private class KeyCeremonyRemoteService extends RemoteKeyCeremonyServiceGrpc.RemoteKeyCeremonyServiceImplBase {

    @Override
    public void registerTrustee(RemoteKeyCeremonyProto.RegisterTrusteeRequest request,
                                StreamObserver<RemoteKeyCeremonyProto.RegisterTrusteeResponse> responseObserver) {

      System.out.printf("KeyCeremonyRemote registerTrustee %s url %s %n", request.getGuardianId(), request.getRemoteUrl());

      if (startedKeyCeremony) {
        responseObserver.onNext(RemoteKeyCeremonyProto.RegisterTrusteeResponse.newBuilder()
                .setError(RemoteKeyCeremonyProto.KeyCeremonyError.newBuilder().setMessage("startedKeyCeremony").build())
                .build());
        responseObserver.onCompleted();
        return;
      }

      KeyCeremonyRemoteTrusteeProxy trustee = KeyCeremonyRemote.this.registerTrustee(request.getGuardianId(), request.getRemoteUrl());
      RemoteKeyCeremonyProto.RegisterTrusteeResponse.Builder response = RemoteKeyCeremonyProto.RegisterTrusteeResponse.newBuilder();
      try {
        response.setGuardianId(trustee.id());
        response.setGuardianXCoordinate(trustee.coordinate());
        response.setQuorum(trustee.quorum());
        responseObserver.onNext(response.build());
        responseObserver.onCompleted();
        logger.atInfo().log("KeyCeremonyRemote registerTrustee registerTrustee %s", trustee.id());

      } catch (Throwable t) {
        logger.atSevere().withCause(t).log("KeyCeremonyRemote registerTrustee failed");
        t.printStackTrace();
        response.setError(RemoteKeyCeremonyProto.KeyCeremonyError.newBuilder().setMessage(t.getMessage()).build());
        responseObserver.onNext(response.build());
        responseObserver.onCompleted();
      }
      // KeyCeremonyRemote.this.checkAllGuardiansAreRegistered();
    }
  }

}
