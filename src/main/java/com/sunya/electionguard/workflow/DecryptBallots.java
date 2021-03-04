package com.sunya.electionguard.workflow;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.beust.jcommander.ParameterException;
import com.google.common.base.Preconditions;
import com.sunya.electionguard.CiphertextTallyBuilder;
import com.sunya.electionguard.DecryptionMediator2;
import com.sunya.electionguard.Election;
import com.sunya.electionguard.ElectionWithPlaceholders;
import com.sunya.electionguard.Guardian;
import com.sunya.electionguard.PlaintextBallot;
import com.sunya.electionguard.PlaintextTally;
import com.sunya.electionguard.CiphertextTally;
import com.sunya.electionguard.Scheduler;
import com.sunya.electionguard.publish.Consumer;
import com.sunya.electionguard.publish.Publisher;
import com.sunya.electionguard.verifier.ElectionRecord;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.util.List;
import java.util.stream.Collectors;

/**
 * A command line program to decrypt a collection of ballots.
 * <p>
 * For command line help:
 * <strong>
 * <pre>
 *  java -classpath electionguard-java-all.jar com.sunya.electionguard.workflow.DecryptBallots --help
 * </pre>
 * </strong>
 *
 * @see <a href="https://www.electionguard.vote/spec/0.95.0/7_Verifiable_decryption/">Ballot Decryption</a>
 */
public class DecryptBallots {

  private static class CommandLine {
    @Parameter(names = {"-in"}, order = 0,
            description = "Directory containing input election record and encrypted ballots and tally", required = true)
    String encryptDir;

    @Parameter(names = {"-guardiansLocation"}, order = 1,
            description = "location of serialized guardian files")
    String guardiansProviderLocation;

    @Parameter(names = {"-guardians"}, order = 2,
            description = "GuardianProvider classname")
    String guardiansProviderClass;

    @Parameter(names = {"-out"}, order = 3,
            description = "Directory where augmented election record is published", required = true)
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
    String progName = DecryptBallots.class.getName();
    DecryptBallots decryptor;
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

    GuardiansProvider guardiansProvider = null;
    try {
      if (cmdLine.guardiansProviderClass != null) {
        try {
          guardiansProvider = makeGuardiansProvider(cmdLine.guardiansProviderClass);
        } catch (Throwable t) {
          t.printStackTrace();
          System.exit(2);
        }
      } else {
        guardiansProvider = new SecretGuardiansProvider(cmdLine.guardiansProviderLocation);
      }
    } catch (Throwable t) {
      System.out.printf("*** Must specify -guardians or valid -guardiansLocation: FAILURE%n");
      t.printStackTrace();
      System.exit(3);
    }

    try {
      Consumer consumer = new Consumer(cmdLine.encryptDir);
      ElectionRecord electionRecord = consumer.readElectionRecord();

      System.out.printf(" BallotDecryptor read from %s%n Write to %s%n", cmdLine.encryptDir, cmdLine.outputDir);
      decryptor = new DecryptBallots(consumer, electionRecord, guardiansProvider);
      if (electionRecord.encryptedTally == null) {
        decryptor.accumulateTally();
      }
      decryptor.decryptTally();
      boolean ok = decryptor.publish(cmdLine.encryptDir, cmdLine.outputDir);
      System.out.printf("*** DecryptBallots %s%n", ok ? "SUCCESS" : "FAILURE");
      System.exit(ok ? 0 : 1);

    } catch (Throwable t) {
      System.out.printf("*** DecryptBallots FAILURE%n");
      t.printStackTrace();
      System.exit(4);

    } finally {
      Scheduler.shutdown();
    }
  }

  public static GuardiansProvider makeGuardiansProvider(String className) throws Throwable {
    Class<?> c = Class.forName(className);
    if (!(GuardiansProvider.class.isAssignableFrom(c))) {
      throw new IllegalArgumentException(String.format("%s must implement %s", c.getName(), GuardiansProvider.class));
    }
    java.lang.reflect.Constructor<GuardiansProvider> constructor = (Constructor<GuardiansProvider>) c.getConstructor();
    return constructor.newInstance();
  }

  ///////////////////////////////////////////////////////////////////////////
  final Consumer consumer;
  final ElectionRecord electionRecord;
  final Election election;

  Iterable<Guardian> guardians;
  CiphertextTally encryptedTally;
  PlaintextTally decryptedTally;
  List<PlaintextBallot> spoiledDecryptedBallots;
  List<PlaintextTally> spoiledDecryptedTallies;
  int quorum;
  int numberOfGuardians;

  public DecryptBallots(Consumer consumer, ElectionRecord electionRecord, GuardiansProvider provider) {
    this.consumer = consumer;
    this.electionRecord = electionRecord;
    this.election = electionRecord.election;
    this.quorum = electionRecord.context.quorum;
    this.numberOfGuardians = electionRecord.context.number_of_guardians;
    // LOOK We could do the accumulation if the encryptedTally doesnt exist
    this.encryptedTally = electionRecord.encryptedTally;

    this.guardians = provider.guardians();
    for (Guardian guardian : provider.guardians()) {
      // LOOK test Guardians against whats in the electionRecord.
    }
    System.out.printf("%nReady to decrypt%n");
  }

  void accumulateTally() {
    System.out.printf("%nAccumulate tally%n");
    ElectionWithPlaceholders metadata = new ElectionWithPlaceholders(this.election);
    CiphertextTallyBuilder ciphertextTally = new CiphertextTallyBuilder("DecryptBallots", metadata, electionRecord.context);
    int nballots = ciphertextTally.batch_append(electionRecord.acceptedBallots);
    this.encryptedTally = ciphertextTally.build();
    System.out.printf(" done accumulating %d ballots in the tally%n", nballots);
  }

  void decryptTally() {
    System.out.printf("%nDecrypt tally%n");
    DecryptionMediator2 mediator = new DecryptionMediator2(electionRecord.context, this.encryptedTally, consumer.spoiledBallotsProto());

    int count = 0;
    for (Guardian guardian : this.guardians) {
      boolean ok = mediator.announce(guardian);
      Preconditions.checkArgument(ok);
      System.out.printf(" Guardian Present: %s%n", guardian.object_id);
      count++;
      if (count == this.quorum) {
        System.out.printf("Quorum of %d reached%n", this.quorum);
        break;
      }
    }

    // Here's where the ciphertext Tally is decrypted.
    this.decryptedTally = mediator.get_plaintext_tally(null).orElseThrow();
    List<DecryptionMediator2.SpoiledBallotAndTally> spoiledTallyAndBallot =
            mediator.decrypt_spoiled_ballots().orElseThrow();
    this.spoiledDecryptedBallots = spoiledTallyAndBallot.stream().map(e -> e.ballot).collect(Collectors.toList());
    this.spoiledDecryptedTallies = spoiledTallyAndBallot.stream().map(e -> e.tally).collect(Collectors.toList());
    System.out.printf("Done decrypting tally%n%n%s%n", this.decryptedTally);
  }

  boolean publish(String inputDir, String publishDir) throws IOException {
    Publisher publisher = new Publisher(publishDir, true, false);
    publisher.writeDecryptionResultsProto(
            this.electionRecord.election,
            this.electionRecord.context,
            this.electionRecord.constants,
            this.electionRecord.guardianCoefficients,
            this.electionRecord.devices,
            this.encryptedTally,
            this.decryptedTally,
            this.spoiledDecryptedBallots,
            this.spoiledDecryptedTallies);

    publisher.copyAcceptedBallots(inputDir);
    return true;
  }
}
