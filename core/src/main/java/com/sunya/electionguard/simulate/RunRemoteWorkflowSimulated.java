package com.sunya.electionguard.simulate;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.beust.jcommander.ParameterException;
import com.google.common.base.Stopwatch;
import com.sunya.electionguard.verifier.VerifyElectionRecord;
import com.sunya.electionguard.workflow.RunAccumulateTally;
import com.sunya.electionguard.workflow.RunEncryptBallots;
import java.util.concurrent.TimeUnit;

/**
 * Runs the entire workflow from start to finish, using simulated remote guardians.
 * Runs the components out of the fatJar, so be sure to build that first.
 * <p>
 * For command line help:
 * <strong>
 * <pre>
 *  java -classpath electionguard-java-all.jar com.sunya.electionguard.guardian.RunRemoteWorkflowSimulated --help
 * </pre>
 * </strong>
 *
 */
public class RunRemoteWorkflowSimulated {

  private static class CommandLine {
    @Parameter(names = {"-in"}, order = 0,
            description = "Directory containing input election manifest", required = true)
    String inputDir;

    @Parameter(names = {"-nguardians"}, order = 2, description = "Number of guardians to create", required = true)
    int nguardians = 6;

    @Parameter(names = {"-quorum"}, order = 3, description = "Number of guardians that make a quorum", required = true)
    int quorum = 5;

    @Parameter(names = {"-trusteeDir"}, order = 4,
            description = "Directory containing Guardian serializations", required = true)
    String trusteeDir;

    @Parameter(names = {"-keyDir"}, order = 5,
            description = "Directory containing keyCeremony output election record", required = true)
    String keyDir;

    @Parameter(names = {"-encryptDir"}, order = 5,
            description = "Directory containing ballot encryption", required = true)
    String encryptDir;

    @Parameter(names = {"-nballots"}, order = 6,
            description = "number of ballots to generate", required = true)
    int nballots;

    @Parameter(names = {"-navailable"}, order = 7, description = "Number of guardians available for decryption")
    int navailable = 0;

    @Parameter(names = {"-out"}, order = 8,
            description = "Directory where complete election record is published", required = true)
    String outputDir;

    @Parameter(names = {"-h", "--help"}, order = 99, description = "Display this help and exit", help = true)

    boolean help = false;

    private final JCommander jc;

    public CommandLine(String progName, String[] args) throws ParameterException {
      this.jc = new JCommander(this);
      this.jc.parse(args);
      jc.setProgramName(String.format("java -classpath electionguard-java-all.jar %s", progName));
    }

    public void printUsage() {
      jc.usage();
    }
  }

  public static void main(String[] args) {
    String progName = RunRemoteWorkflowSimulated.class.getName();
    CommandLine cmdLine;
    Stopwatch stopwatchAll = Stopwatch.createStarted();

    try {
      cmdLine = new CommandLine(progName, args);
      if (cmdLine.help) {
        cmdLine.printUsage();
        return;
      }

    } catch (ParameterException e) {
      System.err.println(e.getMessage());
      System.err.printf("Try '%s --help' for more information.%n", progName);
      return;
    }
    Stopwatch stopwatch = Stopwatch.createStarted();

    // key ceremony
    try {
      KeyCeremonySimulator.main(
              new String[]{
                      "-in",
                      cmdLine.inputDir,
                      "-out",
                      cmdLine.keyDir,
                      "-nguardians",
                      Integer.toString(cmdLine.nguardians),
                      "-quorum",
                      Integer.toString(cmdLine.quorum),
              }
      );
    } catch (Throwable t) {
      System.out.printf("*** KeyCeremonySimulator FAILURE%n");
      t.printStackTrace();
      return;
    }
    System.out.printf("*** KeyCeremonySimulator elapsed = %d ms%n", stopwatch.elapsed(TimeUnit.SECONDS));
    System.out.printf("%n==============================================================%n");
    stopwatch.reset().start();

    // encrypt
    try {
      RunEncryptBallots.main(
              new String[]{
                      "-in",
                      cmdLine.keyDir,
                      "-out",
                      cmdLine.encryptDir,
                      "-nballots",
                      Integer.toString(cmdLine.nballots),
                      "-device",
                      "deviceName",
                      "--save"}
      );
    } catch (Throwable t) {
      System.out.printf("*** KeyCeremony FAILURE%n");
      t.printStackTrace();
      return;
    }
    System.out.printf("*** RunEncryptBallots elapsed = %d ms%n", stopwatch.elapsed(TimeUnit.SECONDS));
    System.out.printf("%n==============================================================%n");
    stopwatch.reset().start();

    // tally
    try {
      RunAccumulateTally.main(
              new String[]{
                      "-in",
                      cmdLine.encryptDir,
                      "-out",
                      cmdLine.encryptDir
              }
      );
    } catch (Throwable t) {
      System.out.printf("*** RunAccumulateTally FAILURE%n");
      t.printStackTrace();
      return;
    }
    System.out.printf("*** RunAccumulateTally elapsed = %d ms%n", stopwatch.elapsed(TimeUnit.MILLISECONDS));
    System.out.printf("%n==============================================================%n");
    stopwatch.reset().start();

    // decrypt
    try {
      RunDecryptionSimulator.main(
              new String[]{
                      "-in",
                      cmdLine.encryptDir,
                      "-trusteeDir",
                      cmdLine.keyDir + "/election_private_data",
                      "-out",
                      cmdLine.outputDir
              }
      );
    } catch (Throwable t) {
      System.out.printf("*** RunDecryptionSimulator FAILURE%n");
      t.printStackTrace();
      return;
    }
    System.out.printf("*** RunDecryptionSimulator elapsed = %d ms%n", stopwatch.elapsed(TimeUnit.SECONDS));
    System.out.printf("%n==============================================================%n");
    stopwatch.reset().start();

    // verify
    try {
      VerifyElectionRecord.main(
              new String[]{
                      "-in",
                      cmdLine.outputDir,
                      "-usePrimes",
              }
      );
    } catch (Throwable t) {
      System.out.printf("*** VerifyElectionRecord FAILURE%n");
      t.printStackTrace();
      return;
    }
    System.out.printf("*** VerifyElectionRecord elapsed = %d ms%n", stopwatch.elapsed(TimeUnit.SECONDS));
    System.out.printf("%n==============================================================%n");

    System.out.printf("%n*** All took = %d min%n", stopwatchAll.elapsed(TimeUnit.MINUTES));
  }

  /*
  static class KeyCeremonySimulator {
    final ElectionConfig config;
    final Manifest manifest;
    final int numberOfGuardians;
    final int quorum;
    final Publisher publisher;
    final List<KeyCeremonyTrusteeSimulator> trusteeProxies;

    public KeyCeremonySimulator(String inputDir, int nguardians, int quorum, String outputDir) throws IOException {
        Consumer consumer = new Consumer(inputDir);
        this.config = consumer.readElectionConfig();
        this.manifest = config.getManifest();
        ManifestInputValidation validator = new ManifestInputValidation(config.getManifest());
        Formatter errors = new Formatter();
        if (!validator.validateElection(errors)) {
          System.out.printf("*** ElectionInputValidation FAILED on %s%n%s", inputDir, errors);
          System.exit(1);
        }
        this.publisher = new Publisher(outputDir, Publisher.Mode.createNew);


      this.numberOfGuardians = nguardians;
      this.quorum = quorum;
      System.out.printf("  Create %d Guardians, quorum = %d%n", this.numberOfGuardians, this.quorum);

      this.trusteeProxies = new ArrayList<>();
      for (int i = 0; i < nguardians; i++) {
        int seq = i + 1;
        KeyCeremonyTrustee trustee = new KeyCeremonyTrustee("trustee" + seq, seq, quorum, null);
        this.trusteeProxies.add(new KeyCeremonyTrusteeSimulator(trustee));
      }
    }

    private void runKeyCeremony() {
      List<KeyCeremonyTrusteeIF> trusteeIfs = new ArrayList<>(trusteeProxies);
      KeyCeremonyRemoteMediator mediator = new KeyCeremonyRemoteMediator(manifest, quorum, trusteeIfs);
      mediator.runKeyCeremony();

      boolean ok = mediator.publishElectionRecord(this.publisher, this.config);
      System.out.printf("%nKey Ceremony publishElectionRecord = %s%n", ok);

      List<KeyCeremonyTrustee> trustees = trusteeProxies.stream()
              .map(t -> t.delegate)
              .toList();
      boolean okt;
      try {
        PrivateData pdata = new PrivateData(publisher.publishPath() + "/private_data", false, false);
        trustees.forEach(t -> pdata.writeTrustee(t));
        okt = true;
      } catch (IOException e) {
        e.printStackTrace();
        okt = false;
      }
      System.out.printf("%nKey Ceremony publish Trustee = %s%n", okt);
    }
  }

   */



}
