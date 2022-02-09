package com.sunya.electionguard.input;

import com.google.common.flogger.FluentLogger;
import com.sunya.electionguard.Manifest;
import com.sunya.electionguard.PlaintextBallot;

import java.util.ArrayList;
import java.util.Formatter;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** Validate input plaintext ballots against the election manifest, give human readable error information. */
public class BallotInputValidation {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final Manifest election;
  private final Map<String, ElectionContest> contestMap;
  private final Map<String, Manifest.BallotStyle> styles;

  public BallotInputValidation(Manifest election) {
    this.election = election;
    this.contestMap = election.contests.stream().collect(Collectors.toMap(c -> c.object_id, ElectionContest::new));
    this.styles = election.ballot_styles.stream().collect(Collectors.toMap(bs -> bs.object_id(), bs -> bs));
  }

  /** Determine if a ballot is valid and well-formed for the given election. */
  public boolean validateBallot(PlaintextBallot ballot, Formatter problems) {
    BallotMessenger ballotMesses = new BallotMessenger(ballot.object_id());
    Manifest.BallotStyle ballotStyle = styles.get(ballot.style_id);
    // Referential integrity of ballot's BallotStyle id
    if (ballotStyle == null) {
      String msg = String.format("Ballot.A.1 Ballot Style '%s' does not exist in election", ballot.style_id);
      ballotMesses.add(msg);
      logger.atWarning().log(msg);
    }

    Set<String> contestIds = new HashSet<>();
    for (PlaintextBallot.Contest ballotContest : ballot.contests) {
      // No duplicate contests
      if (contestIds.contains(ballotContest.contest_id)) {
        String msg = String.format("Ballot.B.1 Multiple Ballot contests have same id '%s'", ballotContest.contest_id);
        ballotMesses.add(msg);
        logger.atWarning().log(msg);
      } else {
        contestIds.add(ballotContest.contest_id);
      }

      ElectionContest contest = contestMap.get(ballotContest.contest_id);
      // Referential integrity of ballotContest id
      if (contest == null) {
        String msg = String.format("Ballot.A.2 Ballot Contest '%s' does not exist in election", ballotContest.contest_id);
        ballotMesses.add(msg);
        logger.atWarning().log(msg);
      } else {
        validateContest(ballotContest, ballotStyle, contest, ballotMesses);
      }
    }
    return ballotMesses.makeMesses(problems);
  }

  /** Determine if contest is valid for ballot style. */
  void validateContest(PlaintextBallot.Contest ballotContest, Manifest.BallotStyle ballotStyle, ElectionContest electionContest, BallotMessenger ballotMesses) {
    BallotMessenger contestMesses = ballotMesses.nested(ballotContest.contest_id);

    int total = 0;
    Set<String> selectionIds = new HashSet<>();
    for (PlaintextBallot.Selection selection : ballotContest.ballot_selections) {
      // No duplicate selections
      if (selectionIds.contains(selection.selection_id)) {
        String msg = String.format("Ballot.B.2 Multiple Ballot selections have same id '%s'", selection.selection_id);
        contestMesses.add(msg);
        logger.atWarning().log(msg);
      } else {
        selectionIds.add(selection.selection_id);
      }

      Manifest.SelectionDescription electionSelection = electionContest.selectionMap.get(selection.selection_id);
      // Referential integrity of ballotSelection id
      if (electionSelection == null) {
        String msg = String.format("Ballot.A.3 Ballot Selection '%s' does not exist in contest", selection.selection_id);
        contestMesses.add(msg);
        logger.atWarning().log(msg);
      } else {
        // Vote can only be a 0 or 1
        if (selection.vote < 0 || selection.vote > 1) {
          String msg = String.format("Ballot.C.1 Ballot Selection '%s' vote (%d) must be 0 or 1", selection.selection_id, selection.vote);
          contestMesses.add(msg);
          logger.atWarning().log(msg);
        } else {
          total += selection.vote;
        }
      }
    }

    // Total votes for contest exceeds allowed limit
    if (total > electionContest.allowed) {
      String msg = String.format("Ballot.C.2 Ballot Selection votes (%d) exceeds limit (%d)", total, electionContest.allowed);
      contestMesses.add(msg);
      logger.atWarning().log(msg);
    }
  }

  private static class ElectionContest {
    private final String contestId;
    private final int allowed;
    private final Map<String, Manifest.SelectionDescription> selectionMap;

    ElectionContest(Manifest.ContestDescription electionContest) {
      this.contestId = electionContest.object_id;
      this.allowed = electionContest.votes_allowed.orElse(0); // LOOK or else what?
      // this.selectionMap = electionContest.ballot_selections.stream().collect(Collectors.toMap(b -> b.object_id, b -> b));
      // allow same object id
      this.selectionMap = new HashMap<>();
      for (Manifest.SelectionDescription sel : electionContest.ballot_selections) {
        this.selectionMap.put(sel.object_id, sel);
      }
    }
  }

  private static class BallotMessenger {
    private final String id;
    private final ArrayList<String> messages = new ArrayList<>();
    private final ArrayList<BallotMessenger> nested = new ArrayList<>();

    public BallotMessenger(String id) {
      this.id = id;
    }

    void add(String mess) {
      messages.add(mess);
    }

    BallotMessenger nested(String id) {
      BallotMessenger mess = new BallotMessenger(id);
      nested.add(mess);
      return mess;
    }

    boolean makeMesses(Formatter problems) {
      if (hasProblem()) {
        problems.format("Ballot '%s' has problems%n", id);
        for (String mess : messages) {
          problems.format("  %s%n", mess);
        }
        for (BallotMessenger nest : nested) {
          if (nest.hasProblem()) {
            problems.format("  Contest '%s' has problems%n", nest.id);
            for (String mess : nest.messages) {
              problems.format("    %s%n", mess);
            }
          }
        }
        problems.format("==============================%n");
        return false;
      }
      return true;
    }

    boolean hasProblem() {
      return !messages.isEmpty() || nested.stream().map(BallotMessenger::hasProblem).findAny().orElse(false);
    }
  }
}
