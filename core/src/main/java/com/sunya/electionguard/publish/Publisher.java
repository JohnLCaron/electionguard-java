package com.sunya.electionguard.publish;

import com.sunya.electionguard.PlaintextBallot;
import com.sunya.electionguard.SubmittedBallot;
import com.sunya.electionguard.protoconvert.ElectionConfigConvert;
import com.sunya.electionguard.protoconvert.ElectionInitializedConvert;
import com.sunya.electionguard.protoconvert.ElectionResultsConvert;
import com.sunya.electionguard.protoconvert.PlaintextBallotToProto;
import com.sunya.electionguard.protoconvert.SubmittedBallotToProto;
import electionguard.ballot.*;
import electionguard.protogen.CiphertextBallotProto;
import electionguard.protogen.ElectionRecordProto;
import electionguard.protogen.PlaintextBallotProto;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Formatter;
import java.util.List;

/**
 * Publishes the Manifest Record to Json or protobuf files.
 */
public class Publisher {

  public enum Mode {
    readonly,
    writeonly, // write new files, but do not create directories
    createIfMissing, // create directories if not already exist
    createNew // create clean directories
  }

  private Mode createPublisherMode;
  private ElectionRecordPath path;
  private Path electionRecordDir;

  public Publisher(String topDir, Mode publisherMode) throws IOException {
    this.createPublisherMode = publisherMode;
    this.path = new ElectionRecordPath(topDir);
    this.electionRecordDir = Path.of(topDir);

    if (createPublisherMode == Mode.createNew) {
      if (!Files.exists(electionRecordDir)) {
        Files.createDirectories(electionRecordDir);
      } else {
        removeAllFiles(electionRecordDir);
      }
    } else if (createPublisherMode == Mode.createIfMissing) {
      if (!Files.exists(electionRecordDir)) {
        Files.createDirectories(electionRecordDir);
      }
    } else {
      if (!Files.exists(electionRecordDir)) {
        throw new IllegalStateException("Non existing election directory " + electionRecordDir);
      }
    }
  }

  /** Delete everything in the output directory, but leave that directory. */
  private void removeAllFiles(Path where) throws IOException {
    if (!where.toFile().exists()) {
      return;
    }

    String filename = where.getFileName().toString();
    Files.walk(where)
            .filter(p -> !p.equals(where))
            .map(Path::toFile)
            .sorted((o1, o2) -> -o1.compareTo(o2))
            .forEach( f-> f.delete());
  }

  /** Make sure output dir exists and is writeable. */
  public boolean validateOutputDir(Formatter error) {
    if (!Files.exists(electionRecordDir)) {
      error.format(" Output directory '%s' does not exist%n", electionRecordDir);
      return false;
    }
    if (!Files.isDirectory(electionRecordDir)) {
      error.format(" Output directory '%s' is not a directory%n", electionRecordDir);
      return false;
    }
    if (!Files.isWritable(electionRecordDir)) {
      error.format(" Output directory '%s' is not writeable%n", electionRecordDir);
      return false;
    }
    if (!Files.isExecutable(electionRecordDir)) {
      error.format(" Output directory '%s' is not executable%n", electionRecordDir);
      return false;
    }
    return true;
  }

  public String publishPath() {
    return electionRecordDir.toString();
  }

  public void writeElectionConfig(ElectionConfig config) throws IOException {
    ElectionRecordProto.ElectionConfig proto = ElectionConfigConvert.publishElectionConfig(config);
    try (FileOutputStream out = new FileOutputStream(path.electionConfigPath())) {
      proto.writeTo(out);
    }
  }


  public void writeElectionInitialized(ElectionInitialized init) throws IOException {
    ElectionRecordProto.ElectionInitialized proto = ElectionInitializedConvert.publishElectionInitialized(init);
    try (FileOutputStream out = new FileOutputStream(path.electionInitializedPath())) {
      proto.writeTo(out);
    }
  }

  public void writeEncryptions(ElectionInitialized init, Iterable<SubmittedBallot> ballots) throws IOException {
    writeElectionInitialized(init);

      try (FileOutputStream out = new FileOutputStream(path.submittedBallotPath())) {
        for (SubmittedBallot ballot : ballots) {
          CiphertextBallotProto.SubmittedBallot ballotProto = SubmittedBallotToProto.translateToProto(ballot);
          ballotProto.writeDelimitedTo(out);
        }
      }
  }

  public void writeTallyResult(TallyResult tally) throws IOException {
    ElectionRecordProto.TallyResult proto = ElectionResultsConvert.publishTallyResult(tally);
    try (FileOutputStream out = new FileOutputStream(path.tallyResultPath())) {
      proto.writeTo(out);
    }
  }

  public void writeDecryptionResults(DecryptionResult dresult) throws IOException {
    ElectionRecordProto.DecryptionResult proto = ElectionResultsConvert.publishDecryptionResult(dresult);
    try (FileOutputStream out = new FileOutputStream(path.decryptionResultPath())) {
      proto.writeTo(out);
    }
  }

  public void writePlaintextBallot(String outputDir, List<PlaintextBallot> plaintextBallots) throws IOException {
    if (!plaintextBallots.isEmpty()) {
      String fileout = path.plaintextBallotPath(outputDir);
      try (FileOutputStream out = new FileOutputStream(fileout)) {
        for (PlaintextBallot ballot : plaintextBallots) {
          PlaintextBallotProto.PlaintextBallot ballotProto = PlaintextBallotToProto.publishPlaintextBallot(ballot);
          ballotProto.writeDelimitedTo(out);
        }
      }
    }
  }

  public void copyAcceptedBallots(String inputDir) throws IOException {
    if (this.createPublisherMode == Publisher.Mode.readonly) {
      throw new UnsupportedOperationException("Trying to write to readonly election record");
    }
    String source = new ElectionRecordPath(inputDir).submittedBallotPath();
    String dest = path.submittedBallotPath();
    if (source.equals(dest)) {
      return;
    }

    System.out.printf("Copy AcceptedBallots from %s to %s%n", source, dest);
    Files.copy(Path.of(source), Path.of(dest), StandardCopyOption.COPY_ATTRIBUTES);
  }
}