package com.sunya.electionguard.publish;

import com.sunya.electionguard.decrypting.DecryptingTrustee;
import com.sunya.electionguard.proto.PlaintextBallotFromProto;
import com.sunya.electionguard.proto.PlaintextBallotToProto;
import com.sunya.electionguard.proto.SubmittedBallotFromProto;
import com.sunya.electionguard.proto.TrusteeFromProto;
import com.sunya.electionguard.standard.GuardianPrivateRecord;
import com.sunya.electionguard.PlaintextBallot;
import electionguard.protogen.CiphertextBallotProto;
import electionguard.protogen.PlaintextBallotProto;
import electionguard.protogen.TrusteeProto;

import javax.annotation.Nullable;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Formatter;
import java.util.List;

/** Publishes the Manifest Record to Json or protobuf files. */
public class PrivateData {
  static final String PRIVATE_DATA_DIR = "election_private_data";

  //// json
  static final String PRIVATE_GUARDIANS_DIR = "private_guardians";
  static final String PRIVATE_BALLOT_DIR = "plaintext_ballots";
  static final String PLAINTEXT_BALLOT_PREFIX = "plaintext_ballot_";
  static final String PRIVATE_GUARDIAN_PREFIX = "private_guardian_";

  //// proto
  static final String TRUSTEES_FILE = "trustees" + Publisher.PROTO_SUFFIX;
  static final String PROTO_BALLOTS_FILE = "plaintextBallots" + Publisher.PROTO_SUFFIX;

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
    Files.createDirectories(privateDirectory);
    System.out.printf("Make private directory %s%n", privateDirectory);
  }

  /** Delete everything in the output directory, but leave that directory. */
  private void removeAllFiles() throws IOException {
    if (!this.privateDirectory.toFile().exists()) {
      return;
    }
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

  public Path trusteePath(String id) {
    String filename = id + Publisher.PROTO_SUFFIX;
    return privateDirectory.resolve(filename);
  }

  public Path privateGuardiansPath() {
    return privateDirectory.resolve(PRIVATE_GUARDIANS_DIR);
  }

  public Path privateBallotsPath() {
    return privateDirectory.resolve(PRIVATE_BALLOT_DIR);
  }

  public Path privateBallotsProtoPath() {
    return privateBallotsPath().resolve(PROTO_BALLOTS_FILE);
  }

  public Path guardiansPrivatePath(String id) {
    String fileName = id + Publisher.JSON_SUFFIX;
    return privateDirectory.resolve(fileName);
  }

  public File[] guardianRecordPrivateFiles() {
    Path where = privateGuardiansPath();
    if (!Files.exists(privateGuardiansPath()) || !Files.isDirectory(privateGuardiansPath())) {
      return new File[0];
    }
    return privateGuardiansPath().toFile().listFiles();
  }

  //////////////////////////////////////////////////////////////////
  // Proto

  public void writeTrusteesProto(TrusteeProto.DecryptingTrustees trusteesProto) throws IOException {
    Files.createDirectories(privateDirectory);

    try (FileOutputStream out = new FileOutputStream(trusteesPath().toFile())) {
      trusteesProto.writeDelimitedTo(out);
    }
  }

  public void overwriteTrusteeProto(TrusteeProto.DecryptingTrustee trusteeProto) throws IOException {
    Path outputPath = trusteePath(trusteeProto.getGuardianId());
    if (Files.exists(outputPath)) {
      Files.delete(outputPath);
    }
    try (FileOutputStream out = new FileOutputStream(outputPath.toFile())) {
      trusteeProto.writeDelimitedTo(out);
    }
  }

  public DecryptingTrustee readDecryptingTrustee(String id) throws IOException {
    Path outputPath = trusteePath(id);
    return TrusteeFromProto.readTrustee(outputPath.toString());
  }

  public static List<DecryptingTrustee> readDecryptingTrustees(String fileOrDir) throws IOException {

    List<DecryptingTrustee> result = new ArrayList<>();
    File dir = new File(fileOrDir);
    if (dir.isDirectory()) {
      for (File file : dir.listFiles((parent, name) -> name.endsWith(Publisher.PROTO_SUFFIX))) {
        result.add(TrusteeFromProto.readTrustee(file.toString()));
      }
    } else {
      try {
        // multiple trustees
        result.addAll(TrusteeFromProto.readTrustees(fileOrDir));
      } catch (Exception e) {
        // one trustee
        result.add(TrusteeFromProto.readTrustee(fileOrDir));
      }
    }
    return result;
  }

  public void writePrivateDataProto(
          @Nullable Iterable<PlaintextBallot> original_ballots,
          @Nullable Iterable<GuardianPrivateRecord> guardians) throws IOException {

    Files.createDirectories(privateDirectory);

    if (guardians != null) {
      throw new UnsupportedOperationException();
    }

    if (original_ballots != null) {
      Files.createDirectories(privateBallotsPath());
      try (FileOutputStream out = new FileOutputStream(privateBallotsProtoPath().toFile())) {
        for (PlaintextBallot ballot : original_ballots) {
          PlaintextBallotProto.PlaintextBallot ballotProto = PlaintextBallotToProto.translateToProto(ballot);
          ballotProto.writeDelimitedTo(out);
        }
      }
    }

    // TODO CIPHERTEXT_BALLOT_PREFIX?
  }

  ////////////////////////////////////////////////////////////////////////////////
  // JSON, probably not needed

  public void writeGuardiansJson(Iterable<GuardianPrivateRecord> guardianRecords) throws IOException {
    Files.createDirectories(privateDirectory);
    for (GuardianPrivateRecord guardianRecord : guardianRecords) {
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
          @Nullable Iterable<GuardianPrivateRecord> guardians) throws IOException {

    Files.createDirectories(privateDirectory);

    if (guardians != null) {
      Files.createDirectories(privateGuardiansPath());
      for (GuardianPrivateRecord guardian : guardians) {
        String guardian_name = PRIVATE_GUARDIAN_PREFIX + guardian.guardian_id() + Publisher.JSON_SUFFIX;
        ConvertToJson.writeGuardianRecordPrivate(guardian, privateGuardiansPath().resolve(guardian_name));
      }
    }

    if (original_ballots != null) {
      Files.createDirectories(privateBallotsPath());
      for (PlaintextBallot plaintext_ballot : original_ballots) {
        String ballot_name = PLAINTEXT_BALLOT_PREFIX + plaintext_ballot.object_id() + Publisher.JSON_SUFFIX;
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
      String ballot_name = PLAINTEXT_BALLOT_PREFIX + plaintext_ballot.object_id() + Publisher.JSON_SUFFIX;
      ConvertToJson.writePlaintextBallot(plaintext_ballot, invalidBallotsPath.resolve(ballot_name));
    }

  }

  public List<GuardianPrivateRecord> readGuardianPrivateJson() throws IOException {
    List<GuardianPrivateRecord> result = new ArrayList<>();
    for (File file : guardianRecordPrivateFiles()) {
      GuardianPrivateRecord fromPython = ConvertFromJson.readGuardianRecordPrivate(file.getAbsolutePath());
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

  public static List<PlaintextBallot> inputBallots(Path fileOrDirPath) throws IOException {
    if (!Files.exists(fileOrDirPath)) {
      return new ArrayList<>();
    }
    List<PlaintextBallot> result = new ArrayList<>();

    File dir = fileOrDirPath.toFile();
    if (dir.isDirectory()) {
      for (File file : dir.listFiles((parent, name) -> name.endsWith(Publisher.JSON_SUFFIX))) {
        result.add(ConvertFromJson.readPlaintextBallot(file.getAbsolutePath()));
      }
    } else if (dir.getAbsolutePath().endsWith("json")) {
      result.add(ConvertFromJson.readPlaintextBallot(dir.getAbsolutePath()));

    } else if (dir.getAbsolutePath().endsWith("protobuf")) {
      // multiple input ballots in a protobuf
      try (FileInputStream input = new FileInputStream(dir.getAbsolutePath())) {
      while (true) {
          PlaintextBallotProto.PlaintextBallot ballotProto = PlaintextBallotProto.PlaintextBallot.parseDelimitedFrom(input);
          if (ballotProto == null) {
            input.close();
            break;
          }
          result.add(PlaintextBallotFromProto.translateFromProto(ballotProto));
        }
      }
    }

    return result;
  }

}
