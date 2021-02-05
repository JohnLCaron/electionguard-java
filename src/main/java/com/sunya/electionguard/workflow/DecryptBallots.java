package com.sunya.electionguard.workflow;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.beust.jcommander.ParameterException;
import com.google.common.base.Preconditions;
import com.sunya.electionguard.CiphertextTallyBuilder;
import com.sunya.electionguard.DecryptionMediator;
import com.sunya.electionguard.DecryptionShare;
import com.sunya.electionguard.Election;
import com.sunya.electionguard.Guardian;
import com.sunya.electionguard.PlaintextTally;
import com.sunya.electionguard.PublishedCiphertextTally;
import com.sunya.electionguard.Scheduler;
import com.sunya.electionguard.publish.Consumer;
import com.sunya.electionguard.publish.Publisher;
import com.sunya.electionguard.verifier.ElectionRecord;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.util.Optional;

/** Decrypt a collection of ballots. */
public class DecryptBallots {

  private static class CommandLine {
    @Parameter(names = {"-in"},
            description = "Directory containing input election record and ballot encryptions", required = true)
    String encryptDir;

    @Parameter(names = {"-guardians"},
            description = "GuardianProvider classname", required = true)
    String guardiansProviderClass;

    @Parameter(names = {"-out"},
            description = "Directory where augmented election record is published", required = true)
    String outputDir;

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
      guardiansProvider = makeGuardiansProvider(cmdLine.guardiansProviderClass);
    } catch (Throwable t) {
      t.printStackTrace();
      System.exit(2);
    }

    try {
      Consumer consumer = new Consumer(cmdLine.encryptDir);
      ElectionRecord electionRecord = consumer.readElectionRecordProto();

      System.out.printf(" BallotDecryptor read from %s%n Write to %s%n", cmdLine.encryptDir, cmdLine.outputDir);
      decryptor = new DecryptBallots(electionRecord, guardiansProvider);
      decryptor.accumulateTally();
      decryptor.decryptTally();
      decryptor.publish(cmdLine.encryptDir, cmdLine.outputDir);
      System.exit(0);

    } catch (Throwable t) {
      t.printStackTrace();
      System.exit(3);

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
  final ElectionRecord electionRecord;
  final Election.InternalElectionDescription metadata; // dont see much point to this.

  Iterable<Guardian> guardians;
  CiphertextTallyBuilder ciphertextTally;
  PublishedCiphertextTally publishedTally;
  PlaintextTally decryptedTally;
  int quorum;
  int numberOfGuardians;

  public DecryptBallots(ElectionRecord electionRecord, GuardiansProvider provider) {
    this.electionRecord = electionRecord;
    this.metadata = new Election.InternalElectionDescription(electionRecord.election);
    this.quorum = electionRecord.context.quorum;
    this.numberOfGuardians = electionRecord.context.number_of_guardians;

    this.guardians = provider.guardians();
    for (Guardian guardian : provider.guardians()) {
      // LOOK test Guardians against whats in the electionRecord.
    }

    System.out.printf("%nReady to decrypt%n");
  }

  void accumulateTally() {
    System.out.printf("%nAccumulate tally%n");
    this.ciphertextTally = new CiphertextTallyBuilder("BallotDecryptor", this.metadata, electionRecord.context);
    int nballots = this.ciphertextTally.tally_ballots(electionRecord.castBallots);
    this.publishedTally = this.ciphertextTally.build();
    System.out.printf(" done accumulating %d ballots in the tally%n", nballots);
  }

  void decryptTally() {
    System.out.printf("%nDecrypt tally%n");

    // LOOK should use publishedTally? does it get mutated ???
    DecryptionMediator mediator = new DecryptionMediator(this.metadata, electionRecord.context, this.ciphertextTally);

    int count = 0;
    for (Guardian guardian : this.guardians) {
      // LOOK not using TallyDecryptionShare?
      // LOOK test Guardians against whats in the electionRecord.
      Optional<DecryptionShare.TallyDecryptionShare> decryption_share = mediator.announce(guardian);
      System.out.printf(" Guardian Present: %s%n", guardian.object_id);
      Preconditions.checkArgument(decryption_share.isPresent());
      count++;
      if (count == this.quorum) {
        System.out.printf("Quorum of %d reached%n", this.quorum);
        break;
      }
    }

    // Here's where the ciphertext Tally is decrypted.
    Optional<PlaintextTally> decryptedTallyO = mediator.getDecryptedTally(false, null);
    this.decryptedTally = decryptedTallyO.orElseThrow();
    System.out.printf("Done decrypting tally%n%n%s%n", this.decryptedTally);
  }

  void publish(String inputDir, String publishDir) throws IOException {
    Publisher publisher = new Publisher(publishDir, true, false);
    publisher.writeElectionRecordProto(
            this.electionRecord.election,
            this.electionRecord.context,
            this.electionRecord.constants,
            this.electionRecord.devices,
            this.electionRecord.castBallots, // LOOK
            this.electionRecord.guardianCoefficients,
            this.publishedTally,
            this.decryptedTally);

    publisher.copyAcceptedBallots(inputDir);
  }
}
