package com.sunya.electionguard.publish;

import com.google.common.collect.Iterables;
import com.sunya.electionguard.AvailableGuardian;
import com.sunya.electionguard.CiphertextElectionContext;
import com.sunya.electionguard.CiphertextTally;
import com.sunya.electionguard.ElectionConstants;
import com.sunya.electionguard.Encrypt;
import com.sunya.electionguard.GuardianRecord;
import com.sunya.electionguard.standard.GuardianRecordPrivate;
import com.sunya.electionguard.Manifest;
import com.sunya.electionguard.PlaintextBallot;
import com.sunya.electionguard.PlaintextTally;
import com.sunya.electionguard.SubmittedBallot;
import com.sunya.electionguard.proto.CiphertextBallotToProto;
import com.sunya.electionguard.proto.ElectionRecordToProto;
import com.sunya.electionguard.proto.PlaintextTallyToProto;
import com.sunya.electionguard.protogen.CiphertextBallotProto;
import com.sunya.electionguard.protogen.ElectionRecordProto;
import com.sunya.electionguard.protogen.PlaintextTallyProto;
import com.sunya.electionguard.protogen.TrusteeProto;
import com.sunya.electionguard.verifier.ElectionRecord;

import javax.annotation.Nullable;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Formatter;
import java.util.List;

/** Publishes the Manifest Record to Json or protobuf files. */
public class PrivateData {
  static final String PRIVATE_DATA_DIR = "election_private_data";

  //// json
  static final String SUFFIX = ".json";
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

  private final Path privateDirectory;

  public PrivateData(String where, boolean removeAllFiles, boolean createDirs) throws IOException {
    this.privateDirectory = Path.of(where).resolve(PRIVATE_DATA_DIR);

    if (removeAllFiles) {
      removeAllFiles();
    }
    if (createDirs) {
      createDirs();
    }
  }

  /** Make sure output dir exists and is writeable. */
  public boolean validateOutputDir(Formatter error) {
    if (!Files.exists(privateDirectory)) {
      error.format(" Output directory '%s' does not exist%n", privateDirectory);
      return false;
    }
    if (!Files.isDirectory(privateDirectory)) {
      error.format(" Output directory '%s' is not a directory%n", privateDirectory);
      return false;
    }
    if (!Files.isWritable(privateDirectory)) {
      error.format(" Output directory '%s' is not writeable%n", privateDirectory);
      return false;
    }
    if (!Files.isExecutable(privateDirectory)) {
      error.format(" Output directory '%s' is not executable%n", privateDirectory);
      return false;
    }
    return true;
  }

  private void createDirs() throws IOException {
  }

  /** Delete everything in the output directory, but leave that directory. */
  private void removeAllFiles() throws IOException {
    Files.walk(this.privateDirectory)
            .filter(p -> !p.equals(privateDirectory))
            .map(Path::toFile)
            .sorted((o1, o2) -> -o1.compareTo(o2))
            .forEach(f -> f.delete());
  }

  public Path privateDirectory() {
    return privateDirectory.toAbsolutePath();
  }

  public Path trusteesPath() {
    return privateDirectory.resolve(TRUSTEES_FILE);
  }

  public Path privateGuardiansPath() {
    return privateDirectory.resolve(PRIVATE_GUARDIANS_DIR);
  }

  public Path privateBallotsPath() {
    return privateDirectory.resolve(PRIVATE_BALLOT_DIR);
  }

  public Path guardiansPrivatePath(String id) {
    String fileName = id + SUFFIX;
    return privateDirectory.resolve(fileName);
  }

  public File[] guardianRecordPrivateFiles() {
    Path where = privateGuardiansPath();
    if (!Files.exists(privateGuardiansPath()) || !Files.isDirectory(privateGuardiansPath())) {
      return new File[0];
    }
    return privateGuardiansPath().toFile().listFiles();
  }

  ////////////////////

  public Path electionRecordProtoPath() {
    return privateDirectory.resolve(ELECTION_RECORD_FILE_NAME).toAbsolutePath();
  }

  public Path ciphertextBallotProtoPath() {
    return privateDirectory.resolve(SUBMITTED_BALLOT_PROTO).toAbsolutePath();
  }

  public Path spoiledBallotProtoPath() {
    return privateDirectory.resolve(SPOILED_BALLOT_FILE).toAbsolutePath();
  }

  //////////////////////////////////////////////////////////////////
  // Proto

  public void writeTrusteesProto(TrusteeProto.DecryptingTrustees trusteesProto) throws IOException {
    Files.createDirectories(privateDirectory);

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
    Path source = new PrivateData(inputDir, false, false).ciphertextBallotProtoPath();
    Path dest = ciphertextBallotProtoPath();
    Files.copy(source, dest, StandardCopyOption.COPY_ATTRIBUTES);
  }

  ////////////////////////////////////////////////////////////////////////////////
  // private, probably not needed

  public void writeGuardiansJson(Iterable<GuardianRecordPrivate> guardianRecords) throws IOException {
    Files.createDirectories(privateDirectory);
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

    Files.createDirectories(privateDirectory);

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
        String ballot_name = PLAINTEXT_BALLOT_PREFIX + plaintext_ballot.object_id() + SUFFIX;
        ConvertToJson.writePlaintextBallot(plaintext_ballot, privateBallotsPath().resolve(ballot_name));
      }
    }

    // TODO CIPHERTEXT_BALLOT_PREFIX?
  }

  // These are input ballots that did not validate (not spoiled ballots). put them somewhere to be examined later.
  public void publish_invalid_ballots(String directory, Iterable<PlaintextBallot> invalid_ballots) throws IOException {
    Path invalidBallotsPath = privateDirectory.resolve(directory);
    Files.createDirectories(invalidBallotsPath);

    for (PlaintextBallot plaintext_ballot : invalid_ballots) {
      String ballot_name = PLAINTEXT_BALLOT_PREFIX + plaintext_ballot.object_id() + SUFFIX;
      ConvertToJson.writePlaintextBallot(plaintext_ballot, invalidBallotsPath.resolve(ballot_name));
    }

  }


  //////////////////////////////////////////////


  public List<GuardianRecordPrivate> readGuardianPrivateJson() throws IOException {
    List<GuardianRecordPrivate> result = new ArrayList<>();
    for (File file : guardianRecordPrivateFiles()) {
      GuardianRecordPrivate fromPython = ConvertFromJson.readGuardianRecordPrivate(file.getAbsolutePath());
      result.add(fromPython);
    }
    return result;
  }

  // Input ballot files for debugging. Not part of the election record.
  public List<PlaintextBallot> inputBallots() throws IOException {
    if (!Files.exists(privateBallotsPath())) {
      return new ArrayList<>();
    }
    File dir = privateBallotsPath().toFile();
    if (dir.listFiles() != null) {
      List<PlaintextBallot> result = new ArrayList<>();
      for (File file : dir.listFiles()) {
        PlaintextBallot fromJson = ConvertFromJson.readPlaintextBallot(file.getAbsolutePath());
        result.add(fromJson);
      }
      return result;
    } else {
      return new ArrayList<>();
    }
  }

}
