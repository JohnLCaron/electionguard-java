package com.sunya.electionguard.publish;

import com.google.common.collect.ImmutableList;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.sunya.electionguard.*;
import net.jqwik.api.Example;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Optional;
import java.util.OptionalInt;

import static com.sunya.electionguard.KeyCeremony.CoefficientValidationSet;
import static com.sunya.electionguard.DecryptionShare.CiphertextDecryptionSelection;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth8.assertThat;

public class TestJsonRoundtrip {

  @Example
  public void testElectionDescriptionRoundtrip() throws IOException {
    File file = File.createTempFile("temp", null);
    file.deleteOnExit();
    String outputFile = file.getAbsolutePath();

    // read json
    ElectionFactory election_factory = new ElectionFactory();
    Election.ElectionDescription description = election_factory.get_hamilton_election_from_file();
    // write json
    ElectionDescriptionToJson writer = new ElectionDescriptionToJson(outputFile);
    writer.write(description);
    // read it back
    ElectionDescriptionFromJson builder = new ElectionDescriptionFromJson(outputFile);
    Election.ElectionDescription roundtrip = builder.build();
    assertThat(roundtrip).isEqualTo(description);
  }

  @Example
  public void testCoefficientRoundtrip() throws IOException {
    File file = /* new File("/home/snake/tmp/test.json"); */ File.createTempFile("temp", null);
    file.deleteOnExit();
    String outputFile = file.getAbsolutePath();

    // original
    CoefficientValidationSet org = KeyCeremony.CoefficientValidationSet.create("test", new ArrayList<>(), new ArrayList<>());
    // write json
    ConvertToJson.write(org, file.toPath());
    // read it back
    CoefficientValidationSet fromFile = ConvertFromJson.readCoefficient(outputFile);
    assertThat(fromFile).isEqualTo(org);
  }

  @Example
  public void testEncryptedTallyRoundtrip() throws IOException {
    File file = new File("/home/snake/tmp/test.json"); // */ File.createTempFile("temp", null);
    // file.deleteOnExit();
    String outputFile = file.getAbsolutePath();

    // original
    // String object_id, ElementModQ description_hash, Map<String, CiphertextTallySelection> tally_selections
    Tally.PublishedCiphertextTally org = Tally.PublishedCiphertextTally.create("testTally", new HashMap<>());
    // write json
    ConvertToJson.write(org, file.toPath());
    // read it back
    Tally.PublishedCiphertextTally fromFile = ConvertFromJson.readCiphertextTally(outputFile);
    assertThat(fromFile).isEqualTo(org);
  }

  @Example
  public void testBallotRoundtrip() throws IOException {
    File file = new File("/home/snake/tmp/test.json"); // */ File.createTempFile("temp", null);
    // file.deleteOnExit();
    String outputFile = file.getAbsolutePath();

    Ballot.CiphertextBallotContest contest = new Ballot.CiphertextBallotContest(
            "testContestRoundtrip",
            Group.ONE_MOD_Q,
            ImmutableList.of(),
            Group.ONE_MOD_Q,
            Optional.of(Group.ONE_MOD_Q),
            Optional.empty());

    Ballot.CiphertextAcceptedBallot org = new Ballot.CiphertextAcceptedBallot(
            "testBallotRoundtrip",
            "ballotStyle",
            Group.ONE_MOD_Q,
            Group.ONE_MOD_Q,
            ImmutableList.of(contest),
            Optional.of(Group.ONE_MOD_Q),
            1234L,
            Group.ONE_MOD_Q,
            Optional.of(Group.ONE_MOD_Q),
            Ballot.BallotBoxState.CAST);

    // write json
    ConvertToJson.write(org, file.toPath());
    // read it back
    Ballot.CiphertextAcceptedBallot fromFile = ConvertFromJson.readBallot(outputFile);
    assertThat(fromFile).isEqualTo(org);
  }

  // https://github.com/hsiafan/gson-java8-datatype
  @Example
  public void testOptional() {
    Gson gson = ConvertFromJson.enhancedGson();

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
  }

  @Example
  public void testCiphertextDecryptionSelectionEmpty() {
    CiphertextDecryptionSelection select = CiphertextDecryptionSelection.create(
            "testCiphertextDecryptionSelectionRoundtrip",
            "guardian_id",
            Group.ONE_MOD_Q,
            Group.ONE_MOD_P,
            Optional.empty(),
            Optional.empty());

    Gson gson = ConvertFromJson.enhancedGson();

    String json = gson.toJson(select, CiphertextDecryptionSelection.class);
    CiphertextDecryptionSelection back = gson.fromJson(json, new TypeToken<CiphertextDecryptionSelection>(){}.getType());
    assertThat(back).isEqualTo(select);
  }

  @Example
  public void testCiphertextDecryptionSelectionHalfEmpty() {
    // ElementModP pad, ElementModP data, ElementModQ challenge, ElementModQ response
    ChaumPedersen.ChaumPedersenProof proof = new ChaumPedersen.ChaumPedersenProof(
            Group.ONE_MOD_P,
            Group.ONE_MOD_P,
            Group.ONE_MOD_Q,
            Group.ONE_MOD_Q
            );

    CiphertextDecryptionSelection select = CiphertextDecryptionSelection.create(
            "testCiphertextDecryptionSelectionRoundtrip",
            "guardian_id",
            Group.ONE_MOD_Q,
            Group.ONE_MOD_P,
            Optional.of(proof),
            Optional.empty());

    Gson gson = ConvertFromJson.enhancedGson();

    String json = gson.toJson(select, CiphertextDecryptionSelection.class);
    CiphertextDecryptionSelection back = gson.fromJson(json, new TypeToken<CiphertextDecryptionSelection>(){}.getType());
    assertThat(back).isEqualTo(select);
  }

  @Example
  public void testCiphertextDecryptionSelectionFull() {
    ChaumPedersen.ChaumPedersenProof proof = new ChaumPedersen.ChaumPedersenProof(
            Group.ONE_MOD_P,
            Group.ONE_MOD_P,
            Group.ONE_MOD_Q,
            Group.ONE_MOD_Q
    );

    CiphertextDecryptionSelection select = CiphertextDecryptionSelection.create(
            "testCiphertextDecryptionSelectionRoundtrip",
            "guardian_id",
            Group.ONE_MOD_Q,
            Group.ONE_MOD_P,
            Optional.of(proof),
            Optional.of(new HashMap<>()));

    Gson gson = ConvertFromJson.enhancedGson();
    String json = gson.toJson(select, CiphertextDecryptionSelection.class);
    CiphertextDecryptionSelection back = gson.fromJson(json, new TypeToken<CiphertextDecryptionSelection>(){}.getType());
    assertThat(back).isEqualTo(select);
  }

}
