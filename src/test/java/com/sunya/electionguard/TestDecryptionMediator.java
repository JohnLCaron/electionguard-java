package com.sunya.electionguard;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Sets;
import net.jqwik.api.Example;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.ShrinkingMode;

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

public class TestDecryptionMediator extends TestProperties {
  private static final int NUMBER_OF_GUARDIANS = 3;
  private static final int QUORUM = 2;
  private static final CeremonyDetails CEREMONY_DETAILS = CeremonyDetails.create(NUMBER_OF_GUARDIANS, QUORUM);

  static Auxiliary.Decryptor identity_auxiliary_decrypt = (m, k) -> Optional.of(new String(m.getBytes()));
  static Auxiliary.Encryptor identity_auxiliary_encrypt = (m, k) -> Optional.of(new Auxiliary.ByteString(m.getBytes()));
  static BallotFactory ballot_factory = new BallotFactory();

  KeyCeremonyMediator key_ceremony;
  List<Guardian> guardians = new ArrayList<>();
  Group.ElementModP joint_public_key;
  InternalElectionDescription metadata;
  CiphertextElectionContext context;
  PlaintextBallot fake_cast_ballot;
  PlaintextBallot fake_spoiled_ballot;
  CiphertextAcceptedBallot encrypted_fake_cast_ballot;
  CiphertextAcceptedBallot encrypted_fake_spoiled_ballot;
  Map<String, BigInteger> expected_plaintext_tally;
  Tally.CiphertextTally ciphertext_tally;

  public TestDecryptionMediator() {
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

    Optional<Group.ElementModP> joinKeyO = this.key_ceremony.publish_joint_key();
    assertThat(joinKeyO).isPresent();
    this.joint_public_key = joinKeyO.get();

    // setup the election
    ElectionDescription election = ElectionFactory.get_fake_election();
    ElectionBuilder builder = new ElectionBuilder(NUMBER_OF_GUARDIANS, QUORUM, election);
    assertThat(builder.build()).isEmpty();  // Can't build without the public key
    builder.set_public_key(this.joint_public_key);

    Optional<ElectionBuilder.DescriptionAndContext> tuple = builder.build();
    assertThat(tuple).isPresent();
    this.metadata = tuple.get().description;
    this.context = tuple.get().context;

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
    this.ciphertext_tally = Tally.tally_ballots(ballot_store, this.metadata, this.context).get();
  }

  /*
  @AfterExample
  public void tearDown() {
    this.key_ceremony.reset(CeremonyDetails.create(NUMBER_OF_GUARDIANS, QUORUM));
  } */

  @Example
  public void test_announce() {
    DecryptionMediator subject = new DecryptionMediator(this.metadata, this.context, this.ciphertext_tally);

    Optional<DecryptionShare.TallyDecryptionShare> result = subject.announce(this.guardians.get(0));

    assertThat(result).isPresent();

    // Can only announce once
    assertThat(subject.announce(this.guardians.get(0)).isEmpty());

    // Cannot submit another share internally
    DecryptionShare.TallyDecryptionShare share = DecryptionShare.TallyDecryptionShare.create(this.guardians.get(0).object_id, ZERO_MOD_P, new HashMap<>(), new HashMap<>());
    assertThat(subject._submit_decryption_share(share)).isFalse();

    // Cannot get plaintext tally without a quorum
    assertThat(subject.get_plaintext_tally(true, null)).isEmpty();
  }

  @Example
  public void test_compute_selection() {
    Tally.CiphertextTallySelection first_selection =
            this.ciphertext_tally.cast.values().stream().flatMap(contest -> contest.tally_selections.values().stream())
                    .findFirst().orElseThrow(RuntimeException::new);

    Optional<DecryptionShare.CiphertextDecryptionSelection> result =
            compute_decryption_share_for_selection(this.guardians.get(0), first_selection, this.context);
    assertThat(result).isPresent();
  }

  /* @Example
  public void test_compute_compensated_selection_failure() {
    Tally.CiphertextTallySelection first_selection =
            this.ciphertext_tally.cast.values().stream().flatMap(contest -> contest.tally_selections.values().stream())
                    .findFirst().orElseThrow(RuntimeException::new);

    // Look mutating
    this.guardians.get(0)._guardian_election_partial_key_backups.remove(this.guardians.get(2).object_id);

    assertThat(this.guardians.get(0).recovery_public_key_for(this.guardians.get(2).object_id)).isEmpty();

    Optional<DecryptionShare.CiphertextCompensatedDecryptionSelection> result =
            compute_compensated_decryption_share_for_selection(
                    this.guardians.get(0),
                    this.guardians.get(2).object_id,
                    first_selection,
                    this.context,
                    identity_auxiliary_decrypt);

    assertThat(result).isEmpty();
  } */

  /**
   * Demonstrates the complete workflow for computing a compensated decryption share
   * for one selection. It is useful for verifying that the workflow is correct.
   */
  @Example
  public void test_compute_compensated_selection() {
    Tally.CiphertextTallySelection first_selection =
            this.ciphertext_tally.cast.values().stream().flatMap(contest -> contest.tally_selections.values().stream())
                    .findFirst().orElseThrow(RuntimeException::new);

    // Compute lagrange coefficients for the guardians that are present
    Group.ElementModQ lagrange_0 = ElectionPolynomial.compute_lagrange_coefficient(
            BigInteger.valueOf(this.guardians.get(0).sequence_order()),
            ImmutableList.of(BigInteger.valueOf(this.guardians.get(1).sequence_order())));
    Group.ElementModQ lagrange_1 = ElectionPolynomial.compute_lagrange_coefficient(
            BigInteger.valueOf(this.guardians.get(1).sequence_order()),
            ImmutableList.of(BigInteger.valueOf(this.guardians.get(0).sequence_order())));

    System.out.printf("lagrange: sequence_orders: (%s, %s, %s)%n",
            this.guardians.get(0).sequence_order(), this.guardians.get(1).sequence_order(), this.guardians.get(2).sequence_order());
    System.out.printf("%s%n", lagrange_0);
    System.out.printf("%s%n", lagrange_1);

    // compute their shares
    Optional<DecryptionShare.CiphertextDecryptionSelection> share_0 =
            compute_decryption_share_for_selection(this.guardians.get(0), first_selection, this.context);

    Optional<DecryptionShare.CiphertextDecryptionSelection> share_1 =
            compute_decryption_share_for_selection(this.guardians.get(1), first_selection, this.context);

    assertThat(share_0).isPresent();
    assertThat(share_1).isPresent();

    // compute compensations shares for the missing guardian
    Optional<DecryptionShare.CiphertextCompensatedDecryptionSelection> compensation_0 =
            compute_compensated_decryption_share_for_selection(
                    this.guardians.get(0),
                    this.guardians.get(2).object_id,
                    first_selection,
                    this.context,
                    identity_auxiliary_decrypt);

    Optional<DecryptionShare.CiphertextCompensatedDecryptionSelection> compensation_1 =
            compute_compensated_decryption_share_for_selection(
                    this.guardians.get(1),
                    this.guardians.get(2).object_id,
                    first_selection,
                    this.context,
                    identity_auxiliary_decrypt);

    assertThat(compensation_0).isPresent();
    assertThat(compensation_1).isPresent();

    DecryptionShare.CiphertextCompensatedDecryptionSelection comp0 = compensation_0.get();
    DecryptionShare.CiphertextCompensatedDecryptionSelection comp1 = compensation_1.get();
    System.out.printf("%nSHARES: %s%n %s%n", compensation_0, compensation_1);

    // Check the share proofs
    assertThat(comp0.proof().is_valid(
            first_selection.ciphertext(),
            this.guardians.get(0).recovery_public_key_for(this.guardians.get(2).object_id).get(),
            comp0.share(),
            this.context.crypto_extended_base_hash)).isTrue();

    assertThat(comp1.proof().is_valid(
            first_selection.ciphertext(),
            this.guardians.get(1).recovery_public_key_for(this.guardians.get(2).object_id).get(),
            comp1.share(),
            this.context.crypto_extended_base_hash)).isTrue();

    List<ElementModP> share_pow_p = ImmutableList.of(pow_p(comp0.share(), lagrange_0), pow_p(comp1.share(), lagrange_1));
    System.out.printf("%nSHARE_POW_P%n%s%n", share_pow_p);

    // reconstruct the missing share from the compensation shares
    ElementModP reconstructed_share = mult_p(share_pow_p);
    System.out.printf("%nRECONSTRUCTED SHARE%s%n", reconstructed_share);

    DecryptionShare.CiphertextDecryptionSelection share_2 = DecryptionShare.create_ciphertext_decryption_selection(
            first_selection.object_id,
            this.guardians.get(2).object_id,
            first_selection.description_hash,
            reconstructed_share,
            Optional.empty(),
            Optional.of(ImmutableMap.of(
                    this.guardians.get(0).object_id, comp0,
                    this.guardians.get(1).object_id, comp1))
    );

    // Decrypt the result
    Optional<Tally.PlaintextTallySelection> result = DecryptWithShares.decrypt_selection_with_decryption_shares(
            first_selection,
            ImmutableMap.of(
                    this.guardians.get(0).object_id,
                    new DecryptionShare.KeyAndSelection(this.guardians.get(0).share_election_public_key().key(), share_0.get()),

                    this.guardians.get(1).object_id,
                    new DecryptionShare.KeyAndSelection(this.guardians.get(1).share_election_public_key().key(), share_1.get()),

                    this.guardians.get(2).object_id,
                    new DecryptionShare.KeyAndSelection(this.guardians.get(2).share_election_public_key().key(), share_2)),
            this.context.crypto_extended_base_hash,
            false);

    System.out.printf("%s%n", result);

    assertThat(result).isPresent();
    BigInteger expected = this.expected_plaintext_tally.get(first_selection.object_id);
    BigInteger actual = result.get().tally();
    assertThat(actual).isEqualTo(expected);
  }

  @Example
  public void test_decrypt_selection_all_present() {

    // find the first selection
    Tally.CiphertextTallyContest first_contest = this.ciphertext_tally.cast.values().stream().findFirst().orElseThrow(IllegalStateException::new);
    Tally.CiphertextTallySelection first_selection = first_contest.tally_selections.values().stream().findFirst().orElseThrow(IllegalStateException::new);

    // precompute decryption shares for the guardians
    DecryptionShare.TallyDecryptionShare first_share = compute_decryption_share(this.guardians.get(0), this.ciphertext_tally, this.context).get();
    DecryptionShare.TallyDecryptionShare second_share = compute_decryption_share(this.guardians.get(1), this.ciphertext_tally, this.context).get();
    DecryptionShare.TallyDecryptionShare third_share = compute_decryption_share(this.guardians.get(2), this.ciphertext_tally, this.context).get();

    // build type: Dict[GUARDIAN_ID, Tuple[ELECTION_PUBLIC_KEY, TallyDecryptionShare]]
    Map<String, DecryptionShare.KeyAndSelection> shares = new HashMap<>();

    shares.put(this.guardians.get(0).object_id, new DecryptionShare.KeyAndSelection(
            this.guardians.get(0).share_election_public_key().key(),
            first_share.contests().get(first_contest.object_id).selections().get(first_selection.object_id)));

    shares.put(this.guardians.get(1).object_id, new DecryptionShare.KeyAndSelection(
            this.guardians.get(1).share_election_public_key().key(),
            second_share.contests().get(first_contest.object_id).selections().get(first_selection.object_id)));

    shares.put(this.guardians.get(2).object_id, new DecryptionShare.KeyAndSelection(
            this.guardians.get(2).share_election_public_key().key(),
            third_share.contests().get(first_contest.object_id).selections().get(first_selection.object_id)));

    Optional<Tally.PlaintextTallySelection> result = DecryptWithShares.decrypt_selection_with_decryption_shares(
            first_selection, shares, this.context.crypto_extended_base_hash, false);

    assertThat(result).isPresent();
    assertThat(this.expected_plaintext_tally.get(first_selection.object_id)).isEqualTo(result.get().tally());
  }

  @Example
  public void test_decrypt_ballot_compensate_all_guardians_present() {
    // precompute decryption shares for the guardians
    PlaintextBallot plaintext_ballot = this.fake_cast_ballot;
    CiphertextAcceptedBallot encrypted_ballot = this.encrypted_fake_cast_ballot;

    Map<String, DecryptionShare.BallotDecryptionShare> shares = new HashMap<>();
    for (int i = 0; i < 3; i++) {
      Guardian guardian = this.guardians.get(i);
      shares.put(guardian.object_id,
              Decryption.compute_decryption_share_for_ballot(guardian, encrypted_ballot, this.context).get());
    }

    Optional<Map<String, Tally.PlaintextTallyContest>> resultO = DecryptWithShares.decrypt_ballot(
            encrypted_ballot, shares, this.context.crypto_extended_base_hash);
    assertThat(resultO).isPresent();
    Map<String, Tally.PlaintextTallyContest> result = resultO.get();

    for (PlaintextBallotContest contest : plaintext_ballot.contests) {
      for (PlaintextBallotSelection selection : contest.ballot_selections) {
        int expected_tally = selection.vote.equalsIgnoreCase("False") ? 0 : 1;
        BigInteger actual_tally = result.get(contest.object_id).selections().get(selection.object_id).tally();
        assertThat(expected_tally).isEqualTo(actual_tally.intValue());
      }
    }
  }

  @Example
  public void test_decrypt_ballot_compensate_missing_guardians() {

    // precompute decryption shares for the guardians
    PlaintextBallot plaintext_ballot = this.fake_cast_ballot;
    CiphertextAcceptedBallot encrypted_ballot = this.encrypted_fake_cast_ballot;
    List<Guardian> available_guardians = this.guardians.subList(0, 2);
    Guardian missing_guardian = this.guardians.get(2);
    String missing_guardian_id = missing_guardian.object_id;

    Map<String, DecryptionShare.BallotDecryptionShare> shares = new HashMap<>();
    for (Guardian guardian : available_guardians) {
      shares.put(guardian.object_id,
              Decryption.compute_decryption_share_for_ballot(guardian, encrypted_ballot, this.context).get());
    }

    Map<String, DecryptionShare.CompensatedBallotDecryptionShare> compensated_shares = new HashMap<>();
    for (Guardian guardian : available_guardians) {
      compensated_shares.put(guardian.object_id,
              Decryption.compute_compensated_decryption_share_for_ballot(
                      guardian, missing_guardian_id, encrypted_ballot, this.context, identity_auxiliary_decrypt).get());
    }

    List<KeyCeremony.PublicKeySet> all_keys = available_guardians.stream().map(g -> g.share_public_keys())
            .collect(Collectors.toList());
    Map<String, Group.ElementModQ> lagrange_coefficients = compute_lagrange_coefficients_for_guardians(all_keys);

    ElectionPublicKey public_key = available_guardians.get(0).guardian_election_public_keys().get(missing_guardian_id);

    DecryptionShare.BallotDecryptionShare reconstructed_share = reconstruct_decryption_ballot(
            missing_guardian_id,
            public_key,
            encrypted_ballot,
            compensated_shares,
            lagrange_coefficients);

    //         all_shares = {**shares, missing_guardian_id: reconstructed_share}
    Map<String, DecryptionShare.BallotDecryptionShare> all_shares = shares.entrySet().stream()
            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    all_shares.put(missing_guardian_id, reconstructed_share);


    // Ballot.CiphertextAcceptedBallot ballot,
    //          Map<String, BallotDecryptionShare> shares,
    //          ElementModQ extended_base_hash
    Optional<Map<String, Tally.PlaintextTallyContest>> resultO = DecryptWithShares.decrypt_ballot(
            encrypted_ballot,
            all_shares,
            this.context.crypto_extended_base_hash);
    assertThat(resultO).isPresent();
    Map<String, Tally.PlaintextTallyContest> result = resultO.get();

    for (PlaintextBallotContest contest : plaintext_ballot.contests) {
      for (PlaintextBallotSelection selection : contest.ballot_selections) {
        int expected_tally = selection.vote.equalsIgnoreCase("False") ? 0 : 1;
        BigInteger actual_tally = result.get(contest.object_id).selections().get(selection.object_id).tally();
        assertThat(expected_tally).isEqualTo(actual_tally.intValue());
      }
    }
  }

  @Example
  public void test_decrypt_spoiled_ballots_all_guardians_present() {
    // precompute decryption shares for the guardians
    DecryptionShare.TallyDecryptionShare first_share = compute_decryption_share(this.guardians.get(0), this.ciphertext_tally, this.context).get();
    DecryptionShare.TallyDecryptionShare second_share = compute_decryption_share(this.guardians.get(1), this.ciphertext_tally, this.context).get();
    DecryptionShare.TallyDecryptionShare third_share = compute_decryption_share(this.guardians.get(2), this.ciphertext_tally, this.context).get();

    Map<String, DecryptionShare.TallyDecryptionShare> shares = new HashMap<>();
    shares.put(this.guardians.get(0).object_id, first_share);
    shares.put(this.guardians.get(1).object_id, second_share);
    shares.put(this.guardians.get(2).object_id, third_share);

    Optional<Map<String, Map<String, Tally.PlaintextTallyContest>>> resultO = DecryptWithShares.decrypt_spoiled_ballots(
            this.ciphertext_tally.spoiled_ballots,
            shares,
            this.context.crypto_extended_base_hash);
    assertThat(resultO).isPresent();
    Map<String, Map<String, Tally.PlaintextTallyContest>> result = resultO.get();

    assertThat(result.containsKey(this.fake_spoiled_ballot.object_id)).isTrue();

    Map<String, Tally.PlaintextTallyContest> spoiled_ballot = result.get(this.fake_spoiled_ballot.object_id);

    for (PlaintextBallotContest contest : this.fake_spoiled_ballot.contests) {
      for (PlaintextBallotSelection selection : contest.ballot_selections) {
        BigInteger actual_tally = spoiled_ballot.get(contest.object_id).selections().get(selection.object_id).tally();
        BigInteger expected_tally = result.get(this.fake_spoiled_ballot.object_id).get(contest.object_id)
                .selections().get(selection.object_id).tally();
        assertThat(expected_tally).isEqualTo(actual_tally);
      }
    }
  }

  @Example
  public void test_get_plaintext_tally_all_guardians_present_simple() {
    DecryptionMediator subject = new DecryptionMediator(this.metadata, this.context, this.ciphertext_tally);

    for (Guardian guardian : this.guardians) {
      assertThat(subject.announce(guardian)).isPresent();
    }

    Optional<Tally.PlaintextTally> decrypted_tallies = subject.get_plaintext_tally(false, null);
    assertThat(decrypted_tallies).isPresent();
    Map<String, BigInteger> result = this._convert_to_selections(decrypted_tallies.get());

    assertThat(result).isNotEmpty();
    assertThat(result).isEqualTo(this.expected_plaintext_tally);

    // Verify we get the same tally back if we call again
    Optional<Tally.PlaintextTally> another_decrypted_tally = subject.get_plaintext_tally(false, null);
    assertThat(another_decrypted_tally).isPresent();

    assertThat(decrypted_tallies.get()).isEqualTo(another_decrypted_tally.get());
  }

  @Example
  public void test_get_plaintext_tally_compensate_missing_guardian_simple() {
    DecryptionMediator subject = new DecryptionMediator(this.metadata, this.context, this.ciphertext_tally);

    assertThat(subject.announce(this.guardians.get(0))).isPresent();
    assertThat(subject.announce(this.guardians.get(1))).isPresent();

    // explicitly compensate to demonstrate that this is possible, but not required
    assertThat(subject.compensate(this.guardians.get(2).object_id, identity_auxiliary_decrypt)).isPresent();

    Optional<Tally.PlaintextTally> decrypted_tallies = subject.get_plaintext_tally(false, null);
    assertThat(decrypted_tallies).isPresent();
    Map<String, BigInteger> result = this._convert_to_selections(decrypted_tallies.get());

    // assert
    assertThat(result).isNotEmpty();
    assertThat(this.expected_plaintext_tally).isEqualTo(result);
  }

  @Property(tries = 8, shrinking = ShrinkingMode.OFF)
  public void test_get_plaintext_tally_all_guardians_present(
          @ForAll("election_description") Election.ElectionDescription description) {

    ElectionBuilder builder = new ElectionBuilder(NUMBER_OF_GUARDIANS, QUORUM, description);
    Optional<ElectionBuilder.DescriptionAndContext> tuple = builder.set_public_key(this.joint_public_key).build();
    assertThat(tuple).isPresent();
    InternalElectionDescription metadata = tuple.get().description;
    CiphertextElectionContext context = tuple.get().context;

    ElectionTestHelper helper = new ElectionTestHelper(random);
    List<Ballot.PlaintextBallot> plaintext_ballots = helper.plaintext_voted_ballots(metadata, 3 + random.nextInt(3));

    Map<String, BigInteger> plaintext_tallies = TallyTestHelper.accumulate_plaintext_ballots(plaintext_ballots);
    Tally.CiphertextTally encrypted_tally = this._generate_encrypted_tally(metadata, context, plaintext_ballots);
    DecryptionMediator subject = new DecryptionMediator(metadata, context, encrypted_tally);

    for (Guardian guardian : this.guardians) {
      assertThat(subject.announce(guardian)).isPresent();
    }

    Optional<Tally.PlaintextTally> decrypted_tallies = subject.get_plaintext_tally(false, null);
    assertThat(decrypted_tallies).isPresent();
    Map<String, BigInteger> result = this._convert_to_selections(decrypted_tallies.get());

    // assert
    assertThat(result).isNotEmpty();
    assertThat(plaintext_tallies).isEqualTo(result);
  }

  Tally.CiphertextTally _generate_encrypted_tally(
          InternalElectionDescription metadata,
          CiphertextElectionContext context,
          List<PlaintextBallot> ballots) {

    // encrypt each ballot
    DataStore store = new DataStore();
    for (PlaintextBallot ballot : ballots) {
      Optional<CiphertextBallot> encrypted_ballot = Encrypt.encrypt_ballot(
              ballot, metadata, context, int_to_q_unchecked(BigInteger.ONE), Optional.empty(), true);
      assertThat(encrypted_ballot).isPresent();
      // add to the ballot store
      store.put(
              encrypted_ballot.get().object_id,
              from_ciphertext_ballot(encrypted_ballot.get(), BallotBoxState.CAST));
    }

    Optional<Tally.CiphertextTally> tally = Tally.tally_ballots(store, metadata, context);
    assertThat(tally).isPresent();
    return tally.get();
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
