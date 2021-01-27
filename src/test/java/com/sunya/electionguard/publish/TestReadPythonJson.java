package com.sunya.electionguard.publish;

import com.sunya.electionguard.*;
import net.jqwik.api.Example;
import net.jqwik.api.lifecycle.BeforeContainer;

import java.io.File;
import java.io.IOException;

import static com.google.common.truth.Truth.assertThat;

public class TestReadPythonJson {
  private static final String pythonPublish = "src/test/data/python-publish/";
  private static final String pythonModified = "src/test/data/python-modified/";
  private static Publisher publisher;

  @BeforeContainer
  public static void setup() throws IOException {
    publisher = new Publisher(pythonModified, false);
  }

  @Example
  public void testConstantsPythonJson() throws IOException {
    Election.ElectionConstants fromPython = ConvertFromJson.readConstants(publisher.constantsFile().toString());
    assertThat(fromPython).isNotNull();
    System.out.printf("%s%n", fromPython);
  }

  @Example
  public void testContextPythonJson() throws IOException {
    Election.CiphertextElectionContext fromPython = ConvertFromJson.readContext(publisher.contextFile().toString());
    assertThat(fromPython).isNotNull();
    System.out.printf("%s%n", fromPython);
  }

  @Example
  public void testElectionPythonJson() throws IOException {
    Election.ElectionDescription fromPython = ConvertFromJson.readElection(publisher.electionFile().toString());
    assertThat(fromPython).isNotNull();
    System.out.printf("%s%n", fromPython);
  }

  @Example
  public void testEncryptedTallyPythonJson() throws IOException {
    PublishedCiphertextTally fromPython = ConvertFromJson.readCiphertextTally(publisher.encryptedTallyFile().toString());
    assertThat(fromPython).isNotNull();
    System.out.printf("%s%n", fromPython);
  }

  @Example
  public void testPlaintextTallyPythonJson() throws IOException {
    PlaintextTally fromPython = ConvertFromJson.readPlaintextTally(publisher.tallyFile().toString());
    assertThat(fromPython).isNotNull();
    System.out.printf("%s%n", fromPython);
  }

  @Example
  public void testDevicesPythonJson() throws IOException {
    for (File file : publisher.deviceFiles()) {
      Encrypt.EncryptionDevice fromPython = ConvertFromJson.readDevice(file.getAbsolutePath());
      assertThat(fromPython).isNotNull();
      System.out.printf("%s%n", fromPython);
    }
  }

  @Example
  public void testCoefficientsPythonJson() throws IOException {
    for (File file : publisher.coefficientsFiles()) {
      System.out.printf(" testCoefficientsPythonJson %s%n", file);

      KeyCeremony.CoefficientValidationSet fromPython = ConvertFromJson.readCoefficient(file.getAbsolutePath());
      assertThat(fromPython).isNotNull();
      System.out.printf("%s%n", fromPython);
    }
  }

  @Example
  public void testBallotsPythonJson() throws IOException {
    for (File file : publisher.ballotFiles()) {
      Ballot.CiphertextAcceptedBallot fromPython = ConvertFromJson.readCiphertextBallot(file.getAbsolutePath());
      assertThat(fromPython).isNotNull();
      System.out.printf("%s%n", fromPython);
    }
  }

  /* LOOK @Example
  public void testSpoiledPythonJson() throws IOException {
    for (File file : publisher.spoiledFiles()) {
      Ballot.PlaintextBallot fromPython = ConvertFromJson.readPlaintextBallot(file.getAbsolutePath());
      assertThat(fromPython).isNotNull();
      System.out.printf("%s%n", fromPython);
    }
  } */

  // LOOK @Example
  public void testProblemPythonJson() throws IOException {
    // this is failing because Optional is encoded as "None".
    String filename = pythonPublish + "/encrypted_ballots/ballot_03a29d15-667c-4ac8-afd7-549f19b8e4eb.json";
    Ballot.CiphertextAcceptedBallot fromPython = ConvertFromJson.readCiphertextBallot(filename);
    assertThat(fromPython).isNotNull();
    System.out.printf("%s%n", fromPython);
  }

}
