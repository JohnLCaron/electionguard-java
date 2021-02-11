package com.sunya.electionguard;

import com.google.common.collect.ImmutableList;
import com.sunya.electionguard.publish.ElectionDescriptionFromJson;

import javax.annotation.Nullable;
import java.io.*;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Random;

import static com.sunya.electionguard.Election.*;
import static com.sunya.electionguard.Group.*;
import static com.sunya.electionguard.ElectionWithPlaceholders.ContestWithPlaceholders;

public class ElectionFactory {
  private static final String simple_election_manifest_filename = "election_manifest_simple.json";
  private static final String hamilton_election_manifest_filename = "hamilton_election_manifest.json";

  public static ElectionDescription get_simple_election_from_file() throws IOException {
    return _get_election_from_file(simple_election_manifest_filename);
  }

  public static ElectionDescription get_hamilton_election_from_file() throws IOException {
    return _get_election_from_file(hamilton_election_manifest_filename);
  }

  /** Get a single Fake Election object that is manually constructed with default values. */
  public static ElectionDescription get_fake_election() {

    BallotStyle fake_ballot_style = new BallotStyle("some-ballot-style-id",
            ImmutableList.of("some-geopoltical-unit-id"), null, null);

    // Referendum selections are simply a special case of `candidate`in the object model
    List<SelectionDescription> fake_referendum_ballot_selections = ImmutableList.of(
            new SelectionDescription("some-object-id-affirmative", "some-candidate-id-1", 0),
            new SelectionDescription("some-object-id-negative", "some-candidate-id-2", 1));

    int sequence_order = 0;
    int number_elected = 1;
    int votes_allowed = 1;
    ReferendumContestDescription fake_referendum_contest = new ReferendumContestDescription(
            "some-referendum-contest-object-id",
            "some-geopoltical-unit-id",
            sequence_order,
            VoteVariationType.one_of_m,
            number_elected,
            votes_allowed,
            "some-referendum-contest-name",
            fake_referendum_ballot_selections, null, null);

    List<SelectionDescription> fake_candidate_ballot_selections = ImmutableList.of(
            new SelectionDescription("some-object-id-candidate-1", "some-candidate-id-1", 0),
            new SelectionDescription("some-object-id-candidate-2", "some-candidate-id-2", 1),
            new SelectionDescription("some-object-id-candidate-3", "some-candidate-id-3", 2)
    );

    int sequence_order_2 = 1;
    int number_elected_2 = 2;
    int votes_allowed_2 = 2;
    CandidateContestDescription fake_candidate_contest = new CandidateContestDescription(
            "some-candidate-contest-object-id",
            "some-geopoltical-unit-id",
            sequence_order_2,
            VoteVariationType.one_of_m,
            number_elected_2,
            votes_allowed_2,
            "some-candidate-contest-name",
            fake_candidate_ballot_selections,
            null, null, null);

    return new ElectionDescription(
            "some-scope-id",
            ElectionType.unknown, OffsetDateTime.now(), OffsetDateTime.now(),
            ImmutableList.of(new GeopoliticalUnit("some-geopoltical-unit-id", "some-gp-unit-name", ReportingUnitType.unknown, null)),
            ImmutableList.of(new Party("some-party-id-1"), new Party("some-party-id-2")),
            ImmutableList.of(new Candidate("some-candidate-id-1"),
                    new Candidate("some-candidate-id-2"),
                    new Candidate("some-candidate-id-3")),
            ImmutableList.of(fake_referendum_contest, fake_candidate_contest),
            ImmutableList.of(fake_ballot_style),
            null, null);
  }

  public static Optional<ElectionBuilder.DescriptionAndContext> get_fake_ciphertext_election(
          ElectionDescription description,
          ElementModP elgamal_public_key) {

    ElectionBuilder builder = new ElectionBuilder(1, 1, description);
    builder.set_public_key(elgamal_public_key);
    return builder.build();
  }

  /** Get a single Fake Ballot object that is manually constructed with default values. */
  static Ballot.PlaintextBallot get_fake_ballot(@Nullable ElectionDescription election, @Nullable String ballot_id) {
    if (election == null) {
      election = get_fake_election();
    }

    if (ballot_id == null) {
      ballot_id = "some-unique-ballot-id-123";
    }

    return new Ballot.PlaintextBallot(
            ballot_id,
            election.ballot_styles.get(0).object_id,
            ImmutableList.of(Encrypt.contest_from(election.contests.get(0)),
                    Encrypt.contest_from(election.contests.get(1)))
    );
  }

  private static ElectionDescription _get_election_from_file(String filename) throws IOException {
    String current = new java.io.File("./src/test/resources/").getCanonicalPath();
    String absFilename = current + "/" + filename;
    ElectionDescriptionFromJson builder = new ElectionDescriptionFromJson(absFilename);
    return builder.build();
  }

  ///////////////////////////////////////////////////////////////////////////////////////
  // should all be in TestUtils ?
  private static final Random random = new Random(System.currentTimeMillis());

  static class SelectionTuple {
    String id;
    SelectionDescription selection_description;

    public SelectionTuple(String id, SelectionDescription selection_description) {
      this.id = id;
      this.selection_description = selection_description;
    }
  }

  static SelectionTuple get_selection_description_well_formed() {
    String candidate_id = String.format("candidate_id-%d", TestUtils.randomInt(20));
    String object_id = String.format("object_id-%d", TestUtils.randomInt());
    int sequence_order = random.nextInt(20);
    return new SelectionTuple(object_id, new SelectionDescription(object_id, candidate_id, sequence_order));
  }

  static ContestWithPlaceholders get_contest_description_well_formed() {
    int sequence_order = TestUtils.randomInt(20);
    String electoral_district_id = "{draw(emails)}-gp-unit";

    int first_int = TestUtils.randomInt(20);
    int second_int = TestUtils.randomInt(20);

    // TODO ISSUE #33: support more votes than seats for other VoteVariationType options
    int number_elected = Math.min(first_int, second_int);
    int votes_allowed = number_elected;

    List<SelectionDescription> selection_descriptions = new ArrayList<>();
    for (int i = 0; i < Math.max(first_int, second_int); i++) {
      String object_id = String.format("object_id-%d", TestUtils.randomInt());
      String candidate_id = String.format("candidate_id-%d", TestUtils.randomInt());
      SelectionDescription selection_description = new SelectionDescription(object_id, candidate_id, i);
      selection_descriptions.add(selection_description);
    }

    ContestDescription contest_description = new ContestDescription(
            String.format("object_id-%d", TestUtils.randomInt()),
            electoral_district_id,
            sequence_order,
            VoteVariationType.n_of_m,
            number_elected,
            votes_allowed,
            "draw(text)",
            selection_descriptions,
            null, null);

    List<SelectionDescription> placeholder_selections = ElectionWithPlaceholders.generate_placeholder_selections_from(contest_description, number_elected);
    return ElectionWithPlaceholders.contest_description_with_placeholders_from(contest_description, placeholder_selections);
  }

}
