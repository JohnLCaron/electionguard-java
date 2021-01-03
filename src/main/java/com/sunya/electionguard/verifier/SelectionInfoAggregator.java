package com.sunya.electionguard.verifier;

import com.sunya.electionguard.Ballot;
import com.sunya.electionguard.Election;
import com.sunya.electionguard.Group;
import com.sunya.electionguard.Tally;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.sunya.electionguard.Group.*;

/**
 * This SelectionInfoAggregator class aims at collecting and storing all the selection information across contest
 * in one place. Its final purpose is to create a list of dictionaries, each dictionary stands for a contest, inside a
 * dictionary are corresponding selection name and its alpha or beta values. Used in decryption verifier.
 */
public class SelectionInfoAggregator {
  Map<String, Integer> order_names_dic;
  Map<Integer, String> names_order_dic;
  Map<String, List<String>> contest_selection_names; // Contest name, list of selection names

  List<Map<String, ElementModP>> dics_by_contest;
  Map<String, Map<String, ElementModP>> total_pad_dic;
  Map<String, Map<String, ElementModP>> total_data_dic;

  SelectionInfoAggregator(Tally.PlaintextTally tally, List<Ballot.CiphertextAcceptedBallot> ballots,
                          Election.ElectionDescription election) {

    this.order_names_dic = new HashMap<>();         // a dictionary to store the contest names and its sequence
    this.names_order_dic = new HashMap<>();
    this.contest_selection_names = new HashMap<>(); // a dictionary to store the contest names and its selection names
    this.dics_by_contest = new ArrayList<>();       // a list to store all the dics, length = 2 * contest_names
    this.total_pad_dic = new HashMap<>();
    this.total_data_dic = new HashMap<>();

    fill_total_pad_data(tally);
    fill_in_contest_dicts(election);
    create_inner_dic();
    fill_in_dics(ballots);
  }

  /**
   * loop over the tally and read alpha/pad and beta/data of each non dummy selections in all contests,
   * store these alphas and betas in the corresponding contest dictionary
   */
  private void fill_total_pad_data(Tally.PlaintextTally tally) {
    for (Map.Entry<String, Tally.PlaintextTallyContest> contestEntry : tally.contests().entrySet()) {
      String contest_name = contestEntry.getKey();
      Tally.PlaintextTallyContest contest = contestEntry.getValue();

      Map<String, ElementModP> curr_dic_pad = new HashMap<>();
      Map<String, ElementModP> curr_dic_data = new HashMap<>();

      for (Map.Entry<String, Tally.PlaintextTallySelection> entry : contest.selections().entrySet()) {
        String selection_name = entry.getKey();
        Tally.PlaintextTallySelection selection = entry.getValue();
        ElementModP total_pad = selection.message().pad;
        ElementModP total_data = selection.message().data;
        curr_dic_pad.put(selection_name, total_pad);
        curr_dic_data.put(selection_name, total_data);
        this.total_pad_dic.put(contest_name, curr_dic_pad);
        this.total_data_dic.put(contest_name, curr_dic_data);
      }
    }
  }

  /**
   * get contest names, its corresponding sequence, and its corresponding selection names from description,
   * (1) order_names_dic : key - sequence order, value - contest name
   * (2) contest_selection_names: key - contest name, value - a list of selection names
   * :return: None
   */
  void fill_in_contest_dicts(Election.ElectionDescription election) {
    for (Election.ContestDescription contest : election.contests) {
      // fill in order_names_dic dict
      // get contest names
      String contest_name = contest.object_id;
      // get contest sequence
      int contest_sequence = contest.sequence_order;
      this.order_names_dic.put(contest_name, contest_sequence);
      this.names_order_dic.put(contest_sequence, contest_name);

      // fill in contest_selection_names dict
      List<String> curr_list = new ArrayList<>();
      for (Election.SelectionDescription selection : contest.ballot_selections) {
        String selection_name = selection.object_id;
        curr_list.add(selection_name);
      }
      this.contest_selection_names.put(contest_name, curr_list);
    }
  }

  /**
   *         create 2 * contest names number of dicts. Two for each contest, one for storing pad values,
   *         one for storing data values. Fill in column names with selections in that specific contest
   */
  private void create_inner_dic() {
     int num = this.order_names_dic.size();

    // create 2 * contest name number of lists
    for (int i = 0; i < num * 2; i++) {
      // get the corresponding contest and selections of this list
      int contest_idx = i / 2;
      String contest_name = this.names_order_dic.get(contest_idx);
      List<String> selection_names = this.contest_selection_names.get(contest_name);

      // create new dict
      Map<String, ElementModP> curr_dic = new HashMap<>();
      for (String selection_name : selection_names) {
        curr_dic.put(selection_name, null);
      }

      // append to dic list
      this.dics_by_contest.add(curr_dic);
    }
  }

  /**
   * loop over encrypted ballots, go through every ballot to get the selection
   * alpha/pad and beta/data
   */
  private void fill_in_dics(List<Ballot.CiphertextAcceptedBallot> ballots) {
    // loop over every ballot file
    for (Ballot.CiphertextAcceptedBallot ballot : ballots) {
      // ignore spoiled ballots
      if (ballot.state == Ballot.BallotBoxState.CAST) {
        for (Ballot.CiphertextBallotContest contest : ballot.contests) {
          String contest_name = contest.object_id;
          int contest_idx = this.order_names_dic.get(contest_name);
          Map<String, ElementModP> curr_pad_dic = this.dics_by_contest.get(contest_idx * 2);
          Map<String, ElementModP> curr_data_dic = this.dics_by_contest.get(contest_idx * 2 + 1);

          // loop over every selection
          for (Ballot.CiphertextBallotSelection selection : contest.ballot_selections) {
            String selection_name = selection.object_id;
            boolean is_placeholder_selection = selection.is_placeholder_selection;

            // ignore placeholders
            if (!is_placeholder_selection) {
              ElementModP pad = selection.ciphertext.pad;
              ElementModP data = selection.ciphertext.data;
              get_accum_product(curr_pad_dic, selection_name, pad);
              get_accum_product(curr_data_dic, selection_name, data);
            }
          }
        }
      }
    }
  }

  /**
   * get the accumulative product of alpha/pad and beta/data for all the selections
   * :param dic: the dictionary alpha or beta values are being added into
   * :param selection_name: name of a selection, noted as "object id" under a selection
   * :param num: a number being multiplied to get the final product
   */
  private static void get_accum_product(Map<String, ElementModP> dic, String selection_name, ElementModP num) {
    ElementModP current = dic.get(selection_name); // TODO use merge?
    if (current == null) {
      dic.put(selection_name, num);
    } else {
      ElementModP product = Group.mult_p(current, num);
      dic.put(selection_name, product);
    }
  }

  /**
   * get the whole list of dictionaries of contest selection information
   * :return:a list of dictionaries of contest selection information
   */
  List<Map<String, ElementModP>> get_dics() {
    return this.dics_by_contest;
  }

  /**
   * get the corresponding dictionary id in the dictionary list by the name of contest
   * :param contest_name: name of a contest, noted as "object id" under contest
   * :param type: a or b, a stands for alpha, b stands for beta, to denote what values the target dictionary contains
   * :return: a dictionary of alpha or beta values of all the selections of a specific contest.
   */
  int get_dic_id_by_contest_name(String contest_name, String type) {
    if (type.equals("a")) {
      return 2 * this.order_names_dic.get(contest_name);
    } else if (type.equals("b")) {
      return 2 * this.order_names_dic.get(contest_name) + 1;
    }
    throw new IllegalArgumentException();
  }

  /**
   * get the total alpha/pad of tallies of all contests
   * :return: a dictionary of alpha/pad of tallies of all contests
   */
  Map<String, Map<String, ElementModP>> get_total_pad() {
     return this.total_pad_dic;
  }

  /**
   * get the total beta/data of tallies of all contests
   * :return: a dictionary of beta/data of tallies of all contests
   */
  Map<String, Map<String, ElementModP>> get_total_data() {
     return this.total_data_dic;
  }
}
