package com.sunya.electionguard.input;

import com.google.common.collect.ImmutableList;
import com.sunya.electionguard.Manifest;
import net.jqwik.api.Example;

import java.util.Formatter;

import static com.google.common.truth.Truth.assertThat;

/** Tester for {@link ManifestInputValidation */
public class TestManifestInputValidation {

  @Example
  public void testDefaults() {
    ManifestInputBuilder ebuilder = new ManifestInputBuilder("test_manifest");
    Manifest election = ebuilder.addContest("contest_id")
            .addSelection("selection_id", "candidate_1")
            .addSelection("selection_id2", "candidate_2")
            .done()
            .build();

    ManifestInputValidation validator = new ManifestInputValidation(election);
    Formatter problems = new Formatter();
    boolean isValid = validator.validateElection(problems);
    System.out.printf("Problems=%n%s", problems);
    assertThat(isValid).isTrue();
  }

  @Example
  public void testBallotStyleBadGpUnit() {
    Manifest.BallotStyle bs = new Manifest.BallotStyle("bad", ImmutableList.of("badGP"), null, null);
    ManifestInputBuilder ebuilder = new ManifestInputBuilder("election_scope_id")
            .setBallotStyle(bs);
    Manifest election = ebuilder.addContest("contest_id")
            .addSelection("selection_id", "candidate_1")
            .addSelection("selection_id2", "candidate_2")
            .done()
            .build();

    ManifestInputValidation validator = new ManifestInputValidation(election);
    Formatter problems = new Formatter();
    boolean isValid = validator.validateElection(problems);
    System.out.printf("Problems=%n%s", problems);
    assertThat(isValid).isFalse();
    assertThat(problems.toString()).contains("BallotStyle 'bad' has geopolitical_unit_id 'badGP' that does not exist in election's geopolitical_units");
  }

  @Example
  public void testDuplicateContestId() {
    ManifestInputBuilder ebuilder = new ManifestInputBuilder("election_scope_id");
    Manifest election = ebuilder.addContest("contest_id")
            .addSelection("selection_id", "candidate_1")
            .addSelection("selection_id2", "candidate_2")
            .done()
        .addContest("contest_id")
            .addSelection("selection_id", "candidate_1")
            .addSelection("selection_id2", "candidate_2")
            .done()
            .build();

    ManifestInputValidation validator = new ManifestInputValidation(election);
    Formatter problems = new Formatter();
    boolean isValid = validator.validateElection(problems);
    System.out.printf("Problems=%n%s", problems);
    assertThat(isValid).isFalse();
    assertThat(problems.toString()).contains("Multiple Contests have same id 'contest_id'");
  }

  @Example
  public void testContestGpunit() {
    ManifestInputBuilder ebuilder = new ManifestInputBuilder("election_scope_id")
            .setGpunit("district9");
    Manifest election = ebuilder.addContest("contest_id")
            .setGpunit("district1")
            .addSelection("selection_id", "candidate_1")
            .addSelection("selection_id2", "candidate_2")
            .done()
            .build();

    ManifestInputValidation validator = new ManifestInputValidation(election);
    Formatter problems = new Formatter();
    boolean isValid = validator.validateElection(problems);
    System.out.printf("Problems=%n%s", problems);
    assertThat(isValid).isFalse();
    assertThat(problems.toString()).contains("Contest's electoral_district_id 'district1' does not exist in election's geopolitical_units");
  }

  @Example
  public void testDuplicateSelectionId() {
    ManifestInputBuilder ebuilder = new ManifestInputBuilder("election_scope_id");
    Manifest election = ebuilder.addContest("contest_id")
            .addSelection("selection_id", "candidate_1")
            .addSelection("selection_id", "candidate_2")
            .done()
            .build();

    ManifestInputValidation validator = new ManifestInputValidation(election);
    Formatter problems = new Formatter();
    boolean isValid = validator.validateElection(problems);
    System.out.printf("Problems=%n%s", problems);
    assertThat(isValid).isFalse();
    assertThat(problems.toString()).contains("Multiple Selections have same id 'selection_id'");
  }

  @Example
  public void testDuplicateSelectionIdGlobal() {
    ManifestInputBuilder ebuilder = new ManifestInputBuilder("election_scope_id");
    Manifest election = ebuilder.addContest("contest_id")
            .addSelection("selection_id1", "candidate_1")
            .addSelection("selection_id2", "candidate_2")
            .done()
            .addContest("contest_id2")
            .addSelection("selection_id1", "candidate_1")
            .addSelection("selection_id3", "candidate_2")
            .done()
            .build();

    ManifestInputValidation validator = new ManifestInputValidation(election);
    Formatter problems = new Formatter();
    boolean isValid = validator.validateElection(problems);
    System.out.printf("Problems=%n%s", problems);
    assertThat(isValid).isFalse();
    assertThat(problems.toString()).contains("Manifest.B.6");
  }

  @Example
  public void testDuplicateCandidateId() {
    ManifestInputBuilder ebuilder = new ManifestInputBuilder("election_scope_id");
    Manifest election = ebuilder.addContest("contest_id")
            .addSelection("selection_id", "candidate_1")
            .addSelection("selection_id2", "candidate_1")
            .done()
            .build();

    ManifestInputValidation validator = new ManifestInputValidation(election);
    Formatter problems = new Formatter();
    boolean isValid = validator.validateElection(problems);
    System.out.printf("Problems=%n%s", problems);
    assertThat(isValid).isFalse();
    assertThat(problems.toString()).contains("Multiple Selections have same candidate id 'candidate_1'");
  }

  @Example
  public void testBadCandidateId() {
    ManifestInputBuilder ebuilder = new ManifestInputBuilder("election_scope_id");
    Manifest election = ebuilder.addContest("contest_id")
            .addSelection("selection_id", "candidate_1")
            .addSelection("selection_id2", "manchurian")
            .done()
            .build();

    ManifestInputValidation validator = new ManifestInputValidation(election);
    Formatter problems = new Formatter();
    boolean isValid = validator.validateElection(problems);
    System.out.printf("Problems=%n%s", problems);
    assertThat(isValid).isFalse();
    assertThat(problems.toString()).contains("Ballot Selection 'selection_id2' candidate_id 'manchurian' does not exist in election's Candidates");
  }

}
