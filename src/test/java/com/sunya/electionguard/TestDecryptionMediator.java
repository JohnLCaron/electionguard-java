package com.sunya.electionguard;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Sets;
import net.jqwik.api.Example;
import net.jqwik.api.lifecycle.AfterContainer;
import org.junit.After;

import java.math.BigInteger;
import java.util.*;
import java.util.stream.Collectors;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth8.assertThat;

import static com.sunya.electionguard.Ballot.*;
import static com.sunya.electionguard.Election.*;
import static com.sunya.electionguard.Group.*;
import static com.sunya.electionguard.KeyCeremony.*;

public class TestDecryptionMediator {
  private static final int NUMBER_OF_GUARDIANS = 3;
  private static final int QUORUM = 2;
  private static final CeremonyDetails CEREMONY_DETAILS = CeremonyDetails.create(NUMBER_OF_GUARDIANS, QUORUM);

  static Auxiliary.Decryptor identity_auxiliary_decrypt = (m, k) -> Optional.of(new String(m.getBytes()));
  static Auxiliary.Encryptor identity_auxiliary_encrypt = (m, k) -> Optional.of(new Auxiliary.ByteString(m.getBytes()));
  static ElectionFactory election_factory = new ElectionFactory();
  static BallotFactory ballot_factory = new BallotFactory();

  KeyCeremonyMediator key_ceremony;
  List<Guardian> guardians = new ArrayList<>();
  Group.ElementModP joint_public_key;
  ElectionDescription election;
  ElectionBuilder builder;
  InternalElectionDescription metadata;
  CiphertextElectionContext context;
  Encrypt.EncryptionDevice encryption_device;
  Encrypt.EncryptionMediator ballot_marking_device;
  PlaintextBallot fake_cast_ballot;
  List<PlaintextBallot> more_fake_ballots;
  PlaintextBallot fake_spoiled_ballot;
  List<PlaintextBallot> more_fake_spoiled_ballots;
  Set<String> selection_ids;
  CiphertextBallot encrypted_fake_cast_ballot;
  CiphertextBallot encrypted_fake_spoiled_ballot;
  List<CiphertextBallot> more_fake_encrypted_ballots;
  List<CiphertextBallot> more_fake_encrypted_spoiled_ballots;
  Map<String, BigInteger> expected_plaintext_tally;
  Tally.CiphertextTally ciphertext_tally;

  public TestDecryptionMediator() {

    this.key_ceremony = new KeyCeremonyMediator(this.CEREMONY_DETAILS);

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
    this.election = election_factory.get_fake_election();
    builder = new ElectionBuilder(this.NUMBER_OF_GUARDIANS, this.QUORUM, this.election);
    assertThat(builder.build()).isEmpty();  // Can't build without the public key
    builder.set_public_key(this.joint_public_key);

    Optional<ElectionBuilder.Tuple> tuple = builder.build();
    assertThat(tuple).isPresent();
    this.metadata = tuple.get().description;
    this.context = tuple.get().context;

    this.encryption_device = new Encrypt.EncryptionDevice("location");
    this.ballot_marking_device = new Encrypt.EncryptionMediator(this.metadata, this.context, this.encryption_device);

    // get some fake ballots
    this.fake_cast_ballot = ballot_factory.get_fake_ballot(this.metadata, "some-unique-ballot-id-cast", true);
    this.more_fake_ballots = new ArrayList<>();
    for (int i = 0; i < 10; i++) {
      this.more_fake_ballots.add(ballot_factory.get_fake_ballot(this.metadata, "some-unique-ballot-id-cast" + 1, true));
    }
    this.fake_spoiled_ballot = ballot_factory.get_fake_ballot(this.metadata, "some-unique-ballot-id-spoiled", true);
    this.more_fake_spoiled_ballots = new ArrayList<>();
    for (int i = 0; i < 2; i++) {
      this.more_fake_spoiled_ballots.add(
              ballot_factory.get_fake_ballot(this.metadata, "some-unique-ballot-id-spoiled" + i, true));
    }

    assertThat(this.fake_cast_ballot.is_valid(this.metadata.ballot_styles.get(0).object_id)).isTrue();
    assertThat(this.fake_spoiled_ballot.is_valid(this.metadata.ballot_styles.get(0).object_id)).isTrue();
    ArrayList<PlaintextBallot> all = new ArrayList<>(this.more_fake_ballots);
    all.add(this.fake_cast_ballot);
    this.expected_plaintext_tally = TestTally.accumulate_plaintext_ballots(all);

    // Fill in the expected values with any missing selections
    // that were not made on any ballots
    this.selection_ids = this.metadata.contests.stream().flatMap(c -> c.ballot_selections.stream())
            .map(s -> s.object_id).collect(Collectors.toSet());

    // missing_selection_ids = selection_ids.difference( set(this.expected_plaintext_tally) )
    Sets.SetView<String> missing_selection_ids = Sets.difference(selection_ids, this.expected_plaintext_tally.keySet());
    for (String id : missing_selection_ids) {
      this.expected_plaintext_tally.put(id, BigInteger.ZERO);
    }

    // Encrypt
    this.encrypted_fake_cast_ballot = this.ballot_marking_device.encrypt(this.fake_cast_ballot).get();
    this.encrypted_fake_spoiled_ballot = this.ballot_marking_device.encrypt(this.fake_spoiled_ballot).get();
    assertThat(this.encrypted_fake_cast_ballot).isNotNull();
    assertThat(this.encrypted_fake_spoiled_ballot).isNotNull();
    assertThat(this.encrypted_fake_cast_ballot.is_valid_encryption(
            this.metadata.description_hash,
            this.joint_public_key,
            this.context.crypto_extended_base_hash))
            .isTrue();

    // encrypt some more fake ballots
    this.more_fake_encrypted_ballots = new ArrayList<>();
    for (PlaintextBallot fake_ballot : this.more_fake_ballots) {
      this.more_fake_encrypted_ballots.add(this.ballot_marking_device.encrypt(fake_ballot).get());
    }
    // encrypt some more fake ballots
    this.more_fake_encrypted_spoiled_ballots = new ArrayList<>();
    for (PlaintextBallot fake_ballot : this.more_fake_spoiled_ballots) {
      this.more_fake_encrypted_spoiled_ballots.add(this.ballot_marking_device.encrypt(fake_ballot).get());
    }

    // configure the ballot box
    DataStore ballot_store = new DataStore();
    BallotBox ballot_box = new BallotBox(this.metadata, this.context, ballot_store);
    ballot_box.cast(this.encrypted_fake_cast_ballot);
    ballot_box.spoil(this.encrypted_fake_spoiled_ballot);

    // Cast some more fake ballots
    for (CiphertextBallot fake_ballot : this.more_fake_encrypted_ballots) {
      ballot_box.cast(fake_ballot);
    }
    // Spoil some more fake ballots
    for (CiphertextBallot fake_ballot : this.more_fake_encrypted_spoiled_ballots) {
      ballot_box.spoil(fake_ballot);
    }

    // generate encrypted tally
    this.ciphertext_tally = Tally.tally_ballots(ballot_store, this.metadata, this.context).get();
  }

  @AfterContainer
  public void tearDown() {
    this.key_ceremony.reset(CeremonyDetails.create(NUMBER_OF_GUARDIANS, QUORUM));
  }

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
            Decryption.compute_decryption_share_for_selection(this.guardians.get(0), first_selection, this.context);
    assertThat(result).isPresent();
  }

  @Example
  public void test_compute_compensated_selection_failure() {
    Tally.CiphertextTallySelection first_selection =
            this.ciphertext_tally.cast.values().stream().flatMap(contest -> contest.tally_selections.values().stream())
                    .findFirst().orElseThrow(RuntimeException::new);

    this.guardians.get(0)._guardian_election_partial_key_backups.remove(this.guardians.get(2).object_id);

    assertThat(this.guardians.get(0).recovery_public_key_for(this.guardians.get(2).object_id)).isEmpty();

    Optional<DecryptionShare.CiphertextCompensatedDecryptionSelection> result =
            Decryption.compute_compensated_decryption_share_for_selection(
                    this.guardians.get(0),
                    this.guardians.get(2).object_id,
                    first_selection,
                    this.context,
                    identity_auxiliary_decrypt);

    assertThat(result).isEmpty();
  }

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
            BigInteger.valueOf(this.guardians.get(0).sequence_order),
            ImmutableList.of(BigInteger.valueOf(this.guardians.get(1).sequence_order)));
    Group.ElementModQ lagrange_1 = ElectionPolynomial.compute_lagrange_coefficient(
            BigInteger.valueOf(this.guardians.get(1).sequence_order),
            ImmutableList.of(BigInteger.valueOf(this.guardians.get(0).sequence_order)));

    System.out.printf("lagrange: sequence_orders: (%s, %s, %s)%n",
            this.guardians.get(0).sequence_order, this.guardians.get(1).sequence_order, this.guardians.get(2).sequence_order);
    System.out.printf("%s%n", lagrange_0);
    System.out.printf("%s%n", lagrange_1);

    // compute their shares
    Optional<DecryptionShare.CiphertextDecryptionSelection> share_0 =
            Decryption.compute_decryption_share_for_selection(this.guardians.get(0), first_selection, this.context);

    Optional<DecryptionShare.CiphertextDecryptionSelection> share_1 =
            Decryption.compute_decryption_share_for_selection(this.guardians.get(1), first_selection, this.context);

    assertThat(share_0).isPresent();
    assertThat(share_1).isPresent();

    // compute compensations shares for the missing guardian
    Optional<DecryptionShare.CiphertextCompensatedDecryptionSelection> compensation_0 =
            Decryption.compute_compensated_decryption_share_for_selection(
                    this.guardians.get(0),
                    this.guardians.get(2).object_id,
                    first_selection,
                    this.context,
                    identity_auxiliary_decrypt);

    Optional<DecryptionShare.CiphertextCompensatedDecryptionSelection> compensation_1 =
            Decryption.compute_compensated_decryption_share_for_selection(
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
            first_selection.ciphertext,
            this.guardians.get(0).recovery_public_key_for(this.guardians.get(2).object_id).get(),
            comp0.share(),
            this.context.crypto_extended_base_hash)).isTrue();

    assertThat(comp1.proof().is_valid(
            first_selection.ciphertext,
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
                    new DecryptionShare.Tuple2(this.guardians.get(0).share_election_public_key().key(), share_0.get()),

                    this.guardians.get(1).object_id,
                    new DecryptionShare.Tuple2(this.guardians.get(1).share_election_public_key().key(), share_1.get()),

                    this.guardians.get(2).object_id,
                    new DecryptionShare.Tuple2(this.guardians.get(2).share_election_public_key().key(), share_2)),
            this.context.crypto_extended_base_hash,
            false);

    System.out.printf("%s%n", result);

    assertThat(result).isPresent();
    BigInteger expected = this.expected_plaintext_tally.get(first_selection.object_id);
    BigInteger actual = result.get().tally;
    assertThat(result.get().tally).isEqualTo(this.expected_plaintext_tally.get(first_selection.object_id));
  }

}
