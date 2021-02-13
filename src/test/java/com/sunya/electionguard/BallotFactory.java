package com.sunya.electionguard;

import com.google.common.base.Preconditions;
import com.sunya.electionguard.publish.PlaintextBallotPojo;

import java.io.*;
import java.util.ArrayList;
import java.util.List;

import static com.google.common.truth.Truth.assertWithMessage;
import static com.sunya.electionguard.Election.*;
import static com.sunya.electionguard.ElectionWithPlaceholders.ContestWithPlaceholders;

public class BallotFactory {
  private static final String simple_ballot_filename = "ballot_in_simple.json";
  private static final String simple_ballots_filename = "plaintext_ballots_simple.json";

  static PlaintextBallot.Selection get_random_selection_from(SelectionDescription description) {
    return Encrypt.selection_from(description, false, TestUtils.randomBool());
  }

  /**
   * Get a randomly filled contest for the given description that
   * may be undervoted and may include explicitly false votes.
   */
  PlaintextBallot.Contest get_random_contest_from(
          ContestDescription description,
          boolean suppress_validity_check, boolean with_trues) { // default false, false


    if (!suppress_validity_check) {
      boolean ok = description.is_valid();
      if (!ok) {
        assertWithMessage("the contest description must be valid").that(description.is_valid()).isTrue();
      }
    }

    List<PlaintextBallot.Selection> selections = new ArrayList<>();
    int voted = 0;

    for (SelectionDescription selection_description : description.ballot_selections) {
      PlaintextBallot.Selection selection = get_random_selection_from(selection_description);
      // the caller may force a true value
      voted += selection.vote;
      if (with_trues && voted <= 1 && selection.vote == 1) {
        selections.add(selection);
        continue;
      }

      // Possibly append the true selection, indicating an undervote
      if (voted <= description.number_elected && TestUtils.randomBool()) {
        selections.add(selection);
        // Possibly append the false selections as well, indicating some choices may be explicitly false
      } else if (TestUtils.randomBool()) {
        selections.add( Encrypt.selection_from(selection_description, false, false));
      }
    }

    return new PlaintextBallot.Contest(description.object_id, selections);
  }

  /** Get a single Fake Ballot object that is manually constructed with default values . */
  PlaintextBallot get_fake_ballot(
          ElectionWithPlaceholders metadata,
          String ballot_id,
          boolean with_trues) { // default true

    Preconditions.checkNotNull(ballot_id);
    String ballotStyleId = metadata.election.ballot_styles.get(0).object_id;
    List<PlaintextBallot.Contest> contests = new ArrayList<>();
    for (ContestWithPlaceholders contest : metadata.get_contests_for(ballotStyleId)) {
      contests.add(this.get_random_contest_from(contest, true, with_trues));
    }

    return new PlaintextBallot(ballot_id, ballotStyleId, contests);
  }

  ///////////////////////////////////////////////////

  static PlaintextBallot.Selection get_selection_well_formed() {
    CiphertextBallot.ExtendedData extra_data = new CiphertextBallot.ExtendedData("random", 33);
    return new PlaintextBallot.Selection("selection-{draw(uuids)}",
                TestUtils.randomBool() ? 1 : 0,
                false,
                TestUtils.randomBool() ? extra_data : null);
  }

  static PlaintextBallot.Selection get_selection_poorly_formed() {
    CiphertextBallot.ExtendedData extra_data = new CiphertextBallot.ExtendedData("random", 33);
    return new PlaintextBallot.Selection("selection-{draw(uuids)}",
            TestUtils.randomBool() ? 2 : 3,
            TestUtils.randomBool(),
            TestUtils.randomBool() ? extra_data : null);
  }

  //////////////////////////////////////////////////////////////////////////////////////

  List<PlaintextBallot> get_simple_ballots_from_file() throws IOException {
    String current = new java.io.File("./src/test/resources/").getCanonicalPath();
    return get_ballots_from_file(current + "/" + simple_ballots_filename);
  }

  public static List<PlaintextBallot> get_ballots_from_file(String filename) throws IOException {
    return PlaintextBallotPojo.get_ballots_from_file(filename);
  }

  PlaintextBallot get_simple_ballot_from_file() throws IOException {
    String current = new java.io.File("./src/test/resources/").getCanonicalPath();
    return get_ballot_from_file(current + "/" + simple_ballot_filename);
  }

  public static PlaintextBallot get_ballot_from_file(String filename) throws IOException {
    return PlaintextBallotPojo.get_ballot_from_file(filename);
  }

}
