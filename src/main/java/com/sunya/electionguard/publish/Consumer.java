package com.sunya.electionguard.publish;

import com.google.common.collect.AbstractIterator;
import com.google.common.collect.ImmutableList;
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
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/** Helper class for consumers of published election records in Json or protobuf. */
public class Consumer {
  private final Publisher publisher;

  public Consumer(Publisher publisher) {
    this.publisher = publisher;
  }

  public Consumer(String topDir) throws IOException {
    publisher = new Publisher(topDir, false, false);
  }

  public ElectionRecord readElectionRecord() throws IOException {
    if (Files.exists(publisher.electionRecordProtoPath())) {
      return readElectionRecordProto();
    } else {
      return readElectionRecordJson();
    }
  }

  public Election readElectionDescription() throws IOException {
    if (Files.exists(publisher.electionRecordProtoPath())) {
      return readElectionRecordProto().election;
    } else {
      return election();
    }
  }

  //////////////////// Json

  public Election election() throws IOException {
    return ConvertFromJson.readElection(publisher.electionPath().toString());
  }

  public CiphertextElectionContext context() throws IOException {
    return ConvertFromJson.readContext(publisher.contextPath().toString());
  }

  public ElectionConstants constants() throws IOException {
    return ConvertFromJson.readConstants(publisher.constantsPath().toString());
  }

  public PlaintextTally decryptedTally() throws IOException {
    return ConvertFromJson.readPlaintextTally(publisher.tallyPath().toString());
  }

  public CiphertextTally ciphertextTally() throws IOException {
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

  public List<CiphertextAcceptedBallot> acceptedBallots() throws IOException {
    List<CiphertextAcceptedBallot> result = new ArrayList<>();
    for (File file : publisher.ballotFiles()) {
      CiphertextAcceptedBallot fromPython = ConvertFromJson.readCiphertextBallot(file.getAbsolutePath());
      result.add(fromPython);
    }
    return result;
  }

  // Decrypted, spoiled ballots
  // LOOK python is not writing these
  public List<PlaintextBallot> spoiledBallots() throws IOException {
    List<PlaintextBallot> result = new ArrayList<>();
    for (File file : publisher.spoiledBallotFiles()) {
      PlaintextBallot fromPython = ConvertFromJson.readPlaintextBallot(file.getAbsolutePath());
      result.add(fromPython);
    }
    return result;
  }

  // Input ballot files for debugging. Not part of the election record.
  public List<PlaintextBallot> inputBallots(String ballotDir) throws IOException {
    File ballotDirFile = new File(ballotDir);
    if (Files.exists(ballotDirFile.toPath()) && ballotDirFile.listFiles() != null) {
      List<PlaintextBallot> result = new ArrayList<>();
      for (File file : ballotDirFile.listFiles()) {
        PlaintextBallot fromJson = ConvertFromJson.readPlaintextBallot(file.getAbsolutePath());
        result.add(fromJson);
      }
      return result;
    } else {
      return new ArrayList<>();
    }
  }

  // Decrypted, spoiled tallies
  // LOOK python has not yet committed PR#305, which removes spoiled_ballots from PlaintextTally.
  //   we have already done so, and publish as json in "spoiled_tallies" directory, which they may not.
  //   for now, we will wait to see what they do, at the cost of not getting spoiledTallies from python.
  public List<PlaintextTally> spoiledTallies() throws IOException {
    List<PlaintextTally> result = new ArrayList<>();
    for (File file : publisher.spoiledTallyFiles()) {
      PlaintextTally fromPython = ConvertFromJson.readPlaintextTally(file.getAbsolutePath());
      result.add(fromPython);
    }
    return result;
    // return result.size() > 0 ? result : spoiledTalliesOld();
  }

  private List<PlaintextTally> spoiledTalliesOld() {
    // PlaintextTallyPojo.deserializeSpoiledTalliesOld(null);
    return null;
  }

  public List<KeyCeremony.CoefficientValidationSet> guardianCoefficients() throws IOException {
    List<KeyCeremony.CoefficientValidationSet> result = new ArrayList<>();
    for (File file : publisher.coefficientsFiles()) {
      KeyCeremony.CoefficientValidationSet fromPython = ConvertFromJson.readCoefficientValidation(file.getAbsolutePath());
      result.add(fromPython);
    }
    return result;
  }

  public ElectionRecord readElectionRecordJson() throws IOException {
    return new ElectionRecord(
            this.constants(), // required
            this.context(), // required
            this.election(), // required
            this.guardianCoefficients(), // required
            this.devices(),
            this.ciphertextTally(),
            this.decryptedTally(),
            CloseableIterableAdapter.wrap(this.acceptedBallots()),
            CloseableIterableAdapter.wrap(this.spoiledBallots()),
            CloseableIterableAdapter.wrap(this.spoiledTallies()));
  }

  ///////////////////////////////////////////// Proto

  public ElectionRecord readElectionRecordProto() throws IOException {
    ElectionRecord fromProto = ElectionRecordFromProto.read(publisher.electionRecordProtoPath().toString());
    return fromProto.setBallots(acceptedBallotsProto(), decryptedSpoiledBallotsProto(), decryptedSpoiledTalliesProto());
  }

  public CloseableIterable<CiphertextAcceptedBallot> acceptedBallotsProto() {
    if (Files.exists(publisher.ciphertextBallotProtoPath())) {
      return () -> new CiphertextAcceptedBallotIterator(publisher.ciphertextBallotProtoPath().toString(), b -> true);
    } else {
      return CloseableIterableAdapter.empty();
    }
  }

  public CloseableIterable<CiphertextAcceptedBallot> spoiledBallotsProto() {
    if (Files.exists(publisher.ciphertextBallotProtoPath())) {
      return () -> new CiphertextAcceptedBallotIterator(publisher.ciphertextBallotProtoPath().toString(),
              b -> b.getState() == CiphertextBallotProto.CiphertextAcceptedBallot.BallotBoxState.SPOILED);
    } else {
      return CloseableIterableAdapter.empty();
    }
  }

  public CloseableIterable<PlaintextBallot> decryptedSpoiledBallotsProto() {
    if (Files.exists(publisher.spoiledBallotProtoPath())) {
      return () -> new PlaintextBallotIterator(publisher.spoiledBallotProtoPath().toString());
    } else {
      return CloseableIterableAdapter.empty();
    }
  }

  public CloseableIterable<PlaintextTally> decryptedSpoiledTalliesProto() {
    if (Files.exists(publisher.spoiledTallyProtoPath())) {
      return () -> new PlaintextTallyIterator(publisher.spoiledTallyProtoPath().toString());
    } else {
      return CloseableIterableAdapter.empty();
    }
  }

  // These create iterators, so that we never have to read in all ballots at once.
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
