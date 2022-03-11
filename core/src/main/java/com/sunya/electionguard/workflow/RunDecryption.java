package com.sunya.electionguard.workflow;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.beust.jcommander.ParameterException;
import com.google.common.base.Stopwatch;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import com.sunya.electionguard.publish.PrivateData;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

/**
 * Runs the decryption, using remote guardians. Encryption and Tally Accumulation has already been run.
 * Runs the components out of the fatJar, so be sure to build that first: "./gradlew clean assemble fatJar"
 * Also be sure to keep RunStandardWorkflow.classpath synched with fatjar SHAPSHOT version.
 * <p>
 * For command line help:
 * <strong>
 * <pre>
 *  java -classpath electionguard-java-all.jar com.sunya.electionguard.workflow.RunDecryption --help
 * </pre>
 * </strong>
 */
public class RunDecryption {
  public static final String classpath = "core/build/libs/core-0.9.5-SNAPSHOT-all.jar";
  private static final String REMOTE_TRUSTEE = "remoteTrustee";
  private static final String CMD_OUTPUT = "/home/snake/tmp/runDecryption/";

  private static class CommandLine {
    @Parameter(names = {"-trusteeDir"}, order = 4,
            description = "Directory containing Guardian serializations", required = true)
    String trusteeDir;

    @Parameter(names = {"-in"}, order = 5,
            description = "Directory containing ballot encryption and tally", required = true)
    String encryptDir;

    @Parameter(names = {"-navailable"}, order = 7, description = "Number of guardians available for decryption", required = true)
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
    String progName = RunDecryption.class.getName();
    CommandLine cmdLine;
    Stopwatch stopwatchAll = Stopwatch.createStarted();
    Stopwatch stopwatch = Stopwatch.createStarted();

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

    ListeningExecutorService service = MoreExecutors.listeningDecorator(Executors.newFixedThreadPool(11));
    List<RunCommand> running = new ArrayList<>();

    System.out.printf("%n4=============================================================%n");
    // DecryptBallots
    int navailable = cmdLine.navailable;
    RunCommand decryptBallots = new RunCommand("DecryptingRemote", service,
            "java",
            "-classpath", classpath,
            "com.sunya.electionguard.decrypting.DecryptingMediatorRunner",
            "-in", cmdLine.encryptDir,
            "-out", cmdLine.outputDir,
            "-navailable", Integer.toString(navailable)
    );

    running.add(decryptBallots);
    try {
      Thread.sleep(1000);
    } catch (Throwable e) {
      e.printStackTrace();
      System.exit(1);
    }

    PrivateData privatePublisher = null;
    try {
      privatePublisher = new PrivateData(cmdLine.trusteeDir, false, false);
    } catch (IOException e) {
      e.printStackTrace();
    }
    for (int i=1; i <= cmdLine.navailable; i++) {
      RunCommand command = new RunCommand("DecryptingRemoteTrustee" + i, service,
              "java",
              "-classpath", classpath,
              "com.sunya.electionguard.decrypting.DecryptingRemoteTrustee",
              "-trusteeFile", privatePublisher.trusteePath(REMOTE_TRUSTEE + i).toString() );
      running.add(command);
    }

    try {
      if (!decryptBallots.waitFor(300)) {
        System.out.format("Kill decryptBallots = %d%n", decryptBallots.kill());
      }
    } catch (Throwable e) {
      e.printStackTrace();
    }

    System.out.printf("*** decryptBallots elapsed = %d sec%n", stopwatch.elapsed(TimeUnit.SECONDS));
    stopwatch.reset().start();
    System.out.printf("%n5=============================================================%n");

    // VerifyElectionRecord
    RunCommand verifyElectionRecord = new RunCommand("VerifyElectionRecord", service,
            "java",
            "-classpath", classpath,
            "com.sunya.electionguard.verifier.VerifyElectionRecord",
            "-in", cmdLine.outputDir);
    running.add(verifyElectionRecord);
    try {
      Thread.sleep(1000);
    } catch (Throwable e) {
      e.printStackTrace();
      System.exit(1);
    }

    try {
      if (!verifyElectionRecord.waitFor(10)) {
        System.out.format("Kill verifyElectionRecord =%d%n", verifyElectionRecord.kill());
      }
    } catch (Throwable e) {
      e.printStackTrace();
    }
    System.out.printf("*** verifyElectionRecord elapsed = %d sec%n", stopwatch.elapsed(TimeUnit.SECONDS));

    System.out.printf("%n*** All took = %d sec%n", stopwatchAll.elapsed(TimeUnit.SECONDS));

    try {
      for (RunCommand command : running) {
        command.kill();
        command.show();
      }
    } catch (IOException e) {
      e.printStackTrace();
    }

    System.exit(0);
  }

  /** Run a command line program asynchronously */
  public static class RunCommand implements Callable<Boolean> {
    final String name;
    final String[] args;

    Process process;
    boolean statusReturn;
    Throwable thrownException;

    RunCommand(String name, ListeningExecutorService service, String... args) {
      this.name = name;
      this.args = args;

      ListenableFuture<Boolean> future = service.submit(this);
      Futures.addCallback(
              future,
              new FutureCallback<>() {
                public void onSuccess(Boolean status) {
                  statusReturn = status;
                }
                public void onFailure(Throwable thrown) {
                  thrownException = thrown;
                }
              },
              service);
    }

    @Override
    public Boolean call() throws Exception {
      return run(args);
    }

    public boolean waitFor(long nsecs) throws InterruptedException {
      if (process != null) {
        return process.waitFor(nsecs, TimeUnit.SECONDS);
      }
      return false;
    }

    public int kill() {
      if (process != null) {
        process.destroyForcibly();
        try {
          return process.waitFor();
        } catch (InterruptedException e) {
          e.printStackTrace();
        }
      }
      return -1;
    }

    public void show() throws IOException {
      System.out.printf("-----------------------------------------%n");
      System.out.printf("Command %s%n", String.join(" ", args));

      System.out.printf("---StdOut---%n");
      try (Stream<String> lines = Files.lines(getStdOutFile().toPath())) {
        lines.forEach(line -> System.out.printf("%s%n", line));
      }

      System.out.printf("---StdErr---%n");
      try (Stream<String> lines = Files.lines(getStdErrFile().toPath())) {
        lines.forEach(line -> System.out.printf("%s%n", line));
      }

      System.out.printf("---Done status = %s%n", this.statusReturn);
    }

    File getStdOutFile() {
      return new File(CMD_OUTPUT + name + ".stdout");
    }

    File getStdErrFile() {
      return new File(CMD_OUTPUT + name + ".stderr");
    }

    private boolean run(String... args) throws IOException {
      System.out.printf(">Running command %s%n", String.join(" ", args));
      ProcessBuilder builder = new ProcessBuilder(args)
              .redirectOutput(getStdOutFile())
              .redirectError(getStdErrFile());
      this.process = builder.start();
      return true;
    }
  }

}
