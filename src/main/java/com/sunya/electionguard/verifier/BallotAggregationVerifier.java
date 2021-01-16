package com.sunya.electionguard.verifier;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Iterables;
import com.google.common.collect.ListMultimap;
import com.google.common.flogger.FluentLogger;
import com.sunya.electionguard.Ballot;
import com.sunya.electionguard.ElGamal;
import com.sunya.electionguard.Group;
import com.sunya.electionguard.Tally;
import com.sunya.electionguard.publish.Consumer;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.sunya.electionguard.Group.ElementModP;

/**
 * This verifies specification section "7. Correctness of Ballot Aggregation", and
 * section "11. Correct Decryption of Tallies"
 */
public class BallotAggregationVerifier {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  final ElectionParameters electionParameters;
  final Consumer consumer;
  final Tally.PlaintextTally tally;
  final Grp grp;

  BallotAggregationVerifier(ElectionParameters electionParameters, Consumer consumer) throws IOException {
    this.electionParameters = electionParameters;
    this.consumer = consumer;
    this.tally = consumer.plaintextTally();
    this.grp = new Grp(electionParameters.large_prime(), electionParameters.small_prime());
  }

  /**
   * 7. Confirm for each (non-dummy) option in each contest in the ballot coding file that the aggregate encryption,
   * (𝐴, 𝐵) satisfies 𝐴 = ∏ 𝛼 and 𝐵 = ∏ 𝛽 where the (𝛼 , 𝛽) are the corresponding encryptions on all cast ballots
   * in the election record.
   */
  boolean verify_ballot_aggregation() throws IOException {
    boolean error = false;
    SelectionAggregator agg = new SelectionAggregator(consumer.ballots());

    for (Tally.PlaintextTallyContest contest : tally.contests().values()) {
      for (Tally.PlaintextTallySelection selection : contest.selections().values()) {
        String key = contest.object_id() + "." + selection.object_id();
        List<ElGamal.Ciphertext> encryptions = agg.selectionEncryptions.get(key);
        ElGamal.Ciphertext product = ElGamal.elgamal_add(Iterables.toArray(encryptions, ElGamal.Ciphertext.class));
        if (!product.equals(selection.message())) {
          System.out.printf(" Ballot Aggregation Validation failed for %s.%n", key);
          error = true;
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
   * (B) M = g t mod p.
   */
  boolean verify_tally_decryption() throws IOException {
    boolean error = false;

    for (Tally.PlaintextTallyContest contest : tally.contests().values()) {
      for (Tally.PlaintextTallySelection selection : contest.selections().values()) {
        List<ElementModP> partialDecryptions = selection.shares().stream().map(s -> s.share()).collect(Collectors.toList());
        ElementModP productMi = Group.mult_p(partialDecryptions);
        ElementModP M = selection.value();
        ElementModP B = selection.message().data;
        if (!B.equals(Group.mult_p(M, productMi))) {
          System.out.printf(" Tally Decryption failed for %s-%s.%n", contest.object_id(), selection.object_id());
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

  /**
   * 7. Confirm for each (non-dummy) option in each contest in the ballot coding file that the aggregate encryption,
   * (𝐴, 𝐵) satisfies 𝐴 = ∏ 𝛼 and 𝐵 = ∏ 𝛽 where the (𝛼 , 𝛽) are the corresponding encryptions on all cast ballots
   * in the election record.
   */
  boolean match_total_across_ballots() throws IOException {
    SelectionAggregator agg = new SelectionAggregator(consumer.ballots());
    SelectionInfoAggregator aggregator = new SelectionInfoAggregator(consumer.plaintextTally(),
            consumer.ballots(), consumer.election());
    boolean error = !this.match_total_across_ballots(aggregator, agg);

    if (error) {
      System.out.printf(" ***Ballot Aggregation Validation failed.%n");
    } else {
      System.out.printf(" Ballot Aggregation Validation success.%n");
    }
    return !error;
  }

  // 7. Confirm for each (non-dummy) option in each contest in the ballot coding file that the aggregate encryption,
  //   (𝐴, 𝐵) satisfies 𝐴 = ∏ 𝛼 and 𝐵 = ∏ 𝛽 where the (𝛼 , 𝛽) are the corresponding encryptions on all cast ballots
  //   in the election record.
  // Section 7. An election verifier must confirm for each (non-placeholder) option in each contest in the ballot
  // coding file that the aggregate encryption (A, B) satisfies A = ∏ j α j and B = ∏ j β j where the
  // (α j , β j ) are the corresponding encryptions on all cast ballots in the election record.
  private boolean match_total_across_ballots(SelectionInfoAggregator aggOld, SelectionAggregator aggNew) {
    boolean error = false;

    List<Map<String, ElementModP>> dics_by_contest = aggOld.get_dics();
    Map<String, Map<String, ElementModP>> total_data_dic = aggOld.get_total_data();
    Map<String, Map<String, ElementModP>> total_pad_dic = aggOld.get_total_pad();

    for (String contest_name : tally.contests().keySet()) {
      //get the corresponding index of pad and data dictionaries given contest name
      int pad_dic_idx = aggOld.get_dic_id_by_contest_name(contest_name, "a");
      int data_dic_idx = aggOld.get_dic_id_by_contest_name(contest_name, "b");
      Map<String, ElementModP> pad_dic = dics_by_contest.get(pad_dic_idx);
      Map<String, ElementModP> data_dic = dics_by_contest.get(data_dic_idx);

      for (String selection_name : pad_dic.keySet()) {
        ElementModP accum_pad = pad_dic.get(selection_name);
        ElementModP tally_pad = total_pad_dic.get(contest_name).get(selection_name);
        ElementModP accum_data = data_dic.get(selection_name);
        ElementModP tally_data = total_data_dic.get(contest_name).get(selection_name);
        if (!accum_pad.equals(tally_pad)) {
          error = true;
        }
        if (!accum_data.equals(tally_data)) {
          error = true;
        }

        String key = contest_name + "." + selection_name;
        List<ElGamal.Ciphertext> encryptions = aggNew.selectionEncryptions.get(key);
        ElGamal.Ciphertext product = ElGamal.elgamal_add(Iterables.toArray(encryptions, ElGamal.Ciphertext.class));
        if (!product.pad.equals(tally_pad)) {
          error = true;
        }
        if (!product.data.equals(tally_data)) {
          error = true;
        }
      }
      if (error) {
        logger.atSevere().log("Tally error.");
      }
    }
    return (!error);
  }

  private static class SelectionAggregator {
    ListMultimap<String, ElGamal.Ciphertext> selectionEncryptions = ArrayListMultimap.create();
    SelectionAggregator(List<Ballot.CiphertextAcceptedBallot> ballots) {
      for (Ballot.CiphertextAcceptedBallot ballot : ballots) {
        if (ballot.state == Ballot.BallotBoxState.CAST) {
          for (Ballot.CiphertextBallotContest contest : ballot.contests) {
            for (Ballot.CiphertextBallotSelection selection : contest.ballot_selections) {
              selectionEncryptions.put(contest.object_id + "." + selection.object_id, selection.ciphertext());
            }
          }
        }
      }
    }
  }
}
