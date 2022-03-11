package com.sunya.electionguard.verifier;

import com.google.common.flogger.FluentLogger;
import com.sunya.electionguard.Manifest;
import com.sunya.electionguard.PlaintextTally;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

/**
 * This verifies specification "12B Correct Decryption of Spoiled Ballots".
 * "An election verifier should also confirm that for each decrypted spoiled ballot, the selections
 * listed in text match the corresponding text in the ballot coding file."
 * @see <a href="https://www.electionguard.vote/spec/0.95.0/9_Verifier_construction/#validation-of-correct-decryption-of-tallies">Tally decryption validation</a>
 */
public class SpoiledBallotVerifier {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  final ElectionRecord electionRecord;
  // Map<CONTEST_ID, SET<SELECTION_ID>>
  final Map<String, Set<String>> names = new HashMap<>();

  SpoiledBallotVerifier(ElectionRecord electionRecord) {
    this.electionRecord = electionRecord;
    for (Manifest.ContestDescription contest : electionRecord.manifest.contests()) {
      HashSet<String> selectionNames = new HashSet<>();
      for (Manifest.SelectionDescription selection : contest.selections()) {
        selectionNames.add(selection.selectionId());
      }
      names.put(contest.contestId(), selectionNames);
    }
  }

  boolean verify_plaintext_ballot() {
    AtomicBoolean valid = new AtomicBoolean(true);

    try (Stream<PlaintextTally> ballots = electionRecord.spoiledBallots.iterator().stream()) {
      ballots.forEach(ballot -> {
        for (PlaintextTally.Contest contest : ballot.contests.values()) {
          Set<String> selectionNames = names.get(contest.contestId());
          if (selectionNames == null) {
            System.out.printf(" ***Ballot Contest id (%s) not contained in ballot coding file.%n", contest.contestId());
            valid.set(false);
            continue;
          }
          for (PlaintextTally.Selection selection : contest.selections().values()) {
            if (!selectionNames.contains(selection.selectionId())) {
              System.out.printf(" ***Ballot Selection id (%s) not contained in contest (%s).%n", selection.selectionId(), contest.contestId());
              valid.set(false);
            }
          }
        }
      });
    }

    if (!valid.get()) {
      System.out.printf(" ***12.B Spoiled PlaintextTally Names Validation failed.%n");
    } else {
      System.out.printf(" 12.B Spoiled PlaintextTally Names Validation success.%n");
    }
    return valid.get();
  }
}
