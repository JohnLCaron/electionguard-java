package com.sunya.electionguard.input;

import com.sunya.electionguard.CiphertextTally;
import com.sunya.electionguard.Manifest;
import com.sunya.electionguard.TestUtils;
import net.jqwik.api.Example;

import java.util.Formatter;

import static com.google.common.truth.Truth.assertThat;

/** Tester for {@link CiphertextTallyInputValidation */
public class TestCiphertextTallyInputValidation {

  private boolean validateElection(Manifest election) {
    ManifestInputValidation validator = new ManifestInputValidation(election);
    Formatter problems = new Formatter();
    boolean isValid = validator.validateElection(problems);
    if (!isValid) {
      System.out.printf("Manifest Problems=%n%s", problems);
    }
    return isValid;
  }

  void validateTally(Manifest election, CiphertextTally tally, String expectMessage) {
    CiphertextTallyInputValidation validator = new CiphertextTallyInputValidation(election);
    Formatter problems = new Formatter();
    boolean isValid = validator.validateTally(tally, problems);
    if (!isValid) {
      System.out.printf("Problems=%n%s", problems);
    }
    if (expectMessage != null) {
      assertThat(isValid).isFalse();
      assertThat(problems.toString()).contains(expectMessage);
    } else {
      assertThat(isValid).isTrue();
    }
  }

  @Example
  public void testContestObjectId() {
    ManifestInputBuilder ebuilder = new ManifestInputBuilder("ballot_id");
    Manifest election = ebuilder.addContest("contest_id")
            .addSelection("selection_id", "candidate_1")
            .addSelection("selection_id2", "candidate_2")
            .done()
            .build();
    assertThat(validateElection(election)).isTrue();

    CiphertextTallyInputBuilder builder = new CiphertextTallyInputBuilder("ctally");
    for (Manifest.ContestDescription contest : election.contests()) {
      CiphertextTallyInputBuilder.ContestBuilder cbuilder = builder.addContest("bad_contest_id", contest.crypto_hash());
      for (Manifest.SelectionDescription selection : contest.ballot_selections()) {
        cbuilder.addSelection(selection.object_id(), selection.crypto_hash(), TestUtils.elgamal_ciphertext());
      }
    }
    validateTally(election, builder.build(), "CiphertextTally.A.1 Tally Contest 'bad_contest_id' does not exist in manifest");
  }

  @Example
  public void testContestHash() {
    ManifestInputBuilder ebuilder = new ManifestInputBuilder("ballot_id");
    Manifest election = ebuilder.addContest("contest_id")
            .addSelection("selection_id", "candidate_1")
            .addSelection("selection_id2", "candidate_2")
            .done()
            .build();
    assertThat(validateElection(election)).isTrue();

    CiphertextTallyInputBuilder builder = new CiphertextTallyInputBuilder("ctally");
    for (Manifest.ContestDescription contest : election.contests()) {
      CiphertextTallyInputBuilder.ContestBuilder cbuilder = builder.addContest(contest.object_id(), TestUtils.elements_mod_q());
      for (Manifest.SelectionDescription selection : contest.ballot_selections()) {
        cbuilder.addSelection(selection.object_id(), selection.crypto_hash(), TestUtils.elgamal_ciphertext());
      }
    }
    validateTally(election, builder.build(), "CiphertextTally.A.1.1 Tally Contest 'contest_id' crypto_hash does not match manifest contest");
  }

  @Example
  public void testSelectionId() {
    ManifestInputBuilder ebuilder = new ManifestInputBuilder("ballot_id");
    Manifest election = ebuilder.addContest("contest_id")
            .addSelection("selection_id", "candidate_1")
            .addSelection("selection_id2", "candidate_2")
            .done()
            .build();
    assertThat(validateElection(election)).isTrue();

    CiphertextTallyInputBuilder builder = new CiphertextTallyInputBuilder("ctally");
    for (Manifest.ContestDescription contest : election.contests()) {
      CiphertextTallyInputBuilder.ContestBuilder cbuilder = builder.addContest(contest.object_id(), contest.crypto_hash());
      for (Manifest.SelectionDescription selection : contest.ballot_selections()) {
        cbuilder.addSelection(selection.object_id() +"bad", selection.crypto_hash(), TestUtils.elgamal_ciphertext());
      }
    }
    validateTally(election, builder.build(), "CiphertextBallot.A.2 Tally Selection 'selection_id2bad' does not exist in contest 'contest_id'");
  }

  @Example
  public void testSelectionHash() {
    ManifestInputBuilder ebuilder = new ManifestInputBuilder("ballot_id");
    Manifest election = ebuilder.addContest("contest_id")
            .addSelection("selection_id", "candidate_1")
            .addSelection("selection_id2", "candidate_2")
            .done()
            .build();
    assertThat(validateElection(election)).isTrue();

    CiphertextTallyInputBuilder builder = new CiphertextTallyInputBuilder("ctally");
    for (Manifest.ContestDescription contest : election.contests()) {
      CiphertextTallyInputBuilder.ContestBuilder cbuilder = builder.addContest(contest.object_id(), contest.crypto_hash());
      for (Manifest.SelectionDescription selection : contest.ballot_selections()) {
        cbuilder.addSelection(selection.object_id(), TestUtils.elements_mod_q(), TestUtils.elgamal_ciphertext());
      }
    }
    validateTally(election, builder.build(), "CiphertextTally.A.2.1 Tally Selection 'contest_id-selection_id' crypto_hash does not match manifest selection");
  }

  @Example
  public void testDuplicateContest() {
    ManifestInputBuilder ebuilder = new ManifestInputBuilder("ballot_id");
    Manifest election = ebuilder.addContest("contest_id")
            .addSelection("selection_id", "candidate_1")
            .addSelection("selection_id2", "candidate_2")
            .done()
            .build();
    assertThat(validateElection(election)).isTrue();

    CiphertextTallyInputBuilder builder = new CiphertextTallyInputBuilder("ctally");
    for (Manifest.ContestDescription contest : election.contests()) {
      CiphertextTallyInputBuilder.ContestBuilder cbuilder = builder.addContest(contest.object_id(), contest.crypto_hash());
      for (Manifest.SelectionDescription selection : contest.ballot_selections()) {
        cbuilder.addSelection(selection.object_id(), selection.crypto_hash(), TestUtils.elgamal_ciphertext());
      }
    }
    validateTally(election, builder.buildBadContest(), "CiphertextTally.B.1 Contest id key 'contest_idbad' doesnt match value 'contest_id'");
  }

  @Example
  public void testDuplicateSelection() {
    ManifestInputBuilder ebuilder = new ManifestInputBuilder("ballot_id");
    Manifest election = ebuilder.addContest("contest_id")
            .addSelection("selection_id", "candidate_1")
            .addSelection("selection_id2", "candidate_2")
            .done()
            .build();
    assertThat(validateElection(election)).isTrue();

    CiphertextTallyInputBuilder builder = new CiphertextTallyInputBuilder("ctally");
    for (Manifest.ContestDescription contest : election.contests()) {
      CiphertextTallyInputBuilder.ContestBuilder cbuilder = builder.addContest(contest.object_id(), contest.crypto_hash());
      for (Manifest.SelectionDescription selection : contest.ballot_selections()) {
        cbuilder.addSelection(selection.object_id(), selection.crypto_hash(), TestUtils.elgamal_ciphertext());
      }
    }
    validateTally(election, builder.buildBadSelection(), "CiphertextTally.B.2 Selection id key 'selection_id2bad' doesnt match value 'selection_id2'");
  }

  /////////////////////////////////////////////////

  @Example
  public void testDefaultOk() {
    ManifestInputBuilder ebuilder = new ManifestInputBuilder("ballot_id");
    Manifest election = ebuilder.addContest("contest_id")
            .addSelection("selection_id", "candidate_1")
            .addSelection("selection_id2", "candidate_2")
            .done()
            .build();
    assertThat(validateElection(election)).isTrue();

    CiphertextTallyInputBuilder builder = new CiphertextTallyInputBuilder("ctally");
    for (Manifest.ContestDescription contest : election.contests()) {
      CiphertextTallyInputBuilder.ContestBuilder cbuilder = builder.addContest(contest.object_id(), contest.crypto_hash());
      for (Manifest.SelectionDescription selection : contest.ballot_selections()) {
        cbuilder.addSelection(selection.object_id(), selection.crypto_hash(), TestUtils.elgamal_ciphertext());
      }
    }
    validateTally(election, builder.build(), null);
  }
}
