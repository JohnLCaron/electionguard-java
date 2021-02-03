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
import com.sunya.electionguard.proto.ElectionRecordFromProto;
import com.sunya.electionguard.publish.Consumer;
import com.sunya.electionguard.publish.Publisher;
import com.sunya.electionguard.verifier.ElectionRecord;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.util.Optional;

/** Encrypt a collection of ballots. */
public class BallotDecryptor {

  private static class CommandLine {
    @Parameter(names = {"-encryptDir"},
            description = "Directory containing ballot encryption", required = true)
    String encryptDir;

    @Parameter(names = {"-guardians"},
            description = "GuardianProvider classname", required = true)
    String guardianProviderClass;

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
    String progName = BallotDecryptor.class.getName();
    BallotDecryptor decryptor;
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

    GuardianProvider guardianProvider = null;
    try {
      guardianProvider = makeGuardianProvider(cmdLine.guardianProviderClass);
    } catch (Throwable t) {
      t.printStackTrace();
      System.exit(2);
    }

    try {
      Consumer consumer = new Consumer(cmdLine.encryptDir);
      ElectionRecord electionRecord = ElectionRecordFromProto.read(consumer.electionRecordProtoFile().toString());

      System.out.printf(" Read from %s%n Write to %s%n", cmdLine.encryptDir, cmdLine.outputDir);
      decryptor = new BallotDecryptor(electionRecord, guardianProvider);
      decryptor.accumulateTally();
      decryptor.decryptTally();
      decryptor.publish(cmdLine.outputDir);

    } catch (Throwable t) {
      t.printStackTrace();
      System.exit(3);
    }
  }

  public static GuardianProvider makeGuardianProvider(String className) throws Throwable {
    Class<?> c = Class.forName(className);
    if (!(GuardianProvider.class.isAssignableFrom(c))) {
      throw new IllegalArgumentException(String.format("%s must implement %s", c.getName(), GuardianProvider.class));
    }
    java.lang.reflect.Constructor<GuardianProvider> constructor = (Constructor<GuardianProvider>) c.getConstructor();
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

  public BallotDecryptor(ElectionRecord electionRecord, GuardianProvider provider) {
    this.electionRecord = electionRecord;
    this.metadata = new Election.InternalElectionDescription(electionRecord.election);
    this.guardians = provider.guardians();
    this.quorum = provider.quorum(); // LOOK doesnt belong here - in the ElectionRecord?

    // LOOK test Guardians against whats in the electionRecord.
    for (Guardian guardian : this.guardians) {
      System.out.printf("Guardian Present: %s%n", guardian.object_id);
    }

    System.out.printf("%nReady to decrypt%n");
  }

  void accumulateTally() {
    System.out.printf("%nAccumulate tally%n");
    this.ciphertextTally = new CiphertextTallyBuilder("BallotDecryptor", this.metadata, electionRecord.context);
    this.ciphertextTally.tally_ballots(electionRecord.castBallots);
    this.publishedTally = this.ciphertextTally.build();
    System.out.printf(" done accumulating tally%n");
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
      System.out.printf("Guardian Present: %s%n", guardian.object_id);
      Preconditions.checkArgument(decryption_share.isPresent());
      count++;
      if (count == this.quorum) {
        System.out.printf("Quorum of %d reached%n", this.quorum);
        break;
      }
    }

    // Here's where the ciphertext Tally is decrypted.
    this.decryptedTally = mediator.getDecryptedTally(false, null).orElseThrow();
    System.out.printf(" done decrypting tally%n");
  }

  void publish(String publishDir) throws IOException {
    Publisher publisher = new Publisher(publishDir, false, false);
    publisher.writeElectionRecordProto(
            this.electionRecord.election,
            this.electionRecord.context,
            this.electionRecord.constants,
            this.electionRecord.devices,
            this.electionRecord.castBallots,
            this.electionRecord.guardianCoefficients,
            this.publishedTally,
            this.decryptedTally);
  }
}
