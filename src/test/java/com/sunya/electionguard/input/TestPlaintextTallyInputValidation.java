package com.sunya.electionguard.input;

import com.sunya.electionguard.CiphertextTally;
import com.sunya.electionguard.DecryptionShare;
import com.sunya.electionguard.PlaintextTally;
import com.sunya.electionguard.Manifest;
import net.jqwik.api.Example;

import java.util.Formatter;
import java.util.HashMap;
import java.util.Map;

import static com.google.common.truth.Truth.assertThat;
import static com.sunya.electionguard.TestUtils.chaum_pedersen_proof;
import static com.sunya.electionguard.TestUtils.elements_mod_p;
import static com.sunya.electionguard.TestUtils.elgamal_ciphertext;

/** Tester for {@link PlaintextTallyInputValidation */
public class TestPlaintextTallyInputValidation {
  private static final int NGUARDIANS = 4;
  private static final int NAVAILABLE = 3;
  private static final int QUORUM = 2;

  static class ElectionAndTallies {
    final Manifest election;
    final CiphertextTally ctally;
    final PlaintextTally tally;

    public ElectionAndTallies(Manifest election, CiphertextTally ctally, PlaintextTally tally) {
      this.election = election;
      this.ctally = ctally;
      this.tally = tally;
    }
  }

  ElectionAndTallies makeTally() {
    ElectionInputBuilder ebuilder = new ElectionInputBuilder("ballot_id");
    Manifest election = ebuilder.addContest("contest_id")
            .addSelection("selection_id", "candidate_1")
            .addSelection("selection_id2", "candidate_2")
            .done()
            .build();
    assertThat(validateElection(election)).isTrue();

    CiphertextTallyInputBuilder builder = new CiphertextTallyInputBuilder("ctally");
    for (Manifest.ContestDescription contest : election.contests) {
      CiphertextTallyInputBuilder.ContestBuilder cbuilder = builder.addContest(contest.object_id, contest.crypto_hash());
      for (Manifest.SelectionDescription selection : contest.ballot_selections) {
        cbuilder.addSelection(selection.object_id, selection.crypto_hash(), elgamal_ciphertext());
      }
    }
    CiphertextTally ctally = builder.build();

    PlaintextTallyInputBuilder tbuilder = new PlaintextTallyInputBuilder("ctally");
    for (CiphertextTally.Contest contest : ctally.contests.values()) {
      PlaintextTallyInputBuilder.ContestBuilder cbuilder = tbuilder.addContest(contest.object_id());
      for (CiphertextTally.Selection selection : contest.selections.values()) {
        cbuilder.addSelection(selection.object_id(), selection.ciphertext())
                .addShare(selection.object_id(), "guardian1", null)
                .addShare(selection.object_id(), "guardian2", null)
                .addShare(selection.object_id(), "guardian3", null)
                .addShare(selection.object_id(), "guardian4", null);
      }
    }

    return new ElectionAndTallies(election, ctally, tbuilder.build());
  }

  private boolean validateElection(Manifest election) {
    ElectionInputValidation validator = new ElectionInputValidation(election);
    Formatter problems = new Formatter();
    boolean isValid = validator.validateElection(problems);
    if (!isValid) {
      System.out.printf("Manifest Problems=%n%s", problems);
    }
    return isValid;
  }

  void validateTally(Manifest election, CiphertextTally ctally, PlaintextTally tally, String expectMessage) {
    PlaintextTallyInputValidation validator = new PlaintextTallyInputValidation(election, ctally, NGUARDIANS, NAVAILABLE);
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
  public void testDefaultOk() {
    ElectionAndTallies tallies = makeTally();
    validateTally(tallies.election, tallies.ctally, tallies.tally, null);
  }

  @Example
  public void testContestId() {
    ElectionAndTallies tallies = makeTally();

    PlaintextTallyInputBuilder tbuilder = new PlaintextTallyInputBuilder("ctally");
    for (CiphertextTally.Contest contest : tallies.ctally.contests.values()) {
      PlaintextTallyInputBuilder.ContestBuilder cbuilder = tbuilder.addContest("bad_contest_id");
      for (CiphertextTally.Selection selection : contest.selections.values()) {
        cbuilder.addSelection(selection.object_id(), selection.ciphertext())
                .addShare(selection.object_id(), "guardian1", null)
                .addShare(selection.object_id(), "guardian2", null)
                .addShare(selection.object_id(), "guardian3", null)
                .addShare(selection.object_id(), "guardian4", null);
      }
    }

    validateTally(tallies.election, tallies.ctally, tbuilder.build(), "PlaintextTally.A.1 Tally Contest 'bad_contest_id' does not exist in manifest");
    validateTally(tallies.election, tallies.ctally, tbuilder.build(), "PlaintextTally.B.1 PlaintextTally Contest 'bad_contest_id' does not exist in CiphertextTally.Contest");
  }

  @Example
  public void testSelectionId() {
    ElectionAndTallies tallies = makeTally();

    PlaintextTallyInputBuilder tbuilder = new PlaintextTallyInputBuilder("ctally");
    for (CiphertextTally.Contest contest : tallies.ctally.contests.values()) {
      PlaintextTallyInputBuilder.ContestBuilder cbuilder = tbuilder.addContest(contest.object_id());
      for (CiphertextTally.Selection selection : contest.selections.values()) {
        cbuilder.addSelection(selection.object_id() + "bad", selection.ciphertext())
                .addShare(selection.object_id(), "guardian1", null)
                .addShare(selection.object_id(), "guardian2", null)
                .addShare(selection.object_id(), "guardian3", null)
                .addShare(selection.object_id(), "guardian4", null);
      }
    }

    validateTally(tallies.election, tallies.ctally, tbuilder.build(), "PlaintextTally.A.2 Tally Selection 'selection_id2bad' does not exist in contest 'contest_id'");
    validateTally(tallies.election, tallies.ctally, tbuilder.build(), "PlaintextTally.B.2 PlaintextTally Selection 'selection_id2bad' does not exist in CiphertextTally contest");
  }

  @Example
  public void testSelectionMessage() {
    ElectionAndTallies tallies = makeTally();

    PlaintextTallyInputBuilder tbuilder = new PlaintextTallyInputBuilder("ctally");
    for (CiphertextTally.Contest contest : tallies.ctally.contests.values()) {
      PlaintextTallyInputBuilder.ContestBuilder cbuilder = tbuilder.addContest(contest.object_id());
      for (CiphertextTally.Selection selection : contest.selections.values()) {
        cbuilder.addSelection(selection.object_id(), elgamal_ciphertext())
                .addShare(selection.object_id(), "guardian1", null)
                .addShare(selection.object_id(), "guardian2", null)
                .addShare(selection.object_id(), "guardian3", null)
                .addShare(selection.object_id(), "guardian4", null);
      }
    }

    validateTally(tallies.election, tallies.ctally, tbuilder.build(), "PlaintextTally.B.2.1 PlaintextTally Selection 'selection_id' message does not match CiphertextTally");
  }

  @Example
  public void testDuplicateContest() {
    ElectionAndTallies tallies = makeTally();

    PlaintextTallyInputBuilder tbuilder = new PlaintextTallyInputBuilder("ctally");
    for (CiphertextTally.Contest contest : tallies.ctally.contests.values()) {
      PlaintextTallyInputBuilder.ContestBuilder cbuilder = tbuilder.addContest(contest.object_id());
      for (CiphertextTally.Selection selection : contest.selections.values()) {
        cbuilder.addSelection(selection.object_id(), selection.ciphertext())
                .addShare(selection.object_id(), "guardian1", null)
                .addShare(selection.object_id(), "guardian2", null)
                .addShare(selection.object_id(), "guardian3", null)
                .addShare(selection.object_id(), "guardian4", null);
      }
    }

    validateTally(tallies.election, tallies.ctally, tbuilder.buildBadContest(), "PlaintextTally.C.1 Contest id key 'contest_idbad' doesnt match value 'contest_id'");
  }

  @Example
  public void testDuplicateSelection() {
    ElectionAndTallies tallies = makeTally();

    PlaintextTallyInputBuilder tbuilder = new PlaintextTallyInputBuilder("ctally");
    for (CiphertextTally.Contest contest : tallies.ctally.contests.values()) {
      PlaintextTallyInputBuilder.ContestBuilder cbuilder = tbuilder.addContest(contest.object_id());
      for (CiphertextTally.Selection selection : contest.selections.values()) {
        cbuilder.addSelection(selection.object_id(), selection.ciphertext())
                .addShare(selection.object_id(), "guardian1", null)
                .addShare(selection.object_id(), "guardian2", null)
                .addShare(selection.object_id(), "guardian3", null)
                .addShare(selection.object_id(), "guardian4", null);
      }
    }

    validateTally(tallies.election, tallies.ctally, tbuilder.buildBadSelection(), "PlaintextTally.C.2 Selection id key 'selection_id2bad' doesnt match value 'selection_id2'");
  }

  @Example
  public void testShareObjectId() {
    ElectionAndTallies tallies = makeTally();

    PlaintextTallyInputBuilder tbuilder = new PlaintextTallyInputBuilder("ctally");
    for (CiphertextTally.Contest contest : tallies.ctally.contests.values()) {
      PlaintextTallyInputBuilder.ContestBuilder cbuilder = tbuilder.addContest(contest.object_id());
      for (CiphertextTally.Selection selection : contest.selections.values()) {
        cbuilder.addSelection(selection.object_id(), selection.ciphertext())
                .addShare(selection.object_id() + "bad", "guardian1", null)
                .addShare(selection.object_id(), "guardian2", null)
                .addShare(selection.object_id(), "guardian3", null)
                .addShare(selection.object_id(), "guardian4", null);
      }
    }

    validateTally(tallies.election, tallies.ctally, tbuilder.build(), "PlaintextTally.D.1 Share id 'selection_idbad' doesnt match selection 'selection_id'");
  }

  @Example
  public void testShareGuardianId() {
    ElectionAndTallies tallies = makeTally();

    PlaintextTallyInputBuilder tbuilder = new PlaintextTallyInputBuilder("ctally");
    for (CiphertextTally.Contest contest : tallies.ctally.contests.values()) {
      PlaintextTallyInputBuilder.ContestBuilder cbuilder = tbuilder.addContest(contest.object_id());
      for (CiphertextTally.Selection selection : contest.selections.values()) {
        cbuilder.addSelection(selection.object_id(), selection.ciphertext())
                .addShare(selection.object_id(), "guardian1", null)
                .addShare(selection.object_id(), "guardian2", null)
                .addShare(selection.object_id(), "guardian3", null)
                .addShare(selection.object_id(), "guardian1", null);
      }
    }

    validateTally(tallies.election, tallies.ctally, tbuilder.build(), "PlaintextTally.D.2 Multiple shares have same guardian_id 'guardian1'");
  }

  @Example
  public void testShareNGuardian() {
    ElectionAndTallies tallies = makeTally();

    PlaintextTallyInputBuilder tbuilder = new PlaintextTallyInputBuilder("ctally");
    for (CiphertextTally.Contest contest : tallies.ctally.contests.values()) {
      PlaintextTallyInputBuilder.ContestBuilder cbuilder = tbuilder.addContest(contest.object_id());
      for (CiphertextTally.Selection selection : contest.selections.values()) {
        cbuilder.addSelection(selection.object_id(), selection.ciphertext())
                .addShare(selection.object_id(), "guardian1", null)
                .addShare(selection.object_id(), "guardian2", null)
                .addShare(selection.object_id(), "guardian3", null);
      }
    }

    validateTally(tallies.election, tallies.ctally, tbuilder.build(), "PlaintextTally.D.3 tallySelection 'selection_id' number of shares = 3 should be 4");
  }

  //////////////////////////////////////
  // recovered parts
  Map<String, DecryptionShare.CiphertextCompensatedDecryptionSelection> makeParts(String object_id, String missing_guardian_id, int n, boolean bad) {
    Map<String, DecryptionShare.CiphertextCompensatedDecryptionSelection> parts = new HashMap<>();
    for (int i=0; i < n; i++) {
      String guardian_id = "guardian"+i;
      parts.put(guardian_id, DecryptionShare.CiphertextCompensatedDecryptionSelection.create(
              object_id,
              bad ? "bad" : guardian_id,
              missing_guardian_id,
              elements_mod_p(),
              elements_mod_p(),
              chaum_pedersen_proof()));
    }
    return parts;
  }

  @Example
  public void testSharePartsOk() {
    ElectionAndTallies tallies = makeTally();

    PlaintextTallyInputBuilder tbuilder = new PlaintextTallyInputBuilder("ctally");
    for (CiphertextTally.Contest contest : tallies.ctally.contests.values()) {
      PlaintextTallyInputBuilder.ContestBuilder cbuilder = tbuilder.addContest(contest.object_id());
      for (CiphertextTally.Selection selection : contest.selections.values()) {
        cbuilder.addSelection(selection.object_id(), selection.ciphertext())
                .addShare(selection.object_id(), "guardian1", null)
                .addShare(selection.object_id(), "guardian2", null)
                .addShare(selection.object_id(), "guardian3", null)
                .addShare(selection.object_id(), "guardian4", null, makeParts(selection.object_id(), "guardian4", NAVAILABLE, false));
      }
    }

    validateTally(tallies.election, tallies.ctally, tbuilder.build(), null);
  }

  @Example
  public void testSharePartsObjectId() {
    ElectionAndTallies tallies = makeTally();

    PlaintextTallyInputBuilder tbuilder = new PlaintextTallyInputBuilder("ctally");
    for (CiphertextTally.Contest contest : tallies.ctally.contests.values()) {
      PlaintextTallyInputBuilder.ContestBuilder cbuilder = tbuilder.addContest(contest.object_id());
      for (CiphertextTally.Selection selection : contest.selections.values()) {
        cbuilder.addSelection(selection.object_id(), selection.ciphertext())
                .addShare(selection.object_id(), "guardian1", null)
                .addShare(selection.object_id(), "guardian2", null)
                .addShare(selection.object_id(), "guardian3", null)
                .addShare(selection.object_id(), "guardian4", null, makeParts("badid", "guardian4", NAVAILABLE, false));
      }
    }

    validateTally(tallies.election, tallies.ctally, tbuilder.build(), "PlaintextTally.E.1 recovered_parts object_id 'badid' doesnt match share object_id 'selection_id'");
    validateTally(tallies.election, tallies.ctally, tbuilder.build(), "PlaintextTally.E.1 recovered_parts object_id 'badid' doesnt match share object_id 'selection_id2'");
  }

  @Example
  public void testSharePartsBadMap() {
    ElectionAndTallies tallies = makeTally();

    PlaintextTallyInputBuilder tbuilder = new PlaintextTallyInputBuilder("ctally");
    for (CiphertextTally.Contest contest : tallies.ctally.contests.values()) {
      PlaintextTallyInputBuilder.ContestBuilder cbuilder = tbuilder.addContest(contest.object_id());
      for (CiphertextTally.Selection selection : contest.selections.values()) {
        cbuilder.addSelection(selection.object_id(), selection.ciphertext())
                .addShare(selection.object_id(), "guardian1", null)
                .addShare(selection.object_id(), "guardian2", null)
                .addShare(selection.object_id(), "guardian3", null)
                .addShare(selection.object_id(), "guardian4", null, makeParts("badid", "guardian4", NAVAILABLE, true));
      }
    }

    validateTally(tallies.election, tallies.ctally, tbuilder.build(), "PlaintextTally.E.2 recovered_parts key 'guardian0' doesnt match value.guardian_id 'bad'");
  }

  @Example
  public void testSharePartsNavailable() {
    ElectionAndTallies tallies = makeTally();

    PlaintextTallyInputBuilder tbuilder = new PlaintextTallyInputBuilder("ctally");
    for (CiphertextTally.Contest contest : tallies.ctally.contests.values()) {
      PlaintextTallyInputBuilder.ContestBuilder cbuilder = tbuilder.addContest(contest.object_id());
      for (CiphertextTally.Selection selection : contest.selections.values()) {
        cbuilder.addSelection(selection.object_id(), selection.ciphertext())
                .addShare(selection.object_id(), "guardian1", null)
                .addShare(selection.object_id(), "guardian2", null)
                .addShare(selection.object_id(), "guardian3", null)
                .addShare(selection.object_id(), "guardian4", null, makeParts(selection.object_id(), "guardian4", QUORUM, false));
      }
    }

    validateTally(tallies.election, tallies.ctally, tbuilder.build(), "PlaintextTally.E.3 share 'selection_id' number of parts = 2 should be navailable = 3");
    validateTally(tallies.election, tallies.ctally, tbuilder.build(), "PlaintextTally.E.3 share 'selection_id2' number of parts = 2 should be navailable = 3");
  }

  @Example
  public void testSharePartsMissingGuardianId() {
    ElectionAndTallies tallies = makeTally();

    PlaintextTallyInputBuilder tbuilder = new PlaintextTallyInputBuilder("ctally");
    for (CiphertextTally.Contest contest : tallies.ctally.contests.values()) {
      PlaintextTallyInputBuilder.ContestBuilder cbuilder = tbuilder.addContest(contest.object_id());
      for (CiphertextTally.Selection selection : contest.selections.values()) {
        cbuilder.addSelection(selection.object_id(), selection.ciphertext())
                .addShare(selection.object_id(), "guardian1", null)
                .addShare(selection.object_id(), "guardian2", null)
                .addShare(selection.object_id(), "guardian3", null)
                .addShare(selection.object_id(), "guardian4", null, makeParts(selection.object_id(), "guardian3", NAVAILABLE, false));
      }
    }

    validateTally(tallies.election, tallies.ctally, tbuilder.build(), "PlaintextTally.E.4 missing_guardian_id 'guardian3' doesnt match share guardian_id 'guardian4'");
  }


  ///////////////////////////////////

  @Example
  public void testDefaultOkToo() {
    ElectionAndTallies tallies = makeTally();

    PlaintextTallyInputBuilder tbuilder = new PlaintextTallyInputBuilder("ctally");
    for (CiphertextTally.Contest contest : tallies.ctally.contests.values()) {
      PlaintextTallyInputBuilder.ContestBuilder cbuilder = tbuilder.addContest(contest.object_id());
      for (CiphertextTally.Selection selection : contest.selections.values()) {
        cbuilder.addSelection(selection.object_id(), selection.ciphertext())
                .addShare(selection.object_id(), "guardian1", null)
                .addShare(selection.object_id(), "guardian2", null)
                .addShare(selection.object_id(), "guardian3", null)
                .addShare(selection.object_id(), "guardian4", null);
      }
    }

    validateTally(tallies.election, tallies.ctally, tbuilder.build(), null);
  }
}
