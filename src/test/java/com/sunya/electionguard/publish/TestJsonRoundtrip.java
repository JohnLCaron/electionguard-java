package com.sunya.electionguard.publish;

import com.sunya.electionguard.*;
import net.jqwik.api.Example;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;

import static com.sunya.electionguard.KeyCeremony.CoefficientValidationSet;

import static com.google.common.truth.Truth.assertThat;

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
  public void testPublishedCiphertextTallyRoundtrip() throws IOException {
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

}
