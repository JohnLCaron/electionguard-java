package com.sunya.electionguard.publish;

import com.google.common.collect.ImmutableList;
import com.sunya.electionguard.*;
import net.jqwik.api.Example;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.google.common.truth.Truth.assertThat;

public class TestJsonRoundtrip {

  @Example
  public void testCoefficientsRoundtrip() throws IOException {
    File file = File.createTempFile("temp", null);
    file.deleteOnExit();
    String outputFile = file.getAbsolutePath();

    int n = 17;
    Map<String, Group.ElementModQ> coeffs = new HashMap<>();
    for (int i=0; i<n; i++) {
      coeffs.put("test"+i+1, Group.rand_q());
    }

    List<AvailableGuardian> ags = new ArrayList<>();
    for (int i=0; i<n; i++) {
      String key = "test"+i+1;
      ags.add(new AvailableGuardian(key, i+1, coeffs.get(key)));
    }

    // original
    LagrangeCoefficientsPojo org = new LagrangeCoefficientsPojo(coeffs);
    // write json
    ConvertToJson.writeCoefficients(ags, file.toPath());
    // read it back
    LagrangeCoefficientsPojo fromFile = ConvertFromJson.readCoefficients(outputFile);
    assertThat(fromFile).isEqualTo(org);
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
            new ElGamal.Ciphertext(Group.TWO_MOD_P, Group.TWO_MOD_P),
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
            false,
            new PlaintextBallot.ExtendedData("stuff", 42));

    PlaintextBallot.Contest contest = new PlaintextBallot.Contest(
            "testContestRoundtrip",
            42,
            ImmutableList.of(selection));

    PlaintextBallot org = new PlaintextBallot(
            "testBallotRoundtrip",
            "ballotStyle",
            ImmutableList.of(contest));

    // write json
    ConvertToJson.writePlaintextBallot(org, file.toPath());
    // read it back
    PlaintextBallot fromFile = ConvertFromJson.readPlaintextBallot(outputFile);
    assertThat(fromFile).isEqualTo(org);
  }

  /* https://github.com/hsiafan/gson-java8-datatype
  @Example
  public void testOptional() {
    Gson gson = GsonTypeAdapters.enhancedGson();

    assertThat(gson.toJson(OptionalInt.of(10))).isEqualTo("10");
    assertThat(gson.fromJson("10", OptionalInt.class)).isEqualTo(OptionalInt.of(10));
    assertThat(gson.toJson(OptionalInt.empty())).isEqualTo("null"); // null
    assertThat(gson.fromJson("null", OptionalInt.class)).isEqualTo(OptionalInt.empty());

    assertThat(gson.toJson(Optional.of("test"))).isEqualTo("\"test\"");
    assertThat(gson.toJson(Optional.of(Optional.of("test")))).isEqualTo("\"test\"");

    assertThat(gson.toJson(Optional.empty())).isEqualTo("null"); // null
    assertThat(gson.toJson(Optional.of(Optional.empty()))).isEqualTo("null"); // null
    assertThat(gson.fromJson("null", Optional.class)).isEqualTo(Optional.empty());

    Optional<String> r1 = gson.fromJson("\"test\"", new TypeToken<Optional<String>>() {
    }.getType());
    assertThat(r1).isEqualTo(Optional.of("test"));

    Optional<Optional<String>> r2 = gson.fromJson("\"test\"", new TypeToken<Optional<Optional<String>>>() {
    }.getType());
    assertThat(r2).isEqualTo(Optional.of(Optional.of("test")));

    Optional<String> r3 = gson.fromJson("null", new TypeToken<Optional<String>>() {
    }.getType());
    assertThat(r3).isEqualTo(Optional.empty());

    Optional<Optional<String>> r4 = gson.fromJson("null", new TypeToken<Optional<Optional<String>>>() {
    }.getType());
    assertThat(r4).isEqualTo(Optional.empty());
  } */

}
