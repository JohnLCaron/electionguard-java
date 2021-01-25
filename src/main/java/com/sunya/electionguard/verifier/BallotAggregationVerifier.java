package com.sunya.electionguard.verifier;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Iterables;
import com.google.common.collect.ListMultimap;
import com.google.common.flogger.FluentLogger;
import com.sunya.electionguard.Ballot;
import com.sunya.electionguard.ElGamal;
import com.sunya.electionguard.Group;
import com.sunya.electionguard.Tally;

import java.io.IOException;
import java.math.BigInteger;
import java.util.List;
import java.util.stream.Collectors;

import static com.sunya.electionguard.Group.ElementModP;

/**
 * This verifies specification section "7. Correctness of Ballot Aggregation", and
 * section "11. Correct Decryption of Tallies"
 */
public class BallotAggregationVerifier {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  final ElectionRecord electionRecord;
  final Tally.PlaintextTally decryptedTally;
  final Grp grp;

  BallotAggregationVerifier(ElectionRecord electionRecord) {
    this.electionRecord = electionRecord;
    this.decryptedTally = electionRecord.decryptedTally;
    this.grp = new Grp(electionRecord.large_prime(), electionRecord.small_prime());
  }

  /**
   * 7. Confirm for each (non-dummy) option in each contest in the ballot coding file that the aggregate encryption,
   * (𝐴, 𝐵) satisfies 𝐴 = ∏ 𝛼 and 𝐵 = ∏ 𝛽 where the (𝛼 , 𝛽) are the corresponding encryptions on all cast ballots
   * in the election record.
   */
  boolean verify_ballot_aggregation() throws IOException {
    boolean error = false;
    SelectionAggregator agg = new SelectionAggregator(electionRecord.castBallots);

    for (Tally.PlaintextTallyContest contest : decryptedTally.contests().values()) {
      for (Tally.PlaintextTallySelection selection : contest.selections().values()) {
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
      System.out.printf(" Ballot Aggregation Validation success.%n");
    }
    return !error;
  }

  /**
   * 11. An election verifier should confirm the following equations for each (non-placeholder) option in
   * each contest in the ballot coding file.
   * (A) B = (M ⋅ (∏ Mi )) mod p.
   * (B) M = g^t mod p.
   */
  boolean verify_tally_decryption() throws IOException {
    boolean error = false;

    for (Tally.PlaintextTallyContest contest : decryptedTally.contests().values()) {
      for (Tally.PlaintextTallySelection selection : contest.selections().values()) {
        String key = contest.object_id() + "." + selection.object_id();
        List<ElementModP> partialDecryptions = selection.shares().stream().map(s -> s.share()).collect(Collectors.toList());
        ElementModP productMi = Group.mult_p(partialDecryptions);
        ElementModP M = selection.value();
        ElementModP B = selection.message().data;
        if (!B.equals(Group.mult_p(M, productMi))) {
          System.out.printf(" 11.A Tally Decryption failed for %s-%s.%n", key);
          error = true;
        }

        ElementModP t = Group.int_to_p_unchecked(BigInteger.valueOf(selection.tally()));
        if (!M.equals(Group.g_pow_p(t))) {
          System.out.printf(" 11.B Tally Decryption failed for %s-%s.%n", key);
          error = true;
        }
      }
    }

    if (error) {
      System.out.printf(" ***Tally Decryption Validation failed.%n");
    } else {
      System.out.printf(" Tally Decryption Validation success.%n");
    }
    return !error;
  }

  private static class SelectionAggregator {
    ListMultimap<String, ElGamal.Ciphertext> selectionEncryptions = ArrayListMultimap.create();

    SelectionAggregator(List<Ballot.CiphertextAcceptedBallot> ballots) {
      for (Ballot.CiphertextAcceptedBallot ballot : ballots) {
        if (ballot.state == Ballot.BallotBoxState.CAST) {
          for (Ballot.CiphertextBallotContest contest : ballot.contests) {
            for (Ballot.CiphertextBallotSelection selection : contest.ballot_selections) {
              String key = contest.object_id + "." + selection.object_id;
              selectionEncryptions.put(key, selection.ciphertext());
            }
          }
        }
      }
    }
  }
}
