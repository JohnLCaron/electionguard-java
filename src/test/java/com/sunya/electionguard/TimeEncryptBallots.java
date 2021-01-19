package com.sunya.electionguard;

import com.google.common.base.Stopwatch;
import com.google.common.collect.ImmutableList;
import com.sunya.electionguard.publish.ConvertFromJson;
import com.sunya.electionguard.publish.ElectionDescriptionFromJson;
import com.sunya.electionguard.publish.Publisher;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.TimeUnit;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;
import static com.google.common.truth.Truth8.assertThat;
import static com.sunya.electionguard.Ballot.*;
import static com.sunya.electionguard.Election.*;

/**
 * Test a complete simple example of executing an End-to-End encrypted election.
 * In a real world scenario all of these steps would not be completed on the same machine.
 */
public class TimeEncryptBallots {
  private static final int NUMBER_OF_GUARDIANS = 6;
  private static final int QUORUM = 6;
  private static final Random random = new Random(System.currentTimeMillis());

  public static void main(String[] args) throws IOException {
    int nballots = Integer.parseInt(args[0]);

    TimeEncryptBallots timer = new TimeEncryptBallots(nballots);
    timer.run();
  }

  String outputDir;
  int nballots;

  // Step 0 - Configure Election
  ElectionDescription description;
  ElectionBuilder election_builder;
  InternalElectionDescription metadata;
  CiphertextElectionContext context;
  ElectionConstants constants;

  // Step 1 - Key Ceremony;
  KeyCeremonyMediator mediator;
  List<Guardian> guardians = new ArrayList<>();
  List<KeyCeremony.CoefficientValidationSet> coefficient_validation_sets = new ArrayList<>();

  // Step 2 - Encrypt Votes
  Encrypt.EncryptionDevice device;
  Encrypt.EncryptionMediator encrypter;
  List<PlaintextBallot> plaintext_ballots = new ArrayList<>();
  List<CiphertextBallot> ciphertext_ballots = new ArrayList<>();

  // Step 3 - Cast and Spoil
  DataStore ballot_store;
  BallotBox ballot_box;

  // Step 4 - Decrypt Tally
  Tally.CiphertextTally ciphertext_tally;
  Tally.PlaintextTally plaintext_tally;
  DecryptionMediator decrypter;

  public TimeEncryptBallots(int nballots) throws IOException {
    Path tmp = Files.createTempDirectory(null);
    tmp.toFile().deleteOnExit();
    // outputDir = "/home/snake/tmp/testEndToEnd";
    outputDir = tmp.toAbsolutePath().toString();
    System.out.printf("=========== outputDir = %s nballots = %d%n", outputDir, nballots);
    this.nballots = nballots;
  }

  public void run() throws IOException {
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

    this.step_5_publish_and_verify();
    timePerBallot = ((float) stopwatch.elapsed(TimeUnit.MILLISECONDS)) / nballots;
    System.out.printf("*** step_5_publish_and_verify elapsed = %s nballots = %d timePerBallot = %f ms%n", stopwatch, nballots, timePerBallot);

    Scheduler.shutdown();
  }

  void step_0_configure_election() throws IOException {
    // Load a pre-configured Election Description
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

    System.out.printf("Verify that the input election meta-data is well-formed%n");
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
    // Setup Guardians
    for (int i = 0; i < NUMBER_OF_GUARDIANS; i++) {
      this.guardians.add(Guardian.createForTesting("guardian_" + i, i, NUMBER_OF_GUARDIANS, QUORUM, null));
    }

    // Setup Mediator
    this.mediator = new KeyCeremonyMediator(this.guardians.get(0).ceremony_details());

    // Attendance (Public Key Share)
    for (Guardian guardian : this.guardians) {
      this.mediator.announce(guardian);
    }

    System.out.printf("Confirms all guardians have shared their public keys%n");
    assertThat(this.mediator.all_guardians_in_attendance()).isTrue();

    // Run the Key Ceremony process,
    // Which shares the keys among the guardians
    Optional<List<Guardian>> orchestrated = this.mediator.orchestrate(null);
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
    for (Guardian guardian : this.guardians) {
      this.coefficient_validation_sets.add(guardian.share_coefficient_validation_set());
    }

    // Build the Election
    this.election_builder.set_public_key(joint_key.get());
    ElectionBuilder.DescriptionAndContext tuple = this.election_builder.build().get();
    this.metadata = tuple.description;
    this.context = tuple.context;
    this.constants = new ElectionConstants();
  }

  // Move on to encrypting ballots
  // Using the `CiphertextElectionContext` encrypt ballots for the election
  void step_2_encrypt_votes(int nballots) throws IOException {
    // Configure the Encryption Device
    this.device = new Encrypt.EncryptionDevice("polling-place-one");
    this.encrypter = new Encrypt.EncryptionMediator(this.metadata, this.context, this.device);
    System.out.printf("%n2. Ready to encrypt at location: %s%n", this.device.location);

    // Encrypt nballots Ballots
    BallotFactory ballotFactory = new BallotFactory();
    for (int i = 0; i < nballots; i++) {
      String ballot_id = "ballot-id-" + random.nextInt();
      PlaintextBallot plaintext_ballot = ballotFactory.get_fake_ballot(this.metadata, ballot_id, true);
      Optional<CiphertextBallot> encrypted_ballot = this.encrypter.encrypt(plaintext_ballot);
      assertThat(encrypted_ballot).isPresent();
      this.ciphertext_ballots.add(encrypted_ballot.get());
      this.plaintext_ballots.add(plaintext_ballot);
    }
  }

  // Next, we cast or spoil the ballots
  //         Accept each ballot by marking it as either cast or spoiled.
  //        This example demonstrates one way to accept ballots using the `BallotBox` class
  void step_3_cast_and_spoil() {
    // Configure the Ballot Box
    this.ballot_store = new DataStore();
    this.ballot_box = new BallotBox(this.metadata, this.context, this.ballot_store);
    System.out.printf("%n3. cast_and_spoil%n");
    // Randomly cast or spoil the ballots
    for (CiphertextBallot ballot : this.ciphertext_ballots) {
      Optional<CiphertextAcceptedBallot> accepted_ballot;
      if (random.nextBoolean()) {
        accepted_ballot = this.ballot_box.cast(ballot);
      } else {
        accepted_ballot = this.ballot_box.spoil(ballot);
      }
      assertThat(accepted_ballot).isPresent();
    }
  }

  /**
   * Homomorphically combine the selections made on all of the cast ballots
   * and use the Available Guardians to decrypt the combined tally.
   * In this way, no individual voter's cast ballot is ever decrypted drectly.
   */
  void step_4_decrypt_tally() {
    // Generate a Homomorphically Accumulated Tally of the ballots
    this.ciphertext_tally =
            Tally.tally_ballots(this.ballot_store, this.metadata, this.context).get();

    // Configure the Decryption
    this.decrypter = new DecryptionMediator(this.metadata, this.context, this.ciphertext_tally);

    // Announce each guardian as present
    for (Guardian guardian : this.guardians) {
      Optional<DecryptionShare.TallyDecryptionShare> decryption_share = this.decrypter.announce(guardian);
      System.out.printf("Guardian Present: %s%n", guardian.object_id);
      assertThat(decryption_share).isPresent();
    }

    // Get the Plain Text Tally
    this.plaintext_tally = this.decrypter.get_plaintext_tally(false, null).get();
    System.out.printf("Tally Decrypted%n");

    // Now, compare the results
    this.compare_results();
  }

  //         Compare the results to ensure the decryption was done correctly
  void compare_results() {
    // Create a representation of each contest's tally
    List<String> selection_ids = new ArrayList<>();
    Map<String, Integer> expected_plaintext_tally = new HashMap<>();
    for (ContestDescriptionWithPlaceholders contest : this.metadata.contests) {
      for (SelectionDescription selection : contest.ballot_selections) {
        selection_ids.add(selection.object_id);
        expected_plaintext_tally.put(selection.object_id, 0);
      }
    }

    // Tally the expected values from the loaded ballots
    for (PlaintextBallot ballot : this.plaintext_ballots) {
      if (this.ballot_store.get(ballot.object_id).get().state == BallotBoxState.CAST) {
        for (PlaintextBallotContest contest : ballot.contests) {
          for (PlaintextBallotSelection selection : contest.ballot_selections) {
            Integer value = expected_plaintext_tally.get(selection.selection_id);
            expected_plaintext_tally.put(selection.selection_id, value + selection.to_int()); // use merge
          }
        }
      }
    }

    // Compare the expected tally to the decrypted tally
    for (Tally.PlaintextTallyContest tally_contest : this.plaintext_tally.contests().values()) {
      for (Tally.PlaintextTallySelection tally_selection : tally_contest.selections().values()) {
        Integer expected = expected_plaintext_tally.get(tally_selection.object_id());
        assertThat(expected).isEqualTo(tally_selection.tally().intValue());
      }
      System.out.printf("----------------------------------%n");
    }

    // Compare the expected values for each spoiled ballot
    for (Map.Entry<String, CiphertextAcceptedBallot> entry : this.ciphertext_tally.spoiled_ballots().entrySet()) {
      String ballot_id = entry.getKey();
      CiphertextAcceptedBallot accepted_ballot = entry.getValue();
      if (accepted_ballot.state == BallotBoxState.SPOILED) {
        for (PlaintextBallot plaintext_ballot : this.plaintext_ballots) {
          if (ballot_id == plaintext_ballot.object_id) {
            for (PlaintextBallotContest contest : plaintext_ballot.contests) {
              for (PlaintextBallotSelection selection : contest.ballot_selections) {
                int expected = selection.to_int();
                Tally.PlaintextTallySelection decrypted_selection = (
                        this.plaintext_tally.spoiled_ballots().get(ballot_id).get(contest.contest_id)
                                .selections().get(selection.selection_id));
                assertThat(expected).isEqualTo(decrypted_selection.tally().intValue());
              }
            }
          }
        }
      }
    }
  }

  // Publish and verify steps of the election
  void step_5_publish_and_verify() throws IOException {
    System.out.printf("%n5. publish%n");
    Publisher publisher = new Publisher(outputDir, true);
    publisher.write(
            this.description,
            this.context,
            this.constants,
            ImmutableList.of(this.device),
            this.ballot_store,
            this.ciphertext_tally.spoiled_ballots().values(),
            this.ciphertext_tally.publish_ciphertext_tally(),
            this.plaintext_tally,
            this.coefficient_validation_sets);

    System.out.printf("%n6. verify%n");
    this.verify_results(publisher);
  }

  // Verify results of election
  void verify_results(Publisher publisher) throws IOException {
    ElectionDescriptionFromJson builder = new ElectionDescriptionFromJson(
            publisher.electionFile().toString());
    ElectionDescription description_from_file = builder.build();
    assertThat(description_from_file).isEqualTo(this.description);

    CiphertextElectionContext context_from_file = ConvertFromJson.readContext(
            publisher.contextFile().toString());
    assertThat(context_from_file).isEqualTo(this.context);

    ElectionConstants constants_from_file = ConvertFromJson.readConstants(
            publisher.constantsFile().toString());
    assertThat(constants_from_file).isEqualTo(this.constants);

    Encrypt.EncryptionDevice device_from_file = ConvertFromJson.readDevice(
            publisher.deviceFile(this.device.uuid).toString());
    assertThat(device_from_file).isEqualTo(this.device);

    for (CiphertextAcceptedBallot ballot : this.ballot_store) {
      CiphertextAcceptedBallot ballot_from_file = ConvertFromJson.readBallot(
              publisher.ballotFile(ballot.object_id).toString());
      assertWithMessage(publisher.ballotFile(ballot.object_id).toString())
              .that(ballot_from_file).isEqualTo(ballot);
    }

    for (CiphertextAcceptedBallot spoiled_ballot : this.ciphertext_tally.spoiled_ballots().values()) {
      CiphertextAcceptedBallot ballot_from_file = ConvertFromJson.readBallot(
              publisher.spoiledFile(spoiled_ballot.object_id).toString());
      assertThat(ballot_from_file).isEqualTo(spoiled_ballot);
    }

    for (KeyCeremony.CoefficientValidationSet coefficient_validation_set : this.coefficient_validation_sets) {
      KeyCeremony.CoefficientValidationSet coefficient_validation_set_from_file = ConvertFromJson.readCoefficient(
              publisher.coefficientsFile(coefficient_validation_set.owner_id()).toString());
      assertThat(coefficient_validation_set_from_file).isEqualTo(coefficient_validation_set);
    }

    Tally.PublishedCiphertextTally ciphertext_tally_from_file = ConvertFromJson.readCiphertextTally(
            publisher.encryptedTallyFile().toString());
    assertThat(ciphertext_tally_from_file).isEqualTo(this.ciphertext_tally.publish_ciphertext_tally());

    Tally.PlaintextTally plainttext_tally_from_file = ConvertFromJson.readPlaintextTally(
            publisher.tallyFile().toString());
    assertThat(plainttext_tally_from_file).isEqualTo(this.plaintext_tally);
  }

}
