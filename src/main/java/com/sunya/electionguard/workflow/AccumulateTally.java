package com.sunya.electionguard.workflow;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.beust.jcommander.ParameterException;
import com.sunya.electionguard.CiphertextTally;
import com.sunya.electionguard.CiphertextTallyBuilder;
import com.sunya.electionguard.Election;
import com.sunya.electionguard.ElectionWithPlaceholders;
import com.sunya.electionguard.Scheduler;
import com.sunya.electionguard.publish.Consumer;
import com.sunya.electionguard.publish.Publisher;
import com.sunya.electionguard.verifier.ElectionRecord;

import java.io.IOException;

/**
 * A command line program to accumulate encrypted ballots.
 * <p>
 * For command line help:
 * <strong>
 * <pre>
 *  java -classpath electionguard-java-all.jar com.sunya.electionguard.workflow.AccumulateTally --help
 * </pre>
 * </strong>
 *
 * @see <a href="https://www.electionguard.vote/spec/0.95.0/7_Verifiable_decryption/">Ballot Decryption</a>
 */
public class AccumulateTally {

  private static class CommandLine {
    @Parameter(names = {"-in"}, order = 0,
            description = "Directory containing input election record and ballot encryptions", required = true)
    String encryptDir;

    @Parameter(names = {"-out"}, order = 3,
            description = "Directory where encrypted tally is published", required = true)
    String outputDir;

    @Parameter(names = {"-h", "--help"},  order = 4, description = "Display this help and exit", help = true)
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
    String progName = AccumulateTally.class.getName();
    AccumulateTally decryptor;
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

      System.out.printf(" AccumulateTally read from %s%n Write to %s%n", cmdLine.encryptDir, cmdLine.outputDir);
      decryptor = new AccumulateTally(consumer, electionRecord);
      decryptor.accumulateTally();
      boolean ok = decryptor.publish(cmdLine.encryptDir, cmdLine.outputDir);
      System.out.printf("*** AccumulateTally %s%n", ok ? "SUCCESS" : "FAILURE");
      System.exit(ok ? 0 : 1);

    } catch (Throwable t) {
      System.out.printf("*** AccumulateTally FAILURE%n");
      t.printStackTrace();
      System.exit(4);

    } finally {
      Scheduler.shutdown();
    }
  }

  ///////////////////////////////////////////////////////////////////////////
  final Consumer consumer;
  final ElectionRecord electionRecord;
  final Election election;

  CiphertextTally encryptedTally;

  public AccumulateTally(Consumer consumer, ElectionRecord electionRecord) {
    this.consumer = consumer;
    this.electionRecord = electionRecord;
    this.election = electionRecord.election;
    System.out.printf("%nReady to decrypt%n");
  }

  void accumulateTally() {
    System.out.printf("%nAccumulate tally%n");
    ElectionWithPlaceholders manifest = new ElectionWithPlaceholders(electionRecord.election);
    CiphertextTallyBuilder ciphertextTally = new CiphertextTallyBuilder("DecryptBallots", manifest, electionRecord.context);
    int nballots = ciphertextTally.batch_append(electionRecord.acceptedBallots);
    this.encryptedTally = ciphertextTally.build();
    System.out.printf(" done accumulating %d ballots in the tally%n", nballots);
  }

  boolean publish(String inputDir, String publishDir) throws IOException {
    Publisher publisher = new Publisher(publishDir, true, false);
    publisher.writeEncryptedTallyProto(this.electionRecord, this.encryptedTally);
    publisher.copyAcceptedBallots(inputDir);
    return true;
  }
}
