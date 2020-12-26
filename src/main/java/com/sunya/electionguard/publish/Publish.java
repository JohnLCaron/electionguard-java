package com.sunya.electionguard.publish;

import com.sunya.electionguard.*;

import javax.annotation.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static com.sunya.electionguard.Election.*;

public class Publish {
  static final String DEVICES_DIR = "devices";
  private static final String COEFFICIENTS_DIR = "coefficients";
  private static final String BALLOTS_DIR = "encrypted_ballots";
  private static final String SPOILED_DIR = "spoiled_ballots";
  static final String PRIVATE_DIR = "private";
  static final String PLAINTEXT_BALLOTS_DIR = "plaintext";
  static final String ENCRYPTED_BALLOTS_DIR = "encrypted";

  private static final String DESCRIPTION_FILE_NAME = "description";
  private static final String CONTEXT_FILE_NAME = "context";
  private static final String CONSTANTS_FILE_NAME = "constants";
  private static final String ENCRYPTED_TALLY_FILE_NAME = "encrypted_tally";
  private static final String TALLY_FILE_NAME = "tally";

  private static final String DEVICE_PREFIX = "device_";
  private static final String COEFFICIENT_PREFIX = "coefficient_validation_set_";
  private static final String BALLOT_PREFIX = "ballot_";

  private static final String PLAINTEXT_BALLOT_PREFIX = "plaintext_ballot_";
  private static final String GUARDIAN_PREFIX = "guardian_";
  private static final String SUFFIX = ".json";

  // TODO #148 Revert PlaintextTally to PublishedPlaintextTally after moving spoiled info

  /**
   * Publishes the election record as json.
   */
  public static void publish(
          ElectionDescription description,
          CiphertextElectionContext context,
          ElectionConstants constants,
          Iterable<Encrypt.EncryptionDevice> devices,
          Iterable<Ballot.CiphertextAcceptedBallot> ciphertext_ballots,
          Iterable<Ballot.CiphertextAcceptedBallot> spoiled_ballots,
          Tally.PublishedCiphertextTally ciphertext_tally,
          Tally.PlaintextTally plaintext_tally,
          @Nullable Iterable<KeyCeremony.CoefficientValidationSet> coefficient_validation_sets,
          String results_directory) throws IOException {

    Path resultsDirPath = Path.of(results_directory);
    ConvertToJson.write(description, resultsDirPath.resolve(DESCRIPTION_FILE_NAME));
    ConvertToJson.write(context, resultsDirPath.resolve(CONTEXT_FILE_NAME));
    ConvertToJson.write(constants, resultsDirPath.resolve(CONSTANTS_FILE_NAME));

    Path devicesDirPath = Path.of(results_directory, DEVICES_DIR);
    Files.createDirectories(devicesDirPath);
    for (Encrypt.EncryptionDevice device : devices) {
      String deviceName = DEVICE_PREFIX + device.uuid;
      ConvertToJson.write(device, devicesDirPath.resolve(deviceName));
    }

    Path coefficientsDirPath = Path.of(results_directory, COEFFICIENTS_DIR);
    Files.createDirectories(coefficientsDirPath);
    if (coefficient_validation_sets != null) {
      for (KeyCeremony.CoefficientValidationSet coefficient_validation_set : coefficient_validation_sets) {
        String coeffName = COEFFICIENT_PREFIX + coefficient_validation_set.owner_id();
        ConvertToJson.write(coefficient_validation_set, coefficientsDirPath.resolve(coeffName));
      }
    }

    Path ballotsDirPath = Path.of(results_directory, BALLOTS_DIR);
    Files.createDirectories(ballotsDirPath);
    for (Ballot.CiphertextAcceptedBallot ballot : ciphertext_ballots) {
      String ballot_name = BALLOT_PREFIX + ballot.object_id;
      ConvertToJson.write(ballot, ballotsDirPath.resolve(ballot_name));
    }

    Path spoiledDirPath = Path.of(results_directory, SPOILED_DIR);
    Files.createDirectories(spoiledDirPath);
    for (Ballot.CiphertextAcceptedBallot ballot : spoiled_ballots) {
      String ballot_name = BALLOT_PREFIX + ballot.object_id;
      ConvertToJson.write(ballot, spoiledDirPath.resolve(ballot_name));
    }

    ConvertToJson.write(ciphertext_tally, resultsDirPath.resolve(ENCRYPTED_TALLY_FILE_NAME));
    ConvertToJson.write(plaintext_tally, resultsDirPath.resolve(TALLY_FILE_NAME));
  }

  /**
   * Publish the private data for an election.
   * Useful for generating sample data sets.
   * Do not use this in a production application.
   */
  static void publish_private_data(
          Iterable<Ballot.PlaintextBallot> plaintext_ballots,
          Iterable<Ballot.CiphertextBallot> ciphertext_ballots,
          Iterable<Guardian> guardians,
          String results_directory) throws IOException {

    Path privateDirPath = Path.of(results_directory, PRIVATE_DIR);
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
