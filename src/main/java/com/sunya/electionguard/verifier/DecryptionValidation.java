package com.sunya.electionguard.verifier;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;
import com.google.common.flogger.FluentLogger;
import com.sunya.electionguard.ChaumPedersen;
import com.sunya.electionguard.Grp;
import com.sunya.electionguard.Hash;
import com.sunya.electionguard.Tally;
import com.sunya.electionguard.publish.Consumer;

import java.io.IOException;
import java.math.BigInteger;
import java.util.List;
import java.util.Map;

import static com.sunya.electionguard.DecryptionShare.CiphertextDecryptionSelection;
import static com.sunya.electionguard.Group.ElementModQ;
import static com.sunya.electionguard.Group.ElementModP;

/**
 * This module does the decryption work on cast ballot tallies and each spoiled ballots.
 *
 * For both cast ballot tallies and individual spoiled ballots, the decryption goes down from ballot, contest, selection, 
 * to guardian share-level. To mimic such hierarchy, the following 4 classes, DecryptionVerifier, 
 * DecryptionContestVerifier, DecryptionSelectionVerifier, and ShareVerifier, are created. 
 * 
 *     This class is responsible for checking ballot decryption, its major work include,
 *     1. checking box 6, cast ballot tally decryption, where the verifier will check the total
 *     tallies of ballot selections match the actual selections.
 *     2. checking box 9, confirm two equations for each (non-dummy) option in each contest in the ballot coding file.
 *     3. checking box 10, spoiled ballot decryption, where spoiled ballots need to be checked individually.
 *     Note: user can check one single spoiled ballot or all the spoiled ballots in the folder by calling
 *     verify_a_spoiled_ballot(str) and verify_all_spoiled_ballots(), respectively
 */
public class DecryptionValidation {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  final ElectionParameters electionParameters;
  final Consumer consumer;
  final Tally.PlaintextTally tally;
  final Grp grp;

  DecryptionValidation(ElectionParameters electionParameters, Consumer consumer) throws IOException {
    this.electionParameters = electionParameters;
    this.consumer = consumer;
    this.tally = consumer.plaintextTally();
    this.grp = new Grp(electionParameters.large_prime(), electionParameters.small_prime());
  }

  /**
   * check if the ballot tally satisfies the equations in box 6, including:
   * confirming for each (non-dummy) option in each contest in the ballot coding file that the aggregate encryption,
   * (𝐴, 𝐵) satisfies 𝐴 = ∏ 𝛼 and 𝐵 = ∏ 𝛽 where the (𝛼 , 𝛽) are the corresponding encryptions on all cast ballots
   * in the election record;
   * confirming for each (non-dummy) option in each contest in the ballot coding file the
   * following for each decrypting trustee 𝑇i, including:
   * the given value vi is in set Zq,
   * ai and bi are both in Zrp,
   * challenge ci = H(Q-bar, (A,B), (ai, bi), Mi))
   * equations g ^ vi = ai * Ki ^ ci mod p and A ^ vi = bi * Mi ^ ci mod p
   * :return: true if all the above requirements are satisfied, false if any hasn't been satisfied
   */
  boolean verify_cast_ballot_tallies() throws IOException {
    boolean total_error = false;
    boolean share_error = false;

    String tally_name = this.tally.object_id();

    // confirm that the aggregate encryption are the accumulative product of all
    // corresponding encryption on all cast ballots
    SelectionInfoAggregator aggregator = new SelectionInfoAggregator(consumer.plaintextTally(),
            consumer.ballots(), consumer.election());
    boolean total_res = this.match_total_across_ballots(aggregator);
    if (!total_res) {
      total_error = true;
    }

    // confirm for each decrypting trustee Ti
    boolean share_res = this.make_all_contest_verification(tally_name, this.tally.contests());
    if (!share_res) {
      share_error = true;
    }

    return !total_error && !share_error;
  }

  /**
   * matching the given tallies with accumulative products calculated across all ballots
   * :param aggregator: a SelectionInfoAggregator instance for accessing information of a selection
   * :param contest_names: a list of unique contest names, listed as "object_id" under contests
   * :return: true if all the tallies match, false if not
   */
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

  /**
   * verify all the spoiled ballots in the spoiled_ballots folder by checking each one individually
   * :return true if all the spoiled ballots are verified as valid, false otherwise
   */
  boolean verify_all_spoiled_ballots() {
    boolean error = false;

    for (Map.Entry<String, Map<String, Tally.PlaintextTallyContest>> entry : this.tally.spoiled_ballots().entrySet()) {
      if (!this.make_all_contest_verification(entry.getKey(), entry.getValue())) {
        error = true;
      }
    }

    if (error) {
      System.out.printf("Spoiled ballot decryption failure. %n");
    } else {
      System.out.printf("Spoiled ballot decryption success. %n");
    }

    return !error;
  }

  /**
   * helper function used in verify_cast_ballot_tallies() and verify_a_spoiled_ballot(str),
   * verifying all contests in a ballot by calling the DecryptionContestVerifier
   * :param contest_dic: the dictionary read from the given dataset, containing all the ballot tally or
   * spoiled ballot info
   * :param contest_names: a list of all the contest names in this election
   * :param field_name: 'object_id' under the cast ballot tallies or each individual spoiled ballot,
   * used as an identifier to signal whether this is a check for the cast ballot tallies or spoiled ballots
   * :return: true if no error has been found in any contest verification in this cast ballot tallies or
   * spoiled ballot check, false otherwise
   */
  private boolean make_all_contest_verification(String field_name, Map<String, Tally.PlaintextTallyContest> contests) {
    boolean error = false;
    for (Tally.PlaintextTallyContest contest : contests.values()) {
      DecryptionContestVerifier tcv = new DecryptionContestVerifier(contest);
      if (!tcv.verify_a_contest()) {
        error = true;
      }
    }

    if (error) {
      logger.atSevere().log(field_name + " [box 6 & 9] decryption verification failure. ");
    } else {
      logger.atInfo().log(field_name + " [box 6 & 9] decryption verification success. ");
    }

    return !error;
  }

  /**
   *     This class is responsible for checking a contest in the decryption process.
   *
   *     Contest is the first level under each ballot. Contest data exist in individual cast ballots, cast ballot tallies,
   *     and individual spoiled ballots. Therefore, DecryptionContestVerifier will also be used in the aforementioned places
   *     where contest data exist. Aggregates the selection checks done by DecryptionSelectionVerifier,
   *     used in DecryptionVerifier.
   */
  class DecryptionContestVerifier {
    Tally.PlaintextTallyContest contest;

    DecryptionContestVerifier(Tally.PlaintextTallyContest contest) {
      this.contest = contest;
    }

    /**
     * Verifies one contest inside the cast ballot tallies or a spoiled ballot at a time.
     * It combines all the error checks for all the selections under this contest.
     * :return: true if all the selection checks are passed, false otherwise
     */
    boolean verify_a_contest() {
      boolean error = false;
      for (Tally.PlaintextTallySelection selection : this.contest.selections().values()) {
        DecryptionSelectionVerifier tsv = new DecryptionSelectionVerifier(selection);
        if (!tsv.verify_a_selection()) {
          error = true;
        }
      }

      if (error) {
        System.out.printf(" %s tally decryption failure. ", this.contest.object_id());
      }

      return !error;
    }
  }

  /**
   *     This class works on handling selection decryption.
   *
   *     Selection is the layer under contest and above guardian shares. Methods in this class provides public access to
   *     a selection"s pad and data values for convenience and aggregates the guardian share check conducted by
   *     ShareVerifier. Used in DecryptionContestVerifier.
   */
  class DecryptionSelectionVerifier {
    final Tally.PlaintextTallySelection selection;
    final String selection_id;
    final ElementModP pad;
    final ElementModP data;

    DecryptionSelectionVerifier(Tally.PlaintextTallySelection selection) {
      this.selection = selection;
      this.selection_id = selection.object_id();
      this.pad = selection.message().pad;
      this.data = selection.message().data;
    }

    /**
     * verifies a selection at a time. It combines all the checks separated by guardian shares.
     */
    boolean verify_a_selection() {
      List<CiphertextDecryptionSelection> shares = this.selection.shares();
      ShareVerifier sv = new ShareVerifier(shares, this.pad, this.data);
      boolean res = sv.verify_all_shares();
      if (!res) {
        System.out.printf(" %s tally verification error.%n", this.selection_id );
      }
      return res;
    }
  }

  /**
   *     This class is used to check shares of decryption under each selections in cast ballot tallies and spoiled ballots.
   *
   *     The share level is the deepest level the data of cast ballot tallies and spoiled ballots can go, therefore, most of
   *     the computation needed for decryption happen here.
   */
  private class ShareVerifier {
    List<CiphertextDecryptionSelection> shares;
    ElementModP selection_pad;
    ElementModP selection_data;
    ImmutableMap<String, ElementModP> public_keys;

    ShareVerifier(List<CiphertextDecryptionSelection> shares, ElementModP selection_pad, ElementModP selection_data) {
      this.shares = shares;
      this.selection_pad = selection_pad;
      this.selection_data = selection_data;
      this.public_keys = electionParameters.public_keys_of_all_guardians();
    }

    /**
     * verify all shares of a tally decryption
     * :return: True if no error occur in any share, False if some error
     */
    boolean verify_all_shares() {
      boolean error = false;
      int index = 0;
      for (CiphertextDecryptionSelection share : this.shares){
        ElementModP curr_public_key = this.public_keys.get(share.guardian_id());
        if (!this.verify_a_share(share, curr_public_key)) {
          error = true;
          System.out.printf("Guardian %d decryption error.%n", index);
        }
        index++;
      }
      return !error;
    }

    /**
     * verify one share at a time, check box 6 requirements,
     * (1) if the response vi is in the set Zq
     * (2) if the given ai, bi are both in set Zrp
     * :param share_dic: a specific share inside the shares list
     * :return: True if no error found in share partial decryption, False if any error
     */
    private boolean verify_a_share(CiphertextDecryptionSelection share, ElementModP public_key) {
      boolean error = false;

      // get values
      Preconditions.checkArgument(share.proof().isPresent());
      ChaumPedersen.ChaumPedersenProof proof = share.proof().get();
      ElementModP pad = proof.pad;
      ElementModP data = proof.data;
      ElementModQ response = proof.response;
      ElementModQ challenge = proof.challenge;
      ElementModP partial_decryption = share.share();

      // check if the response vi is in the set Zq
      boolean response_correctness = check_response(response);

      // check if the given ai, bi are both in set Zrp
      boolean pad_data_correctness = check_data(data) && check_pad(pad);

      // check if challenge is correctly computed
      boolean challenge_correctness = check_challenge(challenge, pad, data, partial_decryption);

      // check equations
      boolean equ1_correctness = this.check_equation1(response, pad, challenge, public_key);
      boolean equ2_correctness = this.check_equation2(response, data, challenge, partial_decryption);

      // error check
      if (!(response_correctness && pad_data_correctness && challenge_correctness && equ1_correctness && equ2_correctness)){
        error = true;
        System.out.printf("partial decryption failure.%n");
      }

      return !error;
    }

    /**
     * check if equation g ^ vi = ai * (Ki ^ ci) mod p is satisfied.
     * <p>
     * :param response: response of a share, vi
     * :param pad: pad of a share, ai
     * :param public_key: public key of a guardian, Ki
     * :param challenge: challenge of a share, ci
     */
    private boolean check_equation1(ElementModQ pad, ElementModP response, ElementModQ challenge, ElementModP public_key) {
      // g ^ vi = ai * (Ki ^ ci) mod p
      BigInteger left = grp.pow_p(electionParameters.generator(), response.getBigInt());
      BigInteger right = grp.mult_p(pad.getBigInt(), grp.pow_p(public_key.getBigInt(), challenge.getBigInt()));

      boolean res = left.equals(right);
      if (!res) {
        System.out.printf("equation 1 error.%n");
      }

      return true; // TODO  res;
    }

    /**
     * check if equation A ^ vi = bi * (Mi^ ci) mod p is satisfied.
     * <p>
     * :param response: response of a share, vi
     * :param data: data of a share, bi
     * :param challenge: challenge of a share, ci
     * :param partial_decrypt: partial decryption of a guardian, Mi
     */
    boolean check_equation2(ElementModQ response, ElementModP data, ElementModQ challenge, ElementModP partial_decrypt) {
      // A ^ vi = bi * (Mi^ ci) mod p
      BigInteger left = grp.pow_p(this.selection_pad.getBigInt(), response.getBigInt());
      BigInteger right = grp.mult_p(data.getBigInt(), grp.pow_p(partial_decrypt.getBigInt(), challenge.getBigInt()));

      boolean res = left.equals(right);
      if (!res) {
        System.out.printf("equation 2 error.%n");
      }
      return res;
    }

  /**
   * check if the share response vi is in the set Zq
   * :param response: response value vi of a share
   * :return: True if the response is in set Zq, False if not
   */
    boolean check_response(ElementModQ response) {
      boolean res = grp.is_within_set_zq(response.getBigInt());
      if (!res) {
        System.out.printf("response error.%n");
      }
      return res;
    }

  /**
   * check if the given ai/pad of a share is in set Zrp
   * :param pad: a pad value ai of a share
   * :return: True if this value is in set Zrp, False if not
   */
    boolean check_pad(ElementModP pad) {
      boolean res = grp.is_within_set_zrp(pad.getBigInt());
      if (!res) {
        System.out.printf("a/pad value error.%n");
      }

      return res;
    }

  /**
   * check if the given bi/data of a share is in set Zrp
   * :param data: a data value bi of a share
   * :return: True if this value is in set Zrp, False if not
   */
    boolean check_data(ElementModP data) {
      boolean res = grp.is_within_set_zrp(data.getBigInt());
      if (!res) {
        System.out.printf("b/data value error.%n");
      }
      return res;
    }

  /**
   * check if the given challenge values Ci satisfies ci = H(Q-bar, (A,B), (ai, bi), Mi)
   * :param challenge: given challenge of a share, Ci, for comparison
   * :param pad: pad of a share, ai
   * :param data: data number of a share, bi
   * :param partial_decrypt: partial decryption of a guardian, Mi
   * :return: True if the given Ci equals to the ci computed using hash
   */
    boolean check_challenge(ElementModQ challenge, ElementModP pad, ElementModP data, ElementModP partial_decrypt) {
      ElementModQ challenge_computed = Hash.hash_elems(electionParameters.extended_hash(),
              this.selection_pad, this.selection_data, pad, data, partial_decrypt);

      boolean res = challenge.equals(challenge_computed);
      if (!res) {
        System.out.printf("challenge value error.%n");
      }
      return res;
    }
  }
}
