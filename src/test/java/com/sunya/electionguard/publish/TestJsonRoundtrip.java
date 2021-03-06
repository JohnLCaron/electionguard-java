package com.sunya.electionguard.publish;

import com.google.common.collect.ImmutableList;
import com.sunya.electionguard.*;
import net.jqwik.api.Example;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Optional;

import static com.google.common.truth.Truth.assertThat;

public class TestJsonRoundtrip {

  // LOOK doesnt work @Example
  public void readGuardianRecordPrivateRoundtrip() throws IOException {
    File file = File.createTempFile("temp", null);
    file.deleteOnExit();
    String outputFile = file.getAbsolutePath();

    // original
    Guardian guardian = Guardian.createForTesting("test", 5, 4, 3, null);
    GuardianRecordPrivate org = guardian.export_private_data();
    // write json
    ConvertToJson.writeGuardianRecordPrivate(org, file.toPath());
    // read it back
    GuardianRecordPrivate fromFile = ConvertFromJson.readGuardianRecordPrivate(outputFile);
    assertThat(fromFile).isEqualTo(org);
  }

  @Example
  public void testGuardianRecordRoundtrip() throws IOException {
    File file = File.createTempFile("temp", null);
    file.deleteOnExit();
    String outputFile = file.getAbsolutePath();

    // original
    Guardian guardian = Guardian.createForTesting("test", 5, 4, 3, null);
    GuardianRecord org = guardian.publish();
    // write json
    ConvertToJson.writeGuardianRecord(org, file.toPath());
    // read it back
    GuardianRecord fromFile = ConvertFromJson.readGuardianRecord(outputFile);
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
    Manifest roundtrip = ConvertFromJson.readElection(outputFile);
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
    Encrypt.EncryptionDevice org = Encrypt.EncryptionDevice.createForTest("deviceTest");
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
  public void testBallotRoundtrip() throws IOException {
    File file = File.createTempFile("temp", null);
    file.deleteOnExit();
    String outputFile = file.getAbsolutePath();

    CiphertextBallot.Contest contest = new CiphertextBallot.Contest(
            "testContestRoundtrip",
            Group.ONE_MOD_Q,
            ImmutableList.of(),
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
            Optional.empty(), // LOOK we took out the nonce in SubmittedBallotPojo, since we cant read None
            BallotBox.State.CAST);

    // write json
    ConvertToJson.writeSubmittedBallot(org, file.toPath());
    // read it back
    SubmittedBallot fromFile = ConvertFromJson.readSubmittedBallot(outputFile);
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
