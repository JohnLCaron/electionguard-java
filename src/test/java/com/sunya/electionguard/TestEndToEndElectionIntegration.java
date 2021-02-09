package com.sunya.electionguard;

import com.google.common.base.Stopwatch;
import com.google.common.collect.ImmutableList;
import com.sunya.electionguard.publish.ConvertFromJson;
import com.sunya.electionguard.publish.ElectionDescriptionFromJson;
import com.sunya.electionguard.publish.Publisher;
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

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth8.assertThat;

/**
 * Test a complete simple example of executing an End-to-End encrypted election.
 * In a real world scenario all of these steps would not be completed on the same machine.
 */
public class TestEndToEndElectionIntegration {
  private static final int NUMBER_OF_GUARDIANS = 7;
  private static final int QUORUM = 5;
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
  ElectionDescription description;
  ElectionBuilder election_builder;
  InternalElectionDescription metadata;
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
  List<PlaintextBallot> plaintext_ballots;
  List<CiphertextBallot> ciphertext_ballots = new ArrayList<>();

  // Step 3 - Cast and Spoil
  DataStore ballot_store;
  BallotBox ballot_box;

  // Step 4 - Decrypt Tally
  DecryptionMediator decrypter;
  CiphertextTallyBuilder ciphertext_tally;
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
    Group.ElementModQ crypto_base_hash = Election.make_crypto_base_hash(NUMBER_OF_GUARDIANS, QUORUM, description);
    // Setup Guardians
    for (int i = 1; i <= NUMBER_OF_GUARDIANS; i++) {
      this.guardianBuilders.add(GuardianBuilder.createForTesting("guardian_" + i, i, NUMBER_OF_GUARDIANS, QUORUM, crypto_base_hash, null));
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
    this.metadata = tuple.metadata;
    this.context = tuple.context;
    this.constants = new ElectionConstants();

    assertThat(this.context.crypto_base_hash).isEqualTo(crypto_base_hash);
  }

  // Move on to encrypting ballots
  // Using the `CiphertextElectionContext` encrypt ballots for the election
  void step_2_encrypt_votes() throws IOException {
    // Configure the Encryption Device
    this.device = new Encrypt.EncryptionDevice("polling-place-one");
    this.encrypter = new Encrypt.EncryptionMediator(this.metadata, this.context, this.device);
    System.out.printf("%n2. Ready to encrypt at location: %s%n", this.device.location);

    // Load some Ballots
    this.plaintext_ballots = new BallotFactory().get_simple_ballots_from_file();
    System.out.printf("Loaded ballots: %d%n", this.plaintext_ballots.size());
    assertThat(this.plaintext_ballots).isNotEmpty();

    // Encrypt the Ballots
    for (PlaintextBallot plaintext_ballot : this.plaintext_ballots) {
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
    this.ballot_box = new BallotBox(this.metadata, this.context, this.ballot_store);
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
    this.ciphertext_tally = new CiphertextTallyBuilder("tally_object_id", this.metadata, this.context);
    this.ciphertext_tally.batch_append(this.ballot_box.accepted());
    // Configure the Decryption
    this.decrypter = new DecryptionMediator(this.context, this.ciphertext_tally, this.ballot_box.getSpoiledBallots());

    // Announce each guardian as present
    int count = 0;
    for (Guardian guardian : this.guardians) {
      System.out.printf("Guardian Present: %s%n", guardian.object_id);
      assertThat(this.decrypter.announce(guardian)).isTrue();
      count++;
      if (count == QUORUM) {
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
    for (ContestDescriptionWithPlaceholders contest : this.metadata.contests) {
      for (SelectionDescription selection : contest.ballot_selections) {
        expected_plaintext_tally.put(selection.object_id, 0);
      }
    }

    // Tally the expected values from the loaded ballots
    for (PlaintextBallot ballot : this.plaintext_ballots) {
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
      for (PlaintextBallot orgBallot : this.plaintext_ballots) {
        if (ballot_id.equals(orgBallot.object_id)) {
          System.out.printf("%nSpoiled Ballot: %s%n", ballot_id);
          PlaintextTally plaintextTally = plaintextTalliesMap.get(orgBallot.object_id);
          compare_spoiled_tally(orgBallot, plaintextTally);
        }
      }
    }
  }

  void compare_spoiled_tally(PlaintextBallot orgBallot, PlaintextTally plaintextTally) {
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

    // Publish and verify steps of the election
  void step_5_publish_and_verify() throws IOException {
    System.out.printf("%n5. publish%n");
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

    System.out.printf("%n6. verify%n");
    this.verify_results(publisher);
  }

  // Verify results of election
  void verify_results(Publisher publisher) throws IOException {
    ElectionDescriptionFromJson builder = new ElectionDescriptionFromJson(
            publisher.electionPath().toString());
    ElectionDescription description_from_file = builder.build();
    assertThat(description_from_file).isEqualTo(this.description);

    CiphertextElectionContext context_from_file = ConvertFromJson.readContext(
            publisher.contextPath().toString());
    assertThat(context_from_file).isEqualTo(this.context);

    ElectionConstants constants_from_file = ConvertFromJson.readConstants(
            publisher.constantsPath().toString());
    assertThat(constants_from_file).isEqualTo(this.constants);

    Encrypt.EncryptionDevice device_from_file = ConvertFromJson.readDevice(
            publisher.devicePath(this.device.uuid).toString());
    assertThat(device_from_file).isEqualTo(this.device);

    for (CiphertextAcceptedBallot ballot : this.ballot_box.getAllBallots()) {
      CiphertextAcceptedBallot ballot_from_file = ConvertFromJson.readCiphertextBallot(
              publisher.ballotPath(ballot.object_id).toString());
      assertWithMessage(publisher.ballotPath(ballot.object_id).toString())
              .that(ballot_from_file).isEqualTo(ballot);
    }

    /** LOOK spoiled_ballot fix this
    for (CiphertextAcceptedBallot spoiled_ballot : this.ciphertext_tally.spoiled_ballots().values()) {
      CiphertextAcceptedBallot ballot_from_file = ConvertFromJson.readBallot(
              publisher.spoiledFile(spoiled_ballot.object_id).toString());
      assertThat(ballot_from_file).isEqualTo(spoiled_ballot);
    } */

    for (KeyCeremony.CoefficientValidationSet coefficient_validation_set : this.coefficient_validation_sets) {
      KeyCeremony.CoefficientValidationSet coefficient_validation_set_from_file = ConvertFromJson.readCoefficient(
              publisher.coefficientsPath(coefficient_validation_set.owner_id()).toString());
      assertThat(coefficient_validation_set_from_file).isEqualTo(coefficient_validation_set);
    }

    PublishedCiphertextTally ciphertext_tally_from_file = ConvertFromJson.readCiphertextTally(
            publisher.encryptedTallyPath().toString());
    assertThat(ciphertext_tally_from_file).isEqualTo(this.ciphertext_tally.build());

    PlaintextTally plaintext_tally_from_file = ConvertFromJson.readPlaintextTally(
            publisher.tallyPath().toString());
    assertThat(plaintext_tally_from_file).isEqualTo(this.decryptedTally);
  }

}
