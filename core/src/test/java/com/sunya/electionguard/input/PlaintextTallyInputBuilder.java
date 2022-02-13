package com.sunya.electionguard.input;

import com.sunya.electionguard.ChaumPedersen;
import com.sunya.electionguard.DecryptionShare;
import com.sunya.electionguard.ElGamal;
import com.sunya.electionguard.Group;
import com.sunya.electionguard.PlaintextTally;

import javax.annotation.Nullable;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static com.sunya.electionguard.TestUtils.chaum_pedersen_proof;
import static com.sunya.electionguard.TestUtils.elements_mod_p;

public class PlaintextTallyInputBuilder {
  private String id;
  private ArrayList<ContestBuilder> contests = new ArrayList<>();

  PlaintextTallyInputBuilder(String tally_id) {
    this.id = tally_id;
  }

  ContestBuilder addContest(String contest_id) {
    ContestBuilder c = new ContestBuilder(contest_id);
    contests.add(c);
    return c;
  }

  PlaintextTally build() {
    return new PlaintextTally(id, contests.stream().collect(Collectors.toMap(c -> c.id, c -> c.build())));
  }

  PlaintextTally buildBadContest() {
    return new PlaintextTally(id, contests.stream().collect(Collectors.toMap(c -> c.id + "bad", c -> c.build())));
  }

  PlaintextTally buildBadSelection() {
    return new PlaintextTally(id, contests.stream().collect(Collectors.toMap(c -> c.id, c -> c.buildBad())));
  }

  public class ContestBuilder {
    private String id;
    // LOOK no hash private Group.ElementModQ hash;
    private ArrayList<SelectionBuilder> selections = new ArrayList<>();

    ContestBuilder(String id) {
      this.id = id;
    }

    SelectionBuilder addSelection(String id, ElGamal.Ciphertext ciphertext) {
      SelectionBuilder s = new SelectionBuilder(id, ciphertext);
      selections.add(s);
      return s;
    }

    PlaintextTallyInputBuilder done() {
      return PlaintextTallyInputBuilder.this;
    }

    PlaintextTally.Contest build() {
      return new PlaintextTally.Contest(id, selections.stream().collect(Collectors.toMap(s -> s.id, s -> s.build())));
    }

    PlaintextTally.Contest buildBad() {
      return new PlaintextTally.Contest(id, selections.stream().collect(Collectors.toMap(s -> s.id + "bad", s -> s.build())));
    }

    public class SelectionBuilder {
      private String id;
      // LOOK no hash private Group.ElementModQ hash;
      private ElGamal.Ciphertext message;
      List<DecryptionShare.CiphertextDecryptionSelection> shares = new ArrayList<>();

      SelectionBuilder(String id, ElGamal.Ciphertext message) {
        this.id = id;
        this.message = message;
      }

      // String object_id,
      //            String guardian_id,
      //            ElementModP share,
      //            Optional<ChaumPedersen.ChaumPedersenProof> proof,
      //            Optional<Map<String, CiphertextCompensatedDecryptionSelection>> recovered_parts
      SelectionBuilder addShare(
              String id,
              String guardian_id,
              @Nullable Map<String, DecryptionShare.CiphertextCompensatedDecryptionSelection> recovered_parts) {
        ChaumPedersen.ChaumPedersenProof proof = (recovered_parts == null) ? chaum_pedersen_proof() : null;

        DecryptionShare.CiphertextDecryptionSelection share = new DecryptionShare.CiphertextDecryptionSelection(
                id, guardian_id, elements_mod_p(), Optional.ofNullable(proof), Optional.ofNullable(recovered_parts));
        shares.add(share);
        return this;
      }

      SelectionBuilder addShare(
              String id,
              String guardian_id,
              @Nullable ChaumPedersen.ChaumPedersenProof proof,
              @Nullable Map<String, DecryptionShare.CiphertextCompensatedDecryptionSelection> recovered_parts) {
        DecryptionShare.CiphertextDecryptionSelection share = new DecryptionShare.CiphertextDecryptionSelection(
                id, guardian_id, elements_mod_p(), Optional.ofNullable(proof), Optional.ofNullable(recovered_parts));
        shares.add(share);
        return this;
      }

      ContestBuilder done() {
        return ContestBuilder.this;
      }

      PlaintextTally.Selection build() {
        Group.ElementModP tally = Group.int_to_p_unchecked(BigInteger.valueOf(1));
        return new PlaintextTally.Selection(id, 1, Group.g_pow_p(tally), message, shares);
      }
    }
  }

}
