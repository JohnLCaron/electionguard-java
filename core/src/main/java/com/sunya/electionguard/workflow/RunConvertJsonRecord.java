package com.sunya.electionguard.workflow;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.beust.jcommander.ParameterException;
import com.sunya.electionguard.ballot.EncryptedTally;
import com.sunya.electionguard.ElectionConstants;
import com.sunya.electionguard.ElectionCryptoContext;
import com.sunya.electionguard.Manifest;
import com.sunya.electionguard.PlaintextTally;
import com.sunya.electionguard.Scheduler;
import com.sunya.electionguard.core.UInt256;
import com.sunya.electionguard.json.JsonConsumer;
import com.sunya.electionguard.publish.ElectionRecordPath;
import com.sunya.electionguard.publish.Publisher;
import electionguard.ballot.DecryptionResult;
import electionguard.ballot.ElectionConfig;
import electionguard.ballot.ElectionInitialized;
import electionguard.ballot.Guardian;
import electionguard.ballot.TallyResult;

import java.io.IOException;

import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;

/**
 * A command line program to convert a JSON version1 election record to protobuf.
 * <p>
 * For command line help:
 * <strong>
 * <pre>
 *  java -classpath electionguard-java-all.jar com.sunya.electionguard.workflow.RunConvertJsonRecord --help
 * </pre>
 * </strong>
 */
public class RunConvertJsonRecord {

  private static class CommandLine {
    @Parameter(names = {"-in"}, order = 0,
            description = "Directory containing JSON election record", required = true)
    String inputDir;

    @Parameter(names = {"-out"}, order = 3,
            description = "Directory where converted Protobuf election record is written", required = true)
    String outputDir;

    @Parameter(names = {"-nguardians"}, order = 2, description = "Number of guardians to create", required = false)
    int nguardians = 0;

    @Parameter(names = {"-quorum"}, order = 3, description = "Number of guardians that make a quorum", required = false)
    int quorum = 0;

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
    String progName = RunConvertJsonRecord.class.getName();
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
      RunConvertJsonRecord converter = new RunConvertJsonRecord(cmdLine);
      boolean ok = converter.convert();
      System.out.printf("*** Converted %s%n", ok ? "SUCCESS" : "FAILURE");
      System.exit(ok ? 0 : 1);

    } catch (Throwable t) {
      System.out.printf("*** RunConvertJsonRecord FAILURE%n");
      t.printStackTrace();
      System.exit(4);

    } finally {
      Scheduler.shutdown();
    }
  }

  ///////////////////////////////////////////////////////////////////////////
  final String inputDir;
  final String publishDir;
  final JsonConsumer consumer;
  final Publisher publisher;
  final int nguardians;
  final int quorum;

  public RunConvertJsonRecord(CommandLine cmdLine) throws IOException {
    this(cmdLine.inputDir, cmdLine.outputDir, cmdLine.nguardians, cmdLine.quorum);
  }
  public RunConvertJsonRecord(String inputDir, String outputDir, int nguardians, int quorum) throws IOException {
    this.inputDir = inputDir;
    this.publishDir = outputDir;
    this.nguardians = nguardians;
    this.quorum = quorum;

    this.consumer = new JsonConsumer(this.inputDir);
    this.publisher = new Publisher(publishDir, Publisher.Mode.createIfMissing);
  }

  boolean convert() throws IOException {
    ElectionConstants constants = consumer.constants();
    if (constants == null) {
      throw new IllegalStateException("Constants file does not exist");
    }

    Manifest manifest = consumer.manifest();
    if (manifest == null) {
      throw new IllegalStateException("Manifest file does not exist");
    }
    ElectionCryptoContext context = consumer.context();
    if (context == null) {
      ElectionConfig config = new ElectionConfig(
              ElectionRecordPath.PROTO_VERSION,
              new electionguard.ballot.ElectionConstants(constants),
              manifest,
              this.nguardians,
              this.quorum,
              emptyMap()
      );
      publisher.writeElectionConfig(config);
      return true; // thats all we can do
    }

    ElectionConfig config = new ElectionConfig(
            ElectionRecordPath.PROTO_VERSION,
            new electionguard.ballot.ElectionConstants(constants),
            manifest,
            nguardians > 0 ? nguardians : context.numberOfGuardians,
            quorum > 0 ? quorum : context.quorum,
            emptyMap()
    );
    publisher.writeElectionConfig(config);

    ElectionInitialized init = new ElectionInitialized(
            config,
            context.jointPublicKey,
            UInt256.fromModQ(context.manifestHash),
            UInt256.fromModQ(context.cryptoBaseHash),
            UInt256.fromModQ(context.cryptoExtendedBaseHash),
            consumer.guardianRecords().stream().map(g -> new Guardian(g)).toList(),
            emptyMap()
    );
    publisher.writeElectionInitialized(init);

    EncryptedTally ciphertextTally = consumer.ciphertextTally();
    if (ciphertextTally != null) {
      TallyResult tallyResult = new TallyResult(
              init,
              ciphertextTally,
              emptyList(), emptyList());
      publisher.writeTallyResult(tallyResult);

      PlaintextTally decryptedTally = consumer.decryptedTally();
      if (decryptedTally != null) {
        DecryptionResult decryptionResult = new DecryptionResult(
                tallyResult,
                decryptedTally,
                consumer.availableGuardians(),
                emptyMap());
        publisher.writeDecryptionResults(decryptionResult);
      }
    }

    publisher.writeSubmittedBallots(consumer.acceptedBallots());
    publisher.writeSpoiledBallots(consumer.spoiledBallots());

    System.out.printf(" done converting JSON in %s to protobuf in %s%n", inputDir, publishDir);
    return true;
  }
}
