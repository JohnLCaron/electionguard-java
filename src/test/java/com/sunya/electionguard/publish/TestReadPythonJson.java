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
    publisher = new Publisher(pythonModified, false, false);
    System.out.printf("TestReadPythonJson from %s%n", publisher.publishPath());
  }

  @Example
  public void testConstantsPythonJson() throws IOException {
    ElectionConstants fromPython = ConvertFromJson.readConstants(publisher.constantsPath().toString());
    assertThat(fromPython).isNotNull();
  }

  @Example
  public void testContextPythonJson() throws IOException {
    CiphertextElectionContext fromPython = ConvertFromJson.readContext(publisher.contextPath().toString());
    assertThat(fromPython).isNotNull();
    System.out.printf("CiphertextElectionContext %s%n", fromPython);
  }

  @Example
  public void testElectionPythonJson() throws IOException {
    Election.ElectionDescription fromPython = ConvertFromJson.readElection(publisher.electionPath().toString());
    assertThat(fromPython).isNotNull();
    System.out.printf("ElectionDescription %s%n", fromPython.election_scope_id);
  }

  @Example
  public void testEncryptedTallyPythonJson() throws IOException {
    CiphertextTally fromPython = ConvertFromJson.readCiphertextTally(publisher.encryptedTallyPath().toString());
    assertThat(fromPython).isNotNull();
    System.out.printf("EncryptedTallyPythonJson %s%n", fromPython);
  }

  @Example
  public void testPlaintextTallyPythonJson() throws IOException {
    PlaintextTally fromPython = ConvertFromJson.readPlaintextTally(publisher.tallyPath().toString());
    assertThat(fromPython).isNotNull();
    System.out.printf("PlaintextTallyPythonJson %s%n", fromPython);
  }

  @Example
  public void testDevicesPythonJson() throws IOException {
    for (File file : publisher.deviceFiles()) {
      Encrypt.EncryptionDevice fromPython = ConvertFromJson.readDevice(file.getAbsolutePath());
      assertThat(fromPython).isNotNull();
      System.out.printf("deviceFiles %s%n", fromPython.location);
    }
  }

  @Example
  public void testCoefficientsPythonJson() throws IOException {
    for (File file : publisher.coefficientsFiles()) {
      KeyCeremony.CoefficientValidationSet fromPython = ConvertFromJson.readCoefficient(file.getAbsolutePath());
      assertThat(fromPython).isNotNull();
      System.out.printf(" CoefficientValidationSet %s%n", fromPython.owner_id());
    }
  }

  @Example
  public void testBallotsPythonJson() throws IOException {
    for (File file : publisher.ballotFiles()) {
      CiphertextAcceptedBallot fromPython = ConvertFromJson.readCiphertextBallot(file.getAbsolutePath());
      assertThat(fromPython).isNotNull();
      System.out.printf("ballotFiles %s%n", fromPython.object_id);
    }
  }

  @Example
  public void testSpoiledPythonJson() throws IOException {
    for (File file : publisher.spoiledBallotFiles()) {
      PlaintextBallot fromPython = ConvertFromJson.readPlaintextBallot(file.getAbsolutePath());
      assertThat(fromPython).isNotNull();
      System.out.printf("spoiledBallotFiles %s%n", fromPython.object_id);
    }
  }

  // LOOK @Example
  public void testProblemPythonJson() throws IOException {
    // this is failing because Optional is encoded as "None".
    String filename = pythonPublish + "/encrypted_ballots/ballot_03a29d15-667c-4ac8-afd7-549f19b8e4eb.json";
    CiphertextAcceptedBallot fromPython = ConvertFromJson.readCiphertextBallot(filename);
    assertThat(fromPython).isNotNull();
    System.out.printf("%s%n", fromPython);
  }

}
