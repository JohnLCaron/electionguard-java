package com.sunya.electionguard.keyceremony;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.beust.jcommander.ParameterException;
import com.google.common.base.Stopwatch;
import com.google.common.flogger.FluentLogger;
import com.sunya.electionguard.ElectionConstants;
import com.sunya.electionguard.Group;
import com.sunya.electionguard.Manifest;
import com.sunya.electionguard.input.ManifestInputValidation;
import com.sunya.electionguard.verifier.ElectionRecord;
import electionguard.protogen.RemoteKeyCeremonyProto;
import electionguard.protogen.RemoteKeyCeremonyServiceGrpc;
import com.sunya.electionguard.publish.Consumer;
import com.sunya.electionguard.publish.Publisher;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.stub.StreamObserver;

import java.io.FileNotFoundException;
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
 * It waits until nguardians register, then starts the key ceremony.
 * <p>
 * For command line help:
 * <strong>
 * <pre>
 *  java -classpath electionguard-java-all.jar com.sunya.electionguard.keyceremony.KeyCeremonyRemote --help
 * </pre>
 * </strong>
 */
public class KeyCeremonyRemote {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private static class CommandLine {
    @Parameter(names = {"-in"}, order = 0,
            description = "Directory containing election manifest", required = true)
    String inputDir;

    @Parameter(names = {"-out"}, order = 1,
            description = "Directory where election record is written", required = true)
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

    boolean allOk = false;
    KeyCeremonyRemote keyCeremony = null;
    try {
      Consumer consumer = new Consumer(cmdLine.inputDir);
      ElectionRecord election = consumer.readElectionRecord();
      ManifestInputValidation validator = new ManifestInputValidation(election.manifest);
      Formatter errors = new Formatter();
      if (!validator.validateElection(errors)) {
        System.out.printf("*** ManifestInputValidation FAILED on %s%n%s", cmdLine.inputDir, errors);
        System.exit(1);
      }

      if (election.constants != null) {
        Group.setPrimes(election.constants);
      }

      keyCeremony = new KeyCeremonyRemote(election.manifest, cmdLine.nguardians, cmdLine.quorum, cmdLine.outputDir);
      keyCeremony.start(cmdLine.port);

      System.out.print("Waiting for guardians to register: elapsed seconds = ");
      Stopwatch stopwatch = Stopwatch.createStarted();
      while (!keyCeremony.ready()) {
        System.out.printf("%s ", stopwatch.elapsed(TimeUnit.SECONDS));
        try {
          Thread.sleep(5000);
        } catch (InterruptedException e) {
          e.printStackTrace();
        }
      }
      System.out.printf("%n");

      allOk = keyCeremony.runKeyCeremony();

    } catch (Throwable t) {
      System.out.printf("*** KeyCeremonyRemote FAILURE = %s%n", t.getMessage());
      t.printStackTrace();
      allOk = false;

    } finally {
      if (keyCeremony != null) {
        keyCeremony.shutdownRemoteTrustees(allOk);
      }
    }
    System.exit(allOk ? 0 : 1);
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

  ///////////////////////////////////////////////////////////////////////////
  final Manifest manifest;
  final int nguardians;
  final int quorum;
  final Publisher publisher;
  final List<KeyCeremonyRemoteTrusteeProxy> trusteeProxies = Collections.synchronizedList(new ArrayList<>());
  boolean startedKeyCeremony = false;

  KeyCeremonyRemote(Manifest manifest, int nguardians, int quorum, String outputDir) throws IOException {
    this.manifest = manifest;
    this.nguardians = nguardians;
    this.quorum = quorum;

    this.publisher = new Publisher(outputDir, Publisher.Mode.createNew);
    Formatter errors = new Formatter();
    if (!publisher.validateOutputDir(errors)) {
      System.out.printf("*** Publisher validateOutputDir FAILED on %s%n%s", outputDir, errors);
      throw new FileNotFoundException(errors.toString());
    }
  }

  boolean ready() {
    return trusteeProxies.size() == nguardians;
  }

  private boolean runKeyCeremony() {
    if (trusteeProxies.size() != nguardians) {
      throw new IllegalStateException(String.format("Need %d guardians, but only %d registered", nguardians,
              trusteeProxies.size()));
    }
    List<KeyCeremonyTrusteeIF> trusteeIfs = new ArrayList<>(trusteeProxies);
    KeyCeremonyRemoteMediator mediator = new KeyCeremonyRemoteMediator(manifest, quorum, trusteeIfs);
    mediator.runKeyCeremony();

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

    return allOk;
  }

  private void shutdownRemoteTrustees(boolean allOk) {
    System.out.printf("Shutdown Remote Trustees%n");
    // tell the remote trustees to finish
    for (KeyCeremonyRemoteTrusteeProxy trustee : trusteeProxies) {
      try {
        boolean ok = trustee.finish(allOk);
        System.out.printf(" KeyCeremonyRemoteTrusteeProxy %s shutdown was success = %s%n", trustee.id(), ok);
      } catch (Throwable t) {
        t.printStackTrace();
      }
    }

    // close the proxy channels
    boolean shutdownOk = true;
    for (KeyCeremonyRemoteTrusteeProxy trustee : trusteeProxies) {
      if (!trustee.shutdown()) {
        shutdownOk = false;
      }
    }
    System.out.printf(" Proxy channel shutdown was success = %s%n", shutdownOk);
  }

  private final AtomicInteger nextCoordinate = new AtomicInteger(0);
  synchronized KeyCeremonyRemoteTrusteeProxy registerTrustee(String guardianId, String url) {
    for (KeyCeremonyRemoteTrusteeProxy proxy : trusteeProxies) {
      if (proxy.id().toLowerCase().contains(guardianId.toLowerCase()) ||
              guardianId.toLowerCase().contains(proxy.id().toLowerCase())) {
        throw new IllegalArgumentException(
                String.format("Trying to add a guardian id '%s' equal or similar to existing '%s'",
                guardianId, proxy.id()));
      }
    }
    KeyCeremonyRemoteTrusteeProxy.Builder builder = KeyCeremonyRemoteTrusteeProxy.builder();
    int coordinate = nextCoordinate.incrementAndGet();
    builder.setTrusteeId(guardianId);
    builder.setUrl(url);
    builder.setCoordinate(coordinate);
    builder.setQuorum(this.quorum);
    KeyCeremonyRemoteTrusteeProxy trustee = builder.build();
    trusteeProxies.add(trustee);
    return trustee;
  }

  private class KeyCeremonyRemoteService extends RemoteKeyCeremonyServiceGrpc.RemoteKeyCeremonyServiceImplBase {

    @Override
    public void registerTrustee(RemoteKeyCeremonyProto.RegisterKeyCeremonyTrusteeRequest request,
                                StreamObserver<RemoteKeyCeremonyProto.RegisterKeyCeremonyTrusteeResponse> responseObserver) {

      System.out.printf("KeyCeremonyRemote registerTrustee %s url %s %n", request.getGuardianId(), request.getRemoteUrl());

      if (startedKeyCeremony) {
        responseObserver.onNext(RemoteKeyCeremonyProto.RegisterKeyCeremonyTrusteeResponse.newBuilder()
                .setError("Already started KeyCeremony")
                .build());
        responseObserver.onCompleted();
        return;
      }

      RemoteKeyCeremonyProto.RegisterKeyCeremonyTrusteeResponse.Builder response = RemoteKeyCeremonyProto.RegisterKeyCeremonyTrusteeResponse.newBuilder();
      try {
        KeyCeremonyRemoteTrusteeProxy trustee = KeyCeremonyRemote.this.registerTrustee(request.getGuardianId(), request.getRemoteUrl());
        response.setGuardianId(trustee.id());
        response.setGuardianXCoordinate(trustee.xCoordinate());
        response.setQuorum(trustee.quorum());
        if (Group.getPrimes().getPrimeOptionType() != ElectionConstants.PrimeOption.Standard) {
          response.setConstants(Group.getPrimes().getPrimeOptionType().name());
        }
        responseObserver.onNext(response.build());
        responseObserver.onCompleted();
        logger.atInfo().log("KeyCeremonyRemote registerTrustee '%s'", trustee.id());

      } catch (Throwable t) {
        logger.atSevere().withCause(t).log("KeyCeremonyRemote registerTrustee failed");
        response.setError(t.getMessage());
        responseObserver.onNext(response.build());
        responseObserver.onCompleted();
      }
    }
  }

}
