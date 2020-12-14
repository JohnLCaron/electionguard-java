package sunya.electionguard;

import com.google.common.collect.ImmutableList;
import com.google.gson.Gson;
import sunya.integration.ElectionBuilderPojo;

import javax.annotation.Nullable;
import java.io.*;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static sunya.electionguard.Election.*;
import static sunya.electionguard.Group.*;

public class ElectionFactory {
  private static final String simple_election_manifest_filename = "election_manifest_simple.json";
  private static final String hamilton_election_manifest_filename = "hamilton_election_manifest.json";

  public ElectionDescription get_simple_election_from_file() throws IOException {
    return this._get_election_from_file(simple_election_manifest_filename);
  }

  public ElectionDescription get_hamilton_election_from_file() throws IOException {
    return this._get_election_from_file(hamilton_election_manifest_filename);
  }

  /**
   * Get a single Fake Election object that is manually constructed with default values.
   */
  public ElectionDescription get_fake_election() {

    BallotStyle fake_ballot_style = new BallotStyle("some-ballot-style-id",
            Optional.of(ImmutableList.of("some-geopoltical-unit-id")),
            Optional.empty(), Optional.empty());

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
            Optional.of(votes_allowed),
            "some-referendum-contest-name",
            fake_referendum_ballot_selections,
            Optional.empty(), Optional.empty());

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
            Optional.of(votes_allowed_2),
            "some-candidate-contest-name",
            fake_candidate_ballot_selections,
            Optional.empty(), Optional.empty(), ImmutableList.of());

    // String election_scope_id, ElectionType type, Instant start_date, Instant end_date,
    // List<GeopoliticalUnit> geopolitical_units, List<Party> parties, List<Candidate> candidates,
    // List<ContestDescription> contests, List<BallotStyle> ballot_styles,
    // Optional<InternationalizedText> name, Optional<ContactInformation> contact_information
    ElectionDescription fake_election = new ElectionDescription(
            "some-scope-id",
            ElectionType.unknown, Instant.now(), Instant.now(),
            ImmutableList.of(new GeopoliticalUnit("some-geopoltical-unit-id", "some-gp-unit-name", ReportingUnitType.unknown, Optional.empty())),
            ImmutableList.of(new Party("some-party-id-1"), new Party("some-party-id-2")),
            ImmutableList.of(new Candidate("some-candidate-id-1"),
                    new Candidate("some-candidate-id-2"),
                    new Candidate("some-candidate-id-3")),
            ImmutableList.of(fake_referendum_contest, fake_candidate_contest),
            ImmutableList.of(fake_ballot_style),
            Optional.empty(), Optional.empty()
    );

    return fake_election;
  }

  public Optional<ElectionBuilder.Tuple> get_fake_ciphertext_election(
          ElectionDescription description,
          ElementModP elgamal_public_key) {

    ElectionBuilder builder = new ElectionBuilder(1, 1, description);
    builder.set_public_key(elgamal_public_key);
    return builder.build();
  }

  /** Get a single Fake Ballot object that is manually constructed with default vaules. */
  Ballot.PlaintextBallot get_fake_ballot(@Nullable ElectionDescription election, @Nullable String ballot_id) {
    if (election == null) {
      election = this.get_fake_election();
    }

    if (ballot_id == null) {
      ballot_id = "some-unique-ballot-id-123";
    }

    Ballot.PlaintextBallot fake_ballot = new Ballot.PlaintextBallot(
            ballot_id,
            election.ballot_styles.get(0).object_id,
            ImmutableList.of(Encrypt.contest_from(election.contests.get(0)),
                    Encrypt.contest_from(election.contests.get(1)))
          );

    return fake_ballot;
  }

  private ElectionDescription _get_election_from_file(String filename) throws IOException {
    String current = new java.io.File("./src/test/resources/").getCanonicalPath();
    System.out.printf("Current '%s'%n", current);

    InputStream is = new FileInputStream((current + "/" + filename));
    Reader reader = new InputStreamReader(is);
    Gson gson = new Gson();
    ElectionBuilderPojo builder = gson.fromJson(reader, ElectionBuilderPojo.class);
    return builder.build();
  }

}
