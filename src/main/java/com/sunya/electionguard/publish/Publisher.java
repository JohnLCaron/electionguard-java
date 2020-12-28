package com.sunya.electionguard.publish;

import com.sunya.electionguard.*;

import javax.annotation.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static com.sunya.electionguard.Election.*;

public class Publisher {
  static final String DEVICES_DIR = "devices";
  static final String COEFFICIENTS_DIR = "coefficients";
  static final String BALLOTS_DIR = "encrypted_ballots";
  static final String SPOILED_DIR = "spoiled_ballots";
  static final String PRIVATE_DIR = "private";
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

  // TODO #148 Revert PlaintextTally to PublishedPlaintextTally after moving spoiled info

  private final Path publishDirectory;
  private final Path devicesDirPath;
  private final Path coefficientsDirPath;
  private final Path ballotsDirPath;
  private final Path spoiledDirPath;

  public Publisher(String where) throws IOException {
    this.publishDirectory = Path.of(where);
    this.devicesDirPath = publishDirectory.resolve(DEVICES_DIR);
    this.coefficientsDirPath = publishDirectory.resolve(COEFFICIENTS_DIR);
    this.ballotsDirPath = publishDirectory.resolve(BALLOTS_DIR);
    this.spoiledDirPath = publishDirectory.resolve(SPOILED_DIR);

    Files.createDirectories(spoiledDirPath);
    Files.createDirectories(ballotsDirPath);
    Files.createDirectories(coefficientsDirPath);
    Files.createDirectories(devicesDirPath);
  }

  public Path descriptionFile() {
    return publishDirectory.resolve(DESCRIPTION_FILE_NAME).toAbsolutePath();
  }

  public Path contextFile() {
    return publishDirectory.resolve(CONTEXT_FILE_NAME).toAbsolutePath();
  }

  public Path constantsFile() {
    return publishDirectory.resolve(CONSTANTS_FILE_NAME).toAbsolutePath();
  }

  public Path tallyFile() {
    return publishDirectory.resolve(TALLY_FILE_NAME).toAbsolutePath();
  }

  public Path encryptedTallyFile() {
    return publishDirectory.resolve(ENCRYPTED_TALLY_FILE_NAME).toAbsolutePath();
  }

  public Path deviceFile(String id) {
    String fileName = DEVICE_PREFIX + id + SUFFIX;
    return devicesDirPath.resolve(fileName);
  }

  public Path coefficientsFile(String id) {
    String fileName = COEFFICIENT_PREFIX + id + SUFFIX;
    return coefficientsDirPath.resolve(fileName);
  }

  public Path ballotFile(String id) {
    String fileName = BALLOT_PREFIX + id + SUFFIX;
    return ballotsDirPath.resolve(fileName);
  }

  public Path spoiledFile(String id) {
    String fileName = BALLOT_PREFIX + id + SUFFIX;
    return spoiledDirPath.resolve(fileName);
  }


  /** Publishes the election record as json. */
  public void write(
          ElectionDescription description,
          CiphertextElectionContext context,
          ElectionConstants constants,
          Iterable<Encrypt.EncryptionDevice> devices,
          Iterable<Ballot.CiphertextAcceptedBallot> ciphertext_ballots,
          Iterable<Ballot.CiphertextAcceptedBallot> spoiled_ballots,
          Tally.PublishedCiphertextTally ciphertext_tally,
          Tally.PlaintextTally plaintext_tally,
          @Nullable Iterable<KeyCeremony.CoefficientValidationSet> coefficient_validation_sets) throws IOException {

    ElectionDescriptionToJson writer = new ElectionDescriptionToJson(descriptionFile().toString());
    writer.write(description);

    ConvertToJson.write(context, this.contextFile());
    ConvertToJson.write(constants, this.constantsFile());

    for (Encrypt.EncryptionDevice device : devices) {
      ConvertToJson.write(device, this.deviceFile(device.uuid));
    }

    if (coefficient_validation_sets != null) {
      for (KeyCeremony.CoefficientValidationSet coefficient_validation_set : coefficient_validation_sets) {
        ConvertToJson.write(coefficient_validation_set, this.coefficientsFile(coefficient_validation_set.owner_id()));
      }
    }

    for (Ballot.CiphertextAcceptedBallot ballot : ciphertext_ballots) {
      ConvertToJson.write(ballot, this.ballotFile(ballot.object_id));
    }

    for (Ballot.CiphertextAcceptedBallot ballot : spoiled_ballots) {
      ConvertToJson.write(ballot, this.spoiledFile(ballot.object_id));
    }

    ConvertToJson.write(ciphertext_tally, encryptedTallyFile());
    ConvertToJson.write(plaintext_tally, tallyFile());
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
}
