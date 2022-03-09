package com.sunya.electionguard.standard;

import com.google.common.collect.Sets;
import com.sunya.electionguard.BallotBox;
import com.sunya.electionguard.BallotFactory;
import com.sunya.electionguard.CiphertextBallot;
import com.sunya.electionguard.ElectionContext;
import com.sunya.electionguard.CiphertextTally;
import com.sunya.electionguard.CiphertextTallyBuilder;
import com.sunya.electionguard.DecryptionShare;
import com.sunya.electionguard.ElectionBuilder;
import com.sunya.electionguard.ElectionFactory;
import com.sunya.electionguard.ElectionTestHelper;
import com.sunya.electionguard.Encrypt;
import com.sunya.electionguard.Group;
import com.sunya.electionguard.InternalManifest;
import com.sunya.electionguard.Manifest;
import com.sunya.electionguard.PlaintextBallot;
import com.sunya.electionguard.PlaintextTally;
import com.sunya.electionguard.SubmittedBallot;
import com.sunya.electionguard.TallyTestHelper;
import com.sunya.electionguard.TestProperties;
import net.jqwik.api.Example;
import net.jqwik.api.Property;
import net.jqwik.api.ShrinkingMode;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth8.assertThat;
import static com.sunya.electionguard.standard.KeyCeremony.CeremonyDetails;

public class TestDecryptionMediator extends TestProperties {
  private static final int NUMBER_OF_GUARDIANS = 3;
  private static final int QUORUM = 2;
  private static final CeremonyDetails CEREMONY_DETAILS = new CeremonyDetails(NUMBER_OF_GUARDIANS, QUORUM);
  private static final Random random = new Random();

  static BallotFactory ballot_factory = new BallotFactory();

  KeyCeremonyMediator key_ceremony_mediator;
  List<Guardian> guardians = new ArrayList<>();
  KeyCeremony.ElectionJointKey joint_public_key;
  Manifest election;
  ElectionContext context;
  InternalManifest metadata;
  Map<String, Integer> expected_plaintext_tally;

  BallotBox ballot_box;
  PlaintextBallot fake_cast_ballot;
  PlaintextBallot fake_spoiled_ballot;
  SubmittedBallot encrypted_fake_cast_ballot;
  SubmittedBallot encrypted_fake_spoiled_ballot;
  CiphertextTally ciphertext_tally;

  List<SubmittedBallot> spoiled_ballots = new ArrayList<>();

  public TestDecryptionMediator() {

    // Key Ceremony
    this.key_ceremony_mediator = new KeyCeremonyMediator("key_ceremony_mediator", CEREMONY_DETAILS);
    for (int i = 0; i < NUMBER_OF_GUARDIANS; i++) {
      int sequence = i + 2;
      this.guardians.add(Guardian.createForTesting("guardian_" + sequence, sequence, NUMBER_OF_GUARDIANS, QUORUM,null));
    }
    KeyCeremonyHelper.perform_full_ceremony(this.guardians, key_ceremony_mediator);
    assertThat(key_ceremony_mediator.publish_joint_key()).isPresent();
    this.joint_public_key = key_ceremony_mediator.publish_joint_key().orElseThrow();

    // setup the election
    Manifest election = ElectionFactory.get_fake_manifest();
    ElectionBuilder builder = new ElectionBuilder(NUMBER_OF_GUARDIANS, QUORUM, election);
    assertThat(builder.build()).isEmpty();  // Can't build without the public key

    builder.set_public_key(this.joint_public_key.joint_public_key());
    builder.set_commitment_hash(this.joint_public_key.commitment_hash());

    ElectionBuilder.DescriptionAndContext tuple = builder.build().orElseThrow();
    this.metadata = tuple.internalManifest;
    this.election = tuple.internalManifest.manifest;
    this.context = tuple.context;

    Encrypt.EncryptionDevice encryption_device = Encrypt.createDeviceForTest("location");
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

    assertThat(this.fake_cast_ballot.is_valid(this.election.ballotStyles().get(0).ballotStyleId())).isTrue();
    assertThat(this.fake_spoiled_ballot.is_valid(this.election.ballotStyles().get(0).ballotStyleId())).isTrue();
    ArrayList<PlaintextBallot> all = new ArrayList<>(more_fake_ballots);
    all.add(this.fake_cast_ballot);

    this.expected_plaintext_tally = TallyTestHelper.accumulate_plaintext_ballots(all);

    // Fill in the expected values with any missing selections
    // that were not made on any ballots
    Set<String> selection_ids = this.election.contests().stream().flatMap(c -> c.selections().stream())
            .map(s -> s.selectionId()).collect(Collectors.toSet());
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
            this.election.cryptoHash(),
            this.joint_public_key.joint_public_key(),
            this.context.cryptoExtendedBaseHash))
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
    CiphertextTallyBuilder tallyb = new CiphertextTallyBuilder("whatever", this.metadata, context);
    tallyb.batch_append(ballot_box.getAcceptedBallotsAsCloseableIterable());
    this.ciphertext_tally = tallyb.build();

    // spoiled ballots
    ballot_box.getSpoiledBallots().forEach(a -> this.spoiled_ballots.add(a));
  }

  @Example
  public void test_announce() {
    DecryptionMediator mediator = new DecryptionMediator("mediator-id", this.context);
    Guardian guardian = this.guardians.get(0);
    KeyCeremony.ElectionPublicKey guardian_key = guardian.share_key();

    DecryptionShare tally_share = guardian.compute_tally_share(this.ciphertext_tally, this.context).orElseThrow();
    Map<String, Optional<DecryptionShare>> ballot_shares =  new HashMap<>();

    mediator.announce(guardian_key, tally_share, ballot_shares);
    assertThat(mediator.get_available_guardians()).hasSize(1);

    // Announce again
    mediator.announce(guardian_key, tally_share, ballot_shares);

    // Can only announce once
    assertThat(mediator.get_available_guardians()).hasSize(1);

    // Cannot get plaintext tally or spoiled ballots without a quorum
    assertThat(mediator.get_plaintext_tally(this.ciphertext_tally)).isEmpty();
    assertThat(mediator.get_plaintext_ballots(this.spoiled_ballots)).isEmpty();
  }

  @Example
  public void test_get_plaintext_with_all_guardians_present() {
    DecryptionMediator mediator = new DecryptionMediator("mediator-id", this.context);

    DecryptionHelper.perform_decryption_setup(
            this.guardians,
            mediator,
            this.context,
            this.ciphertext_tally,
            this.spoiled_ballots);

    PlaintextTally plaintext_tally = mediator.get_plaintext_tally(this.ciphertext_tally).orElseThrow();
    Map<String, PlaintextTally> plaintext_ballots = mediator.get_plaintext_ballots(this.spoiled_ballots).orElseThrow();
    assertThat(plaintext_ballots).hasSize(this.spoiled_ballots.size());

    Map<String, Integer> selections = convert_to_selections(plaintext_tally);
    assertThat(selections).isEqualTo(this.expected_plaintext_tally);

    // Verify we get the same tally back if we call again
    PlaintextTally another_plaintext_tally = mediator.get_plaintext_tally(this.ciphertext_tally).orElseThrow();
    assertThat(another_plaintext_tally).isEqualTo(plaintext_tally);
  }

  @Example
  public void test_get_plaintext_with_a_missing_guardian() {
    DecryptionMediator mediator = new DecryptionMediator("mediator-id", this.context);

    List<Guardian> available_guardians = this.guardians.subList(0, 2);
    List<KeyCeremony.ElectionPublicKey> all_guardian_keys = this.guardians.stream().map(Guardian::share_key).toList();

    DecryptionHelper.perform_compensated_decryption_setup(
            available_guardians,
            all_guardian_keys,
            mediator,
            this.context,
            this.ciphertext_tally,
            this.spoiled_ballots);

    PlaintextTally plaintext_tally = mediator.get_plaintext_tally(this.ciphertext_tally).orElseThrow();
    Map<String, PlaintextTally> plaintext_ballots = mediator.get_plaintext_ballots(this.spoiled_ballots).orElseThrow();
    assertThat(plaintext_ballots).hasSize(this.spoiled_ballots.size());

    // Convert to selections to check for the same tally
    Map<String, Integer> selections = convert_to_selections(plaintext_tally);
    assertThat(selections).isEqualTo(this.expected_plaintext_tally);

    // Verify we get the same tally back if we call again
    PlaintextTally another_plaintext_tally = mediator.get_plaintext_tally(this.ciphertext_tally).orElseThrow();
    assertThat(another_plaintext_tally).isEqualTo(plaintext_tally);
  }

  /* @settings(
        deadline=timedelta(milliseconds=15000),
        suppress_health_check=[HealthCheck.too_slow],
        max_examples=8,
        # disabling the "shrink" phase, because it runs very slowly
        phases=[Phase.explicit, Phase.reuse, Phase.generate, Phase.target],
    )
    @given(data(), integers(1, 3), integers(2, 5))
    def test_get_plaintext_tally_with_all_guardians_present(
            self, values, parties: int, contests: int
    ):
  */
  @Property(tries = 8, shrinking = ShrinkingMode.OFF)
  public void test_get_plaintext_tally_with_all_guardians_present() {

    ElectionBuilder builder = new ElectionBuilder(NUMBER_OF_GUARDIANS, QUORUM, this.election);
    ElectionBuilder.DescriptionAndContext desc = builder.set_public_key(this.joint_public_key.joint_public_key())
            .set_commitment_hash(this.joint_public_key.commitment_hash())
            .build().orElseThrow();

    InternalManifest imanifest = new InternalManifest(this.election);
    ElectionTestHelper electionTestHelper = new ElectionTestHelper(random);
    int nballots = random.nextInt(3) + 3;
    List<PlaintextBallot> plaintext_ballots = electionTestHelper.plaintext_voted_ballots(imanifest, nballots);
    Map<String, Integer> expected_plaintext_tally = TallyTestHelper.accumulate_plaintext_ballots(plaintext_ballots);

    CiphertextTally encrypted_tally = this.generate_encrypted_tally(desc.internalManifest, desc.context, plaintext_ballots);

    DecryptionMediator mediator = new DecryptionMediator("test_get_plaintext_tally_with_all_guardians_present", desc.context);
    DecryptionHelper.perform_decryption_setup(this.guardians, mediator, desc.context, encrypted_tally, new ArrayList<>());

    PlaintextTally plaintext_tally = mediator.get_plaintext_tally(encrypted_tally).orElseThrow();
    Map<String, Integer> selections = convert_to_selections(plaintext_tally);
    assertThat(selections).isEqualTo(expected_plaintext_tally);
  }

  private CiphertextTally generate_encrypted_tally(
          InternalManifest imanifest,
          ElectionContext context,
          List<PlaintextBallot> ballots) {

    // encrypt each ballot
    BallotBox ballot_box = new BallotBox(imanifest.manifest, context);
    for (PlaintextBallot ballot : ballots) {
      Optional<CiphertextBallot> encrypted_ballot = Encrypt.encrypt_ballot(
              ballot, imanifest, context, Group.ONE_MOD_Q, Optional.empty(), true);
      assertThat(encrypted_ballot).isPresent();
      ballot_box.cast(encrypted_ballot.get());
    }

    CiphertextTallyBuilder ciphertext_tally = new CiphertextTallyBuilder("generate_encrypted_tally", imanifest, context);
    ciphertext_tally.batch_append(ballot_box.getAcceptedBallotsAsCloseableIterable());
    return ciphertext_tally.build();
  }

  private Map<String, Integer> convert_to_selections(PlaintextTally tally) {
    Map<String, Integer> plaintext_selections = new HashMap<>();
    for (PlaintextTally.Contest contest : tally.contests.values()) {
      for (Map.Entry<String, PlaintextTally.Selection> entry : contest.selections().entrySet()) {
        plaintext_selections.put(entry.getKey(), entry.getValue().tally());
      }
    }

    return plaintext_selections;
  }

}
