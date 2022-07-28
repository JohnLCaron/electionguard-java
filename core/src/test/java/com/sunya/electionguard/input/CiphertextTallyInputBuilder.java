package com.sunya.electionguard.input;

import com.sunya.electionguard.ballot.EncryptedTally;
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

  EncryptedTally build() {
    return new EncryptedTally(id, contests.stream().collect(Collectors.toMap(c -> c.id, c -> c.build())));
  }

  EncryptedTally buildBadContest() {
    return new EncryptedTally(id, contests.stream().collect(Collectors.toMap(c -> c.id + "bad", c -> c.build())));
  }

  EncryptedTally buildBadSelection() {
    return new EncryptedTally(id, contests.stream().collect(Collectors.toMap(c -> c.id, c -> c.buildBad())));
  }

  public class ContestBuilder {
    private String id;
    private int seq = 1;
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

    EncryptedTally.Contest build() {
      return new EncryptedTally.Contest(id, seq++, hash, selections.stream().collect(Collectors.toMap(s -> s.id, s -> s.build())));
    }

    EncryptedTally.Contest buildBad() {
      return new EncryptedTally.Contest(id, seq++, hash, selections.stream().collect(Collectors.toMap(s -> s.id + "bad", s -> s.build())));
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

      EncryptedTally.Selection build() {
        return new EncryptedTally.Selection(id, seq++, hash, ciphertext);
      }
    }
  }

}
