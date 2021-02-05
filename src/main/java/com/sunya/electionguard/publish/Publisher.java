package com.sunya.electionguard.publish;

import com.sunya.electionguard.*;
import com.sunya.electionguard.proto.CiphertextBallotProto;
import com.sunya.electionguard.proto.CiphertextBallotToProto;
import com.sunya.electionguard.proto.ElectionRecordProto;
import com.sunya.electionguard.proto.ElectionRecordToProto;
import com.sunya.electionguard.proto.KeyCeremonyProto;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import static com.sunya.electionguard.Election.*;

/** Publishes the Election Record to Json or proto files. */
public class Publisher {
  static final String PRIVATE_DIR = "private";

  // json
  static final String DEVICES_DIR = "devices";
  static final String COEFFICIENTS_DIR = "coefficients";
  static final String BALLOTS_DIR = "encrypted_ballots";
  static final String SPOILED_DIR = "spoiled_ballots";
  static final String PLAINTEXT_BALLOTS_DIR = "plaintext";
  static final String ENCRYPTED_BALLOTS_DIR = "encrypted";

  static final String SUFFIX = ".json";
  static final String DESCRIPTION_FILE_NAME = "description" + SUFFIX;
  static final String CONTEXT_FILE_NAME = "context" + SUFFIX;
  static final String CONSTANTS_FILE_NAME = "constants" + SUFFIX;
  static final String ENCRYPTED_TALLY_FILE_NAME = "encrypted_tally" + SUFFIX;
  static final String TALLY_FILE_NAME = "tally" + SUFFIX;

  static final String DEVICE_PREFIX = "device_";
  static final String COEFFICIENT_PREFIX = "coefficient_validation_set_";
  static final String BALLOT_PREFIX = "ballot_";
  static final String PLAINTEXT_BALLOT_PREFIX = "plaintext_ballot_";
  static final String GUARDIAN_PREFIX = "guardian_";

  // proto
  static final String ELECTION_RECORD_FILE_NAME = "electionRecord.proto";
  static final String GUARDIANS_FILE = "guardians.proto";
  static final String CIPHERTEXT_BALLOT_FILE = "ciphertextAcceptedBallot.proto";

  // TODO #148 Revert PlaintextTally to PublishedPlaintextTally after moving spoiled info

  private final Path publishDirectory;
  private final Path devicesDirPath;
  private final Path coefficientsDirPath;
  private final Path ballotsDirPath;
  private final Path spoiledDirPath;
  private final Path privateDirPath;

  public Publisher(String where, boolean removeAllFiles, boolean createDirs) throws IOException {
    this.publishDirectory = Path.of(where);
    this.devicesDirPath = publishDirectory.resolve(DEVICES_DIR);
    this.coefficientsDirPath = publishDirectory.resolve(COEFFICIENTS_DIR);
    this.ballotsDirPath = publishDirectory.resolve(BALLOTS_DIR);
    this.spoiledDirPath = publishDirectory.resolve(SPOILED_DIR);
    this.privateDirPath = publishDirectory.resolve(PRIVATE_DIR);

    if (removeAllFiles) {
      removeAllFiles();
    }
    if (createDirs) {
      createDirs();
    }
  }

  private void createDirs() throws IOException {
    Files.createDirectories(spoiledDirPath);
    Files.createDirectories(ballotsDirPath);
    Files.createDirectories(coefficientsDirPath);
    Files.createDirectories(devicesDirPath);
  }

  /** Delete everything in the output directory, but leave that directory. */
  private void removeAllFiles() throws IOException {
    String filename = publishDirectory.getFileName().toString();
    if (!filename.startsWith("publish")) {
      throw new RuntimeException(String.format("Publish directory '%s' should start with 'publish'", filename));
    }
    Files.walk(publishDirectory)
            .filter(p -> !p.equals(publishDirectory))
            .map(Path::toFile)
            .sorted((o1, o2) -> -o1.compareTo(o2))
            .forEach( f-> f.delete());
  }

  public Path electionPath() {
    return publishDirectory.resolve(DESCRIPTION_FILE_NAME).toAbsolutePath();
  }

  public Path contextPath() {
    return publishDirectory.resolve(CONTEXT_FILE_NAME).toAbsolutePath();
  }

  public Path constantsPath() {
    return publishDirectory.resolve(CONSTANTS_FILE_NAME).toAbsolutePath();
  }

  public Path tallyPath() {
    return publishDirectory.resolve(TALLY_FILE_NAME).toAbsolutePath();
  }

  public Path encryptedTallyPath() {
    return publishDirectory.resolve(ENCRYPTED_TALLY_FILE_NAME).toAbsolutePath();
  }

  public Path devicePath(String id) {
    String fileName = DEVICE_PREFIX + id + SUFFIX;
    return devicesDirPath.resolve(fileName);
  }

  public Path guardiansPath() {
    return privateDirPath.resolve(GUARDIANS_FILE);
  }

  public File[] deviceFiles() {
    return devicesDirPath.toFile().listFiles();
  }

  public Path coefficientsPath(String id) {
    String fileName = COEFFICIENT_PREFIX + id + SUFFIX;
    return coefficientsDirPath.resolve(fileName);
  }

  public File[] coefficientsFiles() {
    return coefficientsDirPath.toFile().listFiles();
  }

  public Path ballotPath(String id) {
    String fileName = BALLOT_PREFIX + id + SUFFIX;
    return ballotsDirPath.resolve(fileName);
  }

  public File[] ballotFiles() {
    return ballotsDirPath.toFile().listFiles();
  }

  ///

  public Path electionRecordProtoPath() {
    return publishDirectory.resolve(ELECTION_RECORD_FILE_NAME).toAbsolutePath();
  }

  public Path ciphertextBallotProtoPath() {
    return publishDirectory.resolve(CIPHERTEXT_BALLOT_FILE).toAbsolutePath();
  }

  //////////////////////////////////////////////////////////////////
  // Json

  /** Publishes the election record as json. */
  public void writeElectionRecordJson(
          ElectionDescription description,
          CiphertextElectionContext context,
          ElectionConstants constants,
          Iterable<Encrypt.EncryptionDevice> devices,
          Iterable<Ballot.CiphertextAcceptedBallot> ciphertext_ballots,
          PublishedCiphertextTally ciphertext_tally,
          PlaintextTally decryptedTally,
          Iterable<KeyCeremony.CoefficientValidationSet> coefficient_validation_sets) throws IOException {

    ConvertToJson.write(description, this.electionPath());
    ConvertToJson.write(context, this.contextPath());
    ConvertToJson.write(constants, this.constantsPath());

    for (Encrypt.EncryptionDevice device : devices) {
      ConvertToJson.write(device, this.devicePath(device.uuid));
    }

    if (coefficient_validation_sets != null) {
      for (KeyCeremony.CoefficientValidationSet coefficient_validation_set : coefficient_validation_sets) {
        ConvertToJson.write(coefficient_validation_set, this.coefficientsPath(coefficient_validation_set.owner_id()));
      }
    }

    for (Ballot.CiphertextAcceptedBallot ballot : ciphertext_ballots) {
      ConvertToJson.write(ballot, this.ballotPath(ballot.object_id));
    }

    ConvertToJson.write(ciphertext_tally, encryptedTallyPath());
    ConvertToJson.write(decryptedTally, tallyPath());
  }

  /**
   * Publish the private data for an election.
   * Useful for generating sample data sets.
   * Do not use this in a production application.
   */
  void publish_private_data(
          Iterable<Ballot.PlaintextBallot> plaintext_ballots,
          Iterable<Ballot.CiphertextBallot> ciphertext_ballots,
          Iterable<Guardian> guardians) throws IOException {

    Path privateDirPath = publishDirectory.resolve(PRIVATE_DIR);
    Files.createDirectories(privateDirPath);

    for (Guardian guardian : guardians){
      String guardian_name = GUARDIAN_PREFIX + guardian.object_id;
      ConvertToJson.write(guardian, privateDirPath.resolve(guardian_name));
    }

    Path ballotsDirPath = privateDirPath.resolve(PLAINTEXT_BALLOTS_DIR);
    Files.createDirectories(ballotsDirPath);
    for (Ballot.PlaintextBallot plaintext_ballot : plaintext_ballots) {
      String ballot_name = PLAINTEXT_BALLOT_PREFIX + plaintext_ballot.object_id + SUFFIX;
      ConvertToJson.write(plaintext_ballot, ballotsDirPath.resolve(ballot_name));
    }

    Path encryptedDirPath = privateDirPath.resolve(ENCRYPTED_BALLOTS_DIR);
    Files.createDirectories(encryptedDirPath);
    for (Ballot.CiphertextBallot ciphertext_ballot : ciphertext_ballots) {
      String ballot_name = BALLOT_PREFIX + ciphertext_ballot.object_id + SUFFIX;
      ConvertToJson.write(ciphertext_ballot, encryptedDirPath.resolve(ballot_name));
    }
  }

  public void publishGuardiansProto(KeyCeremonyProto.Guardians guardiansProto) throws IOException {
    Path privateDirPath = publishDirectory.resolve(PRIVATE_DIR);
    Files.createDirectories(privateDirPath);

    try (FileOutputStream out = new FileOutputStream(guardiansPath().toFile())) {
      guardiansProto.writeDelimitedTo(out);
    }
  }

  //////////////////////////////////////////////////////////////////
  // Proto

  /** Publishes the KeyCeremony part of the election record as proto. */
  public void writeKeyCeremonyProto(
          ElectionDescription description,
          CiphertextElectionContext context,
          ElectionConstants constants,
          Iterable<KeyCeremony.CoefficientValidationSet> coefficient_validation_sets) throws IOException {

    ElectionRecordProto.ElectionRecord ElectionRecordProto = ElectionRecordToProto.buildElectionRecord(
            description, context, constants, coefficient_validation_sets,
            null, null, null);
    try (FileOutputStream out = new FileOutputStream(electionRecordProtoPath().toFile())) {
      ElectionRecordProto.writeDelimitedTo(out);
    }
  }

  /** Publishes the election record as proto. */
  public void writeEncryptionResultsProto(
          ElectionDescription description,
          CiphertextElectionContext context,
          ElectionConstants constants,
          Iterable<KeyCeremony.CoefficientValidationSet> coefficient_validation_sets,
          Iterable<Encrypt.EncryptionDevice> devices,
          Iterable<Ballot.CiphertextAcceptedBallot> accepted_ballots) throws IOException {


    // the ballots are written into their own file
    try (FileOutputStream out = new FileOutputStream(ciphertextBallotProtoPath().toFile())) {
      for (Ballot.CiphertextAcceptedBallot ballot : accepted_ballots) {
        CiphertextBallotProto.CiphertextAcceptedBallot ballotProto = CiphertextBallotToProto.translateToProto(ballot);
        ballotProto.writeDelimitedTo(out);
      }
    }

    ElectionRecordProto.ElectionRecord electionRecordProto = ElectionRecordToProto.buildElectionRecord(
            description, context, constants, coefficient_validation_sets,
            devices, null, null);

    try (FileOutputStream out = new FileOutputStream(electionRecordProtoPath().toFile())) {
      electionRecordProto.writeDelimitedTo(out);
    }
  }

  /** Publishes the election record as proto. */
  public void writeElectionRecordProto(
          ElectionDescription description,
          CiphertextElectionContext context,
          ElectionConstants constants,
          Iterable<Encrypt.EncryptionDevice> devices,
          Iterable<KeyCeremony.CoefficientValidationSet> coefficient_validation_sets,
          PublishedCiphertextTally ciphertext_tally,
          PlaintextTally decryptedTally) throws IOException {

    ElectionRecordProto.ElectionRecord ElectionRecordProto = ElectionRecordToProto.buildElectionRecord(
            description, context, constants, coefficient_validation_sets,
            devices,
            ciphertext_tally,
            decryptedTally);

    try (FileOutputStream out = new FileOutputStream(electionRecordProtoPath().toFile())) {
      ElectionRecordProto.writeDelimitedTo(out);
    }
  }

  public void copyAcceptedBallots(String inputDir) throws IOException {
    Path source = new Publisher(inputDir, false, false).ciphertextBallotProtoPath();
    Path dest = ciphertextBallotProtoPath();
    Files.copy(source, dest, StandardCopyOption.COPY_ATTRIBUTES);
  }

}
