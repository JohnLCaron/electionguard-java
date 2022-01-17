package com.sunya.electionguard.verifier;

import com.google.common.base.Preconditions;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Iterables;
import com.google.common.collect.ListMultimap;
import com.google.common.flogger.FluentLogger;
import com.sunya.electionguard.BallotBox;
import com.sunya.electionguard.SubmittedBallot;
import com.sunya.electionguard.CiphertextBallot;
import com.sunya.electionguard.ElGamal;
import com.sunya.electionguard.PlaintextTally;

import java.util.List;

/**
 * This verifies specification section "7 Correctness of Ballot Aggregation"
 * @see <a href="https://www.electionguard.vote/spec/0.95.0/9_Verifier_construction/#correctness-of-ballot-aggregation">Ballot aggregation validation</a>
 */
public class BallotAggregationVerifier {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  final ElectionRecord electionRecord;
  final PlaintextTally decryptedTally;

  BallotAggregationVerifier(ElectionRecord electionRecord) {
    this.electionRecord = electionRecord;
    this.decryptedTally = electionRecord.decryptedTally;
  }

  /**
   * 7. Confirm for each (non-dummy) option in each contest in the ballot coding file that the aggregate encryption,
   * (𝐴, 𝐵) satisfies 𝐴 = ∏ 𝛼 and 𝐵 = ∏ 𝛽 where the (𝛼 , 𝛽) are the corresponding encryptions on all cast ballots
   * in the election record.
   */
  boolean verify_ballot_aggregation() {
    boolean error = false;
    SelectionAggregator agg = new SelectionAggregator(electionRecord.acceptedBallots);
    Preconditions.checkNotNull(decryptedTally);

    int ncontests = 0;
    int nselections = 0;
    for (PlaintextTally.Contest contest : decryptedTally.contests.values()) {
      ncontests++;
      for (PlaintextTally.Selection selection : contest.selections().values()) {
        nselections++;
        String key = contest.object_id() + "." + selection.object_id();
        List<ElGamal.Ciphertext> encryptions = agg.selectionEncryptions.get(key);
        // LOOK its possible no ballots voted one way or another
        if (!encryptions.isEmpty()) {
          ElGamal.Ciphertext product = ElGamal.elgamal_add(Iterables.toArray(encryptions, ElGamal.Ciphertext.class));
          if (!product.equals(selection.message())) {
            System.out.printf(" 7. Ballot Aggregation Validation failed for %s.%n", key);
            error = true;
          }
        } else {
          System.out.printf("  No ballots for talley key %s%n", key);
        }
      }
    }

    if (error) {
      System.out.printf(" ***Ballot Aggregation Validation failed.%n");
    } else {
      System.out.printf(" Ballot Aggregation Validation success on %d cast ballots and %d contests and %d selections.%n",
              agg.nballotsCast, ncontests, nselections);
    }
    return !error;
  }

  private static class SelectionAggregator {
    ListMultimap<String, ElGamal.Ciphertext> selectionEncryptions = ArrayListMultimap.create();
    int nballotsCast = 0;
    SelectionAggregator(Iterable<SubmittedBallot> ballots) {
      for (SubmittedBallot ballot : ballots) {
        if (ballot.state == BallotBox.State.CAST) {
          nballotsCast++;
          for (CiphertextBallot.Contest contest : ballot.contests) {
            for (CiphertextBallot.Selection selection : contest.ballot_selections) {
              String key = contest.object_id + "." + selection.object_id;
              selectionEncryptions.put(key, selection.ciphertext());
            }
          }
        }
      }
    }
  }
}
