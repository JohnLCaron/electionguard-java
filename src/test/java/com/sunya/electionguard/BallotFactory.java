package com.sunya.electionguard;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static com.google.common.truth.Truth.assertWithMessage;
import static com.sunya.electionguard.Ballot.*;
import static com.sunya.electionguard.Election.*;


public class BallotFactory {
  private static final String simple_ballot_filename = "ballot_in_simple.json";
  private static final String simple_ballots_filename = "plaintext_ballots_simple.json";

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
      contests.add(this.get_random_contest_from(contest, new Random(), false, with_trues));
    }

    return new PlaintextBallot(ballot_id, election.ballot_styles.get(0).object_id, contests);
  }

  PlaintextBallotSelection get_random_selection_from(
          SelectionDescription description,
          Random random_source,
          boolean is_placeholder) { // default false

    boolean selected = random_source.nextBoolean();
    return Encrypt.selection_from(description, is_placeholder, selected);
  }

  /**
   *         Get a randomly filled contest for the given description that
   *         may be undervoted and may include explicitly false votes.
   *         Since this is only used for testing, the random number generator
   *         (`random`) must be provided to make this function deterministic.
   */
  PlaintextBallotContest get_random_contest_from(
          ContestDescription description,
          Random random,
          boolean suppress_validity_check,
          boolean with_trues) {

    if (!suppress_validity_check) {
      assertWithMessage("the contest description must be valid").that(description.is_valid()).isTrue();
    }

    List<PlaintextBallotSelection> selections = new ArrayList<>();

    int voted = 0;

    for (SelectionDescription selection_description : description.ballot_selections) {
      PlaintextBallotSelection selection = this.get_random_selection_from(selection_description, random, false);
          // the caller may force a true value
      voted += selection.to_int();
      if (with_trues && voted <= 1 && selection.to_int() == 1) {
        selections.add(selection);
        continue;
      }

          // Possibly append the true selection, indicating an undervote
      if (voted <= description.number_elected && random.nextBoolean()) {
        selections.add(selection);
        // Possibly append the false selections as well, indicating some choices may be explicitly false
      } else if (random.nextBoolean()) {
        selections.add( Encrypt.selection_from(selection_description, false, false));
      }
    }

    return new PlaintextBallotContest(description.object_id, selections);
  }

}
