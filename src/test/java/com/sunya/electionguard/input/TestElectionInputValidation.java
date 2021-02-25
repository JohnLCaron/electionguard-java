package com.sunya.electionguard.input;

import com.google.common.collect.ImmutableList;
import com.sunya.electionguard.Election;
import com.sunya.electionguard.PlaintextBallot;
import net.jqwik.api.Example;

import java.util.Formatter;

import static com.google.common.truth.Truth.assertThat;

/** Tester for {@link ElectionInputValidation */
public class TestElectionInputValidation {

  @Example
  public void testDefaults() {
    ElectionInputBuilder ebuilder = new ElectionInputBuilder("election_scope_id");
    Election election = ebuilder.addContest("contest_id")
            .addSelection("selection_id", "candidate_1")
            .addSelection("selection_id2", "candidate_2")
            .done()
            .build();

    ElectionInputValidation validator = new ElectionInputValidation(election);
    Formatter problems = new Formatter();
    boolean isValid = validator.validateElection(problems);
    System.out.printf("Problems=%n%s", problems);
    assertThat(isValid).isTrue();
  }

  @Example
  public void testBallotStyleBadGpUnit() {
    Election.BallotStyle bs = new Election.BallotStyle("bad", ImmutableList.of("badGP"), null, null);
    ElectionInputBuilder ebuilder = new ElectionInputBuilder("election_scope_id")
            .setBallotStyle(bs);
    Election election = ebuilder.addContest("contest_id")
            .addSelection("selection_id", "candidate_1")
            .addSelection("selection_id2", "candidate_2")
            .done()
            .build();

    ElectionInputValidation validator = new ElectionInputValidation(election);
    Formatter problems = new Formatter();
    boolean isValid = validator.validateElection(problems);
    System.out.printf("Problems=%n%s", problems);
    assertThat(isValid).isFalse();
    assertThat(problems.toString()).contains("BallotStyle 'bad' has geopolitical_unit_id 'badGP' that does not exist in election's geopolitical_units");
  }

  @Example
  public void testDuplicateContestId() {
    ElectionInputBuilder ebuilder = new ElectionInputBuilder("election_scope_id");
    Election election = ebuilder.addContest("contest_id")
            .addSelection("selection_id", "candidate_1")
            .addSelection("selection_id2", "candidate_2")
            .done()
        .addContest("contest_id")
            .addSelection("selection_id", "candidate_1")
            .addSelection("selection_id2", "candidate_2")
            .done()
            .build();

    ElectionInputValidation validator = new ElectionInputValidation(election);
    Formatter problems = new Formatter();
    boolean isValid = validator.validateElection(problems);
    System.out.printf("Problems=%n%s", problems);
    assertThat(isValid).isFalse();
    assertThat(problems.toString()).contains("Multiple Contests have same id 'contest_id'");
  }

  @Example
  public void testContestGpunit() {
    ElectionInputBuilder ebuilder = new ElectionInputBuilder("election_scope_id")
            .setGpunit("district9");
    Election election = ebuilder.addContest("contest_id")
            .setGpunit("district1")
            .addSelection("selection_id", "candidate_1")
            .addSelection("selection_id2", "candidate_2")
            .done()
            .build();

    ElectionInputValidation validator = new ElectionInputValidation(election);
    Formatter problems = new Formatter();
    boolean isValid = validator.validateElection(problems);
    System.out.printf("Problems=%n%s", problems);
    assertThat(isValid).isFalse();
    assertThat(problems.toString()).contains("Contest's electoral_district_id 'district1' does not exist in election's geopolitical_units");
  }

  @Example
  public void testDuplicateSelectionId() {
    ElectionInputBuilder ebuilder = new ElectionInputBuilder("election_scope_id");
    Election election = ebuilder.addContest("contest_id")
            .addSelection("selection_id", "candidate_1")
            .addSelection("selection_id", "candidate_2")
            .done()
            .build();

    ElectionInputValidation validator = new ElectionInputValidation(election);
    Formatter problems = new Formatter();
    boolean isValid = validator.validateElection(problems);
    System.out.printf("Problems=%n%s", problems);
    assertThat(isValid).isFalse();
    assertThat(problems.toString()).contains("Multiple Selections have same id 'selection_id'");
  }

  @Example
  public void testDuplicateCandidateId() {
    ElectionInputBuilder ebuilder = new ElectionInputBuilder("election_scope_id");
    Election election = ebuilder.addContest("contest_id")
            .addSelection("selection_id", "candidate_1")
            .addSelection("selection_id2", "candidate_1")
            .done()
            .build();

    ElectionInputValidation validator = new ElectionInputValidation(election);
    Formatter problems = new Formatter();
    boolean isValid = validator.validateElection(problems);
    System.out.printf("Problems=%n%s", problems);
    assertThat(isValid).isFalse();
    assertThat(problems.toString()).contains("Multiple Selections have same candidate id 'candidate_1'");
  }

  @Example
  public void testBadCandidateId() {
    ElectionInputBuilder ebuilder = new ElectionInputBuilder("election_scope_id");
    Election election = ebuilder.addContest("contest_id")
            .addSelection("selection_id", "candidate_1")
            .addSelection("selection_id2", "manchurian")
            .done()
            .build();

    ElectionInputValidation validator = new ElectionInputValidation(election);
    Formatter problems = new Formatter();
    boolean isValid = validator.validateElection(problems);
    System.out.printf("Problems=%n%s", problems);
    assertThat(isValid).isFalse();
    assertThat(problems.toString()).contains("Ballot Selection 'selection_id2' candidate_id 'manchurian' does not exist in election's Candidates");
  }

}
