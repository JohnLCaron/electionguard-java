package com.sunya.electionguard.publish;

import com.google.common.collect.AbstractIterator;
import com.sunya.electionguard.*;
import com.sunya.electionguard.proto.CiphertextBallotFromProto;
import com.sunya.electionguard.proto.CiphertextBallotProto;
import com.sunya.electionguard.proto.ElectionRecordFromProto;
import com.sunya.electionguard.verifier.ElectionRecord;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static com.sunya.electionguard.Ballot.CiphertextAcceptedBallot;

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

  public List<Ballot.CiphertextAcceptedBallot> ballots() throws IOException {
    List<Ballot.CiphertextAcceptedBallot> result = new ArrayList<>();
    for (File file : publisher.ballotFiles()) {
      Ballot.CiphertextAcceptedBallot fromPython = ConvertFromJson.readCiphertextBallot(file.getAbsolutePath());
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
            CloseableIterableAdapter.wrap(this.ballots()),
            this.ciphertextTally(),
            this.decryptedTally());
  }

  ////

  public ElectionRecord readElectionRecordProto() throws IOException {
    ElectionRecord fromProto = ElectionRecordFromProto.read(publisher.electionRecordProtoPath().toString());
    return fromProto.setBallots(ballotsProto());
  }

  public CloseableIterable<Ballot.CiphertextAcceptedBallot> ballotsProto() {
    return () -> new ProtoIterator(publisher.ciphertextBallotProtoPath().toString());
  }

  private static class ProtoIterator extends AbstractIterator<CiphertextAcceptedBallot>
                                     implements CloseableIterator<CiphertextAcceptedBallot> {
    private final String filename;
    private FileInputStream input;
    private int count = 0;

    ProtoIterator(String filename) {
      this.filename = filename;
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
        count++;
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

}
