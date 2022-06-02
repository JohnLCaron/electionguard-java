package com.sunya.electionguard.workflow;

import com.beust.jcommander.*;
import com.google.common.base.Stopwatch;
import com.google.common.collect.Iterables;
import com.sunya.electionguard.BallotBox;
import com.sunya.electionguard.InternalManifest;
import com.sunya.electionguard.SubmittedBallot;
import com.sunya.electionguard.input.BallotInputValidation;
import com.sunya.electionguard.CiphertextBallot;
import com.sunya.electionguard.Manifest;
import com.sunya.electionguard.Encrypt;
import com.sunya.electionguard.PlaintextBallot;
import com.sunya.electionguard.input.ManifestInputValidation;
import com.sunya.electionguard.publish.Consumer;
import com.sunya.electionguard.publish.PrivateData;
import com.sunya.electionguard.publish.Publisher;
import com.sunya.electionguard.publish.ElectionRecord;
import electionguard.ballot.ElectionInitialized;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.Formatter;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.TimeUnit;

/**
 * A command line program to encrypt a collection of ballots.
 * Only needs the election public key, does not use local Guardians.
 * <p>
 * For command line help:
 * <strong>
 * <pre>
 *  java -classpath electionguard-java-all.jar com.sunya.electionguard.workflow.RunEncryptBallots --help
 * </pre>
 * </strong>
 */
public class RunEncryptBallots {

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
    String progName = RunEncryptBallots.class.getName();
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
    ElectionInitialized electionInit = consumer.readElectionInitialized();
    ManifestInputValidation validator = new ManifestInputValidation(electionRecord.manifest());
    Formatter errors = new Formatter();
    if (!validator.validateElection(errors)) {
      System.out.printf("*** ElectionInputValidation FAILED on %s%n%s", cmdLine.inputDir, errors);
      System.exit(1);
    }

    // if (electionRecord.constants != null) {
    //  Group.setPrimes(electionRecord.constants);
    // }

    BallotProvider ballotProvider = null;
    if (cmdLine.ballotProviderClass != null) {
      try {
        ballotProvider = makeBallotProvider(cmdLine.ballotProviderClass, electionRecord.manifest(), cmdLine.nballots);
      } catch (Throwable t) {
        t.printStackTrace();
        System.exit(3);
      }
    } else {
      ballotProvider = new FakeBallotProvider(electionRecord.manifest(), cmdLine.nballots);
    }

    System.out.printf(" EncryptBallots: read context from %s%n", cmdLine.inputDir);
    if (cmdLine.ballotProviderClass != null) {
      System.out.printf("   Ballots from %s %n", cmdLine.ballotProviderClass);
    } else {
      System.out.printf("   Generate %d random Ballots %n", cmdLine.nballots);
    }
    System.out.printf("   Write to %s%n", cmdLine.encryptDir);
    RunEncryptBallots encryptor = new RunEncryptBallots(electionRecord, cmdLine.deviceName);
    Stopwatch stopwatch = Stopwatch.createStarted();

    BallotInputValidation ballotValidator = new BallotInputValidation(electionRecord.manifest());
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
            } else {
              System.out.printf("***castOrSpoil success %s%n", encrypted_ballot.get().ballotId);
            }
          } else {
            System.out.printf("***Encryption failed%n");
          }
          originalBallots.add(ballot);
        } else {
          System.out.printf("Ballot %s failed validation%n   %s%n", ballot.object_id(), problems);
          invalidBallots.add(ballot);
        }
      }
    } catch (Throwable t) {
      t.printStackTrace();
      System.exit(4);
    }
    long msecs = stopwatch.elapsed(TimeUnit.MILLISECONDS);
    double secsPer = (.001 * msecs) / cmdLine.nballots;
    System.out.printf("*** encryptBallots elapsed = %d sec %.3f per ballot%n", stopwatch.elapsed(TimeUnit.SECONDS), secsPer);

    try {
      // publish
      Publisher publish = encryptor.publish(cmdLine.encryptDir, electionInit);
      encryptor.saveInvalidBallots(cmdLine.encryptDir, invalidBallots);
      if (cmdLine.save) {
        encryptor.saveOriginalBallots(cmdLine.encryptDir, originalBallots);
      }
      boolean ok = true;

      System.out.printf("*** EncryptBallots %s%n", ok ? "SUCCESS" : "FAILURE");

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
  private static final Random random = new Random();

  final ElectionRecord electionRecord;
  final int numberOfGuardians;
  final int quorum;

  int originalBallotsCount = 0;
  Encrypt.EncryptionDevice device;
  Encrypt.EncryptionMediator encryptor;
  BallotBox ballotBox;

  public RunEncryptBallots(ElectionRecord electionRecord, String deviceName) {
    this.electionRecord = electionRecord;
    this.quorum = electionRecord.quorum();
    this.numberOfGuardians = electionRecord.numberOfGuardians();

    // Configure the Encryption Device
    InternalManifest metadata = new InternalManifest(electionRecord.manifest());
    this.device = Encrypt.createDeviceForTest(deviceName);
    this.encryptor = new Encrypt.EncryptionMediator(metadata, electionRecord, this.device);

    this.ballotBox = new BallotBox(electionRecord.manifest(), electionRecord);
    System.out.printf("%nReady to encrypt with device: '%s'%n", this.device.location());
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

  Publisher publish(String publishDir, ElectionInitialized electionInit) throws IOException {
    int ncast = Iterables.size(this.ballotBox.getCastBallots());
    int nspoiled = Iterables.size(this.ballotBox.getSpoiledBallots());
    int failed = originalBallotsCount - ncast - nspoiled;
    System.out.printf("%nPublish cast = %d spoiled = %d failed = %d total = %d%n%n",
            ncast, nspoiled, failed, originalBallotsCount);

    Publisher publisher = new Publisher(publishDir, Publisher.Mode.createIfMissing);
    publisher.writeElectionInitialized(electionInit);
    publisher.writeSubmittedBallots(this.ballotBox.getAllBallots());
    return publisher;
  }

  void saveOriginalBallots(String outputDir, List<PlaintextBallot> ballots) throws IOException {
    PrivateData publisher = new PrivateData(outputDir, false, true);
    publisher.writeInputBallots(ballots);
    System.out.printf("Save %d original ballots in %s%n", ballots.size(), publisher.inputBallotsFilePath());
  }

  void saveInvalidBallots(String outputDir, List<PlaintextBallot> ballots) throws IOException {
    PrivateData publisher = new PrivateData(outputDir, false, true);
    publisher.writeInvalidBallots(ballots);
    System.out.printf("Save %d invalid ballots in %s%n", ballots.size(), publisher.invalidBallotsFilePath());
  }
}