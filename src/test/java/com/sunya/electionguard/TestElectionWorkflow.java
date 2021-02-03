package com.sunya.electionguard;

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
            description = "GuardianProvider classname", required = true)
    String guardianProviderClass;

    @Parameter(names = {"-encryptDir"},
            description = "Directory containing ballot encryption", required = true)
    String encryptDir;

    @Parameter(names = {"-out"},
            description = "Directory where complete election record is published", required = true)
    String outputDir;

    @Parameter(names = {"-nballots"}, description = "Number of ballots to generate")
    int nballots = 11;

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
            "-out", cmdLine.encryptDir,
            "-nballots", Integer.toString(cmdLine.nballots)
            );
    System.out.printf("%s", out);
    System.out.printf("%n==============================================================%n");

    /* BallotDecryptor
    RunCommand command2 = new RunCommand();
    Formatter out2 = new Formatter();
    command2.run(out2, "java", "-classpath", "build/libs/electionguard-java-0.7-SNAPSHOT-all.jar",
            "com.sunya.electionguard.decryptor.BallotDecryptor",
            "-in", cmdLine.encryptDir,
            "-out", cmdLine.outputDir
    );
    System.out.printf("%s", out2);
    System.out.printf("%n==============================================================%n"); */
  }

  /** Run a command, output of which is added to out. */
  public static class RunCommand {

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
        // this should return immediately since the process has closed stdout
        int status = process.waitFor();

        try (BufferedReader stdout = new BufferedReader(new InputStreamReader(process.getErrorStream()))) {
          out.format("---StdErr---%n");
          String line;
          while ((line = stdout.readLine()) != null) {
            out.format("%s%n", line);
          }
        } catch (IOException e) {
          out.format("%s%n", e.getMessage());
        }

        out.format("---Done status = %s%n", status);
      } catch (Exception e) {
        e.printStackTrace();
        out.format("%s", e.getMessage());
      }
    }
  }

}
