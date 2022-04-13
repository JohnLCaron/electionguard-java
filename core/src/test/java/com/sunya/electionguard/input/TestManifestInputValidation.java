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
            .addBallotStyle(bs);
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
    assertThat(problems.toString()).contains("Manifest.A.1");
    assertThat(problems.toString()).contains("Manifest.A.5");
  }

  @Example
  public void testBadParty() {
    ManifestInputBuilder ebuilder = new ManifestInputBuilder("election_scope_id")
            .addCandidateAndParty("candide", "wayne");
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
    assertThat(problems.toString()).contains("Manifest.A.2");
  }

  @Example
  public void testContestGpunit() {
    ManifestInputBuilder ebuilder = new ManifestInputBuilder("election_scope_id")
            .addGpunit("district9");
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
    assertThat(problems.toString()).contains("Manifest.A.3");
    assertThat(problems.toString()).contains("Manifest.A.5");
  }

  @Example
  public void testBadCandidateId() {
    ManifestInputBuilder ebuilder = new ManifestInputBuilder("election_scope_id");
    Manifest election = ebuilder.addContest("contest_id")
            .addSelection("selection_id", "candidate_1")
            .addSelection("selection_id2", "manchurian")
            .done()
            .removeCandidate("manchurian")
            .build();

    ManifestInputValidation validator = new ManifestInputValidation(election);
    Formatter problems = new Formatter();
    boolean isValid = validator.validateElection(problems);
    System.out.printf("Problems=%n%s", problems);
    assertThat(isValid).isFalse();
    assertThat(problems.toString()).contains("Manifest.A.4");
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
    assertThat(problems.toString()).contains("Manifest.B.1");
    assertThat(problems.toString()).contains("Manifest.B.6");
  }

  @Example
  public void testBadSequence() {
    ManifestInputBuilder ebuilder = new ManifestInputBuilder("election_scope_id");
    Manifest election = ebuilder
         .addContest("contest_id", 42)
            .addSelection("selection_id", "candidate_1")
            .addSelection("selection_id2", "manchurian")
            .done()
         .addContest("contest_id2", 42)
            .addSelection("selection_id3", "candidate_3", 6)
            .addSelection("selection_id4", "mongolian", 6)
            .done()
         .build();

    ManifestInputValidation validator = new ManifestInputValidation(election);
    Formatter problems = new Formatter();
    boolean isValid = validator.validateElection(problems);
    System.out.printf("Problems=%n%s", problems);
    assertThat(isValid).isFalse();
    assertThat(problems.toString()).contains("Manifest.B.2");
    assertThat(problems.toString()).contains("Manifest.B.4");
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
    assertThat(problems.toString()).contains("Manifest.B.6");
    assertThat(problems.toString()).contains("Manifest.B.3");
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
    assertThat(problems.toString()).contains("Manifest.B.5");
  }

}
