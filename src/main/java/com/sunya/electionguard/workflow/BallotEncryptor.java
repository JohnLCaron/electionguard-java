package com.sunya.electionguard.workflow;

import com.beust.jcommander.*;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.sunya.electionguard.Ballot;
import com.sunya.electionguard.BallotBox;
import com.sunya.electionguard.DataStore;
import com.sunya.electionguard.Election;
import com.sunya.electionguard.Encrypt;
import com.sunya.electionguard.Group;
import com.sunya.electionguard.Guardian;
import com.sunya.electionguard.Hash;
import com.sunya.electionguard.KeyCeremony;
import com.sunya.electionguard.KeyCeremonyMediator;
import com.sunya.electionguard.proto.ElectionRecordFromProto;
import com.sunya.electionguard.publish.Consumer;
import com.sunya.electionguard.publish.Publisher;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Random;

/** Encrypt a collection of ballots. */
public class BallotEncryptor {

  private static class CommandLine {
    @Parameter(names = {"-in"},
            description = "Directory containing input election record", required = true)
    String inputDir;

    @Parameter(names = {"-encryptDir"},
            description = "Directory containing ballot encryption", required = true)
    String encryptDir;

    @Parameter(names = {"-guardians"},
            description = "GuardianProvider classname", required = true)
    String guardianProviderClass;

    @Parameter(names = {"-ballots"},
            description = "BallotProvider classname")
    String ballotProviderClass;

    @Parameter(names = {"-nballots"}, description = "Number of ballots to generate")
    int nballots = 11;

    @Parameter(names = {"--proto"}, description = "Input election record is in proto format")
    boolean isProto = false;

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

  public static void main(String[] args) throws IOException {
    String progName = BallotEncryptor.class.getName();
    BallotEncryptor encryptor;
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

    // all we need from election record is the ElectionDescription.
    Consumer consumer = new Consumer(cmdLine.inputDir);
    Election.ElectionDescription election;
    if (cmdLine.isProto) {
      election = ElectionRecordFromProto.read(consumer.electionRecordProtoFile().toString()).election;
    } else {
      election = consumer.election();
    }

    GuardianProvider guardianProvider = null;
    try {
      guardianProvider = makeGuardianProvider(cmdLine.guardianProviderClass);
    } catch (Throwable t) {
      t.printStackTrace();
      System.exit(2);
    }

    BallotProvider ballotProvider = null;
    try {
      ballotProvider = makeBallotProvider(cmdLine.ballotProviderClass, election);
    } catch (Throwable t) {
      t.printStackTrace();
      System.exit(3);
    }

    System.out.printf(" Read from %s%n Ballots from %s%n Guardians from %s%n Write to %s%n",
            cmdLine.inputDir, cmdLine.ballotProviderClass, cmdLine.guardianProviderClass, cmdLine.encryptDir);
    encryptor = new BallotEncryptor(election, guardianProvider);

    try {
      for (Ballot.PlaintextBallot ballot : ballotProvider.ballots()) {
        Optional<Ballot.CiphertextBallot> encrypted_ballot = encryptor.encryptBallot(ballot);
        if (encrypted_ballot.isPresent()) {
          Optional<Ballot.CiphertextAcceptedBallot> accepted = encryptor.castOrSpoil(encrypted_ballot.get(), random.nextBoolean());
          if (accepted.isEmpty()) {
            System.out.printf("***castOrSpoil failed%n");
          }
        } else {
          System.out.printf("***Encryption failed%n");
        }
      }
    } catch (Throwable t) {
      t.printStackTrace();
      System.exit(4);
    }

    try {
      // publish
      encryptor.publish(cmdLine.encryptDir);
    } catch (Throwable t) {
      t.printStackTrace();
      System.exit(5);
    }
  }

  public static BallotProvider makeBallotProvider(String className, Election.ElectionDescription election) throws Throwable {
    Class<?> c = Class.forName(className);
    if (!(BallotProvider.class.isAssignableFrom(c))) {
      throw new IllegalArgumentException(String.format("%s must implement %s", c.getName(), BallotProvider.class));
    }
    java.lang.reflect.Constructor<BallotProvider> constructor =
            (Constructor<BallotProvider>) c.getConstructor(Election.ElectionDescription.class);
    return constructor.newInstance(election);
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
  private static final Random random = new Random(System.currentTimeMillis());

  final int numberOfGuardians;
  final int quorum;
  final Election.ElectionDescription election;

  Group.ElementModP jointKey;
  Election.CiphertextElectionContext context;
  Election.InternalElectionDescription metadata;

  Iterable<Guardian> guardians;
  List<KeyCeremony.CoefficientValidationSet> coefficientValidationSets = new ArrayList<>();
  List<Ballot.PlaintextBallot> originalBallots = new ArrayList<>();

  Encrypt.EncryptionDevice device;
  Encrypt.EncryptionMediator encryptor;
  DataStore ballotStore;
  BallotBox ballotBox;

  public BallotEncryptor(Election.ElectionDescription election, GuardianProvider guardianProvider) {
    this.election = election;
    this.quorum = guardianProvider.quorum(); // LOOK where else can we get this? KeyCeremony.CeremonyDetails
    this.guardians = guardianProvider.guardians();
    this.numberOfGuardians = Iterables.size(this.guardians);

    System.out.printf("%nKey Ceremony%n");
    if (!keyCeremony()) {
      throw new RuntimeException("*** Key Ceremony failed");
    }
    buildElection(this.election, this.jointKey);

    // Configure the Encryption Device
    this.device = new Encrypt.EncryptionDevice("polling-place-one"); // LOOK
    this.encryptor = new Encrypt.EncryptionMediator(this.metadata, this.context, this.device);

    this.ballotStore = new DataStore();
    this.ballotBox = new BallotBox(this.metadata, this.context, this.ballotStore);
    System.out.printf("%nReady to encrypt at location: %s%n", this.device.location);
  }

  /**
   * Using the NUMBER_OF_GUARDIANS, generate public-private keypairs and share
   * representations of those keys with QUORUM of other Guardians.  Then, combine
   * the public election keys to make a joint election key that is used to encrypt ballots
   */
  boolean keyCeremony() {
    // Setup Mediator
    KeyCeremony.CeremonyDetails details = KeyCeremony.CeremonyDetails.create(this.numberOfGuardians,  this.quorum);
    KeyCeremonyMediator keyCeremony = new KeyCeremonyMediator(details);

    // Attendance (Public Key Share)
    for (Guardian guardian : this.guardians) {
      keyCeremony.announce(guardian);
    }

    System.out.printf(" Confirm all guardians have shared their public keys%n");
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
    }

    // Joint Key
    Optional<Group.ElementModP> joint_key = keyCeremony.publish_joint_key();
    System.out.printf(" Create the Joint Election Key%n");
    if (joint_key.isEmpty()) {
      System.out.printf(" *** FAILED%n");
      return false;
    }
    this.jointKey = joint_key.get();

    // Save Validation Keys
    for (Guardian guardian : this.guardians) {
      this.coefficientValidationSets.add(guardian.share_coefficient_validation_set());
    }

    return true;
  }

  void buildElection(Election.ElectionDescription description, Group.ElementModP joint_key) {
    this.metadata = new Election.InternalElectionDescription(description);

    Group.ElementModQ crypto_base_hash = Election.make_crypto_base_hash(this.numberOfGuardians, this.quorum, description);
    // LOOK this will change, add static method in Election
    Group.ElementModQ crypto_extended_base_hash = Hash.hash_elems(crypto_base_hash, joint_key);

    this.context = new Election.CiphertextElectionContext(
            this.numberOfGuardians,
            this.quorum,
            joint_key,
            description.crypto_hash(),
            crypto_base_hash,
            crypto_extended_base_hash);
  }

  Optional<Ballot.CiphertextBallot> encryptBallot(Ballot.PlaintextBallot plaintextBallot) {
    originalBallots.add(plaintextBallot);
    return this.encryptor.encrypt(plaintextBallot);
  }

  // Accept each ballot by marking it as either cast or spoiled.
  Optional<Ballot.CiphertextAcceptedBallot> castOrSpoil(Ballot.CiphertextBallot ballot, boolean spoil) {
    if (spoil) {
      return this.ballotBox.spoil(ballot);
    } else {
      return this.ballotBox.cast(ballot);
    }
  }

  void publish(String publishDir) throws IOException {
    int ncast = Iterables.size(this.ballotBox.getCastBallots());
    int nspoiled = Iterables.size(this.ballotBox.getSpoiledBallots());
    int total = this.originalBallots.size();
    int failed = total - ncast - nspoiled;
    System.out.printf("%nPublish cast = %d spoiled = %d failed = %d total = %d%n%n",
            ncast, nspoiled, failed, total);

    Publisher publisher = new Publisher(publishDir, false, false);
    publisher.writeEncryptionRecordProto(
            this.election,
            this.context,
            new Election.ElectionConstants(), // standard constants
            ImmutableList.of(this.device),
            this.ballotBox.getAllBallots(),
            this.coefficientValidationSets);
  }
}
