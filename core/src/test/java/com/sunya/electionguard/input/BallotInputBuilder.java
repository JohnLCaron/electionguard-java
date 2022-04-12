package com.sunya.electionguard.input;

import com.sunya.electionguard.PlaintextBallot;

import java.util.ArrayList;
import java.util.stream.Collectors;

public class BallotInputBuilder {
  private static final String styleDef = "styling";

  private String id;
  private ArrayList<ContestBuilder> contests = new ArrayList<>();
  private String style = styleDef;

  BallotInputBuilder(String ballot_id) {
    this.id = ballot_id;
  }

  BallotInputBuilder setStyle(String style) {
    this.style = style;
    return this;
  }

  ContestBuilder addContest(String contest_id) {
    ContestBuilder c = new ContestBuilder(contest_id);
    contests.add(c);
    return c;
  }

  PlaintextBallot build() {
    return new PlaintextBallot(id, style, contests.stream().map(ContestBuilder::build).toList(), null);
  }

  public class ContestBuilder {
    private String id;
    private int seq = 1;
    private ArrayList<SelectionBuilder> selections = new ArrayList<>();

    ContestBuilder(String id) {
      this.id = id;
    }

    ContestBuilder addSelection(String id, int vote) {
      SelectionBuilder s = new SelectionBuilder(id, vote);
      selections.add(s);
      return this;
    }

    BallotInputBuilder done() {
      return BallotInputBuilder.this;
    }

    PlaintextBallot.Contest build() {
      return new PlaintextBallot.Contest(id, seq++, selections.stream().map(SelectionBuilder::build).toList());
    }

    public class SelectionBuilder {
      private String id;
      private int vote;

      SelectionBuilder(String id, int vote) {
        this.id = id;
        this.vote = vote;
      }

      PlaintextBallot.Selection build() {
        return new PlaintextBallot.Selection(id, seq++, vote, false, null);
      }
    }
  }

}
