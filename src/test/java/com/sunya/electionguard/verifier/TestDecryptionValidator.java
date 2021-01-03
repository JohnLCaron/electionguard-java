package com.sunya.electionguard.verifier;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;
import com.sunya.electionguard.*;
import com.sunya.electionguard.publish.Consumer;
import net.jqwik.api.Example;
import net.jqwik.api.lifecycle.BeforeContainer;

import java.io.IOException;
import java.math.BigInteger;
import java.util.List;
import java.util.Map;

import static com.google.common.truth.Truth.assertThat;

public class TestDecryptionValidator {
  static ElectionParameters electionParameters;
  static Consumer consumer;
  static DecryptionValidator validator;
  static Grp grp;

  @BeforeContainer
  public static void setUp() throws IOException {
    String topdir = "src/test/data/testEndToEnd";

    // set up
    consumer = new Consumer(topdir);
    electionParameters = new ElectionParameters(consumer);
    System.out.println("set up finished. ");

    System.out.println(" ------------ [box 3, 4, 5] ballot encryption check ------------");
    validator = new DecryptionValidator(electionParameters, consumer);
    grp = new Grp(electionParameters.large_prime(), electionParameters.small_prime());
  }

  @Example
  public void testSelectionEncryptionValidation() throws IOException {
    boolean sevOk = validator.verify_cast_ballot_tallies();
    assertThat(sevOk).isTrue();
  }

  @Example
  public void verify_cast_ballot_tallies() throws IOException {
    Tally.PlaintextTally tally = consumer.plaintextTally();

    SelectionInfoAggregator aggregator = new SelectionInfoAggregator(consumer.plaintextTally(),
            consumer.ballots(), consumer.election());
    boolean total_res = match_total_across_ballots(tally, aggregator);
    assertThat(total_res).isTrue();

    // confirm for each decrypting trustee Ti
    make_all_contest_verification(tally);
  }

  private boolean match_total_across_ballots(Tally.PlaintextTally tally, SelectionInfoAggregator aggregator) {
    boolean error = false;

    List<Map<String, Group.ElementModP>> dics_by_contest = aggregator.get_dics();
    Map<String, Map<String, Group.ElementModP>> total_data_dic = aggregator.get_total_data();
    Map<String, Map<String, Group.ElementModP>> total_pad_dic = aggregator.get_total_pad();

    for (String contest_name : tally.contests().keySet()) {
      //get the corresponding index of pad and data dictionaries given contest name
      int pad_dic_idx = aggregator.get_dic_id_by_contest_name(contest_name, "a");
      int data_dic_idx = aggregator.get_dic_id_by_contest_name(contest_name, "b");
      Map<String, Group.ElementModP> pad_dic = dics_by_contest.get(pad_dic_idx);
      Map<String, Group.ElementModP> data_dic = dics_by_contest.get(data_dic_idx);

      for (String selection_name : pad_dic.keySet()) {
        Group.ElementModP accum_pad = pad_dic.get(selection_name);
        Group.ElementModP tally_pad = total_pad_dic.get(contest_name).get(selection_name);
        Group.ElementModP accum_data = data_dic.get(selection_name);
        Group.ElementModP tally_data = total_data_dic.get(contest_name).get(selection_name);
        if (!accum_pad.equals(tally_pad)) {
          error = true;
        }
        if (!accum_data.equals(tally_data)) {
          error = true;
        }
      }
    }

    return (!error);
  }

  private void make_all_contest_verification(Tally.PlaintextTally tally) {
    for (Tally.PlaintextTallyContest contest : tally.contests().values()) {
      for (Tally.PlaintextTallySelection selection : contest.selections().values()) {
        System.out.printf(" make_all_contest_verification contest = '%s' selection = '%s'%n", contest.object_id(), selection.object_id());
        Group.ElementModP selection_pad = selection.message().pad;
        Group.ElementModP selection_data = selection.message().data;
        List<DecryptionShare.CiphertextDecryptionSelection> shares = selection.shares();
        verify_all_shares(shares, selection_pad, selection_data);
      }
    }
  }

  void verify_all_shares(List<DecryptionShare.CiphertextDecryptionSelection> shares,
                         Group.ElementModP selection_pad, Group.ElementModP selection_data) {
    ImmutableMap<String, Group.ElementModP> public_keys = electionParameters.public_keys_of_all_guardians();
    for (DecryptionShare.CiphertextDecryptionSelection share : shares) {
      System.out.printf(" verify_all_shares guardian = '%s'%n", share.guardian_id());
      Group.ElementModP curr_public_key = public_keys.get(share.guardian_id()); // any chance we arent matching the right share?
      verify_a_share(share, curr_public_key, selection_pad, selection_data);
    }
  }

  private void verify_a_share(DecryptionShare.CiphertextDecryptionSelection share, Group.ElementModP public_key,
                                 Group.ElementModP selection_pad, Group.ElementModP selection_data) {

    // get values
    Preconditions.checkArgument(share.proof().isPresent());
    ChaumPedersen.ChaumPedersenProof proof = share.proof().get();
    Group.ElementModP pad = proof.pad;
    Group.ElementModP data = proof.data;
    Group.ElementModQ response = proof.response;
    Group.ElementModQ challenge = proof.challenge;
    Group.ElementModP partial_decryption = share.share();

    // check if the response vi is in the set Zq
    check_response(response);

    // check if the given ai, bi are both in set Zrp
    check_data(data);
    check_pad(pad);

    // check if challenge is correctly computed
    check_challenge(challenge, pad, data, partial_decryption, selection_pad, selection_data);

    // check equations
    check_equation1(response, pad, challenge, public_key);
    check_equation2(response, data, challenge, partial_decryption, selection_pad);
  }

  /**
   * check if equation g ^ vi = ai * (Ki ^ ci) mod p is satisfied.
   * <p>
   * :param response: response of a share, vi
   * :param pad: pad of a share, ai
   * :param public_key: public key of a guardian, Ki
   * :param challenge: challenge of a share, ci
   */
  private void check_equation1(Group.ElementModQ response, Group.ElementModP pad, Group.ElementModQ challenge, Group.ElementModP public_key) {
    // g ^ vi = ai * (Ki ^ ci) mod p
    BigInteger left = grp.pow_p(electionParameters.generator(), response.getBigInt());
    BigInteger right = grp.mult_p(pad.getBigInt(), grp.pow_p(public_key.getBigInt(), challenge.getBigInt()));
    assertThat(left).isEqualTo(right);
  }

  /**
   * check if equation A ^ vi = bi * (Mi^ ci) mod p is satisfied.
   * <p>
   * :param response: response of a share, vi
   * :param data: data of a share, bi
   * :param challenge: challenge of a share, ci
   * :param partial_decrypt: partial decryption of a guardian, Mi
   * :return True if the equation is satisfied, False if not
   */
  void check_equation2(Group.ElementModQ response, Group.ElementModP data, Group.ElementModQ challenge,
                          Group.ElementModP partial_decrypt, Group.ElementModP selection_pad) {
    // A ^ vi = bi * (Mi^ ci) mod p
    BigInteger left = grp.pow_p(selection_pad.getBigInt(), response.getBigInt());
    BigInteger right = grp.mult_p(data.getBigInt(), grp.pow_p(partial_decrypt.getBigInt(), challenge.getBigInt()));

    assertThat(left).isEqualTo(right);
  }

  /**
   * check if the share response vi is in the set Zq
   * :param response: response value vi of a share
   * :return: True if the response is in set Zq, False if not
   */
  void check_response(Group.ElementModQ response) {
    boolean res = grp.is_within_set_zq(response.getBigInt());
    assertThat(res).isTrue();
  }

  /**
   * check if the given ai/pad of a share is in set Zrp
   * :param pad: a pad value ai of a share
   * :return: True if this value is in set Zrp, False if not
   */
  void check_pad(Group.ElementModP pad) {
    boolean res = grp.is_within_set_zrp(pad.getBigInt());
    assertThat(res).isTrue();
  }

  /**
   * check if the given bi/data of a share is in set Zrp
   * :param data: a data value bi of a share
   * :return: True if this value is in set Zrp, False if not
   */
  void check_data(Group.ElementModP data) {
    boolean res = grp.is_within_set_zrp(data.getBigInt());
    assertThat(res).isTrue();
  }

  /**
   * check if the given challenge values Ci satisfies ci = H(Q-bar, (A,B), (ai, bi), Mi)
   * :param challenge: given challenge of a share, Ci, for comparison
   * :param pad: pad of a share, ai
   * :param data: data number of a share, bi
   * :param partial_decrypt: partial decryption of a guardian, Mi
   * :return: True if the given Ci equals to the ci computed using hash
   */
  void check_challenge(Group.ElementModQ challenge, Group.ElementModP pad, Group.ElementModP data,
                          Group.ElementModP partial_decrypt, Group.ElementModP selection_pad, Group.ElementModP selection_data) {
    Group.ElementModQ challenge_computed = Hash.hash_elems(electionParameters.extended_hash(),
            selection_pad, selection_data, pad, data, partial_decrypt);

    assertThat(challenge).isEqualTo(challenge_computed);
  }
}
