package com.sunya.electionguard.json;

import com.google.common.collect.Iterables;
import com.sunya.electionguard.*;
import com.sunya.electionguard.protoconvert.ElectionInitializedConvert;
import com.sunya.electionguard.protoconvert.ElectionResultsConvert;
import com.sunya.electionguard.protoconvert.SubmittedBallotToProto;
import com.sunya.electionguard.protoconvert.ElectionRecordToProto;
import com.sunya.electionguard.protoconvert.PlaintextTallyToProto;
import com.sunya.electionguard.publish.ElectionRecord;
import com.sunya.electionguard.publish.PrivateData;
import electionguard.ballot.DecryptionResult;
import electionguard.ballot.ElectionInitialized;
import electionguard.protogen.ElectionRecordProto;
import electionguard.protogen.PlaintextTallyProto;

import javax.annotation.Nullable;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Formatter;

import electionguard.protogen.CiphertextBallotProto;
import electionguard.protogen.ElectionRecordProto1;

/** Publishes the Manifest Record to protobuf files. */
public class PublisherOld {
  public enum Mode {readonly,
                    writeonly, // write new files, but do not create directories
                    createIfMissing, // create directories if not already exist
                    createNew // create clean directories
  }

  static final String PROTO_VERSION = "2.0.0";

  static final String PROTO_SUFFIX = ".protobuf";
  static final String DECRYPTING_TRUSTEE_PREFIX = "decryptingTrustee";
  static final String ELECTION_CONFIG_FILE_NAME = "electionConfig" + PROTO_SUFFIX;
  static final String ELECTION_INITIALIZED_FILE_NAME = "electionInitialized" + PROTO_SUFFIX;
  static final String ELECTION_RECORD_FILE_NAME = "electionRecord" + PROTO_SUFFIX;
  static final String TALLY_RESULT_NAME = "tallyResult" + PROTO_SUFFIX;
  static final String DECRYPTION_RESULT_NAME = "decryptionResult" + PROTO_SUFFIX;
  static final String PLAINTEXT_BALLOT_PROTO = "plaintextBallots" + PROTO_SUFFIX;
  static final String SUBMITTED_BALLOT_PROTO = "encryptedBallots" + PROTO_SUFFIX;
  static final String SPOILED_BALLOT_FILE = "spoiledBallotsTally" + PROTO_SUFFIX;

  static final String TRUSTEES_FILE = "trustees" + PROTO_SUFFIX;

  private final String topdir;
  private final Mode createMode;
  private final Path electionRecordDir;

  public PublisherOld(String where, Mode createMode) throws IOException {
    this.topdir = where;
    this.createMode = createMode;

    this.electionRecordDir = Path.of(where);

    if (createMode == Mode.createNew) {
      if (!Files.exists(electionRecordDir)) {
        Files.createDirectories(electionRecordDir);
      } else {
        removeAllFiles();
      }
    } else if (createMode == Mode.createIfMissing) {
      if (!Files.exists(electionRecordDir)) {
        Files.createDirectories(electionRecordDir);
      }
    } else {
      if (!Files.exists(electionRecordDir)) {
        throw new IllegalStateException("Non existing election directory " + electionRecordDir);
      }
    }
  }

  public PublisherOld(Path electionRecordDir, Mode createMode) throws IOException {
    this.createMode = createMode;
    this.topdir = electionRecordDir.toAbsolutePath().toString();

    this.electionRecordDir = electionRecordDir;

    if (createMode == Mode.createNew) {
      if (!Files.exists(electionRecordDir)) {
        Files.createDirectories(electionRecordDir);
      } else {
        removeAllFiles();
      }
    } else if (createMode == Mode.createIfMissing) {
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
  private void removeAllFiles() throws IOException {
    if (!electionRecordDir.toFile().exists()) {
      return;
    }

    String filename = electionRecordDir.getFileName().toString();
    Files.walk(electionRecordDir)
            .filter(p -> !p.equals(electionRecordDir))
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

  ////////////////////

  public Path publishPath() {
    return electionRecordDir.toAbsolutePath();
  }

  public Path electionRecordProtoPath() {
    return electionRecordDir.resolve(ELECTION_RECORD_FILE_NAME).toAbsolutePath();
  }

  public Path decryptionResultPath() {
    return electionRecordDir.resolve(DECRYPTION_RESULT_NAME).toAbsolutePath();
  }

  public Path submittedBallotProtoPath() {
    return electionRecordDir.resolve(SUBMITTED_BALLOT_PROTO).toAbsolutePath();
  }

  public Path spoiledBallotProtoPath() {
    return electionRecordDir.resolve(SPOILED_BALLOT_FILE).toAbsolutePath();
  }

  public static Path decryptingTrusteePath(Path trusteePath, String guardianId) {
    String filename = String.format("decryptingTrustee-%s.protobuf", guardianId);
    return trusteePath.resolve(filename).toAbsolutePath();
  }
  //////////////////////////////////////////////////////////////////
  // Proto

  /** Publishes the starting election record as proto. */
  public void writeStartingProto(
          Manifest description,
          ElectionConstants constants) throws IOException {

    if (createMode == Mode.readonly) {
      throw new UnsupportedOperationException("Trying to write to readonly election record");
    }

    ElectionRecordProto1.ElectionRecord electionRecordProto = ElectionRecordToProto.buildElectionRecord(
            description, constants, null, null,
            null, null, null, null);
    try (FileOutputStream out = new FileOutputStream(electionRecordProtoPath().toFile())) {
      electionRecordProto.writeTo(out);
    }
  }

  public void writeElectionInitialized(ElectionInitialized init) throws IOException {
    ElectionRecordProto.ElectionInitialized proto = ElectionInitializedConvert.publishElectionInitialized(init);
    try (FileOutputStream out = new FileOutputStream(electionRecordProtoPath().toFile())) {
      proto.writeTo(out);
    }
  }

  public void writeDecryptionResults(DecryptionResult dresult) throws IOException {
    ElectionRecordProto.DecryptionResult proto = ElectionResultsConvert.publishDecryptionResult(dresult);
    try (FileOutputStream out = new FileOutputStream(decryptionResultPath().toFile())) {
      proto.writeTo(out);
    }
  }

  /** Publishes the KeyCeremony part of the election record as proto. */
  public void writeKeyCeremonyProto(
          Manifest description,
          ElectionCryptoContext context,
          ElectionConstants constants,
          Iterable<GuardianRecord> guardianRecords) throws IOException {

    if (createMode == Mode.readonly) {
      throw new UnsupportedOperationException("Trying to write to readonly election record");
    }

    if (context.numberOfGuardians != Iterables.size(guardianRecords)) {
      throw new IllegalStateException(String.format("Number of guardians (%d) does not match number of coefficients (%d)",
              context.numberOfGuardians, Iterables.size(guardianRecords)));
    }

    ElectionRecordProto1.ElectionRecord electionRecordProto = ElectionRecordToProto.buildElectionRecord(
            description, constants, context, guardianRecords,
            null, null, null, null);
    try (FileOutputStream out = new FileOutputStream(electionRecordProtoPath().toFile())) {
      electionRecordProto.writeTo(out);
    }
  }

  /** Publishes the ballot Encryptions part of election record as proto. */
  public void writeEncryptionResultsProto(
          ElectionRecord electionRecord,
          Iterable<SubmittedBallot> submittedBallots) throws IOException {

    if (createMode == Mode.readonly) {
      throw new UnsupportedOperationException("Trying to write to readonly election record");
    }

    // the accepted ballots are written into their own file
    int count = 0;
    try (FileOutputStream out = new FileOutputStream(submittedBallotProtoPath().toFile())) {
      for (SubmittedBallot ballot : submittedBallots) {
        CiphertextBallotProto.SubmittedBallot ballotProto = SubmittedBallotToProto.translateToProto(ballot);
        ballotProto.writeDelimitedTo(out);
        count++;
      }
    }
    System.out.printf("Save %d accepted ballots in %s%n", count, submittedBallotProtoPath());

    /*
    ElectionRecordProto1.ElectionRecord electionRecordProto = ElectionRecordToProto.buildElectionRecord(
            electionRecord.manifest,
            electionRecord.constants,
            electionRecord.context,
            electionRecord.guardianRecords,
            devices, null, null, null);

    try (FileOutputStream out = new FileOutputStream(electionRecordProtoPath().toFile())) {
      electionRecordProto.writeTo(out);
    } */
  }

  /** Adds the encryptedTally to the election record. */
  public void writeEncryptedTallyProto(ElectionRecord electionRecord,
                                       CiphertextTally encryptedTally) throws IOException {

    if (createMode == Mode.readonly) {
      throw new UnsupportedOperationException("Trying to write to readonly election record");
    }

    /* ElectionRecordProto1.ElectionRecord electionRecordProto = ElectionRecordToProto.buildElectionRecord(
            electionRecord.manifest, electionRecord.constants, electionRecord.context,
            electionRecord.guardianRecords,
            electionRecord.devices,
            encryptedTally,
            null, null);

    try (FileOutputStream out = new FileOutputStream(electionRecordProtoPath().toFile())) {
      electionRecordProto.writeTo(out);
    } */
  }

  public void writeDecryptedTallyProto(ElectionRecord electionRecord,
                                       PlaintextTally decryptedTally) throws IOException {

    if (createMode == Mode.readonly) {
      throw new UnsupportedOperationException("Trying to write to readonly election record");
    }

    /* ElectionRecordProto1.ElectionRecord ElectionRecordProto = ElectionRecordToProto.buildElectionRecord(
            electionRecord.manifest, electionRecord.constants, electionRecord.context,
            electionRecord.guardianRecords, electionRecord.devices,
            electionRecord.ciphertextTally,
            decryptedTally,
            null);

    try (FileOutputStream out = new FileOutputStream(electionRecordProtoPath().toFile())) {
      ElectionRecordProto.writeTo(out);
    } */
  }

  /** Publishes the ballot and tally Decryptions election record as proto. */
  public void writeDecryptionResultsProto(
          ElectionRecord electionRecord,
          CiphertextTally encryptedTally,
          PlaintextTally decryptedTally,
          Iterable<PlaintextTally> spoiledBallots,
          Iterable<AvailableGuardian> availableGuardians) throws IOException {

    if (createMode == Mode.readonly) {
      throw new UnsupportedOperationException("Trying to write to readonly election record");
    }

    if (spoiledBallots != null) {
      // the spoiledBallots are written into their own file
      try (FileOutputStream out = new FileOutputStream(spoiledBallotProtoPath().toFile())) {
        for (PlaintextTally ballot : spoiledBallots) {
          PlaintextTallyProto.PlaintextTally ballotProto = PlaintextTallyToProto.publishPlaintextTally(ballot);
          ballotProto.writeDelimitedTo(out);
        }
      }
    }

    /* ElectionRecordProto1.ElectionRecord electionRecordProto = ElectionRecordToProto.buildElectionRecord(
            electionRecord.manifest, electionRecord.constants, electionRecord.context,
            electionRecord.guardianRecords, electionRecord.devices,
            encryptedTally,
            decryptedTally,
            availableGuardians);

    try (FileOutputStream out = new FileOutputStream(electionRecordProtoPath().toFile())) {
      electionRecordProto.writeTo(out);
    } */
  }

  /** Publishes the entire election record as proto. */
  public void writeElectionRecordProto(
          Manifest description,
          ElectionCryptoContext context,
          ElectionConstants constants,
          Iterable<GuardianRecord> guardianRecords,
          Iterable<Encrypt.EncryptionDevice> devices,
          Iterable<SubmittedBallot> accepted_ballots,
          CiphertextTally ciphertext_tally,
          PlaintextTally decryptedTally,
          @Nullable Iterable<PlaintextTally> spoiledBallots,
          Iterable<AvailableGuardian> availableGuardians) throws IOException {

    if (createMode == Mode.readonly) {
      throw new UnsupportedOperationException("Trying to write to readonly election record");
    }

    // the accepted ballots are written into their own file
    try (FileOutputStream out = new FileOutputStream(submittedBallotProtoPath().toFile())) {
      for (SubmittedBallot ballot : accepted_ballots) {
        CiphertextBallotProto.SubmittedBallot ballotProto = SubmittedBallotToProto.translateToProto(ballot);
        ballotProto.writeDelimitedTo(out);
      }
    }

    if (spoiledBallots != null) {
      // the spoiledBallots are written into their own file
      try (FileOutputStream out = new FileOutputStream(spoiledBallotProtoPath().toFile())) {
        for (PlaintextTally ballot : spoiledBallots) {
          PlaintextTallyProto.PlaintextTally ballotProto = PlaintextTallyToProto.publishPlaintextTally(ballot);
          ballotProto.writeDelimitedTo(out);
        }
      }
    }

    ElectionRecordProto1.ElectionRecord electionRecordProto = ElectionRecordToProto.buildElectionRecord(
            description, constants, context, guardianRecords,
            devices,
            ciphertext_tally,
            decryptedTally,
            availableGuardians);

    try (FileOutputStream out = new FileOutputStream(electionRecordProtoPath().toFile())) {
      electionRecordProto.writeTo(out);
    }
  }

  /** Copy accepted ballots file from the inputDir to this election record. */
  public void copyAcceptedBallots(String inputDir) throws IOException {
    if (createMode == Mode.readonly) {
      throw new UnsupportedOperationException("Trying to write to readonly election record");
    }
    Path source = new PublisherOld(inputDir, Mode.writeonly).submittedBallotProtoPath();
    Path dest = submittedBallotProtoPath();
    if (source.equals(dest)) {
      return;
    }

    System.out.printf("Copy AcceptedBallots from %s to %s%n", source, dest);
    Files.copy(source, dest, StandardCopyOption.COPY_ATTRIBUTES);
  }

  public PrivateData makePrivateData(boolean removeAllFiles, boolean createDirs) throws IOException {
    return new PrivateData(this.topdir, removeAllFiles, createDirs);
  }

}
