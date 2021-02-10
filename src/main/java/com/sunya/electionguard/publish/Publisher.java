package com.sunya.electionguard.publish;

import com.sunya.electionguard.*;
import com.sunya.electionguard.proto.CiphertextBallotProto;
import com.sunya.electionguard.proto.CiphertextBallotToProto;
import com.sunya.electionguard.proto.ElectionRecordProto;
import com.sunya.electionguard.proto.ElectionRecordToProto;
import com.sunya.electionguard.proto.KeyCeremonyProto;
import com.sunya.electionguard.proto.PlaintextBallotProto;
import com.sunya.electionguard.proto.PlaintextBallotToProto;
import com.sunya.electionguard.proto.PlaintextTallyProto;
import com.sunya.electionguard.proto.PlaintextTallyToProto;

import javax.annotation.Nullable;
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
  static final String SUFFIX = ".json";

  static final String DEVICES_DIR = "devices";
  static final String COEFFICIENTS_DIR = "coefficients";
  static final String BALLOTS_DIR = "encrypted_ballots";
  static final String SPOILED_BALLOT_DIR = "spoiled_ballots";
  static final String SPOILED_TALLY_DIR = "spoiled_tallies";

  static final String DESCRIPTION_FILE_NAME = "description" + SUFFIX;
  static final String CONTEXT_FILE_NAME = "context" + SUFFIX;
  static final String CONSTANTS_FILE_NAME = "constants" + SUFFIX;
  static final String ENCRYPTED_TALLY_FILE_NAME = "encrypted_tally" + SUFFIX;
  static final String TALLY_FILE_NAME = "tally" + SUFFIX;

  static final String DEVICE_PREFIX = "device_";
  static final String COEFFICIENT_PREFIX = "coefficient_validation_set_";
  static final String BALLOT_PREFIX = "ballot_";

  // json private
  static final String PRIVATE_PLAINTEXT_BALLOTS_DIR = "plaintext";
  static final String PRIVATE_ENCRYPTED_BALLOTS_DIR = "encrypted";
  static final String PLAINTEXT_BALLOT_PREFIX = "plaintext_ballot_";
  static final String GUARDIAN_PREFIX = "guardian_";

  // proto
  static final String ELECTION_RECORD_FILE_NAME = "electionRecord.proto";
  static final String GUARDIANS_FILE = "guardians.proto";
  static final String CIPHERTEXT_BALLOT_FILE = "ciphertextAcceptedBallot.proto";
  static final String SPOILED_BALLOT_FILE = "spoiledPlaintextBallot.proto";
  static final String SPOILED_TALLY_FILE = "spoiledPlaintextTally.proto";

  // TODO #148 Revert PlaintextTally to PublishedPlaintextTally after moving spoiled info

  private final Path publishDirectory;
  private final Path devicesDirPath;
  private final Path coefficientsDirPath;
  private final Path ballotsDirPath;
  private final Path spoiledBallotDirPath;
  private final Path spoiledTallyDirPath;
  private final Path privateDirPath;

  public Publisher(String where, boolean removeAllFiles, boolean createDirs) throws IOException {
    this.publishDirectory = Path.of(where);
    this.devicesDirPath = publishDirectory.resolve(DEVICES_DIR);
    this.coefficientsDirPath = publishDirectory.resolve(COEFFICIENTS_DIR);
    this.ballotsDirPath = publishDirectory.resolve(BALLOTS_DIR);
    this.spoiledBallotDirPath = publishDirectory.resolve(SPOILED_BALLOT_DIR);
    this.spoiledTallyDirPath = publishDirectory.resolve(SPOILED_TALLY_DIR);
    this.privateDirPath = publishDirectory.resolve(PRIVATE_DIR);

    if (removeAllFiles) {
      removeAllFiles();
    }
    if (createDirs) {
      createDirs();
    }
  }

  private void createDirs() throws IOException {
    Files.createDirectories(spoiledTallyDirPath);
    Files.createDirectories(spoiledBallotDirPath);
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
    String fileName = DEVICE_PREFIX + id + SUFFIX;
    return devicesDirPath.resolve(fileName);
  }

  public Path guardiansPath() {
    return privateDirPath.resolve(GUARDIANS_FILE);
  }

  public File[] deviceFiles() {
    if (!Files.exists(devicesDirPath)) {
      return new File[0];
    }
    return devicesDirPath.toFile().listFiles();
  }

  public Path coefficientsPath(String id) {
    String fileName = COEFFICIENT_PREFIX + id + SUFFIX;
    return coefficientsDirPath.resolve(fileName);
  }

  public File[] coefficientsFiles() {
    if (!Files.exists(coefficientsDirPath)) {
      return new File[0];
    }
    return coefficientsDirPath.toFile().listFiles();
  }

  public Path ballotPath(String id) {
    String fileName = BALLOT_PREFIX + id + SUFFIX;
    return ballotsDirPath.resolve(fileName);
  }

  public File[] ballotFiles() {
    if (!Files.exists(ballotsDirPath)) {
      return new File[0];
    }
    return ballotsDirPath.toFile().listFiles();
  }

  public Path spoiledBallotPath(String id) {
    String fileName = BALLOT_PREFIX + id + SUFFIX;
    return spoiledBallotDirPath.resolve(fileName);
  }

  public File[] spoiledBallotFiles() {
    if (!Files.exists(spoiledBallotDirPath)) {
      return new File[0];
    }
    return spoiledBallotDirPath.toFile().listFiles();
  }

  public Path spoiledTallyPath(String id) {
    String fileName = BALLOT_PREFIX + id + SUFFIX;
    return spoiledTallyDirPath.resolve(fileName);
  }

  public File[] spoiledTallyFiles() {
    if (!Files.exists(spoiledTallyDirPath)) {
      return new File[0];
    }
    return spoiledTallyDirPath.toFile().listFiles();
  }

  ///

  public Path electionRecordProtoPath() {
    return publishDirectory.resolve(ELECTION_RECORD_FILE_NAME).toAbsolutePath();
  }

  public Path ciphertextBallotProtoPath() {
    return publishDirectory.resolve(CIPHERTEXT_BALLOT_FILE).toAbsolutePath();
  }

  public Path spoiledBallotProtoPath() {
    return publishDirectory.resolve(SPOILED_BALLOT_FILE).toAbsolutePath();
  }

  public Path spoiledTallyProtoPath() {
    return publishDirectory.resolve(SPOILED_TALLY_FILE).toAbsolutePath();
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
          @Nullable Iterable<KeyCeremony.CoefficientValidationSet> coefficient_validation_sets,
          @Nullable Iterable<Ballot.PlaintextBallot> spoiledBallots,
          @Nullable Iterable<PlaintextTally> spoiledTallies) throws IOException {

    ConvertToJson.write(description, this.electionPath());
    ConvertToJson.write(context, this.contextPath());
    ConvertToJson.write(constants, this.constantsPath());

    for (Encrypt.EncryptionDevice device : devices) {
      ConvertToJson.write(device, this.devicePath(device.uuid));
    }

    for (Ballot.CiphertextAcceptedBallot ballot : ciphertext_ballots) {
      ConvertToJson.write(ballot, this.ballotPath(ballot.object_id));
    }

    ConvertToJson.write(ciphertext_tally, encryptedTallyPath());
    ConvertToJson.write(decryptedTally, tallyPath());

    if (coefficient_validation_sets != null) {
      for (KeyCeremony.CoefficientValidationSet coefficient_validation_set : coefficient_validation_sets) {
        ConvertToJson.write(coefficient_validation_set, this.coefficientsPath(coefficient_validation_set.owner_id()));
      }
    }

    if (spoiledBallots != null) {
      for (Ballot.PlaintextBallot ballot : spoiledBallots) {
        ConvertToJson.write(ballot, this.spoiledBallotPath(ballot.object_id));
      }
    }

    if (spoiledTallies != null) {
      for (PlaintextTally tally : spoiledTallies) {
        ConvertToJson.write(tally, this.spoiledTallyPath(tally.object_id));
      }
    }
  }

  /**
   * Publish the private data for an election.
   * Useful for generating sample data sets.
   * Do not use this in a production application.
   */
  public void publish_private_data(
          @Nullable Iterable<Ballot.PlaintextBallot> plaintext_ballots,
          @Nullable Iterable<Ballot.CiphertextBallot> ciphertext_ballots,
          @Nullable Iterable<Guardian> guardians) throws IOException {

    Files.createDirectories(privateDirPath);

    if (guardians != null) {
      for (Guardian guardian : guardians) {
        String guardian_name = GUARDIAN_PREFIX + guardian.object_id;
        ConvertToJson.write(guardian, privateDirPath.resolve(guardian_name));
      }
    }

    if (plaintext_ballots != null) {
      Path ballotsDirPath = privateDirPath.resolve(PRIVATE_PLAINTEXT_BALLOTS_DIR);
      Files.createDirectories(ballotsDirPath);
      for (Ballot.PlaintextBallot plaintext_ballot : plaintext_ballots) {
        String ballot_name = PLAINTEXT_BALLOT_PREFIX + plaintext_ballot.object_id + SUFFIX;
        ConvertToJson.write(plaintext_ballot, ballotsDirPath.resolve(ballot_name));
      }
    }

    if (ciphertext_ballots != null) {
      Path encryptedDirPath = privateDirPath.resolve(PRIVATE_ENCRYPTED_BALLOTS_DIR);
      Files.createDirectories(encryptedDirPath);
      for (Ballot.CiphertextBallot ciphertext_ballot : ciphertext_ballots) {
        String ballot_name = BALLOT_PREFIX + ciphertext_ballot.object_id + SUFFIX;
        ConvertToJson.write(ciphertext_ballot, encryptedDirPath.resolve(ballot_name));
      }
    }
  }

  //////////////////////////////////////////////////////////////////
  // Proto

  public void writeGuardiansProto(KeyCeremonyProto.Guardians guardiansProto) throws IOException {
    Files.createDirectories(privateDirPath);

    try (FileOutputStream out = new FileOutputStream(guardiansPath().toFile())) {
      guardiansProto.writeDelimitedTo(out);
    }
  }

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

  /** Publishes the ballot Encryptions part of election record as proto. */
  public void writeEncryptionResultsProto(
          ElectionDescription description,
          CiphertextElectionContext context,
          ElectionConstants constants,
          Iterable<KeyCeremony.CoefficientValidationSet> coefficient_validation_sets,
          Iterable<Encrypt.EncryptionDevice> devices,
          Iterable<Ballot.CiphertextAcceptedBallot> accepted_ballots) throws IOException {


    // the accepted ballots are written into their own file
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

  /** Publishes the ballot and tally Decryptions part election record as proto. */
  public void writeDecryptionResultsProto(
          ElectionDescription description,
          CiphertextElectionContext context,
          ElectionConstants constants,
          Iterable<KeyCeremony.CoefficientValidationSet> coefficient_validation_sets,
          Iterable<Encrypt.EncryptionDevice> devices,
          PublishedCiphertextTally ciphertext_tally,
          PlaintextTally decryptedTally,
          @Nullable Iterable<Ballot.PlaintextBallot> spoiledBallots,
          @Nullable Iterable<PlaintextTally> spoiledDecryptedTallies) throws IOException {

    if (spoiledBallots != null) {
      // the spoiledBallots are written into their own file
      try (FileOutputStream out = new FileOutputStream(spoiledBallotProtoPath().toFile())) {
        for (Ballot.PlaintextBallot ballot : spoiledBallots) {
          PlaintextBallotProto.PlaintextBallot ballotProto = PlaintextBallotToProto.translateToProto(ballot);
          ballotProto.writeDelimitedTo(out);
        }
      }
    }

    if (spoiledDecryptedTallies != null) {
      // the spoiledDecryptedTallies are written into their own file
      try (FileOutputStream out = new FileOutputStream(spoiledTallyProtoPath().toFile())) {
        for (PlaintextTally tally : spoiledDecryptedTallies) {
          PlaintextTallyProto.PlaintextTally tallyProto = PlaintextTallyToProto.translateToProto(tally);
          tallyProto.writeDelimitedTo(out);
        }
      }
    }

    ElectionRecordProto.ElectionRecord ElectionRecordProto = ElectionRecordToProto.buildElectionRecord(
            description, context, constants, coefficient_validation_sets,
            devices,
            ciphertext_tally,
            decryptedTally);

    try (FileOutputStream out = new FileOutputStream(electionRecordProtoPath().toFile())) {
      ElectionRecordProto.writeDelimitedTo(out);
    }
  }

  /** Publishes the entire election record as proto. */
  public void writeElectionRecordProto(
          ElectionDescription description,
          CiphertextElectionContext context,
          ElectionConstants constants,
          Iterable<KeyCeremony.CoefficientValidationSet> coefficient_validation_sets,
          Iterable<Encrypt.EncryptionDevice> devices,
          Iterable<Ballot.CiphertextAcceptedBallot> accepted_ballots,
          PublishedCiphertextTally ciphertext_tally,
          PlaintextTally decryptedTally,
          @Nullable Iterable<Ballot.PlaintextBallot> spoiledBallots,
          @Nullable Iterable<PlaintextTally> spoiledDecryptedTallies) throws IOException {


    // the accepted ballots are written into their own file
    try (FileOutputStream out = new FileOutputStream(ciphertextBallotProtoPath().toFile())) {
      for (Ballot.CiphertextAcceptedBallot ballot : accepted_ballots) {
        CiphertextBallotProto.CiphertextAcceptedBallot ballotProto = CiphertextBallotToProto.translateToProto(ballot);
        ballotProto.writeDelimitedTo(out);
      }
    }

    if (spoiledBallots != null) {
      // the spoiledBallots are written into their own file
      try (FileOutputStream out = new FileOutputStream(spoiledBallotProtoPath().toFile())) {
        for (Ballot.PlaintextBallot ballot : spoiledBallots) {
          PlaintextBallotProto.PlaintextBallot ballotProto = PlaintextBallotToProto.translateToProto(ballot);
          ballotProto.writeDelimitedTo(out);
        }
      }
    }

    if (spoiledDecryptedTallies != null) {
      // the spoiledDecryptedTallies are written into their own file
      try (FileOutputStream out = new FileOutputStream(spoiledTallyProtoPath().toFile())) {
        for (PlaintextTally tally : spoiledDecryptedTallies) {
          PlaintextTallyProto.PlaintextTally tallyProto = PlaintextTallyToProto.translateToProto(tally);
          tallyProto.writeDelimitedTo(out);
        }
      }
    }

    ElectionRecordProto.ElectionRecord ElectionRecordProto = ElectionRecordToProto.buildElectionRecord(
            description, context, constants, coefficient_validation_sets,
            devices,
            ciphertext_tally,
            decryptedTally);

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

}
