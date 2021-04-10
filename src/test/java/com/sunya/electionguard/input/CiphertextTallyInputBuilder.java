package com.sunya.electionguard.input;

import com.sunya.electionguard.CiphertextTally;
import com.sunya.electionguard.ElGamal;
import com.sunya.electionguard.Group;

import java.util.ArrayList;
import java.util.stream.Collectors;

public class CiphertextTallyInputBuilder {
  private String id;
  private ArrayList<ContestBuilder> contests = new ArrayList<>();

  CiphertextTallyInputBuilder(String tally_id) {
    this.id = tally_id;
  }

  ContestBuilder addContest(String contest_id, Group.ElementModQ hash) {
    ContestBuilder c = new ContestBuilder(contest_id, hash);
    contests.add(c);
    return c;
  }

  CiphertextTally build() {
    return new CiphertextTally(id, contests.stream().collect(Collectors.toMap(c -> c.id, c -> c.build())));
  }

  CiphertextTally buildBadContest() {
    return new CiphertextTally(id, contests.stream().collect(Collectors.toMap(c -> c.id + "bad", c -> c.build())));
  }

  CiphertextTally buildBadSelection() {
    return new CiphertextTally(id, contests.stream().collect(Collectors.toMap(c -> c.id, c -> c.buildBad())));
  }

  public class ContestBuilder {
    private String id;
    private Group.ElementModQ hash;
    private ArrayList<SelectionBuilder> selections = new ArrayList<>();

    ContestBuilder(String id, Group.ElementModQ hash) {
      this.id = id;
      this.hash = hash;
    }

    ContestBuilder addSelection(String id, Group.ElementModQ hash, ElGamal.Ciphertext ciphertext) {
      SelectionBuilder s = new SelectionBuilder(id, hash, ciphertext);
      selections.add(s);
      return this;
    }

    CiphertextTallyInputBuilder done() {
      return CiphertextTallyInputBuilder.this;
    }

    CiphertextTally.Contest build() {
      return new CiphertextTally.Contest(id, hash, selections.stream().collect(Collectors.toMap(s -> s.id, s -> s.build())));
    }

    CiphertextTally.Contest buildBad() {
      return new CiphertextTally.Contest(id, hash, selections.stream().collect(Collectors.toMap(s -> s.id + "bad", s -> s.build())));
    }

    public class SelectionBuilder {
      private String id;
      private Group.ElementModQ hash;
      private ElGamal.Ciphertext ciphertext;

      SelectionBuilder(String id, Group.ElementModQ hash, ElGamal.Ciphertext ciphertext) {
        this.id = id;
        this.hash = hash;
        this.ciphertext = ciphertext;
      }

      CiphertextTally.Selection build() {
        return new CiphertextTally.Selection(id, hash, ciphertext);
      }
    }
  }

}
