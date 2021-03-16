package com.sunya.electionguard.workflow;

import com.beust.jcommander.*;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.sunya.electionguard.BallotBox;
import com.sunya.electionguard.InternalManifest;
import com.sunya.electionguard.SubmittedBallot;
import com.sunya.electionguard.input.BallotInputValidation;
import com.sunya.electionguard.CiphertextBallot;
import com.sunya.electionguard.Manifest;
import com.sunya.electionguard.Encrypt;
import com.sunya.electionguard.PlaintextBallot;
import com.sunya.electionguard.input.ElectionInputValidation;
import com.sunya.electionguard.publish.Consumer;
import com.sunya.electionguard.publish.Publisher;
import com.sunya.electionguard.verifier.ElectionRecord;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.Formatter;
import java.util.List;
import java.util.Optional;
import java.util.Random;

/**
 * A command line program to encrypt a collection of ballots.
 * <p>
 * For command line help:
 * <strong>
 * <pre>
 *  java -classpath electionguard-java-all.jar com.sunya.electionguard.workflow.EncryptBallots --help
 * </pre>
 * </strong>
 *
 * @see <a href="https://www.electionguard.vote/spec/0.95.0/5_Ballot_encryption/">Ballot Encryption</a>
 */
public class EncryptBallots {

  private static class CommandLine {
    @Parameter(names = {"-in"}, order=0,
            description = "Directory containing input election record", required = true)
    String inputDir;

    @Parameter(names = {"-out"}, order=2,
            description = "Directory to write output election record", required = true)
    String encryptDir;

    @Parameter(names = {"-ballots"}, order=3,
            description = "BallotProvider classname")
    String ballotProviderClass;

    @Parameter(names = {"-nballots"}, order=4,
            description = "number of ballots to generate")
    int nballots;

    @Parameter(names = {"-device"}, order=5,
            description = "Name of this device", required = true)
    String deviceName;

    @Parameter(names = {"--save"}, order=6, description = "Save the original ballots for debugging", help = true)
    boolean save = false;

    @Parameter(names = {"-h", "--help"}, order=7, description = "Display this help and exit", help = true)
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
    ElectionRecord electionRecord = consumer.readElectionRecord();
    ElectionInputValidation validator = new ElectionInputValidation(electionRecord.election);
    Formatter errors = new Formatter();
    if (!validator.validateElection(errors)) {
      System.out.printf("*** ElectionInputValidation FAILED on %s%n%s", cmdLine.inputDir, errors);
      System.exit(1);
    }

    BallotProvider ballotProvider = null;
    if (cmdLine.ballotProviderClass != null) {
      try {
        ballotProvider = makeBallotProvider(cmdLine.ballotProviderClass, electionRecord.election, cmdLine.nballots);
      } catch (Throwable t) {
        t.printStackTrace();
        System.exit(3);
      }
    } else {
      ballotProvider = new FakeBallotProvider(electionRecord.election, cmdLine.nballots);
    }

    System.out.printf(" BallotEncryptor read context from %s%n Ballots from %s%n Write to %s%n",
            cmdLine.inputDir, cmdLine.ballotProviderClass, cmdLine.encryptDir);
    EncryptBallots encryptor = new EncryptBallots(electionRecord, cmdLine.deviceName);

    BallotInputValidation ballotValidator = new BallotInputValidation(electionRecord.election);
    List<PlaintextBallot> originalBallots = new ArrayList<>();
    List<PlaintextBallot> invalidBallots = new ArrayList<>();
    try {
      for (PlaintextBallot ballot : ballotProvider.ballots()) {
        Formatter problems = new Formatter();
        if (ballotValidator.validateBallot(ballot, problems)) {
          Optional<CiphertextBallot> encrypted_ballot = encryptor.encryptBallot(ballot);
          if (encrypted_ballot.isPresent()) {
            Optional<SubmittedBallot> accepted = encryptor.castOrSpoil(encrypted_ballot.get(), random.nextBoolean());
            if (accepted.isEmpty()) {
              System.out.printf("***castOrSpoil failed%n");
            }
          } else {
            System.out.printf("***Encryption failed%n");
          }
          originalBallots.add(ballot);
        } else {
          System.out.printf("Ballot %s failed validation%n   %s%n", ballot.object_id, problems.toString());
          invalidBallots.add(ballot);
        }
      }
    } catch (Throwable t) {
      t.printStackTrace();
      System.exit(4);
    }

    try {
      // publish
      Publisher publish = encryptor.publish(cmdLine.encryptDir);
      encryptor.saveInvalidBallots(publish, invalidBallots);
      if (cmdLine.save) {
        encryptor.saveOriginalBallots(publish, originalBallots);
      }
      boolean ok = true;

      System.out.printf("*** EncryptBallots %s%n", ok ? "SUCCESS" : "FAILURE");
      System.exit(ok ? 0 : 1);

    } catch (Throwable t) {
      System.out.printf("*** EncryptBallots FAILURE%n");
      t.printStackTrace();
      System.exit(5);
    }
  }

  public static BallotProvider makeBallotProvider(String className, Manifest election, int nballots) throws Throwable {
    Class<?> c = Class.forName(className);
    if (!(BallotProvider.class.isAssignableFrom(c))) {
      throw new IllegalArgumentException(String.format("%s must implement %s", c.getName(), BallotProvider.class));
    }
    java.lang.reflect.Constructor<BallotProvider> constructor =
            (Constructor<BallotProvider>) c.getConstructor(Manifest.class, Integer.class);
    return constructor.newInstance(election, nballots);
  }

  ///////////////////////////////////////////////////////////////////////////
  private static final Random random = new Random(System.currentTimeMillis());

  final ElectionRecord electionRecord;
  final int numberOfGuardians;
  final int quorum;

  int originalBallotsCount = 0;
  Encrypt.EncryptionDevice device;
  Encrypt.EncryptionMediator encryptor;
  BallotBox ballotBox;

  public EncryptBallots(ElectionRecord electionRecord, String deviceName) {
    this.electionRecord = electionRecord;
    this.quorum = electionRecord.context.quorum;
    this.numberOfGuardians = electionRecord.context.number_of_guardians;

    // Configure the Encryption Device
    InternalManifest metadata = new InternalManifest(electionRecord.election);
    this.device = Encrypt.EncryptionDevice.createForTest(deviceName);
    this.encryptor = new Encrypt.EncryptionMediator(metadata, electionRecord.context, this.device);

    this.ballotBox = new BallotBox(electionRecord.election, electionRecord.context);
    System.out.printf("%nReady to encrypt at location: '%s'%n", this.device.location);
  }

  Optional<CiphertextBallot> encryptBallot(PlaintextBallot plaintextBallot) {
    originalBallotsCount++;
    return this.encryptor.encrypt(plaintextBallot);
  }

  // Accept each ballot by marking it as either cast or spoiled.
  Optional<SubmittedBallot> castOrSpoil(CiphertextBallot ballot, boolean spoil) {
    if (spoil) {
      return this.ballotBox.spoil(ballot);
    } else {
      return this.ballotBox.cast(ballot);
    }
  }

  Publisher publish(String publishDir) throws IOException {
    int ncast = Iterables.size(this.ballotBox.getCastBallots());
    int nspoiled = Iterables.size(this.ballotBox.getSpoiledBallots());
    int failed = originalBallotsCount - ncast - nspoiled;
    System.out.printf("%nPublish cast = %d spoiled = %d failed = %d total = %d%n%n",
            ncast, nspoiled, failed, originalBallotsCount);

    Publisher publisher = new Publisher(publishDir, false, false);
    publisher.writeEncryptionResultsProto(
            electionRecord,
            ImmutableList.of(this.device), // add the device
            this.ballotBox.getAllBallots() // add the encrypted ballots
    );
    return publisher;
  }

  void saveOriginalBallots(Publisher publisher, List<PlaintextBallot> ballots) throws IOException {
    publisher.publish_private_data(ballots, null);
    System.out.printf("Save original ballot in %s%n", publisher.privateDirPath());
  }

  void saveInvalidBallots(Publisher publisher, List<PlaintextBallot> ballots) throws IOException {
    publisher.publish_invalid_ballots("invalid_ballots", ballots);
    System.out.printf("Save invalid ballot in %s/invalid_ballots%n", publisher.publishPath());
  }
}
