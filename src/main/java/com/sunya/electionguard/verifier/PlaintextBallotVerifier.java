package com.sunya.electionguard.verifier;

import com.google.common.flogger.FluentLogger;
import com.sunya.electionguard.Election;
import com.sunya.electionguard.PlaintextBallot;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * This verifies specification "12B Correct Decryption of Spoiled Ballots".
 * An election verifier should also confirm that for each decrypted spoiled ballot, the selections
 * listed in text match the corresponding text in the ballot coding file.
 * @see <a href="https://www.electionguard.vote/spec/0.95.0/9_Verifier_construction/#validation-of-correct-decryption-of-tallies">Tally decryption validation</a>
 */
public class PlaintextBallotVerifier {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  final ElectionRecord electionRecord;
  // Map<CONTEST_ID, SET<SELECTION_ID>>
  final Map<String, Set<String>> names = new HashMap<>();

  PlaintextBallotVerifier(ElectionRecord electionRecord) {
    this.electionRecord = electionRecord;
    for (Election.ContestDescription contest : electionRecord.election.contests) {
      HashSet<String> selectionNames = new HashSet<>();
      for (Election.SelectionDescription selection : contest.ballot_selections) {
        selectionNames.add(selection.object_id);
      }
      names.put(contest.object_id, selectionNames);
    }
  }

  boolean verify_plaintext_ballot() {
    boolean error = false;
    for (PlaintextBallot ballot : electionRecord.spoiledBallots) {
      for (PlaintextBallot.Contest contest : ballot.contests) {
        Set<String> selectionNames = names.get(contest.contest_id);
        if (selectionNames == null) {
          System.out.printf(" ***Ballot Contest id (%s) not contained in ballot coding file.%n", contest.contest_id);
          error = true;
          continue;
        }
        for (PlaintextBallot.Selection selection : contest.ballot_selections) {
          if (selection.is_placeholder_selection) {
            continue;
          }
          if (!selectionNames.contains(selection.selection_id)) {
              System.out.printf(" ***Ballot Selection id (%s) not contained in contest (%s).%n", selection.selection_id, contest.contest_id);
              error = true;
          }
        }
      }
    }

    if (error) {
      System.out.printf(" ***12.B Spoiled PlaintextBallot Names Validation failed.%n");
    } else {
      System.out.printf(" 12.B Spoiled PlaintextBallot Names Validation success.%n");
    }
    return !error;
  }
}
