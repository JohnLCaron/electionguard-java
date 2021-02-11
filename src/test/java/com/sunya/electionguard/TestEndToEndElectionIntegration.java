package com.sunya.electionguard;

import com.google.common.base.Stopwatch;
import com.google.common.collect.ImmutableList;
import com.sunya.electionguard.publish.Consumer;
import com.sunya.electionguard.publish.Publisher;
import com.sunya.electionguard.verifier.ElectionRecord;
import net.jqwik.api.Example;
import net.jqwik.api.lifecycle.BeforeProperty;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

import static com.google.common.truth.Truth.assertWithMessage;
import static com.sunya.electionguard.Ballot.*;
import static com.sunya.electionguard.Election.*;
import static com.sunya.electionguard.ElectionWithPlaceholders.ContestWithPlaceholders;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth8.assertThat;

/**
 * Test a complete simple example of executing an End-to-End encrypted election.
 * Puplishing to JSON.
 */
public class TestEndToEndElectionIntegration {
  private static final int NUMBER_OF_GUARDIANS = 7;
  private static final int QUORUM = 7;
  private static final Random random = new Random(System.currentTimeMillis());

  String outputDir;

  @BeforeProperty
  public void setUp() throws IOException {
    Path tmp = Files.createTempDirectory(null);
    tmp.toFile().deleteOnExit();
    outputDir = "/home/snake/tmp/electionguard/publishEndToEnd";
    // outputDir = tmp.toAbsolutePath().toString();
    System.out.printf("=========== outputDir = %s%n", outputDir);
  }

  // Step 0 - Configure Election
  ElectionDescription election;
  ElectionBuilder election_builder;
  CiphertextElectionContext context;
  ElectionConstants constants;
  ElectionWithPlaceholders metadata;

  // Step 1 - Key Ceremony;
  KeyCeremonyMediator mediator;
  List<GuardianBuilder> guardianBuilders = new ArrayList<>();
  List<Guardian> guardians = new ArrayList<>();
  List<KeyCeremony.CoefficientValidationSet> coefficient_validation_sets = new ArrayList<>();

  // Step 2 - Encrypt Votes
  Encrypt.EncryptionDevice device;
  Encrypt.EncryptionMediator encrypter;
  List<PlaintextBallot> originalPlaintextBallots;
  List<CiphertextBallot> ciphertext_ballots = new ArrayList<>();

  // Step 3 - Cast and Spoil
  DataStore ballot_store;
  BallotBox ballot_box;

  // Step 4 - Decrypt Tally
  DecryptionMediator decrypter;
  PublishedCiphertextTally publishedTally;
  PlaintextTally decryptedTally;
  List<Ballot.PlaintextBallot> spoiledDecryptedBallots;
  List<PlaintextTally> spoiledDecryptedTallies;

  // Execute the simplified end-to-end test demonstrating each component of the system.
  @Example
  public void test_end_to_end_election() throws IOException {
    Stopwatch stopwatch = Stopwatch.createStarted();
    this.step_0_configure_election();
    System.out.printf("*** step0 elapsed = %s%n", stopwatch);
    stopwatch.reset().start();

    this.step_1_key_ceremony();
    System.out.printf("*** step1 elapsed = %s%n", stopwatch);
    stopwatch.reset().start();

    this.step_2_encrypt_votes();
    System.out.printf("*** step2 elapsed = %s%n", stopwatch);
    stopwatch.reset().start();

    this.step_3_cast_and_spoil();
    System.out.printf("*** step3 elapsed = %s%n", stopwatch);
    stopwatch.reset().start();

    this.step_4_decrypt_tally();
    System.out.printf("*** step4 elapsed = %s%n", stopwatch);
    stopwatch.reset().start();

    this.step_5_publish_and_verify();
    System.out.printf("*** step5 elapsed = %s%n", stopwatch);
  }

  void step_0_configure_election() throws IOException {
    System.out.printf("%n0. Verify that the input election meta-data is well-formed%n");
    // TODO: replace with complex election
    this.election = ElectionFactory.get_simple_election_from_file();

    System.out.printf("----------------------------------%n");
    System.out.printf("Election Summary:%nScope: %s%n", this.election.election_scope_id);
    System.out.printf("Geopolitical Units: %d%n", this.election.geopolitical_units.size());
    System.out.printf("Parties: %d%n", this.election.parties.size());
    System.out.printf("Candidates: %d%n", this.election.candidates.size());
    System.out.printf("Contests: %d%n", this.election.contests.size());
    System.out.printf("Ballot Styles: %d%n", this.election.ballot_styles.size());
    System.out.printf("----------------------------------%n");

    assertThat(this.election.is_valid()).isTrue();

    // Create an Election Builder
    this.election_builder = new ElectionBuilder(NUMBER_OF_GUARDIANS, QUORUM, this.election);
    System.out.printf("Created with number_of_guardians: %d quorum: %d%n", NUMBER_OF_GUARDIANS, QUORUM);
  }

  /**
   * Using the NUMBER_OF_GUARDIANS, generate public-private keypairs and share
   * representations of those keys with QUORUM of other Guardians.  Then, combine
   * the public election keys to make a joint election key that is used to encrypt ballots
   */
  void step_1_key_ceremony() {
    System.out.printf("%n1. Key Ceremony%n");
    // Setup Guardians
    for (int i = 1; i <= NUMBER_OF_GUARDIANS; i++) {
      this.guardianBuilders.add(GuardianBuilder.createForTesting("guardian_" + i, i, NUMBER_OF_GUARDIANS, QUORUM, null));
    }

    // Setup Mediator
    this.mediator = new KeyCeremonyMediator(this.guardianBuilders.get(0).ceremony_details());

    // Attendance (Public Key Share)
    for (GuardianBuilder guardian : this.guardianBuilders) {
      this.mediator.announce(guardian);
    }

    System.out.printf("Confirms all guardians have shared their public keys%n");
    assertThat(this.mediator.all_guardians_in_attendance()).isTrue();

    // Run the Key Ceremony process,
    // Which shares the keys among the guardians
    Optional<List<GuardianBuilder>> orchestrated = this.mediator.orchestrate(null);
    System.out.printf("Executes the key exchange between guardians%n");
    assertThat(orchestrated).isPresent();

    System.out.printf("Confirm all guardians have shared their partial key backups%n");
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
    this.election = tuple.metadata.election;
    this.context = tuple.context; // LOOK cant use a random extended_hash here and expect it to validate
    this.constants = new ElectionConstants();
    Group.ElementModQ crypto_base_hash = Election.make_crypto_base_hash(NUMBER_OF_GUARDIANS, QUORUM, election);
    assertThat(this.context.crypto_base_hash).isEqualTo(crypto_base_hash);
  }

  // Move on to encrypting ballots
  // Using the `CiphertextElectionContext` encrypt ballots for the election
  void step_2_encrypt_votes() throws IOException {
    // Configure the Encryption Device
    this.metadata = new ElectionWithPlaceholders(this.election);
    this.device = new Encrypt.EncryptionDevice("polling-place-one");
    this.encrypter = new Encrypt.EncryptionMediator(this.metadata, this.context, this.device);
    System.out.printf("%n2. Ready to encrypt at location: %s%n", this.device.location);

    // Load some Ballots
    this.originalPlaintextBallots = new BallotFactory().get_simple_ballots_from_file();
    System.out.printf("Loaded ballots: %d%n", this.originalPlaintextBallots.size());
    assertThat(this.originalPlaintextBallots).isNotEmpty();

    // Encrypt the Ballots
    for (PlaintextBallot plaintext_ballot : this.originalPlaintextBallots) {
      Optional<CiphertextBallot> encrypted_ballot = this.encrypter.encrypt(plaintext_ballot);
      System.out.printf("Ballot Id: %s%n", plaintext_ballot.object_id);
      assertThat(encrypted_ballot).isPresent();
      this.ciphertext_ballots.add(encrypted_ballot.get());
    }
  }

  //  Accept each ballot by marking it as either cast or spoiled.
  //  This example demonstrates one way to accept ballots using the `BallotBox` class
  void step_3_cast_and_spoil() {
    System.out.printf("%n3. cast_and_spoil%n");

    // LOOK why not hide the datastore in the ballot_box ?
    this.ballot_store = new DataStore();
    this.ballot_box = new BallotBox(this.election, this.context, this.ballot_store);
    // Randomly cast or spoil the ballots
    for (CiphertextBallot ballot : this.ciphertext_ballots) {
      Optional<CiphertextAcceptedBallot> accepted_ballot;
      if (random.nextBoolean()) {
        accepted_ballot = this.ballot_box.cast(ballot);
      } else {
        accepted_ballot = this.ballot_box.spoil(ballot);
      }
      assertThat(accepted_ballot).isPresent();
      System.out.printf("Accepted Ballot Id: %s state = %s%n", ballot.object_id, accepted_ballot.get().state);
    }
  }

  /**
   * Homomorphically combine the selections made on all of the cast ballots
   * and use the Available Guardians to decrypt the combined tally.
   * In this way, no individual voter's cast ballot is ever decrypted directly.
   */
  void step_4_decrypt_tally() {
    System.out.printf("%n4. Homomorphically Accumulate and decrypt tally%n");
    // Generate a Homomorphically Accumulated Tally of the ballots
    CiphertextTallyBuilder ciphertext_tally = new CiphertextTallyBuilder("tally_object_id", this.metadata, this.context);
    ciphertext_tally.batch_append(this.ballot_box.accepted());
    this.publishedTally = ciphertext_tally.build();

    // Configure the Decryption
    this.decrypter = new DecryptionMediator(this.context, this.publishedTally, this.ballot_box.getSpoiledBallots());

    // Announce each guardian as present
    int count = 0;
    for (Guardian guardian : this.guardians) {
      System.out.printf("Guardian Present: %s%n", guardian.object_id);
      assertThat(this.decrypter.announce(guardian)).isTrue();
      count++;
      if ((count == QUORUM) && (count != NUMBER_OF_GUARDIANS)) {
        System.out.printf(" Only %d Guardians are available%n", count);
        break;
      }
    }

    // Here's where the ciphertext Tally is decrypted.
    this.decryptedTally = this.decrypter.decrypt_tally(false, null).orElseThrow();
    List<DecryptWithShares.SpoiledTallyAndBallot> spoiledTallyAndBallot =
            this.decrypter.decrypt_spoiled_ballots().orElseThrow();
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
    // Create a representation of each contest's tally
    Map<String, Integer> expected_plaintext_tally = new HashMap<>();
    for (ContestWithPlaceholders contest : this.metadata.contests) {
      for (SelectionDescription selection : contest.ballot_selections) {
        expected_plaintext_tally.put(selection.object_id, 0);
      }
    }

    // Tally the expected values from the loaded ballots
    for (PlaintextBallot ballot : this.originalPlaintextBallots) {
      if (this.ballot_store.get(ballot.object_id).orElseThrow().state == BallotBoxState.CAST) {
        for (PlaintextBallotContest contest : ballot.contests) {
          for (PlaintextBallotSelection selection : contest.ballot_selections) {
            Integer value = expected_plaintext_tally.get(selection.selection_id);
            expected_plaintext_tally.put(selection.selection_id, value + selection.vote); // use merge
          }
        }
      }
    }

    // Compare the expected tally to the decrypted tally
    for (PlaintextTally.PlaintextTallyContest tally_contest : this.decryptedTally.contests.values()) {
      System.out.printf("Contest: %s%n", tally_contest.object_id());
      for (PlaintextTally.PlaintextTallySelection tally_selection : tally_contest.selections().values()) {
        Integer expected = expected_plaintext_tally.get(tally_selection.object_id());
        System.out.printf("  - Selection: %s expected: %s, actual: %s%n",
                tally_selection.object_id(), expected, tally_selection.tally());
        assertThat(expected).isEqualTo(tally_selection.tally());
      }
      System.out.printf("----------------------------------%n");
    }
  }

  // Compare the decrypted spolied tally to original ballot
  void compare_spoiled_tallies() {
    Map<String, PlaintextTally> plaintextTalliesMap = this.spoiledDecryptedTallies.stream().collect(Collectors.toMap(t -> t.object_id, t -> t));

    for (Ballot.CiphertextAcceptedBallot accepted_ballot : this.ballot_box.getSpoiledBallots()) {
      String ballot_id = accepted_ballot.object_id;
      assertThat(accepted_ballot.state).isEqualTo(BallotBoxState.SPOILED);
      for (PlaintextBallot orgBallot : this.originalPlaintextBallots) {
        if (ballot_id.equals(orgBallot.object_id)) {
          System.out.printf("%nSpoiled Ballot: %s%n", ballot_id);
          PlaintextTally plaintextTally = plaintextTalliesMap.get(orgBallot.object_id);
          TimeIntegrationSteps.compare_spoiled_tally(orgBallot, plaintextTally);
        }
      }
    }
  }

    // Publish and verify steps of the election
  void step_5_publish_and_verify() throws IOException {
    System.out.printf("%n5. publish%n");
    Publisher publisher = new Publisher(outputDir, true, true);
    publisher.writeElectionRecordJson(
            this.election,
            this.context,
            this.constants,
            ImmutableList.of(this.device),
            this.ballot_box.getAllBallots(),
            this.publishedTally,
            this.decryptedTally,
            this.coefficient_validation_sets,
            this.spoiledDecryptedBallots,
            this.spoiledDecryptedTallies);

    System.out.printf("%n6. verify%n");
    this.verify_results(publisher);
  }

  // Verify results of election
  void verify_results(Publisher publisher) throws IOException {
    Consumer consumer = new Consumer(publisher);
    ElectionRecord roundtrip = consumer.readElectionRecordJson();

    assertThat(roundtrip.election).isEqualTo(this.election);
    assertThat(roundtrip.context).isEqualTo(this.context);
    assertThat(roundtrip.constants).isEqualTo(this.constants);
    assertThat(roundtrip.devices.size()).isEqualTo(1);
    assertThat(roundtrip.devices.get(0)).isEqualTo(this.device);
    assertThat(roundtrip.ciphertextTally).isEqualTo(this.publishedTally);
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

}
