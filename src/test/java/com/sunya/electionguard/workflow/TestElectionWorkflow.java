package com.sunya.electionguard.workflow;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.beust.jcommander.ParameterException;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Formatter;

public class TestElectionWorkflow {

  private static class CommandLine {
    @Parameter(names = {"-in"},
            description = "Directory containing input election description", required = true)
    String inputDir;

    @Parameter(names = {"--proto"}, description = "Input election record is in proto format")
    boolean isProto = false;

    @Parameter(names = {"-guardians"},
            description = "CoefficientsProvider classname", required = true)
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

    // BallotEncryptor
    RunCommand command = new RunCommand();
    Formatter out = new Formatter();
    command.run(out, "java",
            "-classpath", "build/libs/electionguard-java-0.7-SNAPSHOT-all.jar",
            "com.sunya.electionguard.workflow.BallotEncryptor",
            "-in", cmdLine.inputDir,
            "-guardians", cmdLine.guardianProviderClass,
            "-ballots", cmdLine.ballotProviderClass,
            "-encryptDir", cmdLine.encryptDir
            );
    System.out.printf("%s", out);
    if (command.statusReturn != 0) {
      System.exit(command.statusReturn);
    }
    System.out.printf("%n==============================================================%n");

    // BallotDecryptor
    RunCommand command2 = new RunCommand();
    Formatter out2 = new Formatter();
    command2.run(out2, "java", "-classpath", "build/libs/electionguard-java-0.7-SNAPSHOT-all.jar",
            "com.sunya.electionguard.workflow.BallotDecryptor",
            "-encryptDir", cmdLine.encryptDir,
            "-guardians", cmdLine.guardianProviderClass,
            "-out", cmdLine.outputDir
    );
    System.out.printf("%s", out2);
    if (command2.statusReturn != 0) {
      System.exit(command2.statusReturn);
    }
    System.out.printf("%n==============================================================%n");

    // BallotDecryptor
    RunCommand command3 = new RunCommand();
    Formatter out3 = new Formatter();
    command3.run(out3, "java", "-classpath", "build/libs/electionguard-java-0.7-SNAPSHOT-all.jar",
            "com.sunya.electionguard.verifier.VerifyElectionRecord",
            "-in", cmdLine.outputDir,
            "--proto"
    );
    System.out.printf("%s", out3);
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
