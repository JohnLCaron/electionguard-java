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

  private final ElectionRecordPath recordPath;

  public PrivateData(String where, boolean removeAllFiles, boolean createDirs) throws IOException {
    this.recordPath = new ElectionRecordPath(Path.of(where).resolve(PRIVATE_DATA_DIR).toAbsolutePath().toString());

    //if (removeAllFiles) {
   //   removeAllFiles();
   // }
    if (createDirs) {
      createDirs();
    }
  }

  /** Make sure output dir exists and is writeable. */
  public boolean validateOutputDir(Formatter error) {

    if (!Files.exists(recordPath.topDirPath())) {
      error.format(" Output directory '%s' does not exist%n", recordPath.topDirPath());
      return false;
    }
    if (!Files.isDirectory(recordPath.topDirPath())) {
      error.format(" Output directory '%s' is not a directory%n", recordPath.topDirPath());
      return false;
    }
    if (!Files.isWritable(recordPath.topDirPath())) {
      error.format(" Output directory '%s' is not writeable%n", recordPath.topDirPath());
      return false;
    }
    if (!Files.isExecutable(recordPath.topDirPath())) {
      error.format(" Output directory '%s' is not executable%n", recordPath.topDirPath());
      return false;
    }
    return true;
  }

  private void createDirs() throws IOException {
    Files.createDirectories(recordPath.topDirPath());
    System.out.printf("Make private directory %s%n", recordPath.topDirPath());
  }

  /** Delete everything in the output directory, but leave that directory.
  private void removeAllFiles() throws IOException {
    if (!Files.exists(recordPath.topDirPath())) {
      return;
    }
    Files.walk(this.recordPath.topDirPath())
            .filter(p -> !p.equals(recordPath.topDirPath()))
            .map(Path::toFile)
            .sorted((o1, o2) -> -o1.compareTo(o2))
            .forEach(f -> f.delete());
  } */

  public String getTopDir() {
    return this.recordPath.getTopDir();
  }

  public Path trusteePath(String id) {
    return this.recordPath.topDirPath().resolve(this.recordPath.decryptingTrusteeName(id));
  }

  public Path inputBallotsFilePath() {
    return this.recordPath.topDirPath().resolve(ElectionRecordPath.INPUT_BALLOTS_FILE);
  }

  public Path invalidBallotsFilePath() {
    return this.recordPath.topDirPath().resolve(ElectionRecordPath.INVALID_BALLOTS_FILE);
  }

  //////////////////////////////////////////////////////////////////
  // Proto

  public void writeTrustee(KeyCeremonyTrustee trustee) {
    TrusteeProto.DecryptingTrustee trusteeProto = KeyCeremonyTrusteeToProto.convertTrustee(trustee);
    Path fileout = this.trusteePath(trustee.id);
    System.out.printf("writeTrustee %s%n", fileout);
    try (FileOutputStream out = new FileOutputStream(fileout.toFile())) {
      trusteeProto.writeTo(out);
    } catch (IOException ioe) {
      throw new RuntimeException(ioe);
    }
  }

  public DecryptingTrustee readDecryptingTrustee(String id) throws IOException {
    Path fileout = this.trusteePath(id);
    System.out.printf("readDecryptingTrustee %s%n", fileout);
    return TrusteeFromProto.readTrustee(fileout.toString());
  }

  public static List<DecryptingTrustee> readDecryptingTrustees(String fileOrDir) throws IOException {
    List<DecryptingTrustee> result = new ArrayList<>();
    File dir = new File(fileOrDir);
    if (dir.isDirectory()) {
      for (File file : dir.listFiles((parent, name) -> name.endsWith(ElectionRecordPath.PROTO_SUFFIX))) {
        result.add(TrusteeFromProto.readTrustee(file.toString()));
      }
    }
    return result;
  }

  public void writeTrustee(DecryptingTrustee trustee) {
    TrusteeProto.DecryptingTrustee proto = publishDecryptingTrustee(trustee);
    Path fileout = this.trusteePath(trustee.id());
    try (FileOutputStream out = new FileOutputStream(fileout.toFile())) {
      proto.writeTo(out);
    } catch (IOException ioe) {
      throw new RuntimeException(ioe);
    }
  }

  // PlaintextBallots

  public void writeInputBallots(Iterable<PlaintextBallot> original_ballots) throws IOException {
    Files.createDirectories(recordPath.topDirPath());
    try (FileOutputStream out = new FileOutputStream(inputBallotsFilePath().toFile())) {
      for (PlaintextBallot ballot : original_ballots) {
        PlaintextBallotProto.PlaintextBallot ballotProto = PlaintextBallotToProto.publishPlaintextBallot(ballot);
        ballotProto.writeDelimitedTo(out);
      }
    }
  }

  public List<PlaintextBallot> readInputBallots() throws IOException {
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
    Files.createDirectories(recordPath.topDirPath());
    try (FileOutputStream out = new FileOutputStream(invalidBallotsFilePath().toFile())) {
      for (PlaintextBallot ballot : invalid_ballots) {
        PlaintextBallotProto.PlaintextBallot ballotProto = PlaintextBallotToProto.publishPlaintextBallot(ballot);
        ballotProto.writeDelimitedTo(out);
      }
    }
  }

  public List<PlaintextBallot> readInvalidBallots() throws IOException {
    return readPlaintextBallots(invalidBallotsFilePath());
  }

}
