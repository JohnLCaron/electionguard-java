package com.sunya.electionguard.verifier;

import com.google.common.flogger.FluentLogger;
import com.sunya.electionguard.Tally;
import com.sunya.electionguard.publish.Consumer;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import static com.sunya.electionguard.Group.ElementModP;

/** This verifies specification section "7. Correctness of Ballot Aggregation". */
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
  boolean match_total_across_ballots() throws IOException {
    boolean total_error = false;

    String tally_name = this.tally.object_id();

    // 7. confirm that the aggregate encryption are the accumulative product of all
    // corresponding encryption on all cast ballots
    SelectionInfoAggregator aggregator = new SelectionInfoAggregator(consumer.plaintextTally(),
            consumer.ballots(), consumer.election());
    boolean total_res = this.match_total_across_ballots(aggregator);
    if (!total_res) {
      total_error = true;
    }

    return !total_error;
  }

  // 7. Confirm for each (non-dummy) option in each contest in the ballot coding file that the aggregate encryption,
  //   (𝐴, 𝐵) satisfies 𝐴 = ∏ 𝛼 and 𝐵 = ∏ 𝛽 where the (𝛼 , 𝛽) are the corresponding encryptions on all cast ballots
  //   in the election record.
  // Section 7. An election verifier must confirm for each (non-placeholder) option in each contest in the ballot
  // coding file that the aggregate encryption (A, B) satisfies A = ∏ j α j and B = ∏ j β j where the
  // (α j , β j ) are the corresponding encryptions on all cast ballots in the election record.
  private boolean match_total_across_ballots(SelectionInfoAggregator aggregator) {
    boolean error = false;

    List<Map<String, ElementModP>> dics_by_contest = aggregator.get_dics();
    Map<String, Map<String, ElementModP>> total_data_dic = aggregator.get_total_data();
    Map<String, Map<String, ElementModP>> total_pad_dic = aggregator.get_total_pad();

    for (String contest_name : tally.contests().keySet()) {
      //get the corresponding index of pad and data dictionaries given contest name
      int pad_dic_idx = aggregator.get_dic_id_by_contest_name(contest_name, "a");
      int data_dic_idx = aggregator.get_dic_id_by_contest_name(contest_name, "b");
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
      }
      if (error) {
        logger.atSevere().log("Tally error.");
      }
    }

    return (!error);
  }
}
