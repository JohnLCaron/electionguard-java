package com.sunya.electionguard.publish;

import com.google.common.collect.AbstractIterator;
import com.sunya.electionguard.*;
import com.sunya.electionguard.protoconvert.SubmittedBallotFromProto;
import com.sunya.electionguard.protoconvert.ElectionRecordFromProto;
import com.sunya.electionguard.protoconvert.PlaintextBallotFromProto;
import com.sunya.electionguard.protoconvert.PlaintextTallyFromProto;
import com.sunya.electionguard.verifier.ElectionRecord;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Formatter;
import java.util.function.Predicate;

import electionguard.protogen.*;


/** Helper class for consumers of published election records in Json or protobuf. */
public class Consumer {
  private final Publisher publisher;

  public Consumer(Publisher publisher) {
    this.publisher = publisher;
  }

  public Consumer(String topDir) throws IOException {
    publisher = new Publisher(topDir, Publisher.Mode.readonly);
  }

  public static Consumer fromElectionRecord(String electionRecordDir) throws IOException {
    return new Consumer(new Publisher(Path.of(electionRecordDir), Publisher.Mode.readonly));
  }

  public String location() {
    return publisher.publishPath().toAbsolutePath().toString();
  }

  public boolean isValidElectionRecord(Formatter error) {
    if (!Files.exists(publisher.publishPath())) {
      error.format("%s does not exist", publisher.publishPath());
      return false;
    }
    return true;
  }

  public ElectionRecord readElectionRecord() throws IOException {
    ElectionRecord result;
    if (Files.exists(publisher.electionRecordProtoPath())) {
      result = readElectionRecordProto();
    } else {
      throw new FileNotFoundException(String.format("No election record found in %s", publisher.publishPath()));
    }
    return result;
  }

  public Manifest readManifest() throws IOException {
    if (Files.exists(publisher.electionRecordProtoPath())) {
      return readElectionRecordProto().manifest;
    } else {
      return null;
    }
  }

  // reads everything
  public ElectionRecord readElectionRecordProto() throws IOException {
    ElectionRecord fromProto = ElectionRecordFromProto.read(publisher.electionRecordProtoPath().toString());
    return fromProto.setBallots(submittedAllBallotsProto(), decryptedSpoiledBallotsProto());
  }

  // all submitted ballots cast or spoiled
  public CloseableIterable<SubmittedBallot> submittedAllBallotsProto() {
    if (Files.exists(publisher.submittedBallotProtoPath())) {
      return () -> new SubmittedBallotIterator(publisher.submittedBallotProtoPath().toString(),
              b -> true);
    } else {
      return CloseableIterableAdapter.empty();
    }
  }

  // all submitted ballots spoiled only
  public CloseableIterable<SubmittedBallot> submittedSpoiledBallotsProto() {
    if (Files.exists(publisher.submittedBallotProtoPath())) {
      return () -> new SubmittedBallotIterator(publisher.submittedBallotProtoPath().toString(),
              b -> b.getState() == CiphertextBallotProto.SubmittedBallot.BallotState.SPOILED);
    } else {
      return CloseableIterableAdapter.empty();
    }
  }

  public CloseableIterable<PlaintextTally> decryptedSpoiledBallotsProto() {
    if (Files.exists(publisher.spoiledBallotProtoPath())) {
      return () -> new PlaintextTallyIterator(publisher.spoiledBallotProtoPath().toString());
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
    private FileInputStream input;

    PlaintextBallotIterator(String filename) {
      this.filename = filename;
    }

    @Override
    protected PlaintextBallot computeNext() {
      try {
        if (input == null) {
          this.input = new FileInputStream(filename);
        }
        PlaintextBallotProto.PlaintextBallot ballotProto = PlaintextBallotProto.PlaintextBallot.parseDelimitedFrom(input);
        if (ballotProto == null) {
          input.close();
          return endOfData();
        }
        return PlaintextBallotFromProto.translateFromProto(ballotProto);
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
