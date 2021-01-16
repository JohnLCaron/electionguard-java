package com.sunya.electionguard.verifier;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;
import com.google.common.flogger.FluentLogger;
import com.sunya.electionguard.ChaumPedersen;
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
 * This verifies specification section "8. Correctness of Partial Decryptions" and section 12
 * "Correct Decryption of Spoiled Ballots"
 */
public class PartialDecryptionsVerifier {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  final ElectionParameters electionParameters;
  final Consumer consumer;
  final Tally.PlaintextTally tally;
  final Grp grp;

  PartialDecryptionsVerifier(ElectionParameters electionParameters, Consumer consumer) throws IOException {
    this.electionParameters = electionParameters;
    this.consumer = consumer;
    this.tally = consumer.plaintextTally();
    this.grp = new Grp(electionParameters.large_prime(), electionParameters.small_prime());
  }

  /**
   * 8. Confirm for each (non-dummy) option in each contest in the ballot coding file, for each decrypting trustee 𝑇i:
   * <ol>
   * <li>The given value vi is in set Zq,</li>
   * <li>The given values ai and bi are both in Zrp,</li>
   * <li>the challenge ci = H(Q-bar, (A,B), (ai, bi), Mi))</li>
   * <li>The equations g ^ vi = ai * Ki ^ ci mod p and A ^ vi = bi * Mi ^ ci mod p are satisfied</li>
   * </ol>
   * </li>
   * </ol>
   */
  boolean verify_cast_ballot_tallies() {
    boolean error = false;

    String tally_name = this.tally.object_id();

    // 8. confirm for each decrypting trustee Ti
    if (!this.make_all_contest_verification(tally_name, this.tally.contests())) {
      System.out.printf(" ***Partial decryptions of cast ballots failure. %n");
    } else {
      System.out.printf(" Partial decryptions of cast ballots success. %n");
    }
    return !error;
  }

  /**
   * 12. An election verifier should confirm the correct decryption of each spoiled ballot using the same
   * process that was used to confirm the election tallies.
   */
  boolean verify_spoiled_ballots() {
    boolean error = false;

    for (Map.Entry<String, Map<String, Tally.PlaintextTallyContest>> entry : this.tally.spoiled_ballots().entrySet()) {
      if (!this.make_all_contest_verification(entry.getKey(), entry.getValue())) {
        error = true;
      }
    }

    if (error) {
      System.out.printf(" ***Spoiled ballot decryption failure. %n");
    } else {
      System.out.printf(" Spoiled ballot decryption success. %n");
    }
    return !error;
  }

  private boolean make_all_contest_verification(String name, Map<String, Tally.PlaintextTallyContest> contests) {
    boolean error = false;
    for (Tally.PlaintextTallyContest contest : contests.values()) {
      DecryptionContestVerifier tcv = new DecryptionContestVerifier(contest);
      if (!tcv.verify_a_contest()) {
        System.out.printf(" Contest %s decryption failure for %s. %n", contest.object_id(), name);
        error = true;
      }
    }
    return !error;
  }

  class DecryptionContestVerifier {
    Tally.PlaintextTallyContest contest;

    DecryptionContestVerifier(Tally.PlaintextTallyContest contest) {
      this.contest = contest;
    }

    boolean verify_a_contest() {
      boolean error = false;
      for (Tally.PlaintextTallySelection selection : this.contest.selections().values()) {
        DecryptionSelectionVerifier tsv = new DecryptionSelectionVerifier(selection);
        if (!tsv.verify_a_selection()) {
          System.out.printf("  Selection %s decryption failure.%n", selection.object_id());
          error = true;
        }
      }
      return !error;
    }
  }

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

    /** Verify a selection at a time. Combine all the checks separated by guardian shares. */
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

  // section 8
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

    /** Verify all shares of a tally decryption */
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

      // 8.A check if the response vi is in the set Zq
      boolean response_correctness = grp.is_within_set_zq(response.getBigInt());

      // 8.B check if the given ai, bi are both in set Zrp
      boolean pad_correct = grp.is_within_set_zrp(pad.getBigInt());
      boolean data_correct = grp.is_within_set_zrp(data.getBigInt());

      // 8.C Check if the given challenge ci = H(Q-bar, (A,B), (ai, bi), Mi)
      ElementModQ challenge_computed = Hash.hash_elems(electionParameters.extended_hash(),
              this.selection_pad, this.selection_data, pad, data, partial_decryption);
      boolean challenge_correctness = challenge_computed.equals(challenge);

      // 8.D g^vi mod p = ai * Ki^ci mod p
      boolean equ1_correctness = this.check_equation1(response, pad, challenge, public_key);

      // 8.E A^vi mod p = bi * Mi ^ ci mod p
      boolean equ2_correctness = this.check_equation2(response, data, challenge, partial_decryption);

      // error check
      if (!(response_correctness && pad_correct &&  data_correct && challenge_correctness &&
              equ1_correctness && equ2_correctness)){
        error = true;
        System.out.printf("partial decryption failure.%n");
      }
      return !error;
    }

    /**
     * 8.D Check if equation g ^ vi mod p = ai * (Ki ^ ci) mod p is satisfied.
     * <p>
     * @param response: response of a share, vi
     * @param pad: pad of a share, ai
     * @param public_key: public key of a guardian, Ki
     * @param challenge: challenge of a share, ci
     */
    private boolean check_equation1(ElementModQ response, ElementModP pad, ElementModQ challenge, ElementModP public_key) {
      // g ^ vi = ai * (Ki ^ ci) mod p
      BigInteger left = grp.pow_p(electionParameters.generator(), response.getBigInt());
      BigInteger right = grp.mult_p(pad.getBigInt(), grp.pow_p(public_key.getBigInt(), challenge.getBigInt()));

      boolean res = left.equals(right);
      if (!res) {
        System.out.printf("equation 1 error.%n");
      }
      return res;
    }

    /**
     * 8.E Check if equation A ^ vi = bi * (Mi^ ci) mod p is satisfied.
     * <p>
     * @param response: response of a share, vi
     * @param data: data of a share, bi
     * @param challenge: challenge of a share, ci
     * @param partial_decrypt: partial decryption of a guardian, Mi
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
  }
}
