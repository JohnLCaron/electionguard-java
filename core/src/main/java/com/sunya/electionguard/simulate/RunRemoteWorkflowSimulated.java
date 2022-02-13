package com.sunya.electionguard.simulate;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.beust.jcommander.ParameterException;
import com.google.common.base.Stopwatch;
import com.sunya.electionguard.workflow.RunRemoteWorkflow;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Formatter;
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
  private static final String classpath = RunRemoteWorkflow.classpath;

  private static class CommandLine {
    @Parameter(names = {"-in"}, order = 0,
            description = "Directory containing input election description", required = true)
    String inputDir;

    @Parameter(names = {"-nguardians"}, order = 2, description = "Number of guardians to create", required = true)
    int nguardians = 6;

    @Parameter(names = {"-quorum"}, order = 3, description = "Number of guardians that make a quorum", required = true)
    int quorum = 5;

    @Parameter(names = {"-encryptDir"}, order = 4,
            description = "Directory containing ballot encryption", required = true)
    String encryptDir;

    @Parameter(names = {"-nballots"}, order = 5,
            description = "number of ballots to generate", required = true)
    int nballots;

    @Parameter(names = {"-out"}, order = 6,
            description = "Directory where complete election record is published", required = true)
    String outputDir;

    @Parameter(names = {"-h", "--help"}, order = 7, description = "Display this help and exit", help = true)
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

    // PerformKeyCeremony
    RunCommand command0 = new RunCommand();
    Formatter out = new Formatter();
    command0.run(out, "java",
            "-classpath", classpath,
            "com.sunya.electionguard.simulate.KeyCeremonySimulator",
            "-in", cmdLine.inputDir,
            "-out", cmdLine.encryptDir,
            "-nguardians", Integer.toString(cmdLine.nguardians),
            "-quorum", Integer.toString(cmdLine.quorum)
    );
    System.out.printf("%s", out);
    if (command0.statusReturn != 0) {
      System.exit(command0.statusReturn);
    }
    System.out.printf("*** elapsed = %d ms%n", stopwatch.elapsed(TimeUnit.MILLISECONDS));
    stopwatch.reset().start();
    System.out.printf("%n==============================================================%n");

    // EncryptBallots
    RunCommand command1 = new RunCommand();
    out = new Formatter();
    command1.run(out, "java",
            "-classpath", classpath,
            "com.sunya.electionguard.workflow.EncryptBallots",
            "-in", cmdLine.encryptDir,
            "-nballots", Integer.toString(cmdLine.nballots),
            "-out", cmdLine.encryptDir,
            "-device", "deviceName"
            );
    System.out.printf("%s", out);
    if (command1.statusReturn != 0) {
      System.exit(command1.statusReturn);
    }
    System.out.printf("*** elapsed = %d sec%n", stopwatch.elapsed(TimeUnit.SECONDS));
    stopwatch.reset().start();
    System.out.printf("%n==============================================================%n");

    // DecryptBallots
    RunCommand command2 = new RunCommand();
    out = new Formatter();
    command2.run(out, "java", "-classpath", classpath,
            "com.sunya.electionguard.simulate.DecryptingSimulator",
            "-in", cmdLine.encryptDir,
            "-guardiansLocation", cmdLine.encryptDir + "/private/trustees.protobuf",
            "-out", cmdLine.outputDir
    );
    System.out.printf("%s", out);
    if (command2.statusReturn != 0) {
      System.exit(command2.statusReturn);
    }
    System.out.printf("*** elapsed = %d sec%n", stopwatch.elapsed(TimeUnit.SECONDS));
    stopwatch.reset().start();
    System.out.printf("%n==============================================================%n");

    // VerifyElectionRecord
    RunCommand command3 = new RunCommand();
    out = new Formatter();
    command3.run(out, "java", "-classpath", classpath,
            "com.sunya.electionguard.verifier.VerifyElectionRecord",
            "-in", cmdLine.outputDir
    );
    System.out.printf("%s", out);
    System.out.printf("*** elapsed = %d sec%n", stopwatch.elapsed(TimeUnit.SECONDS));

    System.out.printf("%n*** All took = %d min%n", stopwatchAll.elapsed(TimeUnit.MINUTES));
    System.exit(command3.statusReturn);
  }

  /** Run a command, output of which is added to out. */
  public static class RunCommand {
    int statusReturn;

    public void run(Formatter out, String... args) {
      System.out.printf("> %s%n", String.join(" ", args));
      Process process;
      try {
        ProcessBuilder builder = new ProcessBuilder(args);
        process = builder.start();
      } catch (IOException e) {
        e.printStackTrace();
        out.format("%s", e.getMessage());
        return;
      }

      try (BufferedReader stdout = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
        out.format("---StdOut---%n");
        String line;
        while ((line = stdout.readLine()) != null) {
          out.format("%s%n", line);
        }
      } catch (IOException e) {
        out.format("%s%n", e.getMessage());
      }

      try {
        this.statusReturn = process.waitFor();

        try (BufferedReader stdout = new BufferedReader(new InputStreamReader(process.getErrorStream()))) {
          out.format("---StdErr---%n");
          String line;
          while ((line = stdout.readLine()) != null) {
            out.format("%s%n", line);
          }
        } catch (IOException e) {
          out.format("%s%n", e.getMessage());
        }

        out.format("---Done status = %s%n", this.statusReturn);
      } catch (Exception e) {
        e.printStackTrace();
        out.format("%s", e.getMessage());
      }
    }
  }

}
