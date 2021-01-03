package com.sunya.electionguard.publish;

import com.sunya.electionguard.*;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/** helper class for consumers of published data */
public class Consumer {
  private final Publisher publisher;
  public Consumer(String topDir) throws IOException {
    publisher = new Publisher(topDir);
  }

  public Election.ElectionDescription election() throws IOException {
    ElectionDescriptionFromJson builder = new ElectionDescriptionFromJson(
            publisher.electionFile().toString());
    return builder.build();
  }

  public Election.CiphertextElectionContext context() throws IOException {
    return ConvertFromJson.readContext(publisher.contextFile().toString());
  }

  public Election.ElectionConstants constants() throws IOException {
    return ConvertFromJson.readConstants(publisher.constantsFile().toString());
  }

  public Tally.PlaintextTally plaintextTally() throws IOException {
    return ConvertFromJson.readPlaintextTally(publisher.tallyFile().toString());
  }

  public Tally.PublishedCiphertextTally ciphertextTally() throws IOException {
    return ConvertFromJson.readCiphertextTally(publisher.encryptedTallyFile().toString());
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
      Ballot.CiphertextAcceptedBallot fromPython = ConvertFromJson.readBallot(file.getAbsolutePath());
      result.add(fromPython);
    }
    return result;
  }

  public List<Ballot.CiphertextAcceptedBallot> spoiled() throws IOException {
    List<Ballot.CiphertextAcceptedBallot> result = new ArrayList<>();
    for (File file : publisher.spoiledFiles()) {
      Ballot.CiphertextAcceptedBallot fromPython = ConvertFromJson.readBallot(file.getAbsolutePath());
      result.add(fromPython);
    }
    return result;
  }

  public List<KeyCeremony.CoefficientValidationSet> coefficients() throws IOException {
    List<KeyCeremony.CoefficientValidationSet> result = new ArrayList<>();
    for (File file : publisher.coefficientsFiles()) {
      KeyCeremony.CoefficientValidationSet fromPython = ConvertFromJson.readCoefficient(file.getAbsolutePath());
      result.add(fromPython);
    }
    return result;
  }
}