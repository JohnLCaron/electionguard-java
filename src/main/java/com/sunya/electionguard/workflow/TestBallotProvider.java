package com.sunya.electionguard.workflow;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.sunya.electionguard.Ballot;
import com.sunya.electionguard.Election;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class TestBallotProvider implements BallotProvider {
  private static final Random random = new Random(System.currentTimeMillis());
  private static final int nballots = 11;
  private final Election.ElectionDescription election;

  public TestBallotProvider(Election.ElectionDescription election) {
    this.election = election;
  }

  // Create nballots randomly generated fake Ballots
  @Override
  public Iterable<Ballot.PlaintextBallot> ballots() {
    // LOOK get rid of InternalElectionDescription
    Election.InternalElectionDescription metadata = new Election.InternalElectionDescription(election);
    BallotFactory ballotFactory = new BallotFactory();

    ImmutableList.Builder<Ballot.PlaintextBallot> builder = ImmutableList.builder();
    for (int i = 0; i < nballots; i++) {
      String ballot_id = "ballot-id-" + random.nextInt();
      builder.add(ballotFactory.get_fake_ballot(metadata, ballot_id));
    }
    return builder.build();
  }


  private static class BallotFactory {
    Ballot.PlaintextBallot get_fake_ballot(
            Election.InternalElectionDescription election,
            String ballot_id) {

      Preconditions.checkNotNull(ballot_id);
      String ballotStyleId = election.ballot_styles.get(0).object_id;
      List<Ballot.PlaintextBallotContest> contests = new ArrayList<>();
      for (Election.ContestDescriptionWithPlaceholders contest : election.get_contests_for(ballotStyleId)) {
        contests.add(this.get_random_contest_from(contest));
      }

      return new Ballot.PlaintextBallot(ballot_id, ballotStyleId, contests);
    }

    Ballot.PlaintextBallotContest get_random_contest_from(Election.ContestDescription contest) {
      int voted = 0;
      List<Ballot.PlaintextBallotSelection> selections = new ArrayList<>();
      for (Election.SelectionDescription selection_description : contest.ballot_selections) {
        Ballot.PlaintextBallotSelection selection = get_random_selection_from(selection_description);
        voted += selection.to_int();
        if (voted <= contest.number_elected) {
          selections.add(selection);
        }
      }
      return new Ballot.PlaintextBallotContest(contest.object_id, selections);
    }

    static Ballot.PlaintextBallotSelection get_random_selection_from(Election.SelectionDescription description) {
      boolean choice = random.nextBoolean();
      return new Ballot.PlaintextBallotSelection(description.object_id, choice ? "true" : "false", false, null);
    }

  }
}
