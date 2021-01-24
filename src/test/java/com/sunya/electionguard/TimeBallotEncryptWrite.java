package com.sunya.electionguard;

import com.google.common.base.Stopwatch;
import com.google.common.collect.ImmutableList;
import com.sunya.electionguard.publish.Publisher;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.TimeUnit;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth8.assertThat;
import static com.sunya.electionguard.Ballot.CiphertextAcceptedBallot;
import static com.sunya.electionguard.Ballot.CiphertextBallot;
import static com.sunya.electionguard.Ballot.PlaintextBallot;
import static com.sunya.electionguard.Election.CiphertextElectionContext;
import static com.sunya.electionguard.Election.ElectionConstants;
import static com.sunya.electionguard.Election.ElectionDescription;
import static com.sunya.electionguard.Election.InternalElectionDescription;

/**
 * Test a complete simple example of executing an End-to-End encrypted election.
 * In a real world scenario all of these steps would not be completed on the same machine.
 */
public class TimeBallotEncryptWrite {
  private static final int NUMBER_OF_GUARDIANS = 6;
  private static final int QUORUM = 5;
  private static final Random random = new Random(System.currentTimeMillis());

  public static void main(String[] args) throws IOException {
    int nballots = Integer.parseInt(args[0]);

    TimeBallotEncryptWrite timer = new TimeBallotEncryptWrite(nballots);
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

  public TimeBallotEncryptWrite(int nballots) throws IOException {
    Path tmp = Files.createTempDirectory(null);
    tmp.toFile().deleteOnExit();
    outputDir = tmp.toAbsolutePath().toString();
    outputDir = "/home/snake/tmp/testBallotReading";
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

    this.step_4_publish_ballot_chain();
    timePerBallot = ((float) stopwatch.elapsed(TimeUnit.MILLISECONDS)) / nballots;
    System.out.printf("*** step_4_publish_ballot_chain elapsed = %s nballots = %d timePerBallot = %f ms%n", stopwatch, nballots, timePerBallot);
    stopwatch.reset().start();

    this.step_5_publish_ballot_chain_proto();
    timePerBallot = ((float) stopwatch.elapsed(TimeUnit.MILLISECONDS)) / nballots;
    System.out.printf("*** step_5_publish_ballot_chain_proto elapsed = %s nballots = %d timePerBallot = %f ms%n", stopwatch, nballots, timePerBallot);
    stopwatch.reset().start();

    Scheduler.shutdown();
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
    ElectionBuilder.DescriptionAndContext tuple = this.election_builder.build().orElseThrow();
    this.election = tuple.description;
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
      this.plaintext_ballots.add(plaintext_ballot);
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

  // Publish ballot chain as Json
  void step_4_publish_ballot_chain() throws IOException {
    System.out.printf("%n4. publish ballot chain as json%n");
    Publisher publisher = new Publisher(outputDir, true);
    publisher.writeBallotChainJson(
            this.description,
            this.context,
            this.constants,
            ImmutableList.of(this.device),
            this.ballot_store,
            this.coefficient_validation_sets);
  }

  // Publish ballot chain as Proto
  void step_5_publish_ballot_chain_proto() throws IOException {
    System.out.printf("%n5. publish ballot chain as proto%n");
    Publisher publisher = new Publisher("/home/snake/tmp/TimeBallotEncryptWrite", true);
    publisher.writeBallotChainProto(
            this.description,
            this.context,
            this.constants,
            this.device,
            this.ballot_store,
            this.coefficient_validation_sets);
  }

}
