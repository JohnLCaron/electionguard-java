package com.sunya.electionguard;

import com.sunya.electionguard.publish.PlaintextBallotFromJson;

import javax.annotation.Nullable;
import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static com.google.common.truth.Truth.assertWithMessage;
import static com.sunya.electionguard.Ballot.*;
import static com.sunya.electionguard.Election.*;

public class BallotFactory {
  private static final String simple_ballot_filename = "ballot_in_simple.json";
  private static final String simple_ballots_filename = "plaintext_ballots_simple.json";

  static PlaintextBallotSelection get_random_selection_from(SelectionDescription description) {
    return Encrypt.selection_from(description, false, TestUtils.randomBool());
  }

  /**
   *         Get a randomly filled contest for the given description that
   *         may be undervoted and may include explicitly false votes.
   *         Since this is only used for testing, the random number generator
   *         (`random`) must be provided to make this function deterministic.
   */
  PlaintextBallotContest get_random_contest_from(
          ContestDescription description,
          boolean suppress_validity_check, // false, false
          boolean with_trues) {

    if (!suppress_validity_check) {
      boolean ok = description.is_valid();
      if (!ok) {
        assertWithMessage("the contest description must be valid").that(description.is_valid()).isTrue();
      }
    }

    List<PlaintextBallotSelection> selections = new ArrayList<>();

    int voted = 0;

    for (SelectionDescription selection_description : description.ballot_selections) {
      PlaintextBallotSelection selection = this.get_random_selection_from(selection_description);
          // the caller may force a true value
      voted += selection.to_int();
      if (with_trues && voted <= 1 && selection.to_int() == 1) {
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

    return new PlaintextBallotContest(description.object_id, selections);
  }

  /**
   * Get a single Fake Ballot object that is manually constructed with default vaules .
   */
  PlaintextBallot get_fake_ballot(
          InternalElectionDescription election,
          @Nullable String ballot_id,
          boolean with_trues) { // default true

    if (ballot_id == null) {
      ballot_id = "some-unique-ballot-id-123";
    }

    List<PlaintextBallotContest> contests = new ArrayList<>();
    for (ContestDescriptionWithPlaceholders contest : election.get_contests_for(election.ballot_styles.get(0).object_id)) {
      contests.add(this.get_random_contest_from(contest, false, with_trues));
    }

    return new PlaintextBallot(ballot_id, election.ballot_styles.get(0).object_id, contests);
  }

  ///////////////////////////////////////////////////

  static PlaintextBallotSelection get_selection_well_formed() {
    ExtendedData extra_data = new ExtendedData("random", 33);
    return new PlaintextBallotSelection("selection-{draw(uuids)}",
                TestUtils.randomBool() ? "true" : "false",
                TestUtils.randomBool(),
                TestUtils.randomBool() ? Optional.of(extra_data) : Optional.empty());
  }

  static PlaintextBallotSelection get_selection_poorly_formed() {
    ExtendedData extra_data = new ExtendedData("random", 33);
    return new PlaintextBallotSelection("selection-{draw(uuids)}",
            TestUtils.randomBool() ? "yeah" : "nope",
            TestUtils.randomBool(),
            TestUtils.randomBool() ? Optional.of(extra_data) : Optional.empty());
  }

  //////////////////////////////////////////////////////////////////////////////////////

  @Nullable
  List<PlaintextBallot> get_simple_ballots_from_file() throws IOException {
    String current = new java.io.File("./src/test/resources/").getCanonicalPath();
    PlaintextBallotFromJson builder = new PlaintextBallotFromJson(current + this.simple_ballots_filename);
    return builder.get_ballots_from_file();
  }

  @Nullable
  PlaintextBallot get_simple_ballot_from_file() throws IOException {
    return this._get_ballot_from_file(this.simple_ballot_filename);
  }

  @Nullable
  private PlaintextBallot _get_ballot_from_file(String filename) throws IOException {
    String current = new java.io.File("./src/test/resources/").getCanonicalPath();
    PlaintextBallotFromJson builder = new PlaintextBallotFromJson(current + filename);
    return builder.get_ballot_from_file();
  }

}
