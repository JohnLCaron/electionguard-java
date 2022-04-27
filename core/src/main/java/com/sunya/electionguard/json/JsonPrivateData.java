package com.sunya.electionguard.json;

import com.sunya.electionguard.PlaintextBallot;
import com.sunya.electionguard.standard.GuardianPrivateRecord;

import javax.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Formatter;
import java.util.List;

public class JsonPrivateData {
  static final String PRIVATE_DATA_DIR = "election_private_data";

  //// json
  static final String PRIVATE_GUARDIANS_DIR = "private_guardians";
  static final String PRIVATE_BALLOT_DIR = "plaintext_ballots";
  static final String PLAINTEXT_BALLOT_PREFIX = "plaintext_ballot_";
  static final String PRIVATE_GUARDIAN_PREFIX = "private_guardian_";

  private final Path privateDirectory;

  public JsonPrivateData(String where, boolean removeAllFiles, boolean createDirs) throws IOException {
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

  public Path privateGuardiansPath() {
    return privateDirectory.resolve(PRIVATE_GUARDIANS_DIR);
  }

  public Path privateBallotsPath() {
    return privateDirectory.resolve(PRIVATE_BALLOT_DIR);
  }

  public Path guardiansPrivatePath(String id) {
    String fileName = id + JsonPublisher.JSON_SUFFIX;
    return privateDirectory.resolve(fileName);
  }

  public File[] guardianRecordPrivateFiles() {
    Path where = privateGuardiansPath();
    if (!Files.exists(privateGuardiansPath()) || !Files.isDirectory(privateGuardiansPath())) {
      return new File[0];
    }
    return privateGuardiansPath().toFile().listFiles();
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
        String guardian_name = PRIVATE_GUARDIAN_PREFIX + guardian.guardian_id() + JsonPublisher.JSON_SUFFIX;
        ConvertToJson.writeGuardianRecordPrivate(guardian, privateGuardiansPath().resolve(guardian_name));
      }
    }

    if (original_ballots != null) {
      Files.createDirectories(privateBallotsPath());
      for (PlaintextBallot plaintext_ballot : original_ballots) {
        String ballot_name = PLAINTEXT_BALLOT_PREFIX + plaintext_ballot.object_id() + JsonPublisher.JSON_SUFFIX;
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
      String ballot_name = PLAINTEXT_BALLOT_PREFIX + plaintext_ballot.object_id() + JsonPublisher.JSON_SUFFIX;
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
      for (File file : dir.listFiles((parent, name) -> name.endsWith(JsonPublisher.JSON_SUFFIX))) {
        result.add(ConvertFromJson.readPlaintextBallot(file.getAbsolutePath()));
      }
    } else if (dir.getAbsolutePath().endsWith("json")) {
      result.add(ConvertFromJson.readPlaintextBallot(dir.getAbsolutePath()));

    }

    return result;
  }

}
