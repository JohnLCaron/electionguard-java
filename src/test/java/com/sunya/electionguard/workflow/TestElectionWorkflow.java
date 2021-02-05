package com.sunya.electionguard.workflow;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.beust.jcommander.ParameterException;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Formatter;

public class TestElectionWorkflow {
  private static final String classpath = "build/libs/electionguard-java-0.8-SNAPSHOT-all.jar";

  private static class CommandLine {
    @Parameter(names = {"-in"},
            description = "Directory containing input election description", required = true)
    String inputDir;

    @Parameter(names = {"--proto"}, description = "Input election record is in proto format")
    boolean isProto = false;

    @Parameter(names = {"-coefficients"},
            description = "CoefficientsProvider classname")
    String coefficientsProviderClass;

    @Parameter(names = {"-nguardians"}, description = "Number of quardians to create (required if no coefficients)")
    int nguardians = 6;

    @Parameter(names = {"-quorum"}, description = "Number of quardians that make a quorum (required if no coefficients)")
    int quorum = 5;

    @Parameter(names = {"-guardians"},
            description = "GuardianProvider classname", required = true)
    String guardianProviderClass;

    @Parameter(names = {"-encryptDir"},
            description = "Directory containing ballot encryption", required = true)
    String encryptDir;

    @Parameter(names = {"-out"},
            description = "Directory where complete election record is published", required = true)
    String outputDir;

    @Parameter(names = {"-ballots"},
            description = "BallotProvider classname")
    String ballotProviderClass;

    @Parameter(names = {"-h", "--help"}, description = "Display this help and exit", help = true)
    boolean help = false;

    private final JCommander jc;

    public CommandLine(String progName, String[] args) throws ParameterException {
      this.jc = new JCommander(this);
      this.jc.parse(args);
      jc.setProgramName(progName); // Displayed in the usage information.
    }

    public void printUsage() {
      jc.usage();
    }
  }

  public static void main(String[] args) {
    String progName = TestElectionWorkflow.class.getName();
    CommandLine cmdLine;

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
            "com.sunya.electionguard.workflow.PerformKeyCeremony",
            "-in", cmdLine.inputDir,
            "-out", cmdLine.encryptDir,
            "-nguardians", Integer.toString(cmdLine.nguardians),
            "-quorum", Integer.toString(cmdLine.quorum)
    );
    System.out.printf("%s", out);
    if (command0.statusReturn != 0) {
      System.exit(command0.statusReturn);
    }
    System.out.printf("%n==============================================================%n");

    // EncryptBallots
    RunCommand command1 = new RunCommand();
    out = new Formatter();
    command1.run(out, "java",
            "-classpath", classpath,
            "com.sunya.electionguard.workflow.EncryptBallots",
            "-in", cmdLine.encryptDir,
            "--proto",
            "-ballots", cmdLine.ballotProviderClass,
            "-out", cmdLine.encryptDir,
            "-device", "deviceName"
            );
    System.out.printf("%s", out);
    if (command1.statusReturn != 0) {
      System.exit(command1.statusReturn);
    }
    System.out.printf("%n==============================================================%n");

    // DecryptBallots
    RunCommand command2 = new RunCommand();
    out = new Formatter();
    command2.run(out, "java", "-classpath", classpath,
            "com.sunya.electionguard.workflow.DecryptBallots",
            "-in", cmdLine.encryptDir,
            "-guardians", cmdLine.guardianProviderClass,
            "-out", cmdLine.outputDir
    );
    System.out.printf("%s", out);
    if (command2.statusReturn != 0) {
      System.exit(command2.statusReturn);
    }
    System.out.printf("%n==============================================================%n");

    // VerifyElectionRecord
    RunCommand command3 = new RunCommand();
    out = new Formatter();
    command3.run(out, "java", "-classpath", classpath,
            "com.sunya.electionguard.verifier.VerifyElectionRecord",
            "-in", cmdLine.outputDir,
            "--proto"
    );
    System.out.printf("%s", out);
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
