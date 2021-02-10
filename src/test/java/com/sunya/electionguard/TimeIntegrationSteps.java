package com.sunya.electionguard;

import com.google.common.base.Stopwatch;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.sunya.electionguard.publish.Consumer;
import com.sunya.electionguard.publish.ConvertFromJson;
import com.sunya.electionguard.publish.Publisher;
import com.sunya.electionguard.verifier.ElectionRecord;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;
import static com.google.common.truth.Truth8.assertThat;
import static com.sunya.electionguard.Ballot.*;
import static com.sunya.electionguard.Election.*;
import static com.sunya.electionguard.Group.rand_q;
import static org.junit.Assert.fail;

/** Time steps for an End-to-End encrypted election, publish to both Json and proto. */
public class TimeIntegrationSteps {
  private static final int NUMBER_OF_GUARDIANS = 6;
  private static final int QUORUM = 5;
  private static final Random random = new Random(System.currentTimeMillis());

  public static void main(String[] args) throws IOException {
    int nballots = Integer.parseInt(args[0]);

    TimeIntegrationSteps timer = new TimeIntegrationSteps(nballots);
    timer.run();
  }

  String outputDir;
  int nballots;

  // Step 0 - Configure Election
  ElectionDescription description;
  ElectionBuilder election_builder;
  InternalElectionDescription election;
  CiphertextElectionContext context;
  ElectionConstants constants;

  // Step 1 - Key Ceremony;
  KeyCeremonyMediator mediator;
  List<GuardianBuilder> guardianBuilders = new ArrayList<>();
  List<Guardian> guardians = new ArrayList<>();
  List<KeyCeremony.CoefficientValidationSet> coefficient_validation_sets = new ArrayList<>();

  // Step 2 - Encrypt Votes
  Encrypt.EncryptionDevice device;
  Encrypt.EncryptionMediator encrypter;
  List<PlaintextBallot> originalPlaintextBallots = new ArrayList<>();
  List<CiphertextBallot> ciphertext_ballots = new ArrayList<>();

  // Step 3 - Cast and Spoil
  DataStore ballot_store;
  BallotBox ballot_box;

  // Step 4 - Decrypt Tally
  DecryptionMediator decryptionMediator;
  CiphertextTallyBuilder ciphertext_tally;
  PlaintextTally decryptedTally;
  List<Ballot.PlaintextBallot> spoiledDecryptedBallots;
  List<PlaintextTally> spoiledDecryptedTallies;

  public TimeIntegrationSteps(int nballots) throws IOException {
    Path tmp = Files.createTempDirectory("publish");
    tmp.toFile().deleteOnExit();
    outputDir = tmp.toAbsolutePath().toString();
    outputDir = "/home/snake/tmp/electionguard/publishTimeIntegrationSteps";
    System.out.printf("=========== outputDir = %s nballots = %d%n", outputDir, nballots);
    this.nballots = nballots;
  }

  public void run() throws IOException {
    try {
      Stopwatch stopwatch = Stopwatch.createStarted();
      this.step_0_configure_election();
      System.out.printf("*** step0 elapsed = %s%n", stopwatch);
      stopwatch.reset().start();

      this.step_1_key_ceremony();
      System.out.printf("*** step_1_key_ceremony elapsed = %s%n", stopwatch);
      stopwatch.reset().start();

      this.step_2_encrypt_votes(nballots);
      float timePerBallot = ((float) stopwatch.elapsed(TimeUnit.MILLISECONDS)) / nballots;
      System.out.printf("*** step_2_encrypt_votes elapsed = %s nballots = %d timePerBallot = %f ms%n", stopwatch, nballots, timePerBallot);
      stopwatch.reset().start();

      this.step_3_cast_and_spoil();
      timePerBallot = ((float) stopwatch.elapsed(TimeUnit.MILLISECONDS)) / nballots;
      System.out.printf("*** step_3_cast_and_spoil elapsed = %s nballots = %d timePerBallot = %f ms%n", stopwatch, nballots, timePerBallot);
      stopwatch.reset().start();

      this.step_4_decrypt_tally();
      timePerBallot = ((float) stopwatch.elapsed(TimeUnit.MILLISECONDS)) / nballots;
      System.out.printf("*** step_4_decrypt_tally elapsed = %s nballots = %d timePerBallot = %f ms%n", stopwatch, nballots, timePerBallot);
      stopwatch.reset().start();

      Publisher publisherJson = this.step_5_publish_and_verify();
      timePerBallot = ((float) stopwatch.elapsed(TimeUnit.MILLISECONDS)) / nballots;
      System.out.printf("*** step_5_publish_and_verify elapsed = %s nballots = %d timePerBallot = %f ms%n", stopwatch, nballots, timePerBallot);

      Publisher publisherProto = this.step_6_publish_election_record_proto();
      timePerBallot = ((float) stopwatch.elapsed(TimeUnit.MILLISECONDS)) / nballots;
      System.out.printf("*** step_6_publish_election_record_proto elapsed = %s nballots = %d timePerBallot = %f ms%n", stopwatch, nballots, timePerBallot);

      this.step_7_read_json_ballots(publisherJson);
      timePerBallot = ((float) stopwatch.elapsed(TimeUnit.MILLISECONDS)) / nballots;
      System.out.printf("*** step_7_read_json_ballots elapsed = %s nballots = %d timePerBallot = %f ms%n", stopwatch, nballots, timePerBallot);

      this.step_8_read_proto_ballots(publisherProto);
      timePerBallot = ((float) stopwatch.elapsed(TimeUnit.MILLISECONDS)) / nballots;
      System.out.printf("*** step_8_read_proto_ballots elapsed = %s nballots = %d timePerBallot = %f ms%n", stopwatch, nballots, timePerBallot);
    } finally {
      Scheduler.shutdown();
    }
  }

  void step_0_configure_election() throws IOException {
    System.out.printf("%n0. Verify that the input election meta-data is well-formed%n");
    // TODO: replace with complex election
    this.description = ElectionFactory.get_simple_election_from_file();

    System.out.printf("----------------------------------%n");
    System.out.printf("Election Summary:%nScope: %s%n", this.description.election_scope_id);
    System.out.printf("Geopolitical Units: %d%n", this.description.geopolitical_units.size());
    System.out.printf("Parties: %d%n", this.description.parties.size());
    System.out.printf("Candidates: %d%n", this.description.candidates.size());
    System.out.printf("Contests: %d%n", this.description.contests.size());
    System.out.printf("Ballot Styles: %d%n", this.description.ballot_styles.size());
    System.out.printf("----------------------------------%n");

    assertThat(this.description.is_valid()).isTrue();

    // Create an Election Builder
    this.election_builder = new ElectionBuilder(NUMBER_OF_GUARDIANS, QUORUM, this.description);
    System.out.printf("Created with number_of_guardians: %d quorum: %d%n", NUMBER_OF_GUARDIANS, QUORUM);
  }

  /**
   * Using the NUMBER_OF_GUARDIANS, generate public-private keypairs and share
   * representations of those keys with QUORUM of other Guardians.  Then, combine
   * the public election keys to make a joint election key that is used to encrypt ballots
   */
  void step_1_key_ceremony() {
    System.out.printf("%n1. Key Ceremony%n");
    Group.ElementModQ crypto_hash = rand_q();
    // Setup Guardians
    for (int i = 0; i < NUMBER_OF_GUARDIANS; i++) {
      int sequence = i + 1;
      this.guardianBuilders.add(GuardianBuilder.createForTesting("guardian_" + sequence, sequence, NUMBER_OF_GUARDIANS, QUORUM, crypto_hash, null));
    }

    // Setup Mediator
    this.mediator = new KeyCeremonyMediator(this.guardianBuilders.get(0).ceremony_details());

    // Attendance (Public Key Share)
    for (GuardianBuilder guardianBuilder : this.guardianBuilders) {
      this.mediator.announce(guardianBuilder);
    }

    System.out.printf("Confirms all guardians have shared their public keys%n");
    assertThat(this.mediator.all_guardians_in_attendance()).isTrue();

    // Run the Key Ceremony process,
    // Which shares the keys among the guardians
    Optional<List<GuardianBuilder>> orchestrated = this.mediator.orchestrate(null);
    System.out.printf("Executes the key exchange between guardians%n");
    assertThat(orchestrated).isPresent();

    System.out.printf("Confirms all guardians have shared their partial key backups%n");
    assertThat(this.mediator.all_election_partial_key_backups_available()).isTrue();

    // Verification
    boolean verified = this.mediator.verify(null);
    System.out.printf("Confirms all guardians truthfully executed the ceremony%n");
    assertThat(verified).isTrue();

    System.out.printf("Confirms all guardians have submitted a verification of the backups of all other guardians%n");
    assertThat(this.mediator.all_election_partial_key_verifications_received()).isTrue();

    System.out.printf("Confirms all guardians have verified the backups of all other guardians%n");
    assertThat(this.mediator.all_election_partial_key_backups_verified()).isTrue();

    // Joint Key
    Optional<Group.ElementModP> joint_key = this.mediator.publish_joint_key();
    System.out.printf("Publishes the Joint Election Key%n");
    assertThat(joint_key).isPresent();

    // Save Validation Keys
    for (GuardianBuilder guardianBuilder : this.guardianBuilders) {
      Guardian guardian = guardianBuilder.build();
      this.guardians.add(guardian);
      this.coefficient_validation_sets.add(guardian.share_coefficient_validation_set());
    }

    // Build the Election
    this.election_builder.set_public_key(joint_key.get());
    ElectionBuilder.DescriptionAndContext tuple = this.election_builder.build().orElseThrow();
    this.election = tuple.metadata;
    this.context = tuple.context;
    this.constants = new ElectionConstants();
  }

  // Move on to encrypting ballots
  // Using the `CiphertextElectionContext` encrypt ballots for the election
  void step_2_encrypt_votes(int nballots) {
    // Configure the Encryption Device
    this.device = new Encrypt.EncryptionDevice("polling-place-one");
    this.encrypter = new Encrypt.EncryptionMediator(this.election, this.context, this.device);
    System.out.printf("%n2. Ready to encrypt at location: %s%n", this.device.location);

    // Encrypt nballots randomly generated fake Ballots
    BallotFactory ballotFactory = new BallotFactory();
    for (int i = 0; i < nballots; i++) {
      String ballot_id = "ballot-id-" + random.nextInt();
      PlaintextBallot plaintext_ballot = ballotFactory.get_fake_ballot(this.election, ballot_id, true);
      Optional<CiphertextBallot> encrypted_ballot = this.encrypter.encrypt(plaintext_ballot);
      assertThat(encrypted_ballot).isPresent();
      this.ciphertext_ballots.add(encrypted_ballot.get());
      this.originalPlaintextBallots.add(plaintext_ballot);
    }
  }

  // Accept each ballot by marking it as either cast or spoiled.
  void step_3_cast_and_spoil() {
    int ncast = 0;
    int nspoil = 0;
    // Configure the Ballot Box
    this.ballot_store = new DataStore();
    this.ballot_box = new BallotBox(this.election, this.context, this.ballot_store);
    System.out.printf("%n3. cast_and_spoil%n");
    // Randomly cast or spoil the ballots
    for (CiphertextBallot ballot : this.ciphertext_ballots) {
      Optional<CiphertextAcceptedBallot> accepted_ballot;
      if (random.nextBoolean()) {
        accepted_ballot = this.ballot_box.cast(ballot);
        ncast++;
      } else {
        accepted_ballot = this.ballot_box.spoil(ballot);
        nspoil++;
      }
      assertThat(accepted_ballot).isPresent();
    }
    System.out.printf(" cast %d spoil %d%n", ncast, nspoil);
  }

  /**
   * Homomorphically combine the selections made on all of the cast ballots
   * and use the Available Guardians to decrypt the combined tally.
   * In this way, no individual voter's cast ballot is ever decrypted directly.
   */
  void step_4_decrypt_tally() {
    System.out.printf("%n4. Homomorphically Accumulate and decrypt tally%n");
    // Generate a Homomorphically Accumulated Tally of the ballots
    this.ciphertext_tally = new CiphertextTallyBuilder("whatever", this.election, this.context);
    this.ciphertext_tally.batch_append(this.ballot_box.accepted());

    // Configure the Decryption
    this.decryptionMediator = new DecryptionMediator(this.context, this.ciphertext_tally, this.ballot_box.getSpoiledBallots());

    // Announce each guardian as present
    for (Guardian guardian : this.guardians) {
      System.out.printf("Guardian Present: %s%n", guardian.object_id);
      assertThat(this.decryptionMediator.announce(guardian)).isTrue();
    }

    // Here's where the ciphertext Tally is decrypted.
    this.decryptedTally = this.decryptionMediator.decrypt_tally(false, null).orElseThrow();
    List<DecryptWithShares.SpoiledTallyAndBallot> spoiledTallyAndBallot =
            this.decryptionMediator.decrypt_spoiled_ballots().orElseThrow();
    this.spoiledDecryptedBallots = spoiledTallyAndBallot.stream().map(e -> e.ballot).collect(Collectors.toList());
    this.spoiledDecryptedTallies = spoiledTallyAndBallot.stream().map(e -> e.tally).collect(Collectors.toList());
    System.out.printf("Tally Decrypted%n");

    // Now, compare the results
    this.compare_results();
    this.compare_spoiled_tallies();
  }

  // Compare the results to ensure the decryption was done correctly
  void compare_results() {
    System.out.printf("%n4.5 Compare results%n");

    Map<String, Integer> expected_plaintext_tally = new HashMap<>(); // Map of selection_id to votes counted
    for (ContestDescriptionWithPlaceholders contest : this.election.contests) {
      for (SelectionDescription selection : contest.ballot_selections) {
        expected_plaintext_tally.put(selection.object_id, 0);
      }
    }

    // Tally the expected values from the plaintext ballots that were cast.
    for (PlaintextBallot ballot : this.originalPlaintextBallots) {
      if (this.ballot_store.get(ballot.object_id).orElseThrow().state == BallotBoxState.CAST) {
        for (PlaintextBallotContest contest : ballot.contests) {
          for (PlaintextBallotSelection selection : contest.ballot_selections) {
            Integer value = expected_plaintext_tally.get(selection.selection_id);
            expected_plaintext_tally.put(selection.selection_id, value + selection.vote); // could use merge
          }
        }
      }
    }

    // Compare the expected tally to the decrypted tally
    for (PlaintextTally.PlaintextTallyContest tally_contest : this.decryptedTally.contests.values()) {
      for (PlaintextTally.PlaintextTallySelection tally_selection : tally_contest.selections().values()) {
        Integer expected = expected_plaintext_tally.get(tally_selection.object_id());
        assertThat(expected).isEqualTo(tally_selection.tally());
      }
    }
  }

  // Compare the decrypted spoiled tally to original ballot
  void compare_spoiled_tallies() {
    // the set of spoiled ballot ids.
    Set<String> spoiled = new HashSet<>();
    for (Ballot.CiphertextAcceptedBallot accepted_ballot : ballot_box.getSpoiledBallots()) {
      if (accepted_ballot.state == BallotBoxState.SPOILED) {
        spoiled.add(accepted_ballot.object_id);
      }
    }
    // the original ballots
    List<PlaintextBallot> ballots = new ArrayList<>();
    for (PlaintextBallot orgBallot : this.originalPlaintextBallots) {
      if (spoiled.contains(orgBallot.object_id)) {
        ballots.add(orgBallot);
      }
    }
    // compare to the tallies.
    compare_spoiled_tallies(ballots, this.spoiledDecryptedTallies);
  }

  // Publish and verify steps of the election
  Publisher step_5_publish_and_verify() throws IOException {
    System.out.printf("%n5. publish results as JSON%n");
    Publisher publisher = new Publisher(outputDir, true, true);
    publisher.writeElectionRecordJson(
            this.description,
            this.context,
            this.constants,
            ImmutableList.of(this.device),
            this.ballot_box.getAllBallots(),
            this.ciphertext_tally.build(),
            this.decryptedTally,
            this.coefficient_validation_sets,
            this.spoiledDecryptedBallots,
            this.spoiledDecryptedTallies);

    System.out.printf("%n5.5. verify%n");
    this.verify_results_json(publisher);
    return publisher;
  }

  // Verify results of election
  void verify_results_json(Publisher publisher) throws IOException {
    Consumer consumer = new Consumer(publisher);
    ElectionRecord roundtrip = consumer.readElectionRecordJson();

    assertThat(roundtrip.election).isEqualTo(this.description);
    assertThat(roundtrip.context).isEqualTo(this.context);
    assertThat(roundtrip.constants).isEqualTo(this.constants);
    assertThat(roundtrip.devices.size()).isEqualTo(1);
    assertThat(roundtrip.devices.get(0)).isEqualTo(this.device);
    assertThat(roundtrip.ciphertextTally).isEqualTo(this.ciphertext_tally.build());
    assertThat(roundtrip.decryptedTally).isEqualTo(this.decryptedTally);

    Map<String, KeyCeremony.CoefficientValidationSet> coeffMap = this.coefficient_validation_sets.stream()
            .collect(Collectors.toMap(b->b.owner_id(), b -> b));
    for (KeyCeremony.CoefficientValidationSet coeff : roundtrip.guardianCoefficients) {
      KeyCeremony.CoefficientValidationSet expected = coeffMap.get(coeff.owner_id());
      assertThat(expected).isNotNull();
      assertWithMessage(coeff.owner_id()).that(coeff).isEqualTo(expected);
    }

    for (CiphertextAcceptedBallot ballot : roundtrip.acceptedBallots) {
      CiphertextAcceptedBallot expected = this.ballot_box.get(ballot.object_id).orElseThrow();
      assertWithMessage(ballot.object_id).that(ballot).isEqualTo(expected);
    }

    Map<String, PlaintextBallot> originalMap = this.originalPlaintextBallots.stream()
            .collect(Collectors.toMap(b->b.object_id, b -> b));
    for (PlaintextBallot ballot : roundtrip.spoiledBallots) {
      PlaintextBallot expected = originalMap.get(ballot.object_id);
      assertThat(expected).isNotNull();
      TimeIntegrationSteps.compare_spoiled_ballot(ballot, expected);
    }
  }

  // Publish election record as proto
  Publisher step_6_publish_election_record_proto() throws IOException {
    System.out.printf("%n6. publish election record as proto%n");
    Path tmp = Files.createTempDirectory("publish");
    tmp.toFile().deleteOnExit();
    String protoDir = tmp.toAbsolutePath().toString();
    protoDir = "/home/snake/tmp/electionguard/publishTimeIntegrationStepsProto";
    Publisher publisher = new Publisher(protoDir, true, false);
    publisher.writeElectionRecordProto(
            this.description,
            this.context,
            this.constants,
            this.coefficient_validation_sets,
            ImmutableList.of(this.device),
            this.ballot_box.accepted(),
            this.ciphertext_tally.build(),
            this.decryptedTally,
            this.spoiledDecryptedBallots,
            this.spoiledDecryptedTallies);

    System.out.printf("%n6.5. verify%n");
    this.verify_results_proto(publisher);
    return publisher;
  }

  // Verify results of election
  void verify_results_proto(Publisher publisher) throws IOException {
    Consumer consumer = new Consumer(publisher);
    ElectionRecord roundtrip = consumer.readElectionRecordProto();

    assertThat(roundtrip.election).isEqualTo(this.description);
    assertThat(roundtrip.context).isEqualTo(this.context);
    assertThat(roundtrip.constants).isEqualTo(this.constants);
    assertThat(roundtrip.devices.size()).isEqualTo(1);
    assertThat(roundtrip.devices.get(0)).isEqualTo(this.device);
    assertThat(roundtrip.ciphertextTally).isEqualTo(this.ciphertext_tally.build());
    assertThat(roundtrip.decryptedTally).isEqualTo(this.decryptedTally);

    Map<String, KeyCeremony.CoefficientValidationSet> coeffMap = this.coefficient_validation_sets.stream()
            .collect(Collectors.toMap(b->b.owner_id(), b -> b));
    for (KeyCeremony.CoefficientValidationSet coeff : roundtrip.guardianCoefficients) {
      KeyCeremony.CoefficientValidationSet expected = coeffMap.get(coeff.owner_id());
      assertThat(expected).isNotNull();
      assertWithMessage(coeff.owner_id()).that(coeff).isEqualTo(expected);
    }

    for (CiphertextAcceptedBallot ballot : roundtrip.acceptedBallots) {
      CiphertextAcceptedBallot expected = this.ballot_box.get(ballot.object_id).orElseThrow();
      assertWithMessage(ballot.object_id).that(ballot).isEqualTo(expected);
    }

    Map<String, PlaintextBallot> originalMap = this.originalPlaintextBallots.stream()
            .collect(Collectors.toMap(b->b.object_id, b -> b));
    for (PlaintextBallot ballot : roundtrip.spoiledBallots) {
      PlaintextBallot expected = originalMap.get(ballot.object_id);
      assertThat(expected).isNotNull();
      TimeIntegrationSteps.compare_spoiled_ballot(ballot, expected);
    }
  }

  void step_7_read_json_ballots(Publisher publisher) throws IOException {
    for (CiphertextAcceptedBallot ballot : this.ballot_box.getAllBallots()) {
      CiphertextAcceptedBallot ballot_from_file = ConvertFromJson.readCiphertextBallot(
              publisher.ballotPath(ballot.object_id).toString());
      assertWithMessage(publisher.ballotPath(ballot.object_id).toString())
              .that(ballot_from_file).isEqualTo(ballot);
    }
  }

  void step_8_read_proto_ballots(Publisher publisher) throws IOException {
    Consumer consumer = new Consumer(publisher);
    ElectionRecord roundtrip = consumer.readElectionRecordProto();

    int count = 0;
    for (CiphertextAcceptedBallot ballot : roundtrip.acceptedBallots) {
      Optional<CiphertextAcceptedBallot> have = this.ballot_box.get(ballot.object_id);
      assertThat(have).isPresent();
      assertWithMessage(ballot.object_id).that(have.get()).isEqualTo(ballot);
      count++;
    }
    assertThat(count).isEqualTo(Iterables.size(this.ballot_box.getAllBallots()));
  }

  ////////////////////////////////////////////////////////////////////////////////////////////////////////////////

  // Compare the decrypted spoiled tally to original ballot
  public static void compare_spoiled_tallies(List<PlaintextBallot> plaintextBallots, List<PlaintextTally> spoiledDecryptedTallies) {
    Map<String, PlaintextTally> plaintextTalliesMap = spoiledDecryptedTallies.stream().collect(Collectors.toMap(t -> t.object_id, t -> t));
    for (PlaintextBallot orgBallot : plaintextBallots) {
      System.out.printf("%nSpoiled Ballot: %s%n", orgBallot.object_id);
      PlaintextTally plaintextTally = plaintextTalliesMap.get(orgBallot.object_id);
      assertThat(plaintextTally).isNotNull();
      compare_spoiled_tally(orgBallot, plaintextTally);
    }
  }

  public static void compare_spoiled_tally(PlaintextBallot orgBallot, PlaintextTally plaintextTally) {
    System.out.printf("%nSpoiled Ballot: %s%n", orgBallot.object_id);
    for (PlaintextBallotContest contest : orgBallot.contests) {
      System.out.printf("%nContest: %s%n", contest.contest_id);
      PlaintextTally.PlaintextTallyContest contestTally = plaintextTally.contests.get(contest.contest_id);
      for (PlaintextBallotSelection selection : contest.ballot_selections) {
        int expected = selection.vote;
        PlaintextTally.PlaintextTallySelection selectionTally = contestTally.selections().get(selection.selection_id);
        System.out.printf("   - Selection: %s expected: %d, actual: %d%n",
                selection.selection_id, expected, selectionTally.tally());
        assertThat(selectionTally.tally()).isEqualTo(expected);
      }
    }
  }

  // roundtrip was encrypted, then decrypted. Now it has all selections, when original may only have the ones voted for.
  // If there are no votes for a contest, it may be missing in the original.
  // The yes votes must match LOOK should equal?
  public static void compare_spoiled_ballot(PlaintextBallot roundtrip, PlaintextBallot expected) {
    for (PlaintextBallotContest roundtripContest : roundtrip.contests) {
      Optional<PlaintextBallotContest> contest = expected.contests.stream()
              .filter(c -> c.contest_id.equals(roundtripContest.contest_id)).findFirst();

      if (contest.isEmpty()) { // all votes must be zero.
        int total = roundtripContest.ballot_selections.stream().mapToInt(s->s.vote).sum();
        assertThat(total).isEqualTo(0);
        continue;
      }
      for (PlaintextBallotSelection roundtripSelection : roundtripContest.ballot_selections) {
        Optional<PlaintextBallotSelection> selection = contest.get().ballot_selections.stream()
                .filter(s -> s.selection_id.equals(roundtripSelection.selection_id)).findFirst();

        if (roundtripSelection.vote == 1) {
          assertThat(selection).isPresent();
          assertThat(selection.get().vote).isEqualTo(1);
        } else if (roundtripSelection.vote == 0) {
          selection.ifPresent(s -> assertThat(s.vote).isEqualTo(0));
        } else {
          fail();
        }
      }
    }

  }


}
