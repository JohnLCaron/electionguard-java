package com.sunya.electionguard.guardian;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.beust.jcommander.ParameterException;
import com.google.common.base.Preconditions;
import com.sunya.electionguard.AvailableGuardian;
import com.sunya.electionguard.CiphertextTally;
import com.sunya.electionguard.CiphertextTallyBuilder;
import com.sunya.electionguard.Group;
import com.sunya.electionguard.InternalManifest;
import com.sunya.electionguard.Manifest;
import com.sunya.electionguard.PlaintextBallot;
import com.sunya.electionguard.PlaintextTally;
import com.sunya.electionguard.Scheduler;
import com.sunya.electionguard.SpoiledBallotAndTally;
import com.sunya.electionguard.input.ElectionInputValidation;
import com.sunya.electionguard.publish.Consumer;
import com.sunya.electionguard.publish.Publisher;
import com.sunya.electionguard.verifier.ElectionRecord;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.util.Formatter;
import java.util.List;
import java.util.Map;
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
public class DecryptingSimulator {

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
    String progName = DecryptingSimulator.class.getName();
    DecryptingSimulator decryptor;
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

    ProxyGuardiansProvider guardiansProvider = null;
    try {
      if (cmdLine.guardiansProviderClass != null) {
        try {
          guardiansProvider = makeGuardiansProvider(cmdLine.guardiansProviderClass);
        } catch (Throwable t) {
          t.printStackTrace();
          System.exit(2);
        }
      } else {
        guardiansProvider = new RemoteGuardiansProvider(cmdLine.guardiansProviderLocation);
      }
    } catch (Throwable t) {
      System.out.printf("*** Must specify -guardians or valid -guardiansLocation: FAILURE%n");
      t.printStackTrace();
      System.exit(3);
    }

    try {
      Consumer consumer = new Consumer(cmdLine.encryptDir);
      ElectionRecord electionRecord = consumer.readElectionRecord();
      // LOOK how to validate guardians??
      ElectionInputValidation validator = new ElectionInputValidation(electionRecord.election);
      Formatter errors = new Formatter();
      if (!validator.validateElection(errors)) {
        System.out.printf("*** ElectionInputValidation FAILED on %s%n%s", cmdLine.encryptDir, errors);
        System.exit(1);
      }

      System.out.printf(" BallotDecryptor read from %s%n Write to %s%n", cmdLine.encryptDir, cmdLine.outputDir);
      decryptor = new DecryptingSimulator(consumer, electionRecord, guardiansProvider);

      // Do the accumulation if the encryptedTally doesnt exist
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

  public static ProxyGuardiansProvider makeGuardiansProvider(String className) throws Throwable {
    Class<?> c = Class.forName(className);
    if (!(ProxyGuardiansProvider.class.isAssignableFrom(c))) {
      throw new IllegalArgumentException(String.format("%s must implement %s", c.getName(), ProxyGuardiansProvider.class));
    }
    Constructor<ProxyGuardiansProvider> constructor = (Constructor<ProxyGuardiansProvider>) c.getConstructor();
    return constructor.newInstance();
  }

  ///////////////////////////////////////////////////////////////////////////
  final Consumer consumer;
  final ElectionRecord electionRecord;
  final Manifest election;

  Iterable<DecryptingTrustee.Proxy> guardians;
  CiphertextTally encryptedTally;
  PlaintextTally decryptedTally;
  List<PlaintextBallot> spoiledDecryptedBallots;
  List<PlaintextTally> spoiledDecryptedTallies;
  List<AvailableGuardian> availableGuardians;
  int quorum;
  int numberOfGuardians;

  public DecryptingSimulator(Consumer consumer, ElectionRecord electionRecord, ProxyGuardiansProvider provider) {
    this.consumer = consumer;
    this.electionRecord = electionRecord;
    this.election = electionRecord.election;
    this.quorum = electionRecord.context.quorum;
    this.numberOfGuardians = electionRecord.context.number_of_guardians;
    this.encryptedTally = electionRecord.encryptedTally;

    this.guardians = provider.guardians();
    for (DecryptingTrustee.Proxy guardian : provider.guardians()) {
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

    // The guardians' election public key is in the electionRecord.guardianCoefficients.
    Map<String, Group.ElementModP> guardianPublicKeys = electionRecord.guardianCoefficients.stream().collect(
            Collectors.toMap(coeff -> coeff.owner_id(), coeff -> coeff.coefficient_commitments().get(0)));

    TrusteeDecryptionMediator mediator = new TrusteeDecryptionMediator(electionRecord.context,
            this.encryptedTally,
            consumer.spoiledBallotsProto(),
            guardianPublicKeys);

    int count = 0;
    for (DecryptingTrustee.Proxy guardian : this.guardians) {
      boolean ok = mediator.announce(guardian);
      Preconditions.checkArgument(ok);
      System.out.printf(" Guardian Present: %s%n", guardian.id());
      count++;
      if (count == this.quorum) {
        System.out.printf("Quorum of %d reached%n", this.quorum);
        break;
      }
    }

    // Here's where the ciphertext Tally is decrypted.
    this.decryptedTally = mediator.get_plaintext_tally().orElseThrow();
    List<SpoiledBallotAndTally> spoiledTallyAndBallot =
            mediator.decrypt_spoiled_ballots().orElseThrow();
    this.spoiledDecryptedBallots = spoiledTallyAndBallot.stream().map(e -> e.ballot).collect(Collectors.toList());
    this.spoiledDecryptedTallies = spoiledTallyAndBallot.stream().map(e -> e.tally).collect(Collectors.toList());
    this.availableGuardians = mediator.getAvailableGuardians();
    System.out.printf("Done decrypting tally%n%n%s%n", this.decryptedTally);
  }

  boolean publish(String inputDir, String publishDir) throws IOException {
    Publisher publisher = new Publisher(publishDir, true, false);
    publisher.writeDecryptionResultsProto(
            this.electionRecord,
            this.encryptedTally,
            this.decryptedTally,
            this.spoiledDecryptedBallots,
            this.spoiledDecryptedTallies,
            availableGuardians);

    publisher.copyAcceptedBallots(inputDir);
    return true;
  }
}
