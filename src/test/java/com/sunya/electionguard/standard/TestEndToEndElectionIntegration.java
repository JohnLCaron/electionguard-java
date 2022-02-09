package com.sunya.electionguard.standard;

import com.google.common.base.Stopwatch;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.sunya.electionguard.Auxiliary;
import com.sunya.electionguard.BallotBox;
import com.sunya.electionguard.BallotFactory;
import com.sunya.electionguard.CiphertextBallot;
import com.sunya.electionguard.CiphertextElectionContext;
import com.sunya.electionguard.CiphertextTally;
import com.sunya.electionguard.CiphertextTallyBuilder;
import com.sunya.electionguard.ElectionBuilder;
import com.sunya.electionguard.ElectionConstants;
import com.sunya.electionguard.ElectionFactory;
import com.sunya.electionguard.Encrypt;
import com.sunya.electionguard.Group;
import com.sunya.electionguard.GuardianRecord;
import com.sunya.electionguard.Hash;
import com.sunya.electionguard.InternalManifest;
import com.sunya.electionguard.Manifest;
import com.sunya.electionguard.PlaintextBallot;
import com.sunya.electionguard.PlaintextTally;
import com.sunya.electionguard.SubmittedBallot;
import com.sunya.electionguard.publish.Consumer;
import com.sunya.electionguard.publish.Publisher;
import com.sunya.electionguard.verifier.ElectionRecord;
import net.jqwik.api.Example;
import net.jqwik.api.lifecycle.BeforeProperty;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.google.common.truth.Truth.assertWithMessage;
import static com.sunya.electionguard.InternalManifest.ContestWithPlaceholders;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth8.assertThat;

/**
 * Test a complete simple example of executing an End-to-End encrypted election, using Python-ported classes.
 * Publishing to JSON.
 */
public class TestEndToEndElectionIntegration {
  private static final int NUMBER_OF_GUARDIANS = 7;
  private static final int QUORUM = 5;
  private static final Random random = new Random(System.currentTimeMillis());

  String outputDir;

  @BeforeProperty
  public void setUp() throws IOException {
    Path tmp = Files.createTempDirectory("publish_");
    tmp.toFile().deleteOnExit();
    outputDir = "/home/snake/tmp/electionguard/publishEndToEnd"; // tmp.toAbsolutePath().toString();
    System.out.printf("=========== outputDir = %s%n", outputDir);
  }

  // Step 0 - Configure Manifest
  Manifest election;
  ElectionBuilder election_builder;
  CiphertextElectionContext context;
  ElectionConstants constants;
  InternalManifest metadata;

  // Step 1 - Key Ceremony;
  KeyCeremonyMediator mediator;
  List<Guardian> guardians = new ArrayList<>();

  // Step 2 - Encrypt Votes
  Encrypt.EncryptionDevice device;
  Encrypt.EncryptionMediator encrypter;
  List<PlaintextBallot> originalPlaintextBallots;
  List<CiphertextBallot> ciphertext_ballots = new ArrayList<>();

  // Step 3 - Cast and Spoil
  BallotBox ballot_box;

  // Step 4 - Decrypt Tally
  DecryptionMediator decryption_mediator;
  CiphertextTally publishedTally;
  PlaintextTally decryptedTally;
  Collection<PlaintextTally> spoiledDecryptedTallies;

  // Step 5 - Publish
  List<GuardianRecord> guardian_records = new ArrayList<>();

  // Execute the simplified end-to-end test demonstrating each component of the system.
  @Example
  public void test_end_to_end_election() throws IOException {
    Stopwatch stopwatch = Stopwatch.createStarted();
    this.step_0_configure_election();
    System.out.printf("*** step_0_configure_election elapsed = %s%n", stopwatch);
    stopwatch.reset().start();

    this.step_1_key_ceremony();
    System.out.printf("*** step_1_key_ceremony elapsed = %s%n", stopwatch);
    stopwatch.reset().start();

    this.step_2_encrypt_votes();
    int nballots = this.originalPlaintextBallots.size();
    float timePerBallot = ((float) stopwatch.elapsed(TimeUnit.MILLISECONDS)) / nballots;
    System.out.printf("*** step_2_encrypt_votes elapsed = %s per ballot = %f ms%n", stopwatch, timePerBallot);
    stopwatch.reset().start();

    this.step_3_cast_and_spoil();
    System.out.printf("*** step_3_cast_and_spoil elapsed = %s%n", stopwatch);
    stopwatch.reset().start();

    this.step_4_decrypt_tally();
    timePerBallot = ((float) stopwatch.elapsed(TimeUnit.MILLISECONDS)) / nballots;
    System.out.printf("*** step_4_decrypt_tally elapsed = %s per ballot = %f ms%n", stopwatch, timePerBallot);
    stopwatch.reset().start();

    this.step_5_publish_and_verify();
    System.out.printf("*** step_5_publish_and_verify elapsed = %s%n", stopwatch);
  }

  void step_0_configure_election() throws IOException {
    System.out.printf("%n0. Verify that the input election meta-data is well-formed%n");
    // TODO: replace with complex election
    this.election = ElectionFactory.get_simple_election_from_file();

    System.out.printf("----------------------------------%n");
    System.out.printf("Manifest Summary:%nScope: %s%n", this.election.election_scope_id);
    System.out.printf("Geopolitical Units: %d%n", this.election.geopolitical_units.size());
    System.out.printf("Parties: %d%n", this.election.parties.size());
    System.out.printf("Candidates: %d%n", this.election.candidates.size());
    System.out.printf("Contests: %d%n", this.election.contests.size());
    System.out.printf("Ballot Styles: %d%n", this.election.ballot_styles.size());
    System.out.printf("----------------------------------%n");

    assertThat(this.election.is_valid()).isTrue();
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
      this.guardians.add(Guardian.createForTesting("guardian_" + i, i, NUMBER_OF_GUARDIANS, QUORUM, null));
    }

    // Setup Mediator
    this.mediator = new KeyCeremonyMediator("mediator_1", this.guardians.get(0).ceremony_details());

    // ROUND 1: Public Key Sharing
    // Announce
    for (Guardian guardian : this.guardians) {
      this.mediator.announce(guardian.share_public_keys());
    }

    // Share Keys
    for (Guardian guardian : this.guardians) {
      List<KeyCeremony.PublicKeySet> announced_keys = this.mediator.share_announced(null).orElseThrow();
      for (KeyCeremony.PublicKeySet key_set : announced_keys) {
        if (!guardian.object_id.equals(key_set.election().owner_id())) {
          guardian.save_guardian_public_keys(key_set);
        }
      }
    }

    System.out.printf("Confirms all guardians have shared their public keys%n");
    assertThat(this.mediator.all_guardians_announced()).isTrue();

    // ROUND 2: Election Partial Key Backup Sharing
    //Share Backups
    for (Guardian sending_guardian : this.guardians) {
      sending_guardian.generate_election_partial_key_backups(Auxiliary.identity_auxiliary_encrypt);
      List<KeyCeremony.ElectionPartialKeyBackup> backups = new ArrayList<>();
      for (Guardian designated_guardian : this.guardians) {
        if (!designated_guardian.object_id.equals(sending_guardian.object_id)) {
          backups.add(sending_guardian.share_election_partial_key_backup(designated_guardian.object_id).orElseThrow());
        }
      }
      this.mediator.receive_backups(backups);
      System.out.printf("Receive election partial key backups from key owning guardian %s%n", sending_guardian.object_id);
      assertThat(backups).hasSize(NUMBER_OF_GUARDIANS - 1);
    }

    System.out.printf("Confirm all guardians have shared their election partial key backups%n");
    assertThat(this.mediator.all_election_partial_key_backups_available()).isTrue();

    // Receive Backups
    for (Guardian designated_guardian : this.guardians) {
      List<KeyCeremony.ElectionPartialKeyBackup> backups = this.mediator.share_backups(designated_guardian.object_id).orElseThrow();
      assertThat(backups).hasSize(NUMBER_OF_GUARDIANS - 1);
      for (KeyCeremony.ElectionPartialKeyBackup backup : backups) {
        designated_guardian.save_election_partial_key_backup(backup);
      }
    }

    // ROUND 3: Verification of Backups
    // Verify Backups
    for (Guardian designated_guardian : this.guardians) {
      List<KeyCeremony.ElectionPartialKeyVerification> verifications = new ArrayList<>();
      for (Guardian backup_owner : this.guardians) {
        if (!designated_guardian.object_id.equals(backup_owner.object_id)) {
          KeyCeremony.ElectionPartialKeyVerification verification =
                  designated_guardian.verify_election_partial_key_backup(
                          backup_owner.object_id, Auxiliary.identity_auxiliary_decrypt).orElseThrow();
          verifications.add(verification);
        }
      }
      this.mediator.receive_backup_verifications(verifications);
    }

    // Verification
    boolean verified = this.mediator.all_backups_verified();
    System.out.printf("Confirms all guardians have verified the backups of all other guardians%n");
    assertThat(verified).isTrue();

    System.out.printf("Confirms all guardians have submitted a verification of the backups of all other guardians%n");
    assertThat(this.mediator.all_election_partial_key_verifications_received()).isTrue();

    System.out.printf("Confirms all guardians have verified the backups of all other guardians%n");
    assertThat(this.mediator.all_election_partial_key_backups_verified()).isTrue();

    // FINAL: Publish Joint Key
    KeyCeremony.ElectionJointKey joint_key = this.mediator.publish_joint_key().orElseThrow();
    System.out.printf("Publishes the Joint Manifest Key%n");
    assertThat(joint_key).isNotNull();

    // Save GuardianRecord
    for (Guardian guardian : this.guardians) {
      GuardianRecord guardianRecord = guardian.publish();
      this.guardian_records.add(guardianRecord);
    }

    // Build the Manifest
    this.election_builder = new ElectionBuilder(NUMBER_OF_GUARDIANS, QUORUM, this.election);
    this.election_builder.set_public_key(joint_key.joint_public_key());
    this.election_builder.set_commitment_hash(joint_key.commitment_hash());

    ElectionBuilder.DescriptionAndContext tuple = this.election_builder.build().orElseThrow();
    this.election = tuple.internalManifest.manifest;
    this.context = tuple.context;
    this.constants = Group.getPrimes();
    Group.ElementModQ crypto_base_hash = CiphertextElectionContext.make_crypto_base_hash(NUMBER_OF_GUARDIANS, QUORUM, election);
    assertThat(this.context.crypto_base_hash).isEqualTo(crypto_base_hash);

    Group.ElementModQ extended_hash = Hash.hash_elems(crypto_base_hash, joint_key.commitment_hash());
    assertThat(this.context.crypto_extended_base_hash).isEqualTo(extended_hash);
    System.out.printf("commitmentHash %s%n", joint_key.commitment_hash());
  }

  // Move on to encrypting ballots
  // Using the `CiphertextElectionContext` encrypt ballots for the election
  void step_2_encrypt_votes() throws IOException {
    // Configure the Encryption Device
    this.metadata = new InternalManifest(this.election);
    this.device = Encrypt.EncryptionDevice.createForTest("polling-place-one");
    this.encrypter = new Encrypt.EncryptionMediator(this.metadata, this.context, this.device);
    System.out.printf("%n2. Ready to encrypt at location: %s%n", this.device.location);

    // Load some Ballots
    this.originalPlaintextBallots = new BallotFactory().get_simple_ballots_from_file();
    System.out.printf("Loaded ballots: %d%n", this.originalPlaintextBallots.size());
    assertThat(this.originalPlaintextBallots).isNotEmpty();

    // Encrypt the Ballots
    for (PlaintextBallot plaintext_ballot : this.originalPlaintextBallots) {
      Optional<CiphertextBallot> encrypted_ballot = this.encrypter.encrypt(plaintext_ballot);
      System.out.printf("Ballot Id: %s%n", plaintext_ballot.object_id());
      assertThat(encrypted_ballot).isPresent();
      this.ciphertext_ballots.add(encrypted_ballot.get());
    }
  }

  //  Accept each ballot by marking it as either cast or spoiled.
  //  This example demonstrates one way to accept ballots using the `BallotBox` class
  void step_3_cast_and_spoil() {
    System.out.printf("%n3. cast_and_spoil%n");
    this.ballot_box = new BallotBox(this.election, this.context);
    // Randomly cast or spoil the ballots
    for (CiphertextBallot ballot : this.ciphertext_ballots) {
      Optional<SubmittedBallot> accepted_ballot;
      if (random.nextBoolean()) {
        accepted_ballot = this.ballot_box.cast(ballot);
      } else {
        accepted_ballot = this.ballot_box.spoil(ballot);
      }
      assertThat(accepted_ballot).isPresent();
      System.out.printf("Accepted Ballot Id: %s state = %s%n", ballot.object_id(), accepted_ballot.get().state);
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
    ciphertext_tally.batch_append(this.ballot_box.getAcceptedBallotsAsCloseableIterable());
    this.publishedTally = ciphertext_tally.build();
    List<SubmittedBallot> spoiled_ballots = Lists.newArrayList(this.ballot_box.getSpoiledBallots());

    // Configure the Decryption
    this.decryption_mediator = new DecryptionMediator("decryption-mediator", this.context);
    DecryptionHelper.perform_compensated_decryption_setup(this.guardians, QUORUM, this.decryption_mediator, this.context,
            this.publishedTally, spoiled_ballots);

    // Decrypt the Tally
    Optional<PlaintextTally> decryptedTallyO = this.decryption_mediator.get_plaintext_tally(this.publishedTally);
    assertThat(decryptedTallyO).isPresent();
    this.decryptedTally = decryptedTallyO.get();
    System.out.printf("Tally Decrypted%n");

    // Decrypt the Spoiled Ballots.
    // Note we use the old decryption_mediator, not the new Optional<List<SpoiledBallotAndTally>> decrypt_spoiled_ballots()
    // Note this returns a PlaintextTally, not a PlaintextBallot (!)
    Optional<Map<String, PlaintextTally>> spoiledDecryptedBallotsO =
            this.decryption_mediator.get_plaintext_ballots(this.ballot_box.getSpoiledBallots());
    assertThat(spoiledDecryptedBallotsO).isPresent();
    this.spoiledDecryptedTallies = spoiledDecryptedBallotsO.get().values();
    System.out.printf("Spoiled Ballot Tallies Decrypted%n");

    // Now, compare the results
    this.compare_results();
    this.compare_spoiled_tallies();
  }

  // Compare the results to ensure the decryption was done correctly
  void compare_results() {
    System.out.printf("%n4.5 Compare results%n");
    // Create a representation of each contest's tally
    Map<String, Integer> expected_plaintext_tally = new HashMap<>();
    for (ContestWithPlaceholders contest : this.metadata.contests.values()) {
      for (Manifest.SelectionDescription selection : contest.ballot_selections) {
        expected_plaintext_tally.put(selection.object_id, 0);
      }
    }

    // Tally the expected values from the loaded ballots
    for (PlaintextBallot ballot : this.originalPlaintextBallots) {
      if (this.ballot_box.get(ballot.object_id()).orElseThrow().state == BallotBox.State.CAST) {
        for (PlaintextBallot.Contest contest : ballot.contests) {
          for (PlaintextBallot.Selection selection : contest.ballot_selections) {
            Integer value = expected_plaintext_tally.get(selection.selection_id);
            expected_plaintext_tally.put(selection.selection_id, value + selection.vote); // use merge
          }
        }
      }
    }

    // Compare the expected tally to the decrypted tally
    for (PlaintextTally.Contest tally_contest : this.decryptedTally.contests.values()) {
      System.out.printf("Contest: %s%n", tally_contest.object_id());
      for (PlaintextTally.Selection tally_selection : tally_contest.selections().values()) {
        Integer expected = expected_plaintext_tally.get(tally_selection.object_id());
        System.out.printf("  - Selection: %s expected: %s, actual: %s%n",
                tally_selection.object_id(), expected, tally_selection.tally());
        assertThat(expected).isEqualTo(tally_selection.tally());
      }
      System.out.printf("----------------------------------%n");
    }
  }

  // Compare the decrypted spoiled tally to original ballot
  void compare_spoiled_tallies() {
    Map<String, PlaintextTally> plaintextTalliesMap = this.spoiledDecryptedTallies.stream().collect(Collectors.toMap(t -> t.object_id, t -> t));

    for (SubmittedBallot accepted_ballot : this.ballot_box.getSpoiledBallots()) {
      String ballot_id = accepted_ballot.object_id();
      assertThat(accepted_ballot.state).isEqualTo(BallotBox.State.SPOILED);
      for (PlaintextBallot orgBallot : this.originalPlaintextBallots) {
        if (ballot_id.equals(orgBallot.object_id())) {
          System.out.printf("%nSpoiled Ballot: %s%n", ballot_id);
          PlaintextTally plaintextTally = plaintextTalliesMap.get(orgBallot.object_id());
          // LOOK TimeIntegrationSteps.compare_spoiled_tally(orgBallot, plaintextTally);
        }
      }
    }
  }

    // Publish and verify steps of the election
  void step_5_publish_and_verify() throws IOException {
    System.out.printf("%n5. publish to %s%n", outputDir);
    Publisher publisher = new Publisher(outputDir, Publisher.Mode.createNew, true);

    publisher.writeElectionRecordJson(
            this.election,
            this.context,
            this.constants,
            ImmutableList.of(this.device),
            this.ballot_box.getAllBallots(),
            this.publishedTally,
            this.decryptedTally,
            this.guardian_records,
            this.spoiledDecryptedTallies,
            this.decryption_mediator.availableGuardians());

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
    assertThat(roundtrip.encryptedTally).isEqualTo(this.publishedTally);
    assertThat(roundtrip.decryptedTally).isEqualTo(this.decryptedTally);

    Map<String, GuardianRecord> coeffMap = this.guardian_records.stream()
            .collect(Collectors.toMap(g->g.guardian_id(), g -> g));
    for (GuardianRecord guardianRecord : roundtrip.guardianRecords) {
      GuardianRecord expected = coeffMap.get(guardianRecord.guardian_id());
      assertThat(expected).isNotNull();
      assertWithMessage(guardianRecord.guardian_id()).that(guardianRecord).isEqualTo(expected);
    }

    // Equation 3.A
    // The hashing is order dependent, use the sequence_order to sort.
    List<GuardianRecord> sorted = roundtrip.guardianRecords.stream()
            .sorted(Comparator.comparing(GuardianRecord::sequence_order))
            .collect(Collectors.toList());
    List<Group.ElementModP> commitments = new ArrayList<>();
    for (GuardianRecord guardian : sorted) {
      commitments.addAll(guardian.election_commitments());
    }

    Group.ElementModQ commitment_hash = Hash.hash_elems(commitments);
    Group.ElementModQ expectedExtendedHash = Hash.hash_elems(roundtrip.baseHash(), commitment_hash);
    assertThat(roundtrip.extendedHash()).isEqualTo(expectedExtendedHash);

    for (SubmittedBallot ballot : roundtrip.acceptedBallots) {
      SubmittedBallot expected = this.ballot_box.get(ballot.object_id()).orElseThrow();
      assertWithMessage(ballot.object_id()).that(ballot).isEqualTo(expected);
    }

    Map<String, PlaintextBallot> originalMap = this.originalPlaintextBallots.stream()
            .collect(Collectors.toMap(b->b.object_id(), b -> b));
    try (Stream<PlaintextTally> ballots = roundtrip.spoiledBallots.iterator().stream()) {
      ballots.forEach(ballot -> {
        PlaintextBallot expected = originalMap.get(ballot.object_id);
        assertThat(expected).isNotNull();
        // LOOK TimeIntegrationSteps.compare_spoiled_ballot(ballot, expected);
      });
    }
  }

}
