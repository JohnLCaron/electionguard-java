package com.sunya.electionguard.standard;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.beust.jcommander.ParameterException;
import com.sunya.electionguard.AvailableGuardian;
import com.sunya.electionguard.CiphertextTallyBuilder;
import com.sunya.electionguard.DecryptionShare;
import com.sunya.electionguard.Manifest;
import com.sunya.electionguard.InternalManifest;
import com.sunya.electionguard.PlaintextTally;
import com.sunya.electionguard.CiphertextTally;
import com.sunya.electionguard.Scheduler;
import com.sunya.electionguard.input.ElectionInputValidation;
import com.sunya.electionguard.publish.Consumer;
import com.sunya.electionguard.publish.Publisher;
import com.sunya.electionguard.verifier.ElectionRecord;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.util.Collection;
import java.util.Formatter;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * A command line program to decrypt a collection of ballots, using standard library and local Guardians.
 * Used in RunStandardWorkflow.
 * DO NOT USE IN PRODUCTION, as Guardian is inherently unsafe.
 * <p>
 * For command line help:
 * <strong>
 * <pre>
 *  java -classpath electionguard-java-all.jar com.sunya.electionguard.standard.DecryptBallots --help
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
    final
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
      ElectionInputValidation validator = new ElectionInputValidation(electionRecord.election);
      Formatter errors = new Formatter();
      if (!validator.validateElection(errors)) {
        System.out.printf("*** ElectionInputValidation FAILED on %s%n%s", cmdLine.encryptDir, errors);
        System.exit(1);
      }

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
  final Manifest election;

  final Iterable<Guardian> guardians;
  CiphertextTally encryptedTally;
  PlaintextTally decryptedTally;
  Collection<PlaintextTally> spoiledDecryptedBallots;
  Map<String, PlaintextTally> spoiledDecryptedTallies;
  List<AvailableGuardian> availableGuardians;
  final int quorum;
  final int numberOfGuardians;

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
    InternalManifest metadata = new InternalManifest(this.election);
    CiphertextTallyBuilder ciphertextTally = new CiphertextTallyBuilder("DecryptBallots", metadata, electionRecord.context);
    int nballots = ciphertextTally.batch_append(electionRecord.acceptedBallots);
    this.encryptedTally = ciphertextTally.build();
    System.out.printf(" done accumulating %d ballots in the tally%n", nballots);
  }

  void decryptTally() {
    System.out.printf("%nDecrypt tally%n");
    DecryptionMediator mediator = new DecryptionMediator("DecryptBallots", electionRecord.context);

    // Announce each guardian as present
    int count = 0;
    for (Guardian guardian : this.guardians) {
      System.out.printf("Guardian Present: %s%n", guardian.object_id);
      KeyCeremony.ElectionPublicKey guardian_key = guardian.share_key();
      DecryptionShare tally_share = guardian.compute_tally_share(this.encryptedTally, electionRecord.context).orElseThrow();
      Map<String, Optional<DecryptionShare>> ballot_shares = guardian.compute_ballot_shares(electionRecord.acceptedBallots, electionRecord.context);
      mediator.announce(guardian_key, tally_share, ballot_shares);

      count++;
      if (count == this.quorum) {
        System.out.printf("Quorum of %d reached%n", this.quorum);
        break;
      }
    }

    // Get the plaintext Tally
    this.decryptedTally = mediator.get_plaintext_tally(this.encryptedTally).orElseThrow();
    System.out.printf("Tally Decrypted%n");

    // Get the plaintext Spoiled Ballots
    this.spoiledDecryptedTallies = mediator.get_plaintext_ballots(electionRecord.spoiledBallots()).orElseThrow();
    System.out.printf("Spoiled Ballot Tallies Decrypted%n");

    this.spoiledDecryptedBallots = this.spoiledDecryptedTallies.values();
    // this.availableGuardians = mediator.getAvailableGuardians(); LOOK WRONG

    System.out.printf("Done decrypting tally%n%n%s%n", this.decryptedTally);
  }

  boolean publish(String inputDir, String publishDir) throws IOException {
    Publisher publisher = new Publisher(publishDir, Publisher.Mode.createIfMissing, false);
    publisher.writeDecryptionResultsJson(
            this.electionRecord,
            this.encryptedTally,
            this.decryptedTally,
            this.spoiledDecryptedBallots,
            this.availableGuardians);

    publisher.copyAcceptedBallots(inputDir);
    return true;
  }
}
