package com.sunya.electionguard.workflow;

import com.beust.jcommander.*;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.sunya.electionguard.Ballot;
import com.sunya.electionguard.BallotBox;
import com.sunya.electionguard.DataStore;
import com.sunya.electionguard.Election;
import com.sunya.electionguard.Encrypt;
import com.sunya.electionguard.proto.ElectionRecordFromProto;
import com.sunya.electionguard.publish.Consumer;
import com.sunya.electionguard.publish.Publisher;
import com.sunya.electionguard.verifier.ElectionRecord;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.util.Optional;
import java.util.Random;

/** Encrypt a collection of ballots. */
public class EncryptBallots {

  private static class CommandLine {
    @Parameter(names = {"-in"},
            description = "Directory containing input election record", required = true)
    String inputDir;

    @Parameter(names = {"-out"},
            description = "Directory to write output election record", required = true)
    String encryptDir;

    @Parameter(names = {"-ballots"},
            description = "BallotProvider classname", required = true)
    String ballotProviderClass;

    @Parameter(names = {"-device"},
            description = "Name of this device", required = true)
    String deviceName;

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
    String progName = EncryptBallots.class.getName();
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

    Consumer consumer = new Consumer(cmdLine.inputDir);
    ElectionRecord electionRecord;
    if (cmdLine.isProto) {
      electionRecord = ElectionRecordFromProto.read(consumer.electionRecordProtoFile().toString());
    } else {
      electionRecord = consumer.getElectionRecord();
    }

    BallotProvider ballotProvider = null;
    try {
      ballotProvider = makeBallotProvider(cmdLine.ballotProviderClass, electionRecord.election);
    } catch (Throwable t) {
      t.printStackTrace();
      System.exit(3);
    }

    System.out.printf(" BallotEncryptor read context from %s%n Ballots from %s%n Write to %s%n",
            cmdLine.inputDir, cmdLine.ballotProviderClass, cmdLine.encryptDir);
    EncryptBallots encryptor = new EncryptBallots(electionRecord, cmdLine.deviceName);

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
      System.exit(0);

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

  ///////////////////////////////////////////////////////////////////////////
  private static final Random random = new Random(System.currentTimeMillis());

  final ElectionRecord electionRecord;
  final int numberOfGuardians;
  final int quorum;

  int originalBallotsCount = 0;
  Encrypt.EncryptionDevice device;
  Encrypt.EncryptionMediator encryptor;
  DataStore ballotStore;
  BallotBox ballotBox;

  public EncryptBallots(ElectionRecord electionRecord, String deviceName) {
    this.electionRecord = electionRecord;
    this.quorum = electionRecord.context.quorum;
    this.numberOfGuardians = electionRecord.context.number_of_guardians;

    // Configure the Encryption Device
    Election.InternalElectionDescription metadata = new Election.InternalElectionDescription(electionRecord.election);
    this.device = new Encrypt.EncryptionDevice(deviceName);
    this.encryptor = new Encrypt.EncryptionMediator(metadata, electionRecord.context, this.device);

    this.ballotStore = new DataStore();
    this.ballotBox = new BallotBox(metadata, electionRecord.context, this.ballotStore);
    System.out.printf("%nReady to encrypt at location: '%s'%n", this.device.location);
  }

  Optional<Ballot.CiphertextBallot> encryptBallot(Ballot.PlaintextBallot plaintextBallot) {
    originalBallotsCount++;
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
    int failed = originalBallotsCount - ncast - nspoiled;
    System.out.printf("%nPublish cast = %d spoiled = %d failed = %d total = %d%n%n",
            ncast, nspoiled, failed, originalBallotsCount);

    Publisher publisher = new Publisher(publishDir, false, false);
    publisher.writeEncryptionRecordProto(
            electionRecord.election,
            electionRecord.context,
            electionRecord.constants,
            ImmutableList.of(this.device),
            this.ballotBox.getAllBallots(), // add the encrypted ballots
            electionRecord.guardianCoefficients);
  }
}
