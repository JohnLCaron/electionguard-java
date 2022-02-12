package com.sunya.electionguard.standard;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Sets;
import com.sunya.electionguard.BallotBox;
import com.sunya.electionguard.BallotFactory;
import com.sunya.electionguard.CiphertextBallot;
import com.sunya.electionguard.CiphertextElectionContext;
import com.sunya.electionguard.CiphertextTally;
import com.sunya.electionguard.CiphertextTallyBuilder;
import com.sunya.electionguard.DecryptWithShares;
import com.sunya.electionguard.DecryptionShare;
import com.sunya.electionguard.ElGamal;
import com.sunya.electionguard.ElectionBuilder;
import com.sunya.electionguard.ElectionFactory;
import com.sunya.electionguard.ElectionPolynomial;
import com.sunya.electionguard.Encrypt;
import com.sunya.electionguard.InternalManifest;
import com.sunya.electionguard.Manifest;
import com.sunya.electionguard.PlaintextBallot;
import com.sunya.electionguard.PlaintextTally;
import com.sunya.electionguard.SubmittedBallot;
import com.sunya.electionguard.TallyTestHelper;
import com.sunya.electionguard.TestProperties;
import net.jqwik.api.Example;

import java.util.*;
import java.util.stream.Collectors;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth8.assertThat;

import static com.sunya.electionguard.Group.*;
import static com.sunya.electionguard.standard.KeyCeremony.*;

// LOOK why is this so slow?
public class TestDecryption extends TestProperties {
  private static final int NUMBER_OF_GUARDIANS = 3;
  private static final int QUORUM = 2;
  private static final CeremonyDetails CEREMONY_DETAILS = new CeremonyDetails(NUMBER_OF_GUARDIANS, QUORUM);

  static BallotFactory ballot_factory = new BallotFactory();

  KeyCeremonyMediator key_ceremony_mediator;
  List<Guardian> guardians = new ArrayList<>();
  ElectionJointKey joint_public_key;
  Manifest election;
  InternalManifest metadata;

  CiphertextElectionContext context;
  PlaintextBallot fake_cast_ballot;
  PlaintextBallot fake_spoiled_ballot;
  SubmittedBallot encrypted_fake_cast_ballot;
  SubmittedBallot encrypted_fake_spoiled_ballot;
  Map<String, Integer> expected_plaintext_tally;
  CiphertextTally publishedTally;
  BallotBox ballot_box;

  public TestDecryption() {
    this.key_ceremony_mediator = new KeyCeremonyMediator("key_ceremony_mediator_mediator", CEREMONY_DETAILS);

    // Setup Guardians
    for (int i = 0; i < NUMBER_OF_GUARDIANS; i++) {
      int sequence = i + 2;
      guardians.add(Guardian.createForTesting("guardian_" + sequence, sequence, NUMBER_OF_GUARDIANS, QUORUM, null));
    }
    KeyCeremonyHelper.perform_full_ceremony(guardians, key_ceremony_mediator);

    Optional<ElectionJointKey> joinKeyO = this.key_ceremony_mediator.publish_joint_key();
    assertThat(joinKeyO).isPresent();
    this.joint_public_key = joinKeyO.get();

    // setup the election
    Manifest election = ElectionFactory.get_fake_manifest();
    ElectionBuilder builder = new ElectionBuilder(NUMBER_OF_GUARDIANS, QUORUM, election);
    assertThat(builder.build()).isEmpty();  // Can't build without the public key

    builder.set_public_key(this.joint_public_key.joint_public_key());
    builder.set_commitment_hash(this.joint_public_key.commitment_hash());
    ElectionBuilder.DescriptionAndContext tuple = builder.build().orElseThrow();
    this.election = tuple.internalManifest.manifest;
    this.context = tuple.context;
    this.metadata = tuple.internalManifest;

    Encrypt.EncryptionDevice encryption_device = Encrypt.EncryptionDevice.createForTest("location");
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

    assertThat(this.fake_cast_ballot.is_valid(this.election.ballot_styles.get(0).object_id())).isTrue();
    assertThat(this.fake_spoiled_ballot.is_valid(this.election.ballot_styles.get(0).object_id())).isTrue();
    ArrayList<PlaintextBallot> all = new ArrayList<>(more_fake_ballots);
    all.add(this.fake_cast_ballot);
    this.expected_plaintext_tally = TallyTestHelper.accumulate_plaintext_ballots(all);

    // Fill in the expected values with any missing selections
    // that were not made on any ballots
    Set<String> selection_ids = this.election.contests.stream().flatMap(c -> c.ballot_selections.stream())
            .map(s -> s.object_id).collect(Collectors.toSet());
    // missing_selection_ids = selection_ids.difference( set(this.expected_plaintext_tally) )
    Sets.SetView<String> missing_selection_ids = Sets.difference(selection_ids, this.expected_plaintext_tally.keySet());
    for (String id : missing_selection_ids) {
      this.expected_plaintext_tally.put(id, 0);
    }

    // Encrypt
    CiphertextBallot temp_encrypted_fake_cast_ballot = ballot_marking_device.encrypt(this.fake_cast_ballot).orElseThrow();
    CiphertextBallot temp_encrypted_fake_spoiled_ballot = ballot_marking_device.encrypt(this.fake_spoiled_ballot).orElseThrow();
    assertThat(temp_encrypted_fake_cast_ballot).isNotNull();
    assertThat(temp_encrypted_fake_spoiled_ballot).isNotNull();
    assertThat(temp_encrypted_fake_cast_ballot.is_valid_encryption(
            this.election.crypto_hash(),
            this.joint_public_key.joint_public_key(),
            this.context.crypto_extended_base_hash))
            .isTrue();

    // encrypt some more fake ballots
    List<CiphertextBallot> more_fake_encrypted_ballots = new ArrayList<>();
    for (PlaintextBallot fake_ballot : more_fake_ballots) {
      more_fake_encrypted_ballots.add(ballot_marking_device.encrypt(fake_ballot).orElseThrow());
    }
    // encrypt some more fake ballots
    List<CiphertextBallot> more_fake_encrypted_spoiled_ballots = new ArrayList<>();
    for (PlaintextBallot fake_ballot : more_fake_spoiled_ballots) {
      more_fake_encrypted_spoiled_ballots.add(ballot_marking_device.encrypt(fake_ballot).orElseThrow());
    }

    // configure the ballot box
    this.ballot_box = new BallotBox(this.election, this.context);
    this.encrypted_fake_cast_ballot = ballot_box.cast(temp_encrypted_fake_cast_ballot).orElseThrow();
    this.encrypted_fake_spoiled_ballot = ballot_box.spoil(temp_encrypted_fake_spoiled_ballot).orElseThrow();

    // Cast some more fake ballots
    for (CiphertextBallot fake_ballot : more_fake_encrypted_ballots) {
      ballot_box.cast(fake_ballot);
    }
    // Spoil some more fake ballots
    for (CiphertextBallot fake_ballot : more_fake_encrypted_spoiled_ballots) {
      ballot_box.spoil(fake_ballot);
    }

    // generate encrypted tally
    CiphertextTallyBuilder ciphertext_tally = new CiphertextTallyBuilder("some-id", metadata, this.context);
    ciphertext_tally.batch_append(ballot_box.getAcceptedBallotsAsCloseableIterable());
    this.publishedTally = ciphertext_tally.build();
  }

  @Example
  public void test_compute_decryption_share() {
    Guardian guardian = this.guardians.get(0);

    // Guardian doesn't give keys
    ElementModQ broken_secret_key = ZERO_MOD_Q;
    ElectionKeyPair broken_guardian_key_pair = new ElectionKeyPair(
            guardian.object_id,
            guardian.sequence_order,
            new ElGamal.KeyPair(broken_secret_key, guardian.election_keys().key_pair().public_key),
            guardian.election_keys().polynomial());

    Optional<DecryptionShare> broken_share = Decryptions.compute_decryption_share(
            broken_guardian_key_pair,
            this.publishedTally,
            this.context);

    assertThat(broken_share).isEmpty();

    // Normal use case
    Optional<DecryptionShare> share = Decryptions.compute_decryption_share(
            guardian.election_keys(),
            this.publishedTally,
            this.context);
    assertThat(share).isPresent();
  }

  @Example
  public void test_compute_compensated_decryption_share() {
    Guardian guardian = this.guardians.get(0);
    Guardian missing_guardian = this.guardians.get(2);

    ElectionPublicKey public_key = guardian.share_key();
    ElectionPublicKey missing_guardian_public_key = missing_guardian.share_key();
    ElectionPartialKeyBackup missing_guardian_backup =
            missing_guardian.share_election_partial_key_backup(guardian.object_id).orElseThrow();

    Optional<DecryptionShare.CompensatedDecryptionShare> share = Decryptions.compute_compensated_decryption_share(
            public_key,
            missing_guardian_public_key,
            missing_guardian_backup,
            this.publishedTally,
            this.context);

    assertThat(share).isPresent();
  }

  @Example
  public void test_compute_selection() {
    CiphertextTally.Selection first_selection =
            this.publishedTally.contests.values().stream().flatMap(contest -> contest.selections.values().stream())
                    .findFirst().orElseThrow(RuntimeException::new);

    Optional<DecryptionShare.CiphertextDecryptionSelection> result =
            Decryptions.compute_decryption_share_for_selection(this.guardians.get(0).election_keys(), first_selection, this.context);
    assertThat(result).isPresent();
  }

  @Example
  public void test_compute_compensated_selection() {
    Guardian available_guardian_1 = this.guardians.get(0);
    Guardian available_guardian_2 = this.guardians.get(1);
    Guardian missing_guardian = this.guardians.get(2);

    ElectionPublicKey available_guardian_1_key = available_guardian_1.share_key();
    ElectionPublicKey available_guardian_2_key = available_guardian_2.share_key();
    ElectionPublicKey missing_guardian_key = missing_guardian.share_key();
            
    CiphertextTally.Selection first_selection =
            this.publishedTally.contests.values().stream().flatMap(contest -> contest.selections.values().stream())
                    .findFirst().orElseThrow(RuntimeException::new);

    // Compute lagrange coefficients for the guardians that are present
    ElementModQ lagrange_0 = ElectionPolynomial.compute_lagrange_coefficient(
            available_guardian_1.sequence_order,
            ImmutableList.of(available_guardian_2.sequence_order));
    ElementModQ lagrange_1 = ElectionPolynomial.compute_lagrange_coefficient(
            available_guardian_2.sequence_order,
            ImmutableList.of(available_guardian_1.sequence_order));

    System.out.printf("lagrange: sequence_orders: (%d, %d, %d)%n",
            available_guardian_1.sequence_order, available_guardian_2.sequence_order, missing_guardian.sequence_order);
    System.out.printf("%s%n", lagrange_0);
    System.out.printf("%s%n", lagrange_1);

    // compute their shares
    Optional<DecryptionShare.CiphertextDecryptionSelection> share_0 =
            Decryptions.compute_decryption_share_for_selection(available_guardian_1.election_keys(), first_selection, this.context);

    Optional<DecryptionShare.CiphertextDecryptionSelection> share_1 =
            Decryptions.compute_decryption_share_for_selection(available_guardian_2.election_keys(), first_selection, this.context);

    assertThat(share_0).isPresent();
    assertThat(share_1).isPresent();

    // compute compensations shares for the missing guardian
    Optional<DecryptionShare.CiphertextCompensatedDecryptionSelection> compensation_0 =
            Decryptions.compute_compensated_decryption_share_for_selection(
                    available_guardian_1.share_key(),
                    missing_guardian.share_key(),
                    missing_guardian.share_election_partial_key_backup(available_guardian_1.object_id).orElseThrow(),
                    first_selection,
                    this.context);

    Optional<DecryptionShare.CiphertextCompensatedDecryptionSelection> compensation_1 =
            Decryptions.compute_compensated_decryption_share_for_selection(
                    available_guardian_2.share_key(),
                    missing_guardian.share_key(),
                    missing_guardian.share_election_partial_key_backup(available_guardian_2.object_id).orElseThrow(),
                    first_selection,
                    this.context);

    assertThat(compensation_0).isPresent();
    assertThat(compensation_1).isPresent();

    DecryptionShare.CiphertextCompensatedDecryptionSelection comp0 = compensation_0.get();
    DecryptionShare.CiphertextCompensatedDecryptionSelection comp1 = compensation_1.get();
    System.out.printf("%nSHARES: %s%n %s%n", compensation_0, compensation_1);

    // Check the share proofs
    assertThat(comp0.proof().is_valid(
            first_selection.ciphertext(),
            Decryptions.compute_recovery_public_key(available_guardian_1_key, missing_guardian_key),
            comp0.share(),
            this.context.crypto_extended_base_hash)).isTrue();

    assertThat(comp1.proof().is_valid(
            first_selection.ciphertext(),
            Decryptions.compute_recovery_public_key(available_guardian_2_key, missing_guardian_key),
            comp1.share(),
            this.context.crypto_extended_base_hash)).isTrue();

    List<ElementModP> share_pow_p = ImmutableList.of(
            pow_p(comp0.share(), lagrange_0), 
            pow_p(comp1.share(), lagrange_1));
    System.out.printf("%nSHARE_POW_P%n%s%n", share_pow_p);

    // reconstruct the missing share from the compensation shares
    ElementModP reconstructed_share = mult_p(share_pow_p);
    System.out.printf("%nRECONSTRUCTED SHARE%s%n", reconstructed_share);

    DecryptionShare.CiphertextDecryptionSelection share_2 = DecryptionShare.create_ciphertext_decryption_selection(
            first_selection.object_id(),
            this.guardians.get(2).object_id,
            reconstructed_share,
            Optional.empty(),
            Optional.of(ImmutableMap.of(
                    available_guardian_1.object_id, comp0,
                    available_guardian_2.object_id, comp1))
    );

    // Decrypt the result
    Optional<PlaintextTally.Selection> result = DecryptWithShares.decrypt_selection_with_decryption_shares(
            first_selection,
            ImmutableMap.of(
                    this.guardians.get(0).object_id,
                    new DecryptionShare.KeyAndSelection(available_guardian_1.share_key().key(), share_0.get()),

                    this.guardians.get(1).object_id,
                    new DecryptionShare.KeyAndSelection(available_guardian_2.share_key().key(), share_1.get()),

                    this.guardians.get(2).object_id,
                    new DecryptionShare.KeyAndSelection(missing_guardian.share_key().key(), share_2)),
            this.context.crypto_extended_base_hash,
            false);

    System.out.printf("%s%n", result);

    assertThat(result).isPresent();
    Integer expected = this.expected_plaintext_tally.get(first_selection.object_id());
    Integer actual = result.get().tally();
    assertThat(actual).isEqualTo(expected);
  }
  
  @Example
  public void test_compute_compensated_selection_failure() {
    Guardian available_guardian = this.guardians.get(0);
    Guardian missing_guardian = this.guardians.get(2);

    CiphertextTally.Selection first_selection =
            this.publishedTally.contests.values().stream().flatMap(contest -> contest.selections.values().stream())
                    .findFirst().orElseThrow(RuntimeException::new);
    
    // Get backup for missing guardian instead of one sent by guardian
    ElectionPartialKeyBackup incorrect_backup
            = available_guardian.share_election_partial_key_backup(missing_guardian.object_id).orElseThrow();
    
    Optional<DecryptionShare.CiphertextCompensatedDecryptionSelection> result =
            Decryptions.compute_compensated_decryption_share_for_selection(
                    available_guardian.share_key(),
                    missing_guardian.share_key(),
                    incorrect_backup,
                    first_selection,
                    this.context);

    assertThat(result).isEmpty();
  }

  @Example
  public void test_reconstruct_decryption_share() {
    List<Guardian> available_guardians = this.guardians.subList(0,2);
    Guardian missing_guardian = this.guardians.get(2);

    List<ElectionPublicKey> available_guardians_keys =
            available_guardians.stream().map(Guardian::share_key).collect(Collectors.toList());
    ElectionPublicKey missing_guardian_key = missing_guardian.share_key();
    Map<String, ElectionPartialKeyBackup> missing_guardian_backups = missing_guardian.share_election_partial_key_backups().stream()
            .collect(Collectors.toMap(b -> b.designated_id(), b -> b));
  
    CiphertextTally tally = this.publishedTally;

    Map<String, DecryptionShare.CompensatedDecryptionShare> compensated_shares =
            available_guardians.stream().collect(Collectors.toMap(
                    g -> g.object_id,
                    g -> Decryptions.compute_compensated_decryption_share(
                            g.share_key(),
                            missing_guardian_key,
                            missing_guardian_backups.get(g.object_id),
                            tally,
                            this.context).orElseThrow())
            );

    Map<String, ElementModQ> lagrange_coefficients =
            Decryptions.compute_lagrange_coefficients_for_guardians(available_guardians_keys);

    DecryptionShare share =
            Decryptions.reconstruct_decryption_share(missing_guardian_key, tally, compensated_shares, lagrange_coefficients);

    assertThat(compensated_shares.size()).isEqualTo(QUORUM);
    assertThat(lagrange_coefficients.size()).isEqualTo(QUORUM);
    assertThat(share).isNotNull();
  }

  @Example
  public void test_reconstruct_decryption_shares_for_ballot() {
    List<Guardian> available_guardians = this.guardians.subList(0,2);
    List<ElectionPublicKey> available_guardians_keys =
            available_guardians.stream().map(Guardian::share_key).collect(Collectors.toList());

    Guardian missing_guardian = this.guardians.get(2);
    ElectionPublicKey missing_guardian_key = missing_guardian.share_key();
    Map<String, ElectionPartialKeyBackup> missing_guardian_backups = missing_guardian.share_election_partial_key_backups().stream()
            .collect(Collectors.toMap(b -> b.designated_id(), b -> b));

    SubmittedBallot ballot = encrypted_fake_cast_ballot;

    Map<String, DecryptionShare.CompensatedDecryptionShare> compensated_ballot_shares = new HashMap<>();
    for (Guardian available_guardian : available_guardians) {
      Optional<DecryptionShare.CompensatedDecryptionShare> compensated_share = Decryptions.compute_compensated_decryption_share_for_ballot(
              available_guardian.share_key(),
              missing_guardian_key,
              missing_guardian_backups.get(available_guardian.object_id),
              ballot,
              this.context);
      compensated_share.ifPresent(share -> compensated_ballot_shares.put(available_guardian.object_id, share));
    }

    Map<String, ElementModQ> lagrange_coefficients = Decryptions.compute_lagrange_coefficients_for_guardians(available_guardians_keys);

    DecryptionShare missing_ballot_share = Decryptions.reconstruct_decryption_share_for_ballot(
            missing_guardian_key,
            ballot,
            compensated_ballot_shares,
            lagrange_coefficients);

    assertThat(lagrange_coefficients.size()).isEqualTo(QUORUM);
    assertThat(compensated_ballot_shares.size()).isEqualTo(available_guardians.size());
    assertThat(lagrange_coefficients.size()).isEqualTo(available_guardians.size());
    assertThat(missing_ballot_share).isNotNull();
  }

  @Example
  public void test_reconstruct_decryption_share_for_spoiled_ballot() {
    List<Guardian> available_guardians = this.guardians.subList(0,2);
    List<ElectionPublicKey> available_guardians_keys =
            available_guardians.stream().map(Guardian::share_key).collect(Collectors.toList());

    Guardian missing_guardian = this.guardians.get(2);
    ElectionPublicKey missing_guardian_key = missing_guardian.share_key();
    Map<String, ElectionPartialKeyBackup> missing_guardian_backups = missing_guardian.share_election_partial_key_backups().stream()
            .collect(Collectors.toMap(b -> b.designated_id(), b -> b));

    SubmittedBallot ballot = encrypted_fake_spoiled_ballot;

    Map<String, DecryptionShare.CompensatedDecryptionShare> compensated_shares = new HashMap<>();
    for (Guardian available_guardian : available_guardians) {
      Optional<DecryptionShare.CompensatedDecryptionShare> compensated_share = Decryptions.compute_compensated_decryption_share_for_ballot(
              available_guardian.share_key(),
              missing_guardian_key,
              missing_guardian_backups.get(available_guardian.object_id),
              ballot,
              this.context);
      compensated_share.ifPresent(share -> compensated_shares.put(available_guardian.object_id, share));
    }

    Map<String, ElementModQ> lagrange_coefficients = Decryptions.compute_lagrange_coefficients_for_guardians(available_guardians_keys);

    DecryptionShare missing_ballot_share = Decryptions.reconstruct_decryption_share_for_ballot(
            missing_guardian_key,
            ballot,
            compensated_shares,
            lagrange_coefficients);

    assertThat(lagrange_coefficients.size()).isEqualTo(QUORUM);
    assertThat(compensated_shares.size()).isEqualTo(available_guardians.size());
    assertThat(lagrange_coefficients.size()).isEqualTo(available_guardians.size());
    assertThat(missing_ballot_share).isNotNull();
  }

}
