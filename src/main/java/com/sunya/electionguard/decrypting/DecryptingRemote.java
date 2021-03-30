package com.sunya.electionguard.decrypting;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.beust.jcommander.ParameterException;
import com.google.common.base.Preconditions;
import com.google.common.flogger.FluentLogger;
import com.sunya.electionguard.CiphertextTally;
import com.sunya.electionguard.CiphertextTallyBuilder;
import com.sunya.electionguard.Group;
import com.sunya.electionguard.InternalManifest;
import com.sunya.electionguard.PlaintextTally;
import com.sunya.electionguard.Scheduler;
import com.sunya.electionguard.guardian.TrusteeDecryptionMediator;
import com.sunya.electionguard.input.ElectionInputValidation;
import com.sunya.electionguard.proto.CommonConvert;
import com.sunya.electionguard.proto.CommonProto;
import com.sunya.electionguard.proto.DecryptingProto;
import com.sunya.electionguard.proto.DecryptingServiceGrpc;
import com.sunya.electionguard.publish.Consumer;
import com.sunya.electionguard.publish.Publisher;
import com.sunya.electionguard.verifier.ElectionRecord;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.stub.StreamObserver;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Formatter;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * A command line program to decrypt a tally and optionally a collection of ballots with remote Guardians.
 * It opens up a channel to allow guardians to register with it.
 * It waits until it nguardians register, then starts the decryption.
 * <p>
 * For command line help:
 * <strong>
 * <pre>
 *  java -classpath electionguard-java-all.jar com.sunya.electionguard.workflow.DecryptingRemote --help
 * </pre>
 * </strong>
 */
class DecryptingRemote {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private static class CommandLine {
    @Parameter(names = {"-in"}, order = 0,
            description = "Directory containing input election record and encrypted ballots and tally", required = true)
    String encryptDir;

    @Parameter(names = {"-out"}, order = 3,
            description = "Directory where augmented election record is published", required = true)
    String outputDir;

    @Parameter(names = {"-port"}, order = 5, description = "The port to run the server on")
    int port = 17711;

    @Parameter(names = {"-h", "--help"}, order = 6, description = "Display this help and exit", help = true)
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
    String progName = DecryptingRemote.class.getName();
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
      Consumer consumer = new Consumer(cmdLine.encryptDir);
      ElectionRecord electionRecord = consumer.readElectionRecord();
      ElectionInputValidation validator = new ElectionInputValidation(electionRecord.election);
      Formatter errors = new Formatter();
      if (!validator.validateElection(errors)) {
        System.out.printf("*** ElectionInputValidation FAILED on %s%n%s", cmdLine.encryptDir, errors);
        System.exit(1);
      }

      // LOOK check that outputDir exists and can be written to
      Publisher publisher = new Publisher(cmdLine.outputDir, false, false);
      if (!publisher.validateOutputDir(errors)) {
        System.out.printf("*** Publisher validateOutputDir FAILED on %s%n%s", cmdLine.outputDir, errors);
        System.exit(1);
      }

      DecryptingRemote decryptor = new DecryptingRemote(electionRecord, cmdLine.encryptDir, cmdLine.outputDir);
      decryptor.start(cmdLine.port);
      decryptor.blockUntilShutdown();

    } catch (Throwable t) {
      System.out.printf("*** DecryptBallots FAILURE%n");
      t.printStackTrace();
      System.exit(3);

    } finally {
      Scheduler.shutdown();
    }
  }

  ///////////////////////////////////////////////////////////////////////////
  private Server server;

  private void start(int port) throws IOException {
    server = ServerBuilder.forPort(port) //
            .addService(new DecryptingRegistrationService()) //
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

  ///////////////////////////////////////////////////////////////////////////
  final ElectionRecord electionRecord;
  final String encryptDir;
  final String outputDir;

  final int nguardians;
  final int quorum;
  final List<DecryptingRemoteTrusteeProxy> trusteeProxies = Collections.synchronizedList(new ArrayList<>());
  boolean startedDecryption = false;

  CiphertextTally encryptedTally;
  PlaintextTally decryptedTally;

  DecryptingRemote(ElectionRecord electionRecord, String encryptDir, String outputDir) {
    this.electionRecord = electionRecord;
    this.encryptDir = encryptDir;
    this.outputDir = outputDir;
    this.nguardians = electionRecord.context.number_of_guardians;
    this.quorum = electionRecord.context.quorum;
  }

  private synchronized void checkAllGuardiansAreRegistered() {
    System.out.printf(" Number of Guardians registered = %d, quorum = %d nguardians = %d%n",
            this.trusteeProxies.size(), this.quorum, this.nguardians);
    if (this.trusteeProxies.size() == this.nguardians) {
      this.startedDecryption = true;
      System.out.printf("Begin Key Ceremony%n");
      try {
        Thread.sleep(5000);
      } catch (InterruptedException e) {
        e.printStackTrace();
      }
      runDecryption();
    }
  }

  private void runDecryption() {
    // Do the accumulation if the encryptedTally doesnt exist
    if (this.electionRecord.encryptedTally == null) {
      accumulateTally();
    } else {
      this.encryptedTally = this.electionRecord.encryptedTally;
    }

    decryptTally();

    boolean ok = false;
    try {
      publish(encryptDir, outputDir);
      ok = true;
    } catch (IOException e) {
      e.printStackTrace();
      ok = false;
    }

    System.out.printf("*** DecryptBallots %s%n", ok ? "SUCCESS" : "FAILURE");
    System.exit(ok ? 0 : 1);
  }

  void accumulateTally() {
    System.out.printf("%nAccumulate tally%n");
    InternalManifest metadata = new InternalManifest(this.electionRecord.election);
    CiphertextTallyBuilder ciphertextTally = new CiphertextTallyBuilder("DecryptBallots", metadata, electionRecord.context);
    int nballots = ciphertextTally.batch_append(electionRecord.acceptedBallots);
    this.encryptedTally = ciphertextTally.build();
    System.out.printf(" done accumulating %d ballots in the tally%n", nballots);
  }

  void decryptTally() {
    // LOOK validate tally is well formed
    System.out.printf("%nDecrypt tally%n");

    // The guardians' election public key is in the electionRecord.guardianCoefficients.
    Map<String, Group.ElementModP> guardianPublicKeys = electionRecord.guardianCoefficients.stream().collect(
            Collectors.toMap(coeff -> coeff.owner_id(), coeff -> coeff.coefficient_commitments().get(0)));

    TrusteeDecryptionMediator mediator = new TrusteeDecryptionMediator(electionRecord.context,
            electionRecord.encryptedTally,
            new ArrayList<>(), // LOOK not doing spoiled ballots yet
            guardianPublicKeys);

    int count = 0;
    for (DecryptingRemoteTrusteeProxy guardian : this.trusteeProxies) {
      boolean ok = mediator.announce(guardian);
      Preconditions.checkArgument(ok);
      System.out.printf(" Guardian Present: %s%n", guardian.id());
      count++;
      if (count == this.quorum) {
        System.out.printf("Quorum of %d reached%n", this.quorum);
        break;
      }
    }

    // Here's where the ciphertext Tally is decrypted.
    this.decryptedTally = mediator.get_plaintext_tally().orElseThrow();
    //List<SpoiledBallotAndTally> spoiledTallyAndBallot =
    //        mediator.decrypt_spoiled_ballots().orElseThrow();
    //this.spoiledDecryptedBallots = spoiledTallyAndBallot.stream().map(e -> e.ballot).collect(Collectors.toList());
    //this.spoiledDecryptedTallies = spoiledTallyAndBallot.stream().map(e -> e.tally).collect(Collectors.toList());
    //this.availableGuardians = mediator.getAvailableGuardians();
    System.out.printf("Done decrypting tally%n%n%s%n", this.decryptedTally);

    // tell the remote trustees to shutdown
    boolean shutdownOk = true;
    for (DecryptingRemoteTrusteeProxy trustee : trusteeProxies) {
      if (!trustee.shutdown()) {
        shutdownOk = false;
      }
    }
    System.out.printf("Key Ceremony Trustees shutdown was success = %s%n", shutdownOk);
  }

  void publish(String inputDir, String publishDir) throws IOException {
    Publisher publisher = new Publisher(publishDir, true, false);
    publisher.writeDecryptionResultsProto(
            this.electionRecord,
            this.encryptedTally,
            this.decryptedTally,
            new ArrayList<>(), new ArrayList<>(), new ArrayList<>());

    publisher.copyAcceptedBallots(inputDir);
  }

  private synchronized DecryptingRemoteTrusteeProxy registerTrustee(DecryptingProto.RegisterDecryptingTrusteeRequest request) {
    DecryptingRemoteTrusteeProxy.Builder builder = DecryptingRemoteTrusteeProxy.builder();
    builder.setTrusteeId(request.getGuardianId());
    builder.setUrl(request.getRemoteUrl());
    builder.setXCoordinate(request.getGuardianXCoordinate());
    builder.setElectionPublicKey(CommonConvert.convertElementModP(request.getPublicKey()));
    DecryptingRemoteTrusteeProxy trustee = builder.build();
    trusteeProxies.add(trustee);
    return trustee;
  }

  private class DecryptingRegistrationService extends DecryptingServiceGrpc.DecryptingServiceImplBase {

    //     public void registerTrustee(com.sunya.electionguard.proto.DecryptingProto.RegisterDecryptingTrusteeRequest request,
    //        io.grpc.stub.StreamObserver<com.sunya.electionguard.proto.DecryptingProto.RegisterDecryptingTrusteeResponse> responseObserver) {
    @Override
    public void registerTrustee(DecryptingProto.RegisterDecryptingTrusteeRequest request,
                                StreamObserver<DecryptingProto.RegisterDecryptingTrusteeResponse> responseObserver) {

      System.out.printf("KeyCeremonyRemote registerTrustee %s url %s %n", request.getGuardianId(), request.getRemoteUrl());

      if (startedDecryption) {
        responseObserver.onNext(DecryptingProto.RegisterDecryptingTrusteeResponse.newBuilder()
                .setError(CommonProto.RemoteError.newBuilder().setMessage("Already started Decryption").build()).build());
        responseObserver.onCompleted();
        return;
      }

      DecryptingProto.RegisterDecryptingTrusteeResponse.Builder response = DecryptingProto.RegisterDecryptingTrusteeResponse.newBuilder();
      try {
        DecryptingRemoteTrusteeProxy trustee = DecryptingRemote.this.registerTrustee(request);
        response.setOk(true);

        responseObserver.onNext(response.build());
        responseObserver.onCompleted();
        logger.atInfo().log("KeyCeremonyRemote registerTrustee registerTrustee %s", trustee.id());

      } catch (Throwable t) {
        logger.atSevere().withCause(t).log("KeyCeremonyRemote sendPublicKeys failed");
        t.printStackTrace();
        response.setError(CommonProto.RemoteError.newBuilder().setMessage(t.getMessage()).build());
        responseObserver.onNext(response.build());
        responseObserver.onCompleted();
      }
      DecryptingRemote.this.checkAllGuardiansAreRegistered();
    }
  }

}
