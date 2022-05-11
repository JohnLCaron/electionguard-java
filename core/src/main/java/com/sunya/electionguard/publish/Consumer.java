package com.sunya.electionguard.publish;

import com.google.common.collect.AbstractIterator;
import com.sunya.electionguard.*;
import com.sunya.electionguard.json.JsonConsumer;
import com.sunya.electionguard.protoconvert.ElectionConfigConvert;
import com.sunya.electionguard.protoconvert.ElectionInitializedConvert;
import com.sunya.electionguard.protoconvert.ElectionResultsConvert;
import com.sunya.electionguard.protoconvert.SubmittedBallotFromProto;
import com.sunya.electionguard.protoconvert.PlaintextBallotFromProto;
import com.sunya.electionguard.protoconvert.PlaintextTallyFromProto;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Formatter;
import java.util.function.Predicate;

import electionguard.protogen.*;


/** Helper class for consumers of published election records in Json or protobuf. */
public class Consumer {
  public final ElectionRecordPath path;

  public Consumer(String topDir) throws IOException {
    path = new ElectionRecordPath(topDir);
  }

  public boolean isValidElectionRecord(Formatter error) {
    if (!Files.exists(Path.of(path.getTopDir()))) {
      error.format("%s does not exist", path.getTopDir());
      return false;
    }
    return true;
  }

  public ElectionRecord readElectionRecord() throws IOException {
    if (Files.exists(Path.of(path.decryptionResultPath()))) {
      return new ElectionRecordFromProto(readDecryptionResult(), this);
    }
    if (Files.exists(Path.of(path.tallyResultPath()))) {
      return new ElectionRecordFromProto(readTallyResult(), this);
    }
    if (Files.exists(Path.of(path.electionInitializedPath()))) {
      return new ElectionRecordFromProto(readElectionInitialized(), this);
    }
    if (Files.exists(Path.of(path.electionConfigPath()))) {
      return new ElectionRecordFromProto(readElectionConfig());
    }

    JsonConsumer jsonConsumer = new JsonConsumer(path.getTopDir());
    return jsonConsumer.readElectionRecord();
  }

  public electionguard.ballot.ElectionConfig readElectionConfig() throws IOException {
    return ElectionConfigConvert.read(path.electionConfigPath());
  }

  public electionguard.ballot.ElectionInitialized readElectionInitialized() throws IOException {
    return ElectionInitializedConvert.read(path.electionInitializedPath());
  }

  public electionguard.ballot.TallyResult readTallyResult() throws IOException {
    ElectionRecordProto.TallyResult proto;
    try (FileInputStream inp = new FileInputStream(path.tallyResultPath())) {
      proto = ElectionRecordProto.TallyResult.parseFrom(inp);
    }
    return ElectionResultsConvert.importTallyResult(proto);
  }

  public electionguard.ballot.DecryptionResult readDecryptionResult() throws IOException {
    ElectionRecordProto.DecryptionResult proto;
    try (FileInputStream inp = new FileInputStream(path.decryptionResultPath())) {
      proto = ElectionRecordProto.DecryptionResult.parseFrom(inp);
    }
    return ElectionResultsConvert.importDecryptionResult(proto);
  }

  // all submitted ballots cast or spoiled
  public CloseableIterable<SubmittedBallot> iterateSubmittedBallots() {
    if (Files.exists(Path.of(path.submittedBallotPath()))) {
      return () -> new SubmittedBallotIterator(path.submittedBallotPath(),
              b -> true);
    } else {
      return CloseableIterableAdapter.empty();
    }
  }

  // all submitted ballots cast only
  public CloseableIterable<SubmittedBallot> iterateCastBallots() {
    if (Files.exists(Path.of(path.submittedBallotPath()))) {
      return () -> new SubmittedBallotIterator(path.submittedBallotPath(),
              b -> b.getState() == CiphertextBallotProto.SubmittedBallot.BallotState.CAST);
    } else {
      return CloseableIterableAdapter.empty();
    }
  }

  // all submitted ballots spoiled only
  public CloseableIterable<SubmittedBallot> iterateSpoiledBallots() {
    if (Files.exists(Path.of(path.submittedBallotPath()))) {
      return () -> new SubmittedBallotIterator(path.submittedBallotPath(),
              b -> b.getState() == CiphertextBallotProto.SubmittedBallot.BallotState.SPOILED);
    } else {
      return CloseableIterableAdapter.empty();
    }
  }

  public CloseableIterable<PlaintextTally> iterateSpoiledBallotTallies() {
    if (Files.exists(Path.of(path.spoiledBallotPath()))) {
      return () -> new PlaintextTallyIterator(path.spoiledBallotPath());
    } else {
      return CloseableIterableAdapter.empty();
    }
  }

  public CloseableIterable<PlaintextBallot> iteratePlaintextBallots(String ballotDir, Predicate<PlaintextBallot> filter) {
    if (Files.exists(Path.of(path.plaintextBallotPath(ballotDir)))) {
      return () -> new PlaintextBallotIterator(path.plaintextBallotPath(ballotDir), filter);
    } else {
      return CloseableIterableAdapter.empty();
    }
  }

  // These create iterators, so that we never have to read in all ballots at once.
  // Making them Closeable makes sure that the FileInputStream gets closed.

  private static class SubmittedBallotIterator extends AbstractIterator<SubmittedBallot>
                                     implements CloseableIterator<SubmittedBallot> {
    private final String filename;
    private final Predicate<CiphertextBallotProto.SubmittedBallot> filter;
    private FileInputStream input;
    SubmittedBallotIterator(String filename, Predicate<CiphertextBallotProto.SubmittedBallot> filter) {
      this.filename = filename;
      this.filter = filter;
    }

    @Override
    protected SubmittedBallot computeNext() {
      try {
        if (input == null) {
          this.input = new FileInputStream(filename);
        }
        while (true) {
          CiphertextBallotProto.SubmittedBallot ballotProto = CiphertextBallotProto.SubmittedBallot.parseDelimitedFrom(input);
          if (ballotProto == null) {
            input.close();
            return endOfData();
          }
          if (!filter.test(ballotProto)) {
            continue; // skip it
          }
          return SubmittedBallotFromProto.translateFromProto(ballotProto);
        }
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
    }

    @Override
    public void close() {
      if (input != null) {
        try {
          input.close();
          input = null;
        } catch (IOException e) {
          throw new RuntimeException(e);
        }
      }
    }
  }

  private static class PlaintextBallotIterator extends AbstractIterator<PlaintextBallot>
          implements CloseableIterator<PlaintextBallot> {
    private final String filename;
    private final Predicate<PlaintextBallot> filter;
    private FileInputStream input;

    PlaintextBallotIterator(String filename, Predicate<PlaintextBallot> filter) {
      this.filename = filename;
      this.filter = filter;
    }

    @Override
    protected PlaintextBallot computeNext() {
      try {
        if (input == null) {
          this.input = new FileInputStream(filename);
        }
        while (true) {
          PlaintextBallotProto.PlaintextBallot ballotProto = PlaintextBallotProto.PlaintextBallot.parseDelimitedFrom(input);
          if (ballotProto == null) {
            input.close();
            return endOfData();
          }
          PlaintextBallot ballot = PlaintextBallotFromProto.translateFromProto(ballotProto);
          if (!filter.test(ballot)) {
            continue; // skip it
          }
          return ballot;
        }
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
    }

    @Override
    public void close() {
      if (input != null) {
        try {
          input.close();
          input = null;
        } catch (IOException e) {
          throw new RuntimeException(e);
        }
      }
    }
  }

  private static class PlaintextTallyIterator extends AbstractIterator<PlaintextTally>
          implements CloseableIterator<PlaintextTally> {
    private final String filename;
    private FileInputStream input;

    PlaintextTallyIterator(String filename) {
      this.filename = filename;
    }

    @Override
    protected PlaintextTally computeNext() {
      try {
        if (input == null) {
          this.input = new FileInputStream(filename);
        }
        PlaintextTallyProto.PlaintextTally tallyProto = PlaintextTallyProto.PlaintextTally.parseDelimitedFrom(input);
        if (tallyProto == null) {
          input.close();
          return endOfData();
        }
        return PlaintextTallyFromProto.importPlaintextTally(tallyProto);
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
    }

    @Override
    public void close() {
      if (input != null) {
        try {
          input.close();
          input = null;
        } catch (IOException e) {
          throw new RuntimeException(e);
        }
      }
    }
  }
}
