package com.sunya.electionguard.publish;

import com.sunya.electionguard.*;
import com.sunya.electionguard.verifier.TestParameterVerifier;
import net.jqwik.api.Example;
import net.jqwik.api.lifecycle.BeforeContainer;

import java.io.File;
import java.io.IOException;

import static com.google.common.truth.Truth.assertThat;

public class TestReadPythonJson {
  private static final String pythonPublish = TestParameterVerifier.topdirJsonPython;
  private static Publisher publisher;

  @BeforeContainer
  public static void setup() throws IOException {
    publisher = new Publisher(pythonPublish, Publisher.Mode.readonly, true);
    System.out.printf("TestReadPythonJson from %s%n", publisher.publishPath());
  }

  @Example
  public void testCoefficientsPythonJson() throws IOException {
    Coefficients fromPython = ConvertFromJson.readCoefficients(publisher.coefficientsPath().toString());
    assertThat(fromPython).isNotNull();
    System.out.printf("%n%s%n%n", fromPython);
  }

  @Example
  public void testConstantsPythonJson() throws IOException {
    ElectionConstants fromPython = ConvertFromJson.readConstants(publisher.constantsPath().toString());
    assertThat(fromPython).isNotNull();
    assertThat(fromPython).isEqualTo(ElectionConstants.LARGE_TEST_CONSTANTS);
    System.out.printf("%n%s%n%n", fromPython);
  }

  @Example
  public void testContextPythonJson() throws IOException {
    CiphertextElectionContext fromPython = ConvertFromJson.readContext(publisher.contextPath().toString());
    assertThat(fromPython).isNotNull();
    System.out.printf("%n%s%n%n", fromPython);
  }

  @Example
  public void testEncryptedTallyPythonJson() throws IOException {
    CiphertextTally fromPython = ConvertFromJson.readCiphertextTally(publisher.encryptedTallyPath().toString());
    assertThat(fromPython).isNotNull();
    System.out.printf("%n%s%n%n", fromPython);
  }

  @Example
  public void testManifestPythonJson() throws IOException {
    Manifest fromPython = ConvertFromJson.readManifest(publisher.manifestPath().toString());
    assertThat(fromPython).isNotNull();
    System.out.printf("%n%s%n%n", fromPython);
  }

  @Example
  public void testPlaintextTallyPythonJson() throws IOException {
    PlaintextTally fromPython = ConvertFromJson.readPlaintextTally(publisher.tallyPath().toString());
    assertThat(fromPython).isNotNull();
    System.out.printf("%n%s%n%n", fromPython);
  }

  @Example
  public void testEncyptionDevicesPythonJson() throws IOException {
    for (File file : publisher.deviceFiles()) {
      Encrypt.EncryptionDevice fromPython = ConvertFromJson.readDevice(file.getAbsolutePath());
      assertThat(fromPython).isNotNull();
      System.out.printf("%n%s%n%n", fromPython);
    }
  }

  @Example
  public void testGuardianRecordsPythonJson() throws IOException {
    for (File file : publisher.guardianRecordsFiles()) {
      GuardianRecord fromPython = ConvertFromJson.readGuardianRecord(file.getAbsolutePath());
      assertThat(fromPython).isNotNull();
      System.out.printf("%n%s%n%n", fromPython);
    }
  }

  @Example
  public void testSpoiledBallotsPythonJson() throws IOException {
    // LOOK exclude PlaintextBallotSelection.extra_data to allow to read
    for (File file : publisher.spoiledBallotFiles()) {
      PlaintextTally fromPython = ConvertFromJson.readPlaintextTally(file.getAbsolutePath());
      assertThat(fromPython).isNotNull();
      System.out.printf("%n%s%n%n", fromPython);
    }
  }

  @Example
  public void testSubmittedBallotsPythonJson() throws IOException {
    for (File file : publisher.ballotFiles()) {
      SubmittedBallot fromPython = ConvertFromJson.readSubmittedBallot(file.getAbsolutePath());
      assertThat(fromPython).isNotNull();
      System.out.printf("%n%s%n%n", fromPython);
    }
  }

  // LOOK @Example
  public void testProblemPythonJson() throws IOException {
    // this is failing because Optional is encoded as "None".
    String filename = pythonPublish + "/encrypted_ballots/ballot_03a29d15-667c-4ac8-afd7-549f19b8e4eb.json";
    SubmittedBallot fromPython = ConvertFromJson.readSubmittedBallot(filename);
    assertThat(fromPython).isNotNull();
    System.out.printf("%s%n", fromPython);
  }

}
