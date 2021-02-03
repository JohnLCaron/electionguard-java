package com.sunya.electionguard.workflow;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.beust.jcommander.ParameterException;
import com.google.common.base.Preconditions;
import com.google.common.collect.Iterables;
import com.sunya.electionguard.CiphertextTallyBuilder;
import com.sunya.electionguard.DecryptionMediator;
import com.sunya.electionguard.DecryptionShare;
import com.sunya.electionguard.Election;
import com.sunya.electionguard.Group;
import com.sunya.electionguard.Guardian;
import com.sunya.electionguard.KeyCeremony;
import com.sunya.electionguard.KeyCeremonyMediator;
import com.sunya.electionguard.PlaintextTally;
import com.sunya.electionguard.PublishedCiphertextTally;
import com.sunya.electionguard.Scheduler;
import com.sunya.electionguard.proto.ElectionRecordFromProto;
import com.sunya.electionguard.publish.Consumer;
import com.sunya.electionguard.publish.Publisher;
import com.sunya.electionguard.verifier.ElectionRecord;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Encrypt a collection of ballots. */
public class BallotDecryptor {

  private static class CommandLine {
    @Parameter(names = {"-encryptDir"},
            description = "Directory containing ballot encryption", required = true)
    String encryptDir;

    @Parameter(names = {"-guardians"},
            description = "CoefficientsProvider classname", required = true)
    String coefficientsProviderClass;

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

    CoefficientsProvider coefficientsProvider = null;
    try {
      coefficientsProvider = makeCoefficientsProvider(cmdLine.coefficientsProviderClass);
    } catch (Throwable t) {
      t.printStackTrace();
      System.exit(2);
    }

    try {
      Consumer consumer = new Consumer(cmdLine.encryptDir);
      ElectionRecord electionRecord = ElectionRecordFromProto.read(consumer.electionRecordProtoFile().toString());

      System.out.printf(" BallotDecryptor read from %s%n Write to %s%n", cmdLine.encryptDir, cmdLine.outputDir);
      decryptor = new BallotDecryptor(electionRecord, coefficientsProvider);
      decryptor.accumulateTally();
      decryptor.decryptTally();
      decryptor.publish(cmdLine.outputDir);
      System.exit(0);

    } catch (Throwable t) {
      t.printStackTrace();
      System.exit(3);

    } finally {
      Scheduler.shutdown();
    }
  }

  public static CoefficientsProvider makeCoefficientsProvider(String className) throws Throwable {
    Class<?> c = Class.forName(className);
    if (!(CoefficientsProvider.class.isAssignableFrom(c))) {
      throw new IllegalArgumentException(String.format("%s must implement %s", c.getName(), CoefficientsProvider.class));
    }
    java.lang.reflect.Constructor<CoefficientsProvider> constructor = (Constructor<CoefficientsProvider>) c.getConstructor();
    return constructor.newInstance();
  }

  ///////////////////////////////////////////////////////////////////////////
  final ElectionRecord electionRecord;
  final Election.InternalElectionDescription metadata; // dont see much point to this.

  List<Guardian> guardians;
  CiphertextTallyBuilder ciphertextTally;
  PublishedCiphertextTally publishedTally;
  PlaintextTally decryptedTally;
  int quorum;
  int numberOfGuardians;

  public BallotDecryptor(ElectionRecord electionRecord, CoefficientsProvider provider) {
    this.electionRecord = electionRecord;
    this.metadata = new Election.InternalElectionDescription(electionRecord.election);
    this.quorum = provider.quorum();
    this.numberOfGuardians = Iterables.size(provider.guardianCoefficients());
    Group.ElementModQ crypto_base_hash = Election.make_crypto_base_hash(this.numberOfGuardians, this.quorum, electionRecord.election);

    this.guardians = new ArrayList<>();
    for (KeyCeremony.CoefficientSet coeffSet : provider.guardianCoefficients()) {
      this.guardians.add(Guardian.create(coeffSet, numberOfGuardians, quorum, crypto_base_hash));
      // LOOK test Guardians against whats in the electionRecord.
    }

    System.out.printf("%nReady to decrypt%n");

    // Run the key ceremony in order to fill in the guardian fields, if quorum < nguardians
    if ((this.quorum < this.numberOfGuardians) && !keyCeremony()) {
      throw new RuntimeException("keyCeremony failed");
    }
  }

  boolean keyCeremony() {
    // Setup Mediator
    KeyCeremony.CeremonyDetails details = KeyCeremony.CeremonyDetails.create(this.numberOfGuardians,  this.quorum);
    KeyCeremonyMediator keyCeremony = new KeyCeremonyMediator(details);

    // Attendance (Public Key Share)
    int count = 0;
    for (Guardian guardian : this.guardians) {
      keyCeremony.announce(guardian);
      count++;
      if (count == this.quorum) {
        break;
      }
    }
    System.out.printf(" %d Guardians in attendance (%d/%d) %n", count, this.quorum, this.numberOfGuardians);

    /* System.out.printf(" Confirm all guardians have shared their public keys%n");
    if (!keyCeremony.all_guardians_in_attendance()) {
      System.out.printf(" *** FAILED%n");
      return false;
    }

    // Run the Key Ceremony process, which shares the keys among the guardians
    Optional<List<Guardian>> orchestrated = keyCeremony.orchestrate(null);
    System.out.printf(" Execute the key exchange between guardians%n");
    if (orchestrated.isEmpty()) {
      System.out.printf(" *** FAILED%n");
      return false;
    }

    System.out.printf(" Confirm all guardians have shared their partial key backups%n");
    if (!keyCeremony.all_election_partial_key_backups_available()) {
      System.out.printf(" *** FAILED%n");
      return false;
    }

    // Verification
    boolean verified = keyCeremony.verify(null);
    System.out.printf(" Confirm all guardians truthfully executed the ceremony%n");
    if (!verified) {
      System.out.printf(" *** FAILED%n");
      return false;
    }

    System.out.printf(" Confirm all guardians have submitted a verification of the backups of all other guardians%n");
    if (!keyCeremony.all_election_partial_key_verifications_received()) {
      System.out.printf(" *** FAILED%n");
      return false;
    }

    System.out.printf(" Confirm all guardians have verified the backups of all other guardians%n");
    if (!keyCeremony.all_election_partial_key_backups_verified()) {
      System.out.printf(" *** FAILED%n");
      return false;
    } */

    return true;
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
    Optional<PlaintextTally> decryptedTallyO = mediator.getDecryptedTally(false, null);
    this.decryptedTally = decryptedTallyO.orElseThrow();
    System.out.printf(" done decrypting tally%n");
  }

  void publish(String publishDir) throws IOException {
    Publisher publisher = new Publisher(publishDir, true, false);
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
