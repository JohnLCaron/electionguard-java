package com.sunya.electionguard;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.google.common.truth.Truth.assertWithMessage;

public class TallyTestHelper {

  /**
   * Internal helper function for testing: takes a list of plaintext ballots as input,
   * digs into all of the individual selections and then accumulates them, using
   * their `selection_id` fields as keys. This function only knows what to do with
   * `n_of_m` elections. It's not a general-purpose tallying mechanism for other
   * election types.
   * <p>
   * @param ballots: a list of plaintext ballots
   * @return a map from PlaintextBallotSelection.selection_id's to integer totals
   */
  public static Map<String, Integer> accumulate_plaintext_ballots(List<PlaintextBallot> ballots) {

    Map<String, Integer> result = new HashMap<>();
    for (PlaintextBallot ballot : ballots) {
      for (PlaintextBallot.Contest contest : ballot.contests) {
        for (PlaintextBallot.Selection selection : contest.ballot_selections) {
          assertWithMessage("Placeholder selections should not exist in the plaintext ballots")
                  .that(selection.is_placeholder_selection).isFalse();
          // returns 1 or 0 for n-of-m ballot selections
          result.merge(selection.selection_id, selection.vote, Integer::sum);
        }
      }
    }
    return result;
  }
}
