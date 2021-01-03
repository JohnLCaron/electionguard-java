package com.sunya.electionguard;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Sets;
import net.jqwik.api.Example;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.ShrinkingMode;
import net.jqwik.api.lifecycle.AfterContainer;
import net.jqwik.api.lifecycle.AfterExample;

import java.math.BigInteger;
import java.util.*;
import java.util.stream.Collectors;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth8.assertThat;
import static com.sunya.electionguard.Ballot.*;
import static com.sunya.electionguard.Decryption.*;
import static com.sunya.electionguard.Election.*;
import static com.sunya.electionguard.Group.*;
import static com.sunya.electionguard.KeyCeremony.*;

public class TestDecryptionMediatorProblem extends TestProperties {
  private static final int NUMBER_OF_GUARDIANS = 3;
  private static final int QUORUM = 2;
  private static final CeremonyDetails CEREMONY_DETAILS = CeremonyDetails.create(NUMBER_OF_GUARDIANS, QUORUM);

  static Auxiliary.Decryptor identity_auxiliary_decrypt = (m, k) -> Optional.of(new String(m.getBytes()));
  static Auxiliary.Encryptor identity_auxiliary_encrypt = (m, k) -> Optional.of(new Auxiliary.ByteString(m.getBytes()));
  static ElectionFactory election_factory = new ElectionFactory();
  static BallotFactory ballot_factory = new BallotFactory();

  KeyCeremonyMediator key_ceremony;
  List<Guardian> guardians = new ArrayList<>();
  ElementModP joint_public_key;
  InternalElectionDescription metadata;
  CiphertextElectionContext context;
  Map<String, BigInteger> expected_plaintext_tally;

  // PlaintextBallot fake_cast_ballot;
  // PlaintextBallot fake_spoiled_ballot;
  // CiphertextAcceptedBallot encrypted_fake_cast_ballot;
  // CiphertextAcceptedBallot encrypted_fake_spoiled_ballot;
  // Tally.CiphertextTally ciphertext_tally;

  public TestDecryptionMediatorProblem() {

    this.key_ceremony = new KeyCeremonyMediator(CEREMONY_DETAILS);

    // Setup Guardians
    for (int i = 0; i < NUMBER_OF_GUARDIANS; i++) {
      int sequence = i + 2;
      this.guardians.add(new Guardian("guardian_" + sequence, sequence, NUMBER_OF_GUARDIANS, QUORUM, null));
    }

    // Attendance (Public Key Share)
    for (Guardian guardian : this.guardians) {
      this.key_ceremony.announce(guardian);
    }

    this.key_ceremony.orchestrate(identity_auxiliary_encrypt);
    this.key_ceremony.verify(identity_auxiliary_decrypt);

    Optional<ElementModP> joinKeyO = this.key_ceremony.publish_joint_key();
    assertThat(joinKeyO).isPresent();
    this.joint_public_key = joinKeyO.get();

    // setup the election
    ElectionDescription election = election_factory.get_fake_election();
    ElectionBuilder builder = new ElectionBuilder(NUMBER_OF_GUARDIANS, QUORUM, election);
    assertThat(builder.build()).isEmpty();  // Can't build without the public key
    builder.set_public_key(this.joint_public_key);

    Optional<ElectionBuilder.DescriptionAndContext> tuple = builder.build();
    assertThat(tuple).isPresent();
    this.metadata = tuple.get().description;
    this.context = tuple.get().context;


    /*
    Encrypt.EncryptionDevice encryption_device = new Encrypt.EncryptionDevice("location");
    Encrypt.EncryptionMediator ballot_marking_device = new Encrypt.EncryptionMediator(this.metadata, this.context, encryption_device);

    // get some fake ballots
    this.fake_cast_ballot = ballot_factory.get_fake_ballot(this.metadata, "some-unique-ballot-id-cast", true);
    List<PlaintextBallot> more_fake_ballots = new ArrayList<>();
    for (int i = 0; i < 10; i++) {
      more_fake_ballots.add(
              ballot_factory.get_fake_ballot(this.metadata, "some-unique-ballot-id-cast" + i, true));
    }
    this.fake_spoiled_ballot = ballot_factory.get_fake_ballot(this.metadata, "some-unique-ballot-id-spoiled", true);
    List<PlaintextBallot> more_fake_spoiled_ballots = new ArrayList<>();
    for (int i = 0; i < 2; i++) {
      more_fake_spoiled_ballots.add(
              ballot_factory.get_fake_ballot(this.metadata, "some-unique-ballot-id-spoiled" + i, true));
    }

    assertThat(this.fake_cast_ballot.is_valid(this.metadata.ballot_styles.get(0).object_id)).isTrue();
    assertThat(this.fake_spoiled_ballot.is_valid(this.metadata.ballot_styles.get(0).object_id)).isTrue();
    ArrayList<PlaintextBallot> all = new ArrayList<>(more_fake_ballots);
    all.add(this.fake_cast_ballot);

    this.expected_plaintext_tally = TallyTestHelper.accumulate_plaintext_ballots(all);

    // Fill in the expected values with any missing selections
    // that were not made on any ballots
    Set<String> selection_ids = this.metadata.contests.stream().flatMap(c -> c.ballot_selections.stream())
            .map(s -> s.object_id).collect(Collectors.toSet());
    // missing_selection_ids = selection_ids.difference( set(this.expected_plaintext_tally) )
    Sets.SetView<String> missing_selection_ids = Sets.difference(selection_ids, this.expected_plaintext_tally.keySet());
    for (String id : missing_selection_ids) {
      this.expected_plaintext_tally.put(id, BigInteger.ZERO);
    }

    // Encrypt
    CiphertextBallot temp_encrypted_fake_cast_ballot = ballot_marking_device.encrypt(this.fake_cast_ballot).get();
    CiphertextBallot temp_encrypted_fake_spoiled_ballot = ballot_marking_device.encrypt(this.fake_spoiled_ballot).get();
    assertThat(temp_encrypted_fake_cast_ballot).isNotNull();
    assertThat(temp_encrypted_fake_spoiled_ballot).isNotNull();
    assertThat(temp_encrypted_fake_cast_ballot.is_valid_encryption(
            this.metadata.description_hash,
            this.joint_public_key,
            this.context.crypto_extended_base_hash))
            .isTrue();

    // encrypt some more fake ballots
    List<CiphertextBallot> more_fake_encrypted_ballots = new ArrayList<>();
    for (PlaintextBallot fake_ballot : more_fake_ballots) {
      more_fake_encrypted_ballots.add(ballot_marking_device.encrypt(fake_ballot).get());
    }
    // encrypt some more fake ballots
    List<CiphertextBallot> more_fake_encrypted_spoiled_ballots = new ArrayList<>();
    for (PlaintextBallot fake_ballot : more_fake_spoiled_ballots) {
      more_fake_encrypted_spoiled_ballots.add(ballot_marking_device.encrypt(fake_ballot).get());
    }

    // configure the ballot box
    DataStore ballot_store = new DataStore();
    BallotBox ballot_box = new BallotBox(this.metadata, this.context, ballot_store);
    this.encrypted_fake_cast_ballot = ballot_box.cast(temp_encrypted_fake_cast_ballot).get();
    this.encrypted_fake_spoiled_ballot = ballot_box.spoil(temp_encrypted_fake_spoiled_ballot).get();

    // Cast some more fake ballots
    for (CiphertextBallot fake_ballot : more_fake_encrypted_ballots) {
      ballot_box.cast(fake_ballot);
    }
    // Spoil some more fake ballots
    for (CiphertextBallot fake_ballot : more_fake_encrypted_spoiled_ballots) {
      ballot_box.spoil(fake_ballot);
    }

    // generate encrypted tally
    this.ciphertext_tally = Tally.tally_ballots(ballot_store, this.metadata, this.context).get(); */
  }

  @AfterExample
  public void tearDown() {
    this.key_ceremony.reset(CeremonyDetails.create(NUMBER_OF_GUARDIANS, QUORUM));
  }

  @Example
  public void testProblemWithMissingSelections() {
    // these are cast
    ArrayList<PlaintextBallot> castBallots = new ArrayList<>();
    for (int i = 0; i < 10; i++) {
      castBallots.add(ballot_factory.get_fake_ballot(this.metadata, "some-unique-ballot-id-cast" + i, true));
    }
    Map<String, BigInteger> expected_tally = TallyTestHelper.accumulate_plaintext_ballots(castBallots);
    System.out.printf("%n testProblemWithSpoiledBallots expected %s%n", expected_tally);

    // these are spoiled
    List<PlaintextBallot> spoiledBallots = new ArrayList<>();
    for (int i = 0; i < 2; i++) {
      spoiledBallots.add(
              ballot_factory.get_fake_ballot(this.metadata, "some-unique-ballot-id-spoiled" + i, true));
    }

    // Fill in the expected values with any missing selections
    // that were not made on any ballots
    Set<String> selection_ids = this.metadata.contests.stream().flatMap(c -> c.ballot_selections.stream())
            .map(s -> s.object_id).collect(Collectors.toSet());
    // missing_selection_ids = selection_ids.difference( set(this.expected_plaintext_tally) )
    Sets.SetView<String> missing_selection_ids = Sets.difference(selection_ids, expected_tally.keySet());
    for (String id : missing_selection_ids) {
      expected_tally.put(id, BigInteger.ZERO);
    }

    // encrypt ballots
    Encrypt.EncryptionDevice encryption_device = new Encrypt.EncryptionDevice("location");
    Encrypt.EncryptionMediator ballot_marking_device = new Encrypt.EncryptionMediator(this.metadata, this.context, encryption_device);

    List<CiphertextBallot> allECastBallots = new ArrayList<>();
    for (PlaintextBallot ballot : castBallots) {
      allECastBallots.add(ballot_marking_device.encrypt(ballot).get());
    }
    List<CiphertextBallot> allESpoiledBallots = new ArrayList<>();
    for (PlaintextBallot ballot : spoiledBallots) {
      allESpoiledBallots.add(ballot_marking_device.encrypt(ballot).get());
    }

    // configure the ballot box
    DataStore ballot_store = new DataStore();
    BallotBox ballot_box = new BallotBox(this.metadata, this.context, ballot_store);

    // cast the ballots
    for (CiphertextBallot cballot : allECastBallots) {
      assertThat(ballot_box.cast(cballot)).isPresent();
    }
    // spoil the ballots
    for (CiphertextBallot cballot : allESpoiledBallots) {
      assertThat(ballot_box.spoil(cballot)).isPresent();
    }

    // generate encrypted tally
    Tally.CiphertextTally ctally = Tally.tally_ballots(ballot_store, this.metadata, this.context).get();

    // now decrypt it
    DecryptionMediator decryptionMediator = new DecryptionMediator(this.metadata, this.context, ctally);
    this.guardians.forEach(decryptionMediator::announce);
    Optional<Tally.PlaintextTally> decrypted_tallies = decryptionMediator.get_plaintext_tally(false, null);
    assertThat(decrypted_tallies).isNotNull();

    Map<String, BigInteger> result = this._convert_to_selections(decrypted_tallies.get());
    System.out.printf("%n==================%ntestProblem result %s%n", result);
    assertThat(result).isEqualTo(expected_tally);
  }

  @Example
  public void testProblemWithSpoiledBallots() {
    // these are cast
    ArrayList<PlaintextBallot> castBallots = new ArrayList<>();
    for (int i = 0; i < 10; i++) {
      castBallots.add(ballot_factory.get_fake_ballot(this.metadata, "some-unique-ballot-id-cast" + i, true));
    }
    Map<String, BigInteger> expected = TallyTestHelper.accumulate_plaintext_ballots(castBallots);
    System.out.printf("%n testProblemWithSpoiledBallots expected %s%n", expected);

    // these are spoiled
    List<PlaintextBallot> spoiledBallots = new ArrayList<>();
    for (int i = 0; i < 2; i++) {
      spoiledBallots.add(
              ballot_factory.get_fake_ballot(this.metadata, "some-unique-ballot-id-spoiled" + i, true));
    }

    // encrypt ballots
    Encrypt.EncryptionDevice encryption_device = new Encrypt.EncryptionDevice("location");
    Encrypt.EncryptionMediator ballot_marking_device = new Encrypt.EncryptionMediator(this.metadata, this.context, encryption_device);

    List<CiphertextBallot> allECastBallots = new ArrayList<>();
    for (PlaintextBallot ballot : castBallots) {
      allECastBallots.add(ballot_marking_device.encrypt(ballot).get());
    }
    List<CiphertextBallot> allESpoiledBallots = new ArrayList<>();
    for (PlaintextBallot ballot : spoiledBallots) {
      allESpoiledBallots.add(ballot_marking_device.encrypt(ballot).get());
    }

    // configure the ballot box
    DataStore ballot_store = new DataStore();
    BallotBox ballot_box = new BallotBox(this.metadata, this.context, ballot_store);

    // cast the ballots
    for (CiphertextBallot cballot : allECastBallots) {
      assertThat(ballot_box.cast(cballot)).isPresent();
    }
    // spoil the ballots
    for (CiphertextBallot cballot : allESpoiledBallots) {
      assertThat(ballot_box.spoil(cballot)).isPresent();
    }

    // generate encrypted tally
    Tally.CiphertextTally ctally = Tally.tally_ballots(ballot_store, this.metadata, this.context).get();

    // now decrypt it
    DecryptionMediator decryptionMediator = new DecryptionMediator(this.metadata, this.context, ctally);
    this.guardians.forEach(decryptionMediator::announce);
    Optional<Tally.PlaintextTally> decrypted_tallies = decryptionMediator.get_plaintext_tally(false, null);
    assertThat(decrypted_tallies).isNotNull();

    Map<String, BigInteger> result = this._convert_to_selections(decrypted_tallies.get());
    System.out.printf("%n==================%ntestProblem result %s%n", result);
    assertThat(result).isEqualTo(expected);
  }

  @Example
  public void testProblemWithManyBallots() {
    ArrayList<PlaintextBallot> allPlaintextBallots = new ArrayList<>();
    for (int i = 0; i < 10; i++) {
      allPlaintextBallots.add(ballot_factory.get_fake_ballot(this.metadata, "some-unique-ballot-id-cast" + i, true));
    }
    Map<String, BigInteger> expected_tally = TallyTestHelper.accumulate_plaintext_ballots(allPlaintextBallots);
    System.out.printf("%n testProblem expected %s%n", expected_tally);

    // Fill in the expected values with any missing selections
    // that were not made on any ballots
    Set<String> selection_ids = this.metadata.contests.stream().flatMap(c -> c.ballot_selections.stream())
            .map(s -> s.object_id).collect(Collectors.toSet());
    // missing_selection_ids = selection_ids.difference( set(this.expected_plaintext_tally) )
    Sets.SetView<String> missing_selection_ids = Sets.difference(selection_ids, expected_tally.keySet());
    for (String id : missing_selection_ids) {
      expected_tally.put(id, BigInteger.ZERO);
    }

    Encrypt.EncryptionDevice encryption_device = new Encrypt.EncryptionDevice("location");
    Encrypt.EncryptionMediator ballot_marking_device = new Encrypt.EncryptionMediator(this.metadata, this.context, encryption_device);

    // encrypt ballots
    List<CiphertextBallot> allEncryptedBallots = new ArrayList<>();
    for (PlaintextBallot ballot : allPlaintextBallots) {
      allEncryptedBallots.add(ballot_marking_device.encrypt(ballot).get());
    }

    // configure the ballot box
    DataStore ballot_store = new DataStore();
    BallotBox ballot_box = new BallotBox(this.metadata, this.context, ballot_store);

    // cast the ballots
    for (CiphertextBallot cballot : allEncryptedBallots) {
      assertThat(ballot_box.cast(cballot)).isPresent();
    }

    // generate encrypted tally
    Tally.CiphertextTally ctally = Tally.tally_ballots(ballot_store, this.metadata, this.context).get();

    // now decrypt it
    DecryptionMediator decryptionMediator = new DecryptionMediator(this.metadata, this.context, ctally);
    this.guardians.forEach(decryptionMediator::announce);
    Optional<Tally.PlaintextTally> decrypted_tallies = decryptionMediator.get_plaintext_tally(false, null);
    assertThat(decrypted_tallies).isNotNull();

    Map<String, BigInteger> result = this._convert_to_selections(decrypted_tallies.get());
    System.out.printf("%n==================%ntestProblem result %s%n", result);
    assertThat(result).isEqualTo(expected_tally);
  }

  // test_get_plaintext_tally_all_guardians_present_simple
  @Example
  public void testProblemOneBallot() {
    PlaintextBallot castBallot = ballot_factory.get_fake_ballot(this.metadata, "some-unique-ballot-id-cast", true);
    System.out.printf("testProblem castBallot %s%n", castBallot);

    ArrayList<PlaintextBallot> all = new ArrayList<>();
    all.add(castBallot);
    Map<String, BigInteger> expected_tally = TallyTestHelper.accumulate_plaintext_ballots(all);
    System.out.printf("%n testProblem expected %s%n", expected_tally);

    // Fill in the expected values with any missing selections
    // that were not made on any ballots
    Set<String> selection_ids = this.metadata.contests.stream().flatMap(c -> c.ballot_selections.stream())
            .map(s -> s.object_id).collect(Collectors.toSet());
    // missing_selection_ids = selection_ids.difference( set(this.expected_plaintext_tally) )
    Sets.SetView<String> missing_selection_ids = Sets.difference(selection_ids, expected_tally.keySet());
    for (String id : missing_selection_ids) {
      expected_tally.put(id, BigInteger.ZERO);
    }

    Encrypt.EncryptionDevice encryption_device = new Encrypt.EncryptionDevice("location");
    Encrypt.EncryptionMediator ballot_marking_device = new Encrypt.EncryptionMediator(this.metadata, this.context, encryption_device);
    CiphertextBallot encryptedCastBallot = ballot_marking_device.encrypt(castBallot).get();

    // configure the ballot box
    DataStore ballot_store = new DataStore();
    BallotBox ballot_box = new BallotBox(this.metadata, this.context, ballot_store);
    CiphertextAcceptedBallot acceptedCastBallot= ballot_box.cast(encryptedCastBallot).get();

    // generate encrypted tally
    Tally.CiphertextTally ctally = Tally.tally_ballots(ballot_store, this.metadata, this.context).get();

    // now decrypt it
    DecryptionMediator decryptionMediator = new DecryptionMediator(this.metadata, this.context, ctally);
    this.guardians.forEach(decryptionMediator::announce);
    Optional<Tally.PlaintextTally> decrypted_tallies = decryptionMediator.get_plaintext_tally(false, null);
    assertThat(decrypted_tallies).isNotNull();

    Map<String, BigInteger> result = this._convert_to_selections(decrypted_tallies.get());
    System.out.printf("%n==================%ntestProblem result %s%n", result);
    assertThat(result).isEqualTo(expected_tally);
  }

  private Map<String, BigInteger> _convert_to_selections(Tally.PlaintextTally tally) {
    Map<String, BigInteger> plaintext_selections = new HashMap<>();
    for (Tally.PlaintextTallyContest contest : tally.contests().values()) {
      for (Map.Entry<String, Tally.PlaintextTallySelection> entry : contest.selections().entrySet()) {
        plaintext_selections.put(entry.getKey(), entry.getValue().tally());
      }
    }

    return plaintext_selections;
  }

}