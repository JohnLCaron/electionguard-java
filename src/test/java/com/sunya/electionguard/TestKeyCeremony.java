package com.sunya.electionguard;

import com.google.common.collect.ImmutableList;
import net.jqwik.api.Example;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

import static com.google.common.truth.Truth.assertThat;
import static com.sunya.electionguard.standard.KeyCeremony.*;

public class TestKeyCeremony {
  private static final String SENDER_GUARDIAN_ID = "Test Guardian 1";
  private static final int SENDER_SEQUENCE_ORDER = 1;
  private static final String RECIPIENT_GUARDIAN_ID = "Test Guardian 2";
  private static final String ALTERNATE_VERIFIER_GUARDIAN_ID = "Test Guardian 3";
  private static final int RECIPIENT_SEQUENCE_ORDER = 2;
  private static final int QUORUM = 3;

  @Example
  public void test_generate_election_key_pair() {
    ElectionKeyPair election_key_pair = generate_election_key_pair("owner", 42, QUORUM, null);

    assertThat(election_key_pair).isNotNull();
    assertThat(election_key_pair.owner_id()).isEqualTo("owner");
    assertThat(election_key_pair.sequence_order()).isEqualTo(42);
    assertThat(election_key_pair.key_pair().public_key).isNotNull();
    assertThat(election_key_pair.key_pair().secret_key).isNotNull();
    assertThat(election_key_pair.polynomial()).isNotNull();
    for (SchnorrProof proof : election_key_pair.polynomial().coefficient_proofs) {
      assertThat(proof.is_valid()).isTrue();
    }
  }

  @Example
  public void test_generate_election_partial_key_backup() {
    ElectionKeyPair election_key_pair = generate_election_key_pair("owner", 42, QUORUM, null);
    ElectionKeyPair sender_election_key_pair = generate_election_key_pair(SENDER_GUARDIAN_ID, SENDER_SEQUENCE_ORDER, QUORUM, null);

    ElectionPublicKey designated_guardian_key = ElectionPublicKey.create(
            RECIPIENT_GUARDIAN_ID,
            RECIPIENT_SEQUENCE_ORDER,
            sender_election_key_pair.key_pair().public_key,
            new ArrayList<>(),
            new ArrayList<>());

    ElectionPartialKeyBackup backup = generate_election_partial_key_backup(
            SENDER_GUARDIAN_ID,
            election_key_pair.polynomial(),
            designated_guardian_key);

    assertThat(backup.designated_id()).isEqualTo(RECIPIENT_GUARDIAN_ID);
    assertThat(backup.designated_sequence_order()).isEqualTo(RECIPIENT_SEQUENCE_ORDER);
    assertThat(backup.value()).isNotNull();
  }

  @Example
  public void test_verify_election_partial_key_backup() {
    ElectionKeyPair sender_election_key_pair = generate_election_key_pair(SENDER_GUARDIAN_ID, SENDER_SEQUENCE_ORDER, QUORUM, null);

    ElectionPublicKey publicKey = ElectionPublicKey.create(
            RECIPIENT_GUARDIAN_ID,
            RECIPIENT_SEQUENCE_ORDER,
            sender_election_key_pair.key_pair().public_key,
            new ArrayList<>(),
            new ArrayList<>());

    ElectionPartialKeyBackup partial_key_backup = generate_election_partial_key_backup(
            SENDER_GUARDIAN_ID,
            sender_election_key_pair.polynomial(),
            publicKey);

    ElectionPartialKeyVerification verification = verify_election_partial_key_backup(
            RECIPIENT_GUARDIAN_ID,
            partial_key_backup,
            sender_election_key_pair.share());

    assertThat(verification).isNotNull();
    assertThat(verification.owner_id()).isEqualTo(SENDER_GUARDIAN_ID);
    assertThat(verification.designated_id()).isEqualTo(RECIPIENT_GUARDIAN_ID);
    assertThat(verification.verifier_id()).isEqualTo(RECIPIENT_GUARDIAN_ID);
    assertThat(verification.verified()).isTrue();
  }

  @Example
  public void test_generate_election_partial_key_challenge() {
    ElectionKeyPair sender_election_key_pair = generate_election_key_pair(SENDER_GUARDIAN_ID, 2, QUORUM, null);
    ElectionPublicKey publicKey = ElectionPublicKey.create(
            RECIPIENT_GUARDIAN_ID,
            RECIPIENT_SEQUENCE_ORDER,
            sender_election_key_pair.key_pair().public_key,
            new ArrayList<>(),
            new ArrayList<>());
    ElectionPartialKeyBackup partial_key_backup = generate_election_partial_key_backup(
            SENDER_GUARDIAN_ID,
            sender_election_key_pair.polynomial(),
            publicKey);

    ElectionPartialKeyChallenge challenge = generate_election_partial_key_challenge(
            partial_key_backup, sender_election_key_pair.polynomial());

    assertThat(challenge).isNotNull();
    assertThat(challenge.designated_id()).isEqualTo(RECIPIENT_GUARDIAN_ID);
    assertThat(challenge.designated_sequence_order()).isEqualTo(RECIPIENT_SEQUENCE_ORDER);
    assertThat(challenge.value()).isNotNull();
    assertThat(challenge.coefficient_commitments().size()).isEqualTo(QUORUM);
    assertThat(challenge.coefficient_proofs().size()).isEqualTo(QUORUM);
    for (SchnorrProof proof : challenge.coefficient_proofs()) {
      assertThat(proof.is_valid()).isTrue();
    }
  }

  @Example
  public void test_verify_election_partial_key_challenge() {
    ElectionKeyPair sender_election_key_pair = generate_election_key_pair(SENDER_GUARDIAN_ID, 2, QUORUM, null);
    ElectionPublicKey publicKey = ElectionPublicKey.create(
            RECIPIENT_GUARDIAN_ID,
            RECIPIENT_SEQUENCE_ORDER,
            sender_election_key_pair.key_pair().public_key,
            new ArrayList<>(),
            new ArrayList<>());
    ElectionPartialKeyBackup partial_key_backup = generate_election_partial_key_backup(
            SENDER_GUARDIAN_ID,
            sender_election_key_pair.polynomial(),
            publicKey);

    ElectionPartialKeyChallenge challenge = generate_election_partial_key_challenge(
            partial_key_backup, sender_election_key_pair.polynomial());

    ElectionPartialKeyVerification verification = verify_election_partial_key_challenge(ALTERNATE_VERIFIER_GUARDIAN_ID, challenge);

    assertThat(verification).isNotNull();
    assertThat(verification.owner_id()).isEqualTo(SENDER_GUARDIAN_ID);
    assertThat(verification.designated_id()).isEqualTo(RECIPIENT_GUARDIAN_ID);
    assertThat(verification.verifier_id()).isEqualTo(ALTERNATE_VERIFIER_GUARDIAN_ID);
    assertThat(verification.verified()).isTrue();
  }

  @Example
  public void test_combine_election_public_keys() {

    List<ElectionPublicKey> public_keys = new ArrayList<>();
    public_keys.add(ElectionPublicKey.create(
                    RECIPIENT_GUARDIAN_ID,
                    RECIPIENT_SEQUENCE_ORDER,
                    Group.TWO_MOD_P,
                    ImmutableList.of(Group.TWO_MOD_P, Group.TWO_MOD_P), new ArrayList<>()));
    public_keys.add(
            ElectionPublicKey.create(
                    SENDER_GUARDIAN_ID,
                    SENDER_SEQUENCE_ORDER,
                    Group.TWO_MOD_P,
                    ImmutableList.of(Group.TWO_MOD_P, Group.TWO_MOD_P), new ArrayList<>()));

    ElectionJointKey joint_key = combine_election_public_keys(public_keys);
    assertThat(joint_key).isNotNull();

    Group.ElementModP expected_key = Group.int_to_p_unchecked(BigInteger.valueOf(4));
    assertThat(joint_key.joint_public_key()).isEqualTo(expected_key);

    Group.ElementModQ expected_hash = Hash.hash_elems(
            ImmutableList.of(Group.TWO_MOD_P, Group.TWO_MOD_P, Group.TWO_MOD_P, Group.TWO_MOD_P));
    assertThat(joint_key.commitment_hash()).isEqualTo(expected_hash);

  }

}
