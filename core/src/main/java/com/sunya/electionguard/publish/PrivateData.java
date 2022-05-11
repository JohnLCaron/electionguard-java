package com.sunya.electionguard.publish;

import com.sunya.electionguard.decrypting.DecryptingTrustee;
import com.sunya.electionguard.keyceremony.KeyCeremonyTrustee;
import com.sunya.electionguard.protoconvert.KeyCeremonyTrusteeToProto;
import com.sunya.electionguard.protoconvert.PlaintextBallotFromProto;
import com.sunya.electionguard.protoconvert.PlaintextBallotToProto;
import com.sunya.electionguard.protoconvert.TrusteeFromProto;
import com.sunya.electionguard.PlaintextBallot;
import electionguard.protogen.PlaintextBallotProto;
import electionguard.protogen.TrusteeProto;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Formatter;
import java.util.List;

import static com.sunya.electionguard.protoconvert.DecryptingTrusteeToProto.publishDecryptingTrustee;

/** Publishes the Manifest Record to Json or protobuf files. */
public class PrivateData {
  static final String PRIVATE_DATA_DIR = "election_private_data";
  static final String INPUT_BALLOTS_FILE = "inputBallots" + ElectionRecordPath.PROTO_SUFFIX;
  static final String INVALID_BALLOTS_FILE = "invalidBallots" + ElectionRecordPath.PROTO_SUFFIX;

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

  public Path trusteePath(String id) {
    String filename = id + PublisherOld.PROTO_SUFFIX;
    return privateDirectory.resolve(filename);
  }

  public Path inputBallotsFilePath() {
    return privateDirectory.resolve(INPUT_BALLOTS_FILE);
  }

  public Path invalidBallotsFilePath() {
    return privateDirectory.resolve(INVALID_BALLOTS_FILE);
  }

  //////////////////////////////////////////////////////////////////
  // Proto
  public void overwriteTrusteeProto(TrusteeProto.DecryptingTrustee trusteeProto) throws IOException {
    Path outputPath = trusteePath(trusteeProto.getGuardianId());
    if (Files.exists(outputPath)) {
      Files.delete(outputPath);
    }
    try (FileOutputStream out = new FileOutputStream(outputPath.toFile())) {
      trusteeProto.writeDelimitedTo(out);
    }
  }

  public void writeTrustee(KeyCeremonyTrustee trustee) {
    TrusteeProto.DecryptingTrustee trusteeProto = KeyCeremonyTrusteeToProto.convertTrustee(trustee);
    Path fileout = PublisherOld.decryptingTrusteePath(this.privateDirectory, trustee.id);
    System.out.printf("writeTrustee %s%n", fileout);
    try (FileOutputStream out = new FileOutputStream(fileout.toFile())) {
      trusteeProto.writeTo(out);
    } catch (IOException ioe) {
      throw new RuntimeException(ioe);
    }
  }

  public DecryptingTrustee readDecryptingTrustee(String id) throws IOException {
    Path fileout = PublisherOld.decryptingTrusteePath(this.privateDirectory, id);
    System.out.printf("readDecryptingTrustee %s%n", fileout);
    return TrusteeFromProto.readTrustee(fileout.toString());
  }

  public static List<DecryptingTrustee> readDecryptingTrustees(String fileOrDir) throws IOException {

    List<DecryptingTrustee> result = new ArrayList<>();
    File dir = new File(fileOrDir);
    if (dir.isDirectory()) {
      for (File file : dir.listFiles((parent, name) -> name.endsWith(PublisherOld.PROTO_SUFFIX))) {
        result.add(TrusteeFromProto.readTrustee(file.toString()));
      }
    }
    return result;
  }

  public void writeTrustee(DecryptingTrustee trustee) {
    TrusteeProto.DecryptingTrustee proto = publishDecryptingTrustee(trustee);
    Path fileout = PublisherOld.decryptingTrusteePath(this.privateDirectory, trustee.id());
    try (FileOutputStream out = new FileOutputStream(fileout.toFile())) {
      proto.writeTo(out);
    } catch (IOException ioe) {
      throw new RuntimeException(ioe);
    }
  }

  // PlaintextBallots

  public void writeInputBallots(Iterable<PlaintextBallot> original_ballots) throws IOException {
    Files.createDirectories(privateDirectory);
    try (FileOutputStream out = new FileOutputStream(inputBallotsFilePath().toFile())) {
      for (PlaintextBallot ballot : original_ballots) {
        PlaintextBallotProto.PlaintextBallot ballotProto = PlaintextBallotToProto.publishPlaintextBallot(ballot);
        ballotProto.writeDelimitedTo(out);
      }
    }
  }

  public List<PlaintextBallot> inputBallots() throws IOException {
    return readPlaintextBallots(inputBallotsFilePath());
  }

  public static List<PlaintextBallot> readPlaintextBallots(Path fileOrDirPath) throws IOException {
    if (!Files.exists(fileOrDirPath)) {
      return new ArrayList<>();
    }
    List<PlaintextBallot> result = new ArrayList<>();

    File dir = fileOrDirPath.toFile();
    if (dir.getAbsolutePath().endsWith("protobuf")) {
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

  public void writeInvalidBallots(Iterable<PlaintextBallot> invalid_ballots) throws IOException {
    Files.createDirectories(privateDirectory);
    try (FileOutputStream out = new FileOutputStream(invalidBallotsFilePath().toFile())) {
      for (PlaintextBallot ballot : invalid_ballots) {
        PlaintextBallotProto.PlaintextBallot ballotProto = PlaintextBallotToProto.publishPlaintextBallot(ballot);
        ballotProto.writeDelimitedTo(out);
      }
    }
  }

  public List<PlaintextBallot> invalidBallots() throws IOException {
    return readPlaintextBallots(invalidBallotsFilePath());
  }

}
