package com.sunya.electionguard.publish;

import com.google.common.collect.AbstractIterator;
import com.sunya.electionguard.*;
import com.sunya.electionguard.proto.CiphertextBallotFromProto;
import com.sunya.electionguard.proto.CiphertextBallotProto;
import com.sunya.electionguard.proto.ElectionRecordFromProto;
import com.sunya.electionguard.proto.PlaintextBallotFromProto;
import com.sunya.electionguard.proto.PlaintextBallotProto;
import com.sunya.electionguard.proto.PlaintextTallyFromProto;
import com.sunya.electionguard.proto.PlaintextTallyProto;
import com.sunya.electionguard.verifier.ElectionRecord;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

import static com.sunya.electionguard.Ballot.CiphertextAcceptedBallot;
import static com.sunya.electionguard.Ballot.PlaintextBallot;

/** Helper class for consumers of published data in Json. */
public class Consumer {
  private final Publisher publisher;

  public Consumer(Publisher publisher) {
    this.publisher = publisher;
  }

  public Consumer(String topDir) throws IOException {
    publisher = new Publisher(topDir, false, false);
  }

  public Election.ElectionDescription election() throws IOException {
    ElectionDescriptionFromJson builder = new ElectionDescriptionFromJson(
            publisher.electionPath().toString());
    return builder.build();
  }

  public Election.CiphertextElectionContext context() throws IOException {
    return ConvertFromJson.readContext(publisher.contextPath().toString());
  }

  public Election.ElectionConstants constants() throws IOException {
    return ConvertFromJson.readConstants(publisher.constantsPath().toString());
  }

  public PlaintextTally decryptedTally() throws IOException {
    return ConvertFromJson.readPlaintextTally(publisher.tallyPath().toString());
  }

  public PublishedCiphertextTally ciphertextTally() throws IOException {
    return ConvertFromJson.readCiphertextTally(publisher.encryptedTallyPath().toString());
  }

  public List<Encrypt.EncryptionDevice> devices() throws IOException {
    List<Encrypt.EncryptionDevice> result = new ArrayList<>();
    for (File file : publisher.deviceFiles()) {
      Encrypt.EncryptionDevice fromPython = ConvertFromJson.readDevice(file.getAbsolutePath());
      result.add(fromPython);
    }
    return result;
  }

  public List<Ballot.CiphertextAcceptedBallot> acceptedBallots() throws IOException {
    List<Ballot.CiphertextAcceptedBallot> result = new ArrayList<>();
    for (File file : publisher.ballotFiles()) {
      Ballot.CiphertextAcceptedBallot fromPython = ConvertFromJson.readCiphertextBallot(file.getAbsolutePath());
      result.add(fromPython);
    }
    return result;
  }

  // Decrypted, spoiled ballots
  public List<Ballot.PlaintextBallot> spoiledBallots() throws IOException {
    List<Ballot.PlaintextBallot> result = new ArrayList<>();
    for (File file : publisher.spoiledBallotFiles()) {
      Ballot.PlaintextBallot fromPython = ConvertFromJson.readPlaintextBallot(file.getAbsolutePath());
      result.add(fromPython);
    }
    return result;
  }

  public List<KeyCeremony.CoefficientValidationSet> guardianCoefficients() throws IOException {
    List<KeyCeremony.CoefficientValidationSet> result = new ArrayList<>();
    for (File file : publisher.coefficientsFiles()) {
      KeyCeremony.CoefficientValidationSet fromPython = ConvertFromJson.readCoefficient(file.getAbsolutePath());
      result.add(fromPython);
    }
    return result;
  }

  public ElectionRecord readElectionRecordJson() throws IOException {
    return new ElectionRecord(
            this.constants(),
            this.context(),
            this.election(),
            this.guardianCoefficients(),
            this.devices(),
            this.ciphertextTally(),
            this.decryptedTally(),
            CloseableIterableAdapter.wrap(this.acceptedBallots()),
            CloseableIterableAdapter.wrap(this.spoiledBallots()),
            null); // TODO
  }

  /////////////////////////////////////////////////////////////////////

  public ElectionRecord readElectionRecordProto() throws IOException {
    ElectionRecord fromProto = ElectionRecordFromProto.read(publisher.electionRecordProtoPath().toString());
    return fromProto.setBallots(acceptedBallotsProto(), decryptedSpoiledBallotsProto(), decryptedSpoiledTalliesProto());
  }

  public CloseableIterable<Ballot.CiphertextAcceptedBallot> acceptedBallotsProto() {
    return () -> new CiphertextAcceptedBallotIterator(publisher.ciphertextBallotProtoPath().toString(), b -> true);
  }

  public CloseableIterable<Ballot.CiphertextAcceptedBallot> spoiledBallotsProto() {
    return () -> new CiphertextAcceptedBallotIterator(publisher.ciphertextBallotProtoPath().toString(),
            b -> b.getState() == CiphertextBallotProto.CiphertextAcceptedBallot.BallotBoxState.SPOILED);
  }

  public CloseableIterable<Ballot.PlaintextBallot> decryptedSpoiledBallotsProto() {
    return () -> new PlaintextBallotIterator(publisher.spoiledBallotProtoPath().toString());
  }

  public CloseableIterable<PlaintextTally> decryptedSpoiledTalliesProto() {
    return () -> new PlaintextTallyIterator(publisher.spoiledTallyProtoPath().toString());
  }

  // These create iterators, so we never have to read in all ballots at once.
  // Making them Closeable makes sure that the FileInputStream gets closed.

  private static class CiphertextAcceptedBallotIterator extends AbstractIterator<CiphertextAcceptedBallot>
                                     implements CloseableIterator<CiphertextAcceptedBallot> {
    private final String filename;
    private final Predicate<CiphertextBallotProto.CiphertextAcceptedBallot> filter;
    private FileInputStream input;
    CiphertextAcceptedBallotIterator(String filename, Predicate<CiphertextBallotProto.CiphertextAcceptedBallot> filter) {
      this.filename = filename;
      this.filter = filter;
    }

    @Override
    protected CiphertextAcceptedBallot computeNext() {
      try {
        if (input == null) {
          this.input = new FileInputStream(filename);
        }
        CiphertextBallotProto.CiphertextAcceptedBallot ballotProto = CiphertextBallotProto.CiphertextAcceptedBallot.parseDelimitedFrom(input);
        if (ballotProto == null) {
          input.close();
          return endOfData();
        }
        if (!filter.test(ballotProto)) {
          return computeNext(); // LOOK fix recursion
        }
        return CiphertextBallotFromProto.translateFromProto(ballotProto);
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
    }

    @Override
    public void close() throws IOException {
      if (input != null) {
        input.close();
        input = null;
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
    public void close() throws IOException {
      if (input != null) {
        input.close();
        input = null;
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
        return PlaintextTallyFromProto.translateFromProto(tallyProto);
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
    }

    @Override
    public void close() throws IOException {
      if (input != null) {
        input.close();
        input = null;
      }
    }
  }

}
