package com.sunya.electionguard.publish;

import com.google.common.collect.Iterables;
import com.sunya.electionguard.*;
import com.sunya.electionguard.proto.CiphertextBallotToProto;
import com.sunya.electionguard.proto.ElectionRecordToProto;
import com.sunya.electionguard.proto.PlaintextBallotToProto;
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
import com.sunya.electionguard.protogen.PlaintextBallotProto;
import com.sunya.electionguard.protogen.TrusteeProto;

/** Publishes the Manifest Record to Json or protobuf files. */
public class Publisher {
  static final String PRIVATE_DIR = "private";

  //// json
  static final String SUFFIX = ".json";

  static final String DEVICES_DIR = "encryption_devices";
  static final String GUARDIANS_DIR = "guardians";
  static final String SUBMITTED_BALLOTS_DIR = "submitted_ballots"; // encrypted
  static final String SPOILED_BALLOTS_DIR = "spoiled_ballots"; // plaintext

  static final String MANIFEST_FILE_NAME = "manifest" + SUFFIX;
  static final String CONTEXT_FILE_NAME = "context" + SUFFIX;
  static final String CONSTANTS_FILE_NAME = "constants" + SUFFIX;
  static final String COEFFICIENTS_FILE_NAME = "coefficients" + SUFFIX;
  static final String ENCRYPTED_TALLY_FILE_NAME = "encrypted_tally" + SUFFIX;
  static final String TALLY_FILE_NAME = "tally" + SUFFIX;

  static final String DEVICE_PREFIX = "device_";
  static final String GUARDIAN_PREFIX = "guardian_";
  static final String SPOILED_BALLOT_PREFIX = "spoiled_ballot_";
  static final String SUBMITTED_BALLOT_PREFIX = "submitted_ballot_";

  static final String AVAILABLE_GUARDIAN_PREFIX = "available_guardian_";

  // JSON Private
  static final String PRIVATE_DATA_DIR = "election_private_data";
  static final String PRIVATE_GUARDIANS_DIR = "private_guardians";
  static final String PRIVATE_BALLOT_DIR = "plaintext_ballots";
  static final String PLAINTEXT_BALLOT_PREFIX = "plaintext_ballot_";
  static final String CIPHERTEXT_BALLOT_PREFIX = "ciphertext_ballot_";
  static final String PRIVATE_GUARDIAN_PREFIX = "private_guardian_";

  //// proto
  static final String PROTO_SUFFIX = ".protobuf";
  static final String ELECTION_RECORD_FILE_NAME = "electionRecord" + PROTO_SUFFIX;
  static final String GUARDIANS_FILE = "guardians" + PROTO_SUFFIX;
  static final String SUBMITTED_BALLOT_PROTO = "submittedBallot" + PROTO_SUFFIX;
  static final String SPOILED_BALLOT_FILE = "spoiledBallotTally" + PROTO_SUFFIX;
  static final String TRUSTEES_FILE = "trustees" + PROTO_SUFFIX;

  private final Path publishDirectory;
  private final Path devicesDirPath;
  private final Path ballotsDirPath;
  private final Path spoiledBallotDirPath;
  private final Path guardianDirPath;
  private final Path privateDirPath;

  public Publisher(String where, boolean removeAllFiles, boolean createDirs) throws IOException {
    this.publishDirectory = Path.of(where);
    this.devicesDirPath = publishDirectory.resolve(DEVICES_DIR);
    this.ballotsDirPath = publishDirectory.resolve(SUBMITTED_BALLOTS_DIR);
    this.guardianDirPath = publishDirectory.resolve(GUARDIANS_DIR);
    this.spoiledBallotDirPath = publishDirectory.resolve(SPOILED_BALLOTS_DIR);
    this.privateDirPath = publishDirectory.resolve(PRIVATE_DATA_DIR);
    // this.availableGuardianDirPath = publishDirectory.resolve(AVAILABLE_GUARDIANS_DIR);

    if (removeAllFiles) {
      removeAllFiles();
    }
    if (createDirs) {
      createDirs();
    }
  }

  /** Make sure output dir exists and is writeable. */
  public boolean validateOutputDir(Formatter error) {
    if (!Files.exists(publishDirectory)) {
      error.format(" Output directory '%s' does not exist%n", publishDirectory);
      return false;
    }
    if (!Files.isDirectory(publishDirectory)) {
      error.format(" Output directory '%s' is not a directory%n", publishDirectory);
      return false;
    }
    if (!Files.isWritable(publishDirectory)) {
      error.format(" Output directory '%s' is not writeable%n", publishDirectory);
      return false;
    }
    if (!Files.isExecutable(publishDirectory)) {
      error.format(" Output directory '%s' is not executable%n", publishDirectory);
      return false;
    }
    return true;
  }

  private void createDirs() throws IOException {
    Files.createDirectories(devicesDirPath);
    Files.createDirectories(guardianDirPath);
    Files.createDirectories(ballotsDirPath);
    Files.createDirectories(spoiledBallotDirPath);
  }

  /** Delete everything in the output directory, but leave that directory. */
  private void removeAllFiles() throws IOException {
    String filename = publishDirectory.getFileName().toString();
    if (!(filename.startsWith("publish") || filename.startsWith("encryptor") || filename.startsWith("decryptor"))) {
      throw new RuntimeException(String.format("Publish directory '%s' should start with 'publish'", filename));
    }
    Files.walk(publishDirectory)
            .filter(p -> !p.equals(publishDirectory))
            .map(Path::toFile)
            .sorted((o1, o2) -> -o1.compareTo(o2))
            .forEach( f-> f.delete());
  }

  public Path manifestPath() {
    return publishDirectory.resolve(MANIFEST_FILE_NAME).toAbsolutePath();
  }

  public Path contextPath() {
    return publishDirectory.resolve(CONTEXT_FILE_NAME).toAbsolutePath();
  }

  public Path constantsPath() {
    return publishDirectory.resolve(CONSTANTS_FILE_NAME).toAbsolutePath();
  }

  public Path coefficientsPath() {
    return publishDirectory.resolve(COEFFICIENTS_FILE_NAME).toAbsolutePath();
  }

  public Path publishPath() {
    return publishDirectory.toAbsolutePath();
  }

  public Path privateDirPath() {
    return privateDirPath.toAbsolutePath();
  }

  public Path tallyPath() {
    return publishDirectory.resolve(TALLY_FILE_NAME).toAbsolutePath();
  }

  public Path encryptedTallyPath() {
    return publishDirectory.resolve(ENCRYPTED_TALLY_FILE_NAME).toAbsolutePath();
  }

  public Path devicePath(String id) {
    return devicesDirPath.resolve(DEVICE_PREFIX + id + SUFFIX);
  }

  public Path trusteesPath() {
    return privateDirPath.resolve(TRUSTEES_FILE);
  }

  public Path privateGuardiansPath() {
    return privateDirPath.resolve(PRIVATE_GUARDIANS_DIR);
  }

  public Path privateBallotsPath() {
    return privateDirPath.resolve(PRIVATE_BALLOT_DIR);
  }

  public File[] deviceFiles() {
    if (!Files.exists(devicesDirPath) || !Files.isDirectory(devicesDirPath)) {
      return new File[0];
    }
    return devicesDirPath.toFile().listFiles();
  }

  public Path guardianRecordsPath(String id) {
    String fileName = GUARDIAN_PREFIX + id + SUFFIX;
    return guardianDirPath.resolve(fileName);
  }

  public File[] guardianRecordsFiles() {
    if (!Files.exists(guardianDirPath) || !Files.isDirectory(guardianDirPath)) {
      return new File[0];
    }
    return guardianDirPath.toFile().listFiles();
  }

  public Path guardiansPrivatePath(String id) {
    String fileName = id + SUFFIX;
    return privateDirPath.resolve(fileName);
  }

  public File[] guardianRecordPrivateFiles() {
    if (!Files.exists(privateGuardiansPath()) || !Files.isDirectory(privateGuardiansPath())) {
      return new File[0];
    }
    return privateGuardiansPath().toFile().listFiles();
  }

  public Path ballotPath(String id) {
    String fileName = SUBMITTED_BALLOT_PREFIX + id + SUFFIX;
    return ballotsDirPath.resolve(fileName);
  }

  public File[] ballotFiles() {
    if (!Files.exists(ballotsDirPath) || !Files.isDirectory(ballotsDirPath)) {
      return new File[0];
    }
    return ballotsDirPath.toFile().listFiles();
  }

  public Path spoiledBallotPath(String id) {
    String fileName = SPOILED_BALLOT_PREFIX + id + SUFFIX;
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
    return publishDirectory.resolve(ELECTION_RECORD_FILE_NAME).toAbsolutePath();
  }

  public Path ciphertextBallotProtoPath() {
    return publishDirectory.resolve(SUBMITTED_BALLOT_PROTO).toAbsolutePath();
  }

  public Path spoiledBallotProtoPath() {
    return publishDirectory.resolve(SPOILED_BALLOT_FILE).toAbsolutePath();
  }

  //////////////////////////////////////////////////////////////////
  // Json

  public void writeKeyCeremonyJson(
          Manifest manifest,
          CiphertextElectionContext context,
          ElectionConstants constants,
          Iterable<GuardianRecord> guardianRecords) throws IOException {

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

    ConvertToJson.writeElection(manifest, this.manifestPath());
    ConvertToJson.writeContext(context, this.contextPath());
    ConvertToJson.writeConstants(constants, this.constantsPath());

    for (Encrypt.EncryptionDevice device : devices) {
      ConvertToJson.writeDevice(device, this.devicePath(device.location));
    }

    for (SubmittedBallot ballot : ciphertext_ballots) {
      ConvertToJson.writeSubmittedBallot(ballot, this.ballotPath(ballot.object_id));
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

  public void writeTrusteesProto(TrusteeProto.DecryptingTrustees trusteesProto) throws IOException {
    Files.createDirectories(privateDirPath);

    try (FileOutputStream out = new FileOutputStream(trusteesPath().toFile())) {
      trusteesProto.writeDelimitedTo(out);
    }
  }


  public static void overwriteTrusteeProto(String outputDir, TrusteeProto.DecryptingTrustee trusteeProto) throws IOException {
    Path outputPath = Path.of(outputDir + "/" + trusteeProto.getGuardianId() + PROTO_SUFFIX);
    if (Files.exists(outputPath)) {
      Files.delete(outputPath);
    }
    try (FileOutputStream out = new FileOutputStream(outputPath.toFile())) {
      trusteeProto.writeDelimitedTo(out);
    }
  }

  /** Publishes the KeyCeremony part of the election record as proto. */
  public void writeKeyCeremonyProto(
          Manifest description,
          CiphertextElectionContext context,
          ElectionConstants constants,
          Iterable<GuardianRecord> guardianRecords) throws IOException {

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


    // the accepted ballots are written into their own file
    try (FileOutputStream out = new FileOutputStream(ciphertextBallotProtoPath().toFile())) {
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


    // the accepted ballots are written into their own file
    try (FileOutputStream out = new FileOutputStream(ciphertextBallotProtoPath().toFile())) {
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
    Path source = new Publisher(inputDir, false, false).ciphertextBallotProtoPath();
    Path dest = ciphertextBallotProtoPath();
    Files.copy(source, dest, StandardCopyOption.COPY_ATTRIBUTES);
  }

  ////////////////////////////////////////////////////////////////////////////////
  // private, probably not needed

  public void writeGuardiansJson(Iterable<GuardianRecordPrivate> guardianRecords) throws IOException {
    Files.createDirectories(privateDirPath);
    for (GuardianRecordPrivate guardianRecord : guardianRecords) {
      ConvertToJson.writeGuardianRecordPrivate(guardianRecord, this.guardiansPrivatePath(guardianRecord.guardian_id()));
    }
  }

  /**
   * Publish the private data for an election.
   * Useful for generating sample data sets.
   * Do not use this in a production application.
   */
  public void publish_private_data(
          @Nullable Iterable<PlaintextBallot> original_ballots,
          @Nullable Iterable<GuardianRecordPrivate> guardians) throws IOException {

    Files.createDirectories(privateDirPath);

    if (guardians != null) {
      Files.createDirectories(privateGuardiansPath());
      for (GuardianRecordPrivate guardian : guardians) {
        String guardian_name = PRIVATE_GUARDIAN_PREFIX + guardian.guardian_id() + SUFFIX;
        ConvertToJson.writeGuardianRecordPrivate(guardian, privateGuardiansPath().resolve(guardian_name));
      }
    }

    if (original_ballots != null) {
      Files.createDirectories(privateBallotsPath());
      for (PlaintextBallot plaintext_ballot : original_ballots) {
        String ballot_name = PLAINTEXT_BALLOT_PREFIX + plaintext_ballot.object_id + SUFFIX;
        ConvertToJson.writePlaintextBallot(plaintext_ballot, privateBallotsPath().resolve(ballot_name));
      }
    }

    // TODO CIPHERTEXT_BALLOT_PREFIX?
  }

  // These are input ballots that did not validate (not spoiled ballots). put them somewhere to be examined later.
  public void publish_invalid_ballots(String directory, Iterable<PlaintextBallot> invalid_ballots) throws IOException {
    Path invalidBallotsPath = publishDirectory.resolve(directory);
    Files.createDirectories(invalidBallotsPath);

    for (PlaintextBallot plaintext_ballot : invalid_ballots) {
      String ballot_name = PLAINTEXT_BALLOT_PREFIX + plaintext_ballot.object_id + SUFFIX;
      ConvertToJson.writePlaintextBallot(plaintext_ballot, invalidBallotsPath.resolve(ballot_name));
    }

  }

}
