package com.sunya.electionguard.workflow;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.sunya.electionguard.InternalManifest;
import com.sunya.electionguard.Manifest;
import com.sunya.electionguard.PlaintextBallot;

import static com.sunya.electionguard.InternalManifest.ContestWithPlaceholders;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** Create nballots randomly generated fake Ballots. */
public class FakeBallotProvider implements BallotProvider {
  private static final Random random = new Random();
  private final int nballots;
  private final Manifest election;

  public FakeBallotProvider(Manifest election, Integer nballots) {
    this.election = election;
    this.nballots = nballots != null && nballots > 0 ? nballots : 11;
  }

  @Override
  public Iterable<PlaintextBallot> ballots() {
    BallotFactory ballotFactory = new BallotFactory();
    InternalManifest metadata = new InternalManifest(election);

    ImmutableList.Builder<PlaintextBallot> builder = ImmutableList.builder();
    for (int i = 0; i < nballots; i++) {
      String ballot_id = "ballot-id-" + random.nextInt();
      builder.add(ballotFactory.get_fake_ballot(metadata, ballot_id));
    }
    return builder.build();
  }


  private static class BallotFactory {
    PlaintextBallot get_fake_ballot(InternalManifest metadata, String ballot_id) {
      Preconditions.checkNotNull(ballot_id);
      String ballotStyleId = metadata.manifest.ballot_styles().get(0).object_id();
      List<PlaintextBallot.Contest> contests = new ArrayList<>();
      for (ContestWithPlaceholders contestp : metadata.get_contests_for_style(ballotStyleId)) {
        contests.add(this.get_random_contest_from(contestp.contest));
      }
      return new PlaintextBallot(ballot_id, ballotStyleId, contests);
    }

    PlaintextBallot.Contest get_random_contest_from(Manifest.ContestDescription contest) {
      int voted = 0;
      List<PlaintextBallot.Selection> selections = new ArrayList<>();
      for (Manifest.SelectionDescription selection_description : contest.ballot_selections()) {
        PlaintextBallot.Selection selection = get_random_selection_from(selection_description);
        voted += selection.vote;
        if (voted <= contest.number_elected()) {
          selections.add(selection);
        }
      }
      return new PlaintextBallot.Contest(
              contest.object_id(),
              contest.sequence_order(),
              selections);
    }

    static PlaintextBallot.Selection get_random_selection_from(Manifest.SelectionDescription description) {
      boolean choice = random.nextBoolean();
      return new PlaintextBallot.Selection(description.object_id(), description.sequence_order(),
              choice ? 1 : 0, false, null);
    }

  }
}
