package com.sunya.electionguard.publish;

import com.google.common.collect.AbstractIterator;
import com.sunya.electionguard.*;
import com.sunya.electionguard.proto.CiphertextBallotFromProto;
import com.sunya.electionguard.proto.ElectionRecordFromProto;
import com.sunya.electionguard.proto.PlaintextBallotFromProto;
import com.sunya.electionguard.proto.PlaintextTallyFromProto;
import com.sunya.electionguard.verifier.ElectionRecord;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Formatter;
import java.util.List;
import java.util.function.Predicate;

import com.sunya.electionguard.protogen.*;


/** Helper class for consumers of published election records in Json or protobuf. */
public class Consumer {
  private final Publisher publisher;

  public Consumer(Publisher publisher) {
    this.publisher = publisher;
  }

  public Consumer(String topDir) throws IOException {
    publisher = new Publisher(topDir, Publisher.Mode.readonly, false);
  }

  public String location() {
    return publisher.publishPath().toAbsolutePath().toString();
  }

  public boolean isValidElectionRecord(Formatter error) {
    if (!Files.exists(publisher.publishPath())) {
      error.format("%s does not exist", publisher.publishPath());
      return false;
    }
    if (!Files.exists(publisher.electionRecordProtoPath()) && !Files.exists(publisher.constantsPath())) {
      error.format("%s does not contain proto or json files", publisher.publishPath());
      return false;
    }
    return true;
  }

  public ElectionRecord readElectionRecord() throws IOException {
    ElectionRecord result;
    if (Files.exists(publisher.electionRecordProtoPath())) {
      result = readElectionRecordProto();
    } else if (Files.exists(publisher.constantsPath()))  {
      result = readElectionRecordJson();
    } else {
      throw new FileNotFoundException(String.format("No election record found in %s", publisher.publishPath()));
    }

    /* check constants
    if (!result.constants.equals(Group.getPrimes())) {
      System.out.printf("** Non-standard constants in %s%n", publisher.publishPath());
      Group.setPrimes(result.constants);
    } */
    return result;
  }

  public Manifest readManifest() throws IOException {
    if (Files.exists(publisher.electionRecordProtoPath())) {
      return readElectionRecordProto().election;
    } else {
      return election();
    }
  }

  //////////////////// Json

  public ElectionRecord readElectionRecordJson() throws IOException {
    return new ElectionRecord(
            ElectionRecord.currentVersion, // logic is that it would fail on an earlier version TODO add to Json
            this.constants(), // required
            this.context(), // required
            this.election(), // required
            this.guardianRecords(), // required
            this.devices(),
            this.ciphertextTally(),
            this.decryptedTally(),
            CloseableIterableAdapter.wrap(this.acceptedBallots()),
            CloseableIterableAdapter.wrap(this.spoiledBallots()),
            this.availableGuardians());
  }

  public Manifest election() throws IOException {
    return ConvertFromJson.readElection(publisher.manifestPath().toString());
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

  public List<SubmittedBallot> acceptedBallots() throws IOException {
    List<SubmittedBallot> result = new ArrayList<>();
    for (File file : publisher.ballotFiles()) {
      SubmittedBallot fromPython = ConvertFromJson.readSubmittedBallot(file.getAbsolutePath());
      result.add(fromPython);
    }
    return result;
  }

  public List<AvailableGuardian> availableGuardians() throws IOException {
    // TODO are these guarenteed to be in order ?
    List<GuardianRecord> grs = new ArrayList<>(guardianRecords());
    grs.sort(Comparator.comparingInt(GuardianRecord::sequence_order));
    Coefficients pojo = ConvertFromJson.readCoefficients(publisher.coefficientsPath().toString());
    // Preconditions.checkArgument(grs.size() == pojo.coefficients.size());
    List<AvailableGuardian> result = new ArrayList<>();
    for (int i = 0; i < pojo.coefficients.size(); i++) {
      GuardianRecord gr = grs.get(i);
      AvailableGuardian avail = new AvailableGuardian(gr.guardian_id(), gr.sequence_order(), pojo.coefficients.get(i));
      result.add(avail);
    }
    return result;
  }

  // Decrypted, spoiled ballots
  public List<PlaintextTally> spoiledBallots() throws IOException {
    List<PlaintextTally> result = new ArrayList<>();
    for (File file : publisher.spoiledBallotFiles()) {
      try {
        PlaintextTally fromPython = ConvertFromJson.readPlaintextTally(file.getAbsolutePath());
        result.add(fromPython);
      } catch (Exception e) {
        // System.out.printf("Exception %s on %s%n", e.getMessage(), file.getAbsolutePath());
      }
    }
    return result;
  }

  public List<GuardianRecord> guardianRecords() throws IOException {
    List<GuardianRecord> result = new ArrayList<>();
    for (File file : publisher.guardianRecordsFiles()) {
      GuardianRecord fromPython = ConvertFromJson.readGuardianRecord(file.getAbsolutePath());
      result.add(fromPython);
    }
    return result;
  }

  ///////////////////////////////////////////// Proto

  // reads everything
  public ElectionRecord readElectionRecordProto() throws IOException {
    ElectionRecord fromProto = ElectionRecordFromProto.read(publisher.electionRecordProtoPath().toString());
    return fromProto.setBallots(submittedCastBallotsProto(), decryptedSpoiledBallotsProto());
  }

  // all submitted ballots cast
  public CloseableIterable<SubmittedBallot> submittedCastBallotsProto() {
    if (Files.exists(publisher.submittedBallotProtoPath())) {
      return () -> new SubmittedBallotIterator(publisher.submittedBallotProtoPath().toString(),
              b -> b.getState() == CiphertextBallotProto.SubmittedBallot.BallotBoxState.CAST);
    } else {
      return CloseableIterableAdapter.empty();
    }
  }

  public CloseableIterable<SubmittedBallot> submittedSpoiledBallotsProto() {
    if (Files.exists(publisher.submittedBallotProtoPath())) {
      return () -> new SubmittedBallotIterator(publisher.submittedBallotProtoPath().toString(),
              b -> b.getState() == CiphertextBallotProto.SubmittedBallot.BallotBoxState.SPOILED);
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
        CiphertextBallotProto.SubmittedBallot ballotProto = CiphertextBallotProto.SubmittedBallot.parseDelimitedFrom(input);
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
        return PlaintextTallyFromProto.translateFromProto(tallyProto);
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
