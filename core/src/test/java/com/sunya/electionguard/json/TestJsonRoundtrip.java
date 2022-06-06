package com.sunya.electionguard.json;

import com.google.common.collect.ImmutableList;
import com.sunya.electionguard.*;
import electionguard.ballot.ElectionConfig;
import net.jqwik.api.Example;

import java.io.File;
import java.io.IOException;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.google.common.truth.Truth.assertThat;
import static com.sunya.electionguard.Group.g_pow_p;
import static com.sunya.electionguard.SchnorrProof.make_schnorr_proof;

public class TestJsonRoundtrip {

  @Example
  public void testCoefficientsRoundtrip() throws IOException {
    File file = File.createTempFile("temp", null);
    file.deleteOnExit();
    String outputFile = file.getAbsolutePath();

    int n = 17;
    Map<String, Group.ElementModQ> coeffs = new HashMap<>();
    for (int i = 0; i < n; i++) {
      coeffs.put("test" + i + 1, Group.rand_q());
    }

    List<AvailableGuardian> ags = new ArrayList<>();
    for (int i = 0; i < n; i++) {
      String key = "test" + i + 1;
      ags.add(new AvailableGuardian(key, i + 1, coeffs.get(key)));
    }

    // original
    LagrangeCoefficientsPojo org = new LagrangeCoefficientsPojo(coeffs);
    // write json
    ConvertToJson.writeCoefficients(ags, file.toPath());
    // read it back
    LagrangeCoefficientsPojo fromFile = ConvertFromJson.readCoefficients(outputFile);
    compareCoeff(fromFile, org);
    assertThat(fromFile).isEqualTo(org);
  }

  void compareCoeff(LagrangeCoefficientsPojo round, LagrangeCoefficientsPojo expected) {
    for (Map.Entry<String, Group.ElementModQ> roundE : round.coefficients.entrySet()) {
      Group.ElementModQ expectedV = expected.coefficients.get(roundE.getKey());
      assertThat(expectedV).isNotNull();
      assertThat(roundE.getValue()).isEqualTo(expectedV);
    }
  }

  @Example
  public void testConvertToNormal() throws IOException {
    BigInteger notnormal = new BigInteger("89940FEA8014812318EA706F2E6CC89088969A79E8477439849C729BD5EB03", 16);
    BigInteger expected = new BigInteger("0089940FEA8014812318EA706F2E6CC89088969A79E8477439849C729BD5EB03", 16);

    Group.ElementModQ nonormalQ = Group.int_to_q(notnormal).orElseThrow();
    Group.ElementModQ expectedQ = Group.int_to_q(expected).orElseThrow();

    Map<String, Group.ElementModQ> coeffs = new HashMap<>();
    coeffs.put("test", nonormalQ);

    List<AvailableGuardian> ags = new ArrayList<>();
    ags.add(new AvailableGuardian("test", 1, nonormalQ));

    // expected after normalization
    Map<String, Group.ElementModQ> coeffsN= new HashMap<>();
    coeffsN.put("test", expectedQ);
    LagrangeCoefficientsPojo expectedPojo = new LagrangeCoefficientsPojo(coeffsN);

    // write json
    File file = File.createTempFile("temp", null);
    file.deleteOnExit();
    String outputFile = file.getAbsolutePath();
    ConvertToJson.writeCoefficients(ags, file.toPath());

    // read it back
    LagrangeCoefficientsPojo fromFile = ConvertFromJson.readCoefficients(outputFile);
    compareCoeff(fromFile, expectedPojo);
    assertThat(fromFile).isEqualTo(expectedPojo);
  }

  @Example
  public void testElectionDescriptionRoundtrip() throws IOException {
    File file = File.createTempFile("temp", null);
    file.deleteOnExit();
    String outputFile = file.getAbsolutePath();

    // read json
    Manifest description = ElectionFactory.get_hamilton_election_from_file();
    // write json
    ConvertToJson.writeElection(description, file.toPath());
    // read it back
    Manifest roundtrip = ConvertFromJson.readManifest(outputFile);
    assertThat(roundtrip).isEqualTo(description);
  }


  @Example
  public void testGuardianRecordRoundtrip() throws IOException {
    File file = File.createTempFile("temp", null);
    file.deleteOnExit();
    String outputFile = file.getAbsolutePath();

    Group.ElementModQ a = Group.rand_q();
    ElGamal.KeyPair keypair = new ElGamal.KeyPair(a, g_pow_p(a));
    SchnorrProof proof = make_schnorr_proof(keypair, Group.rand_q());

    // String guardianId, // Unique identifier of the guardian.
    //        int xCoordinate, // Actually the x coordinate, must be > 0
    //        Group.ElementModP guardianPublicKey, // Guardian's election public key for encrypting election objects.
    //        List<Group.ElementModP> coefficientCommitments,
    //        // Commitment for each coefficient of the guardians secret polynomial. First commitment is the election_public_key.
    //        List<SchnorrProof> coefficientProofs
    GuardianRecord org = new GuardianRecord("test", 42, Group.TWO_MOD_P,
            List.of(Group.TWO_MOD_P), List.of(proof));

    // write json
    ConvertToJson.writeGuardianRecord(org, file.toPath());
    // read it back
    GuardianRecord fromFile = ConvertFromJson.readGuardianRecord(outputFile);
    assertThat(fromFile).isEqualTo(org);
  }

  @Example
  public void testAvailableGuardianRoundtrip() throws IOException {
    File file = File.createTempFile("temp", null);
    file.deleteOnExit();
    String outputFile = file.getAbsolutePath();

    // original
    AvailableGuardian org = new AvailableGuardian("test", 42, Group.TWO_MOD_Q);
    // write json
    ConvertToJson.writeAvailableGuardian(org, file.toPath());
    // read it back
    AvailableGuardian fromFile = ConvertFromJson.readAvailableGuardian(outputFile);
    assertThat(fromFile).isEqualTo(org);
  }

  @Example
  public void testDeviceRoundtrip() throws IOException {
    File file = File.createTempFile("temp", null);
    file.deleteOnExit();
    String outputFile = file.getAbsolutePath();

    // original
    Encrypt.EncryptionDevice org = Encrypt.createDeviceForTest("deviceTest");
    // write json
    ConvertToJson.writeDevice(org, file.toPath());
    // read it back
    Encrypt.EncryptionDevice fromFile = ConvertFromJson.readDevice(outputFile);
    assertThat(fromFile).isEqualTo(org);
  }

  @Example
  public void testPlaintextTallyRoundtrip() throws IOException {
    File file = File.createTempFile("temp", null);
    String outputFile = file.getAbsolutePath();

    // original
    PlaintextTally org = new PlaintextTally("testTally", new HashMap<>());
    // write json
    ConvertToJson.writePlaintextTally(org, file.toPath());
    // read it back
    PlaintextTally fromFile = ConvertFromJson.readPlaintextTally(outputFile);
    assertThat(fromFile).isEqualTo(org);
  }

  @Example
  public void testEncryptedTallyRoundtrip() throws IOException {
    File file = File.createTempFile("temp", null);
    file.deleteOnExit();
    String outputFile = file.getAbsolutePath();

    // original
    // String object_id, ElementModQ description_hash, Map<String, CiphertextTallySelection> tally_selections
    CiphertextTally org = new CiphertextTally("testTally", new HashMap<>());
    // write json
    ConvertToJson.writeCiphertextTally(org, file.toPath());
    // read it back
    CiphertextTally fromFile = ConvertFromJson.readCiphertextTally(outputFile);
    assertThat(fromFile).isEqualTo(org);
  }

  @Example
  public void testCyphertextBallotRoundtrip() throws IOException {
    File file = File.createTempFile("temp", null);
    file.deleteOnExit();
    String outputFile = file.getAbsolutePath();

    // String object_id, int sequence_order, Group.ElementModQ description_hash, ElGamal.Ciphertext ciphertext,
    //                     Group.ElementModQ crypto_hash, boolean is_placeholder_selection, Optional<Group.ElementModQ> nonce,
    //                     Optional<ChaumPedersen.DisjunctiveChaumPedersenProof> proof, Optional<ElGamal.Ciphertext> extended_data
    CiphertextBallot.Selection selection = new CiphertextBallot.Selection(
            "testSelectiontRoundtrip",
            43,
            Group.rand_q(),
            new ElGamal.Ciphertext(Group.TWO_MOD_P, Group.TWO_MOD_P),
            Group.rand_q(),
            false,
            Optional.empty(),
            Optional.empty(),
            Optional.empty());

    CiphertextBallot.Contest contest = new CiphertextBallot.Contest(
            "testContestRoundtrip",
            42,
            Group.ONE_MOD_Q,
            ImmutableList.of(selection),
            Group.ONE_MOD_Q,
            Optional.of(Group.ONE_MOD_Q),
            Optional.empty());

    SubmittedBallot org = new SubmittedBallot(
            "testBallotRoundtrip",
            "ballotStyle",
            Group.ONE_MOD_Q,
            Group.ONE_MOD_Q,
            ImmutableList.of(contest),
            Group.ONE_MOD_Q,
            1234L,
            Group.ONE_MOD_Q,
            BallotBox.State.CAST);

    // write json
    ConvertToJson.writeSubmittedBallot(org, file.toPath());
    // read it back
    SubmittedBallot fromFile = ConvertFromJson.readSubmittedBallot(outputFile);
    assertThat(fromFile).isEqualTo(org);
  }

  @Example
  public void testPlaintextBallotRoundtrip() throws IOException {
    File file = File.createTempFile("temp", null);
    file.deleteOnExit();
    String outputFile = file.getAbsolutePath();

    // String selection_id, int sequence_order, int vote, boolean is_placeholder_selection,
    //                     @Nullable ExtendedData extended_data
    PlaintextBallot.Selection selection = new PlaintextBallot.Selection(
            "testSelectiontRoundtrip",
            43,
            1,
            "stuff");

    PlaintextBallot.Contest contest = new PlaintextBallot.Contest(
            "testContestRoundtrip",
            42,
            ImmutableList.of(selection));

    PlaintextBallot org = new PlaintextBallot(
            "testBallotRoundtrip",
            "ballotStyle",
            ImmutableList.of(contest),
            null);

    // write json
    ConvertToJson.writePlaintextBallot(org, file.toPath());
    // read it back
    PlaintextBallot fromFile = ConvertFromJson.readPlaintextBallot(outputFile);
    assertThat(fromFile).isEqualTo(org);
  }

}
