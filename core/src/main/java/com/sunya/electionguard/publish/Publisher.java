package com.sunya.electionguard.publish;

import com.google.common.collect.Iterables;
import com.sunya.electionguard.*;
import com.sunya.electionguard.proto.CiphertextBallotToProto;
import com.sunya.electionguard.proto.ElectionRecordToProto;
import com.sunya.electionguard.proto.PlaintextTallyToProto;
import com.sunya.electionguard.protogen.PlaintextTallyProto;
import com.sunya.electionguard.verifier.ElectionRecord;

import javax.annotation.Nullable;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Formatter;

import com.sunya.electionguard.protogen.CiphertextBallotProto;
import com.sunya.electionguard.protogen.ElectionRecordProto;

/** Publishes the Manifest Record to Json or protobuf files. */
public class Publisher {
  public enum Mode {readonly,
                    writeonly, // write new files, but do not create directories
                    createIfMissing, // create directories if not already exist
                    createNew // create clean directories
  }

  static final String ELECTION_RECORD_DIR = "election_record";

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

  //// proto
  static final String PROTO_SUFFIX = ".protobuf";
  static final String ELECTION_RECORD_FILE_NAME = "electionRecord" + PROTO_SUFFIX;
  static final String GUARDIANS_FILE = "guardians" + PROTO_SUFFIX;
  static final String SUBMITTED_BALLOT_PROTO = "submittedBallots" + PROTO_SUFFIX;
  static final String SPOILED_BALLOT_FILE = "spoiledBallotsTally" + PROTO_SUFFIX;
  static final String TRUSTEES_FILE = "trustees" + PROTO_SUFFIX;

  private final String topdir;
  private final Mode createMode;
  private final Path electionRecordDir;
  private final Path devicesDirPath;
  private final Path ballotsDirPath;
  private final Path spoiledBallotDirPath;
  private final Path guardianDirPath;

  public Publisher(String where, Mode createMode, boolean isJsonOutput) throws IOException {
    this.topdir = where;
    this.createMode = createMode;
    this.electionRecordDir = Path.of(where).resolve(ELECTION_RECORD_DIR);
    this.devicesDirPath = electionRecordDir.resolve(DEVICES_DIR);
    this.ballotsDirPath = electionRecordDir.resolve(SUBMITTED_BALLOTS_DIR);
    this.guardianDirPath = electionRecordDir.resolve(GUARDIANS_DIR);
    this.spoiledBallotDirPath = electionRecordDir.resolve(SPOILED_BALLOTS_DIR);
    // this.availableGuardianDirPath = publishDirectory.resolve(AVAILABLE_GUARDIANS_DIR);

    if (createMode == Mode.createNew) {
      if (!Files.exists(electionRecordDir)) {
        Files.createDirectories(electionRecordDir);
      } else {
        removeAllFiles();
      }
      if (isJsonOutput) {
        createDirs();
      }
    } else if (createMode == Mode.createIfMissing) {
      if (!Files.exists(electionRecordDir)) {
        Files.createDirectories(electionRecordDir);
      }
      if (isJsonOutput) {
        createDirs();
      }
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
    if (!filename.startsWith("election_record")) {
      throw new RuntimeException(String.format("Publish directory '%s' should start with 'election_record'", filename));
    }
    Files.walk(electionRecordDir)
            .filter(p -> !p.equals(electionRecordDir))
            .map(Path::toFile)
            .sorted((o1, o2) -> -o1.compareTo(o2))
            .forEach( f-> f.delete());
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


  public PrivateData makePrivateData(boolean removeAllFiles, boolean createDirs) throws IOException {
    return new PrivateData(this.topdir, removeAllFiles, createDirs);
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

  ////////////////////

  public Path electionRecordProtoPath() {
    return electionRecordDir.resolve(ELECTION_RECORD_FILE_NAME).toAbsolutePath();
  }

  public Path submittedBallotProtoPath() {
    return electionRecordDir.resolve(SUBMITTED_BALLOT_PROTO).toAbsolutePath();
  }

  public Path spoiledBallotProtoPath() {
    return electionRecordDir.resolve(SPOILED_BALLOT_FILE).toAbsolutePath();
  }

  //////////////////////////////////////////////////////////////////
  // Json

  public void writeKeyCeremonyJson(
          Manifest manifest,
          CiphertextElectionContext context,
          ElectionConstants constants,
          Iterable<GuardianRecord> guardianRecords) throws IOException {

    if (createMode == Mode.readonly) {
      throw new UnsupportedOperationException("Trying to write to readonly election record");
    }

    ConvertToJson.writeElection(manifest, this.manifestPath());
    ConvertToJson.writeContext(context, this.contextPath());
    ConvertToJson.writeConstants(constants, this.constantsPath());

    for (GuardianRecord guardianRecord : guardianRecords) {
      ConvertToJson.writeGuardianRecord(guardianRecord, this.guardianRecordsPath(guardianRecord.guardian_id()));
    }
  }

  public void writeDecryptionResultsJson(
          ElectionRecord election,
          CiphertextTally encryptedTally,
          PlaintextTally decryptedTally,
          Iterable<PlaintextTally> spoiledBallots,
          Iterable<AvailableGuardian> availableGuardians) throws IOException {

    if (createMode == Mode.readonly) {
      throw new UnsupportedOperationException("Trying to write to readonly election record");
    }

    ConvertToJson.writeElection(election.election, this.manifestPath());
    ConvertToJson.writeContext(election.context, this.contextPath());
    ConvertToJson.writeConstants(election.constants, this.constantsPath());
    // TODO election.devices

    ConvertToJson.writeCiphertextTally(encryptedTally, encryptedTallyPath());
    ConvertToJson.writePlaintextTally(decryptedTally, tallyPath());

    if (spoiledBallots != null) {
      for (PlaintextTally ballot : spoiledBallots) {
        ConvertToJson.writePlaintextTally(ballot, this.spoiledBallotPath(ballot.object_id));
      }
    }

    if (availableGuardians != null) {
      ConvertToJson.writeCoefficients(availableGuardians, this.coefficientsPath());
    }
  }

  /** Publishes the election record as json. */
  public void writeElectionRecordJson(
          Manifest manifest,
          CiphertextElectionContext context,
          ElectionConstants constants,
          Iterable<Encrypt.EncryptionDevice> devices,
          Iterable<SubmittedBallot> ciphertext_ballots,
          CiphertextTally ciphertext_tally,
          PlaintextTally decryptedTally,
          @Nullable Iterable<GuardianRecord> guardianRecords,
          @Nullable Iterable<PlaintextTally> spoiledBallots,
          @Nullable Iterable<AvailableGuardian> availableGuardians) throws IOException {

    if (createMode == Mode.readonly) {
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
        ConvertToJson.writeGuardianRecord(guardianRecord, this.guardianRecordsPath(guardianRecord.guardian_id()));
      }
    }

    if (spoiledBallots != null) {
      for (PlaintextTally ballot : spoiledBallots) {
        ConvertToJson.writePlaintextTally(ballot, this.spoiledBallotPath(ballot.object_id));
      }
    }

    if (availableGuardians != null) {
      ConvertToJson.writeCoefficients(availableGuardians, this.coefficientsPath());
    }
  }

  //////////////////////////////////////////////////////////////////
  // Proto

  /** Publishes the KeyCeremony part of the election record as proto. */
  public void writeKeyCeremonyProto(
          Manifest description,
          CiphertextElectionContext context,
          ElectionConstants constants,
          Iterable<GuardianRecord> guardianRecords) throws IOException {

    if (createMode == Mode.readonly) {
      throw new UnsupportedOperationException("Trying to write to readonly election record");
    }

    if (context.number_of_guardians != Iterables.size(guardianRecords)) {
      throw new IllegalStateException(String.format("Number of guardians (%d) does not match number of coefficients (%d)",
              context.number_of_guardians, Iterables.size(guardianRecords)));
    }

    ElectionRecordProto.ElectionRecord electionRecordProto = ElectionRecordToProto.buildElectionRecord(
            description, context, constants, guardianRecords,
            null, null, null, null);
    try (FileOutputStream out = new FileOutputStream(electionRecordProtoPath().toFile())) {
      electionRecordProto.writeDelimitedTo(out);
    }
  }

  /** Publishes the ballot Encryptions part of election record as proto. */
  public void writeEncryptionResultsProto(
          ElectionRecord electionRecord,
          Iterable<Encrypt.EncryptionDevice> devices,
          Iterable<SubmittedBallot> submittedBallots) throws IOException {

    if (createMode == Mode.readonly) {
      throw new UnsupportedOperationException("Trying to write to readonly election record");
    }

    // the accepted ballots are written into their own file
    try (FileOutputStream out = new FileOutputStream(submittedBallotProtoPath().toFile())) {
      for (SubmittedBallot ballot : submittedBallots) {
        CiphertextBallotProto.SubmittedBallot ballotProto = CiphertextBallotToProto.translateToProto(ballot);
        ballotProto.writeDelimitedTo(out);
      }
    }

    ElectionRecordProto.ElectionRecord electionRecordProto = ElectionRecordToProto.buildElectionRecord(
            electionRecord.election,
            electionRecord.context,
            electionRecord.constants,
            electionRecord.guardianRecords,
            devices, null, null, null);

    try (FileOutputStream out = new FileOutputStream(electionRecordProtoPath().toFile())) {
      electionRecordProto.writeDelimitedTo(out);
    }
  }

  /** Adds the encryptedTally to the election record. */
  public void writeEncryptedTallyProto(ElectionRecord electionRecord,
                                       CiphertextTally encryptedTally) throws IOException {

    if (createMode == Mode.readonly) {
      throw new UnsupportedOperationException("Trying to write to readonly election record");
    }

    ElectionRecordProto.ElectionRecord ElectionRecordProto = ElectionRecordToProto.buildElectionRecord(
            electionRecord.election, electionRecord.context, electionRecord.constants,
            electionRecord.guardianRecords,
            electionRecord.devices,
            encryptedTally,
            null, null);

    try (FileOutputStream out = new FileOutputStream(electionRecordProtoPath().toFile())) {
      ElectionRecordProto.writeDelimitedTo(out);
    }
  }

  public void writeDecryptedTallyProto(ElectionRecord electionRecord,
                                       PlaintextTally decryptedTally) throws IOException {

    if (createMode == Mode.readonly) {
      throw new UnsupportedOperationException("Trying to write to readonly election record");
    }

    ElectionRecordProto.ElectionRecord ElectionRecordProto = ElectionRecordToProto.buildElectionRecord(
            electionRecord.election, electionRecord.context, electionRecord.constants,
            electionRecord.guardianRecords, electionRecord.devices,
            electionRecord.encryptedTally,
            decryptedTally,
            null);

    try (FileOutputStream out = new FileOutputStream(electionRecordProtoPath().toFile())) {
      ElectionRecordProto.writeDelimitedTo(out);
    }
  }

  /** Publishes the ballot and tally Decryptions election record as proto. */
  public void writeDecryptionResultsProto(
          ElectionRecord electionRecord,
          CiphertextTally encryptedTally,
          PlaintextTally decryptedTally,
          Iterable<PlaintextTally> spoiledBallots,
          Iterable<AvailableGuardian> availableGuardians) throws IOException {

    if (createMode == Mode.readonly) {
      throw new UnsupportedOperationException("Trying to write to readonly election record");
    }

    if (spoiledBallots != null) {
      // the spoiledBallots are written into their own file
      try (FileOutputStream out = new FileOutputStream(spoiledBallotProtoPath().toFile())) {
        for (PlaintextTally ballot : spoiledBallots) {
          PlaintextTallyProto.PlaintextTally ballotProto = PlaintextTallyToProto.translateToProto(ballot);
          ballotProto.writeDelimitedTo(out);
        }
      }
    }

    ElectionRecordProto.ElectionRecord ElectionRecordProto = ElectionRecordToProto.buildElectionRecord(
            electionRecord.election, electionRecord.context, electionRecord.constants,
            electionRecord.guardianRecords, electionRecord.devices,
            encryptedTally,
            decryptedTally,
            availableGuardians);

    try (FileOutputStream out = new FileOutputStream(electionRecordProtoPath().toFile())) {
      ElectionRecordProto.writeDelimitedTo(out);
    }
  }

  /** Publishes the entire election record as proto. */
  public void writeElectionRecordProto(
          Manifest description,
          CiphertextElectionContext context,
          ElectionConstants constants,
          Iterable<GuardianRecord> guardianRecords,
          Iterable<Encrypt.EncryptionDevice> devices,
          Iterable<SubmittedBallot> accepted_ballots,
          CiphertextTally ciphertext_tally,
          PlaintextTally decryptedTally,
          @Nullable Iterable<PlaintextTally> spoiledBallots,
          Iterable<AvailableGuardian> availableGuardians) throws IOException {

    if (createMode == Mode.readonly) {
      throw new UnsupportedOperationException("Trying to write to readonly election record");
    }

    // the accepted ballots are written into their own file
    try (FileOutputStream out = new FileOutputStream(submittedBallotProtoPath().toFile())) {
      for (SubmittedBallot ballot : accepted_ballots) {
        CiphertextBallotProto.SubmittedBallot ballotProto = CiphertextBallotToProto.translateToProto(ballot);
        ballotProto.writeDelimitedTo(out);
      }
    }

    if (spoiledBallots != null) {
      // the spoiledBallots are written into their own file
      try (FileOutputStream out = new FileOutputStream(spoiledBallotProtoPath().toFile())) {
        for (PlaintextTally ballot : spoiledBallots) {
          PlaintextTallyProto.PlaintextTally ballotProto = PlaintextTallyToProto.translateToProto(ballot);
          ballotProto.writeDelimitedTo(out);
        }
      }
    }

    ElectionRecordProto.ElectionRecord ElectionRecordProto = ElectionRecordToProto.buildElectionRecord(
            description, context, constants, guardianRecords,
            devices,
            ciphertext_tally,
            decryptedTally,
            availableGuardians);

    try (FileOutputStream out = new FileOutputStream(electionRecordProtoPath().toFile())) {
      ElectionRecordProto.writeDelimitedTo(out);
    }
  }

  /** Copy accepted ballots file from the inputDir to this election record. */
  public void copyAcceptedBallots(String inputDir) throws IOException {
    if (createMode == Mode.readonly) {
      throw new UnsupportedOperationException("Trying to write to readonly election record");
    }

    Path source = new Publisher(inputDir, Mode.writeonly, false).submittedBallotProtoPath();
    Path dest = submittedBallotProtoPath();
    Files.copy(source, dest, StandardCopyOption.COPY_ATTRIBUTES);
  }

  // These are input ballots that did not validate (not spoiled ballots). put them somewhere to be examined later.
  public void publish_invalid_ballots(String directory, Iterable<PlaintextBallot> invalid_ballots) throws IOException {
    if (createMode == Mode.readonly) {
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
