package com.sunya.electionguard.json;

import com.sunya.electionguard.AvailableGuardian;
import com.sunya.electionguard.CiphertextTally;
import com.sunya.electionguard.ElectionConstants;
import com.sunya.electionguard.ElectionCryptoContext;
import com.sunya.electionguard.Encrypt;
import com.sunya.electionguard.GuardianRecord;
import com.sunya.electionguard.Manifest;
import com.sunya.electionguard.PlaintextBallot;
import com.sunya.electionguard.PlaintextTally;
import com.sunya.electionguard.SubmittedBallot;
import com.sunya.electionguard.publish.ElectionRecord;

import javax.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Formatter;

/** Publishes the Manifest Record to JSON */
public class JsonPublisher {
  //// json
  static final String JSON_SUFFIX = ".json";

  static final String DEVICES_DIR = "encryption_devices";
  static final String GUARDIANS_DIR = "guardians";
  static final String SUBMITTED_BALLOTS_DIR = "submitted_ballots"; // encrypted
  static final String SPOILED_BALLOTS_DIR = "spoiled_ballots"; // plaintext
  static final String INVALID_BALLOTS_DIR = "invalid_ballots"; // plaintext

  static final String MANIFEST_FILE_NAME = "manifest" + JSON_SUFFIX;
  static final String CONTEXT_FILE_NAME = "context" + JSON_SUFFIX;
  static final String CONSTANTS_FILE_NAME = "constants" + JSON_SUFFIX;
  static final String COEFFICIENTS_FILE_NAME = "coefficients" + JSON_SUFFIX;
  static final String ENCRYPTED_TALLY_FILE_NAME = "encrypted_tally" + JSON_SUFFIX;
  static final String TALLY_FILE_NAME = "tally" + JSON_SUFFIX;

  static final String DEVICE_PREFIX = "device_";
  static final String GUARDIAN_PREFIX = "guardian_";
  static final String SPOILED_BALLOT_PREFIX = "spoiled_ballot_";
  static final String SUBMITTED_BALLOT_PREFIX = "submitted_ballot_";
  static final String PLAINTEXT_BALLOT_PREFIX = "plaintext_ballot_";

  static final String AVAILABLE_GUARDIAN_PREFIX = "available_guardian_";

  private final String topdir;
  private final PublisherOld.Mode createMode;
  private final Path electionRecordDir;
  private final Path devicesDirPath;
  private final Path ballotsDirPath;
  private final Path spoiledBallotDirPath;
  private final Path guardianDirPath;

  public JsonPublisher(String where, PublisherOld.Mode createMode) throws IOException {
    this.topdir = where;
    this.createMode = createMode;

    this.electionRecordDir = Path.of(where);
    this.devicesDirPath = electionRecordDir.resolve(DEVICES_DIR);
    this.ballotsDirPath = electionRecordDir.resolve(SUBMITTED_BALLOTS_DIR);
    this.guardianDirPath = electionRecordDir.resolve(GUARDIANS_DIR);
    this.spoiledBallotDirPath = electionRecordDir.resolve(SPOILED_BALLOTS_DIR);
    // this.availableGuardianDirPath = publishDirectory.resolve(AVAILABLE_GUARDIANS_DIR);

    if (createMode == PublisherOld.Mode.createNew) {
      if (!Files.exists(electionRecordDir)) {
        Files.createDirectories(electionRecordDir);
      } else {
        // too dangerous removeAllFiles();
      }
      createDirs();
    } else if (createMode == PublisherOld.Mode.createIfMissing) {
      if (!Files.exists(electionRecordDir)) {
        Files.createDirectories(electionRecordDir);
      }
      createDirs();
    } else {
      if (!Files.exists(electionRecordDir)) {
        throw new IllegalStateException("Non existing election directory " + electionRecordDir);
      }
    }
  }

  public JsonPublisher(Path electionRecordDir, PublisherOld.Mode createMode) throws IOException {
    this.createMode = createMode;
    this.topdir = electionRecordDir.toAbsolutePath().toString();

    this.electionRecordDir = electionRecordDir;
    this.devicesDirPath = electionRecordDir.resolve(DEVICES_DIR);
    this.ballotsDirPath = electionRecordDir.resolve(SUBMITTED_BALLOTS_DIR);
    this.guardianDirPath = electionRecordDir.resolve(GUARDIANS_DIR);
    this.spoiledBallotDirPath = electionRecordDir.resolve(SPOILED_BALLOTS_DIR);
    // this.availableGuardianDirPath = publishDirectory.resolve(AVAILABLE_GUARDIANS_DIR);

    if (createMode == PublisherOld.Mode.createNew) {
      if (!Files.exists(electionRecordDir)) {
        Files.createDirectories(electionRecordDir);
      } else {
        removeAllFiles();
      }
      createDirs();
    } else if (createMode == PublisherOld.Mode.createIfMissing) {
      if (!Files.exists(electionRecordDir)) {
        Files.createDirectories(electionRecordDir);
      }
      createDirs();
    } else {
      if (!Files.exists(electionRecordDir)) {
        throw new IllegalStateException("Non existing election directory " + electionRecordDir);
      }
    }
  }

  private void createDirs() throws IOException {
    Files.createDirectories(devicesDirPath);
    Files.createDirectories(guardianDirPath);
    Files.createDirectories(ballotsDirPath);
    Files.createDirectories(spoiledBallotDirPath);
  }

  /** Delete everything in the output directory, but leave that directory. */
  private void removeAllFiles() throws IOException {
    if (!electionRecordDir.toFile().exists()) {
      return;
    }

    String filename = electionRecordDir.getFileName().toString();
    Files.walk(electionRecordDir)
            .filter(p -> !p.equals(electionRecordDir))
            .map(Path::toFile)
            .sorted((o1, o2) -> -o1.compareTo(o2))
            .forEach(f -> f.delete());
  }

  /** Make sure output dir exists and is writeable. */
  public boolean validateOutputDir(Formatter error) {
    if (!Files.exists(electionRecordDir)) {
      error.format(" Output directory '%s' does not exist%n", electionRecordDir);
      return false;
    }
    if (!Files.isDirectory(electionRecordDir)) {
      error.format(" Output directory '%s' is not a directory%n", electionRecordDir);
      return false;
    }
    if (!Files.isWritable(electionRecordDir)) {
      error.format(" Output directory '%s' is not writeable%n", electionRecordDir);
      return false;
    }
    if (!Files.isExecutable(electionRecordDir)) {
      error.format(" Output directory '%s' is not executable%n", electionRecordDir);
      return false;
    }
    return true;
  }


  public JsonPrivateData makePrivateData(boolean removeAllFiles, boolean createDirs) throws IOException {
    return new JsonPrivateData(this.topdir, removeAllFiles, createDirs);
  }

  public Path manifestPath() {
    return electionRecordDir.resolve(MANIFEST_FILE_NAME).toAbsolutePath();
  }

  public Path contextPath() {
    return electionRecordDir.resolve(CONTEXT_FILE_NAME).toAbsolutePath();
  }

  public Path constantsPath() {
    return electionRecordDir.resolve(CONSTANTS_FILE_NAME).toAbsolutePath();
  }

  public Path coefficientsPath() {
    return electionRecordDir.resolve(COEFFICIENTS_FILE_NAME).toAbsolutePath();
  }

  public Path publishPath() {
    return electionRecordDir.toAbsolutePath();
  }

  public Path tallyPath() {
    return electionRecordDir.resolve(TALLY_FILE_NAME).toAbsolutePath();
  }

  public Path encryptedTallyPath() {
    return electionRecordDir.resolve(ENCRYPTED_TALLY_FILE_NAME).toAbsolutePath();
  }

  public Path devicePath(String id) {
    return devicesDirPath.resolve(DEVICE_PREFIX + id + JSON_SUFFIX);
  }

  public File[] deviceFiles() {
    if (!Files.exists(devicesDirPath) || !Files.isDirectory(devicesDirPath)) {
      return new File[0];
    }
    return devicesDirPath.toFile().listFiles();
  }

  public Path guardianRecordsPath(String id) {
    String fileName = GUARDIAN_PREFIX + id + JSON_SUFFIX;
    return guardianDirPath.resolve(fileName);
  }

  public File[] guardianRecordsFiles() {
    if (!Files.exists(guardianDirPath) || !Files.isDirectory(guardianDirPath)) {
      return new File[0];
    }
    return guardianDirPath.toFile().listFiles();
  }

  public Path ballotPath(String id) {
    String fileName = SUBMITTED_BALLOT_PREFIX + id + JSON_SUFFIX;
    return ballotsDirPath.resolve(fileName);
  }

  public File[] ballotFiles() {
    if (!Files.exists(ballotsDirPath) || !Files.isDirectory(ballotsDirPath)) {
      return new File[0];
    }
    return ballotsDirPath.toFile().listFiles();
  }

  public Path spoiledBallotPath(String id) {
    String fileName = SPOILED_BALLOT_PREFIX + id + JSON_SUFFIX;
    return spoiledBallotDirPath.resolve(fileName);
  }

  public File[] spoiledBallotFiles() {
    if (!Files.exists(spoiledBallotDirPath) || !Files.isDirectory(spoiledBallotDirPath)) {
      return new File[0];
    }
    return spoiledBallotDirPath.toFile().listFiles();
  }

  //////////////////////////////////////////////////////////////////
  // Json

  public void writeKeyCeremonyJson(
          Manifest manifest,
          ElectionCryptoContext context,
          ElectionConstants constants,
          Iterable<GuardianRecord> guardianRecords) throws IOException {

    if (createMode == PublisherOld.Mode.readonly) {
      throw new UnsupportedOperationException("Trying to write to readonly election record");
    }

    ConvertToJson.writeElection(manifest, this.manifestPath());
    ConvertToJson.writeContext(context, this.contextPath());
    ConvertToJson.writeConstants(constants, this.constantsPath());

    for (GuardianRecord guardianRecord : guardianRecords) {
      ConvertToJson.writeGuardianRecord(guardianRecord, this.guardianRecordsPath(guardianRecord.guardianId()));
    }
  }

  public void writeDecryptionResultsJson(
          ElectionRecord election,
          CiphertextTally encryptedTally,
          PlaintextTally decryptedTally,
          Iterable<PlaintextTally> spoiledBallots,
          Iterable<AvailableGuardian> availableGuardians) throws IOException {

    if (createMode == PublisherOld.Mode.readonly) {
      throw new UnsupportedOperationException("Trying to write to readonly election record");
    }

    ConvertToJson.writeElection(election.manifest(), this.manifestPath());
    // ConvertToJson.writeContext(election.context, this.contextPath());
    // ConvertToJson.writeConstants(election.constants, this.constantsPath());
    // TODO election.devices

    ConvertToJson.writeCiphertextTally(encryptedTally, encryptedTallyPath());
    ConvertToJson.writePlaintextTally(decryptedTally, tallyPath());

    if (spoiledBallots != null) {
      for (PlaintextTally ballot : spoiledBallots) {
        ConvertToJson.writePlaintextTally(ballot, this.spoiledBallotPath(ballot.tallyId));
      }
    }

    if (availableGuardians != null) {
      ConvertToJson.writeCoefficients(availableGuardians, this.coefficientsPath());
    }
  }

  /** Publishes the election record as json. */
  public void writeElectionRecordJson(
          Manifest manifest,
          ElectionCryptoContext context,
          ElectionConstants constants,
          Iterable<Encrypt.EncryptionDevice> devices,
          Iterable<SubmittedBallot> ciphertext_ballots,
          CiphertextTally ciphertext_tally,
          PlaintextTally decryptedTally,
          @Nullable Iterable<GuardianRecord> guardianRecords,
          @Nullable Iterable<PlaintextTally> spoiledBallots,
          @Nullable Iterable<AvailableGuardian> availableGuardians) throws IOException {

    if (createMode == PublisherOld.Mode.readonly) {
      throw new UnsupportedOperationException("Trying to write to readonly election record");
    }

    ConvertToJson.writeElection(manifest, this.manifestPath());
    ConvertToJson.writeContext(context, this.contextPath());
    ConvertToJson.writeConstants(constants, this.constantsPath());

    for (Encrypt.EncryptionDevice device : devices) {
      ConvertToJson.writeDevice(device, this.devicePath(device.location()));
    }

    for (SubmittedBallot ballot : ciphertext_ballots) {
      ConvertToJson.writeSubmittedBallot(ballot, this.ballotPath(ballot.object_id()));
    }

    ConvertToJson.writeCiphertextTally(ciphertext_tally, encryptedTallyPath());
    ConvertToJson.writePlaintextTally(decryptedTally, tallyPath());

    if (guardianRecords != null) {
      for (GuardianRecord guardianRecord : guardianRecords) {
        ConvertToJson.writeGuardianRecord(guardianRecord, this.guardianRecordsPath(guardianRecord.guardianId()));
      }
    }

    if (spoiledBallots != null) {
      for (PlaintextTally ballot : spoiledBallots) {
        ConvertToJson.writePlaintextTally(ballot, this.spoiledBallotPath(ballot.tallyId));
      }
    }

    if (availableGuardians != null) {
      ConvertToJson.writeCoefficients(availableGuardians, this.coefficientsPath());
    }
  }

  // These are input ballots that did not validate (not spoiled ballots). put them somewhere to be examined later.
  public void publish_invalid_ballots(String directory, Iterable<PlaintextBallot> invalid_ballots) throws IOException {
    if (createMode == PublisherOld.Mode.readonly) {
      throw new UnsupportedOperationException("Trying to write to readonly election record");
    }

    Path invalidBallotsPath = electionRecordDir.resolve(directory);
    Files.createDirectories(invalidBallotsPath);

    for (PlaintextBallot plaintext_ballot : invalid_ballots) {
      String ballot_name = PLAINTEXT_BALLOT_PREFIX + plaintext_ballot.object_id() + JSON_SUFFIX;
      ConvertToJson.writePlaintextBallot(plaintext_ballot, invalidBallotsPath.resolve(ballot_name));
    }

  }

}
