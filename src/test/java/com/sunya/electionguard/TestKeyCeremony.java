package com.sunya.electionguard;

import net.jqwik.api.Example;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth8.assertThat;
import static com.sunya.electionguard.KeyCeremony.*;

public class TestKeyCeremony {
  private static final String SENDER_GUARDIAN_ID = "Test Guardian 1";
  private static final int SENDER_SEQUENCE_ORDER = 1;
  private static final String RECIPIENT_GUARDIAN_ID = "Test Guardian 2";
  private static final String ALTERNATE_VERIFIER_GUARDIAN_ID = "Test Guardian 3";
  private static final int RECIPIENT_SEQUENCE_ORDER = 2;
  private static final int NUMBER_OF_GUARDIANS = 5;
  private static final int QUORUM = 3;

  static Auxiliary.Decryptor identity_auxiliary_decrypt = (m, k) -> Optional.of(new String(m.getBytes()));
  static Auxiliary.Encryptor identity_auxiliary_encrypt = (m, k) -> Optional.of(new Auxiliary.ByteString(m.getBytes()));

  @Example
  public void test_generate_rsa_auxiliary_key_pair() {
    Auxiliary.KeyPair auxiliary_key_pair = generate_rsa_auxiliary_key_pair();

    assertThat(auxiliary_key_pair).isNotNull();
    assertThat(auxiliary_key_pair.public_key).isNotNull();
    assertThat(auxiliary_key_pair.secret_key).isNotNull();
  }

  @Example
  public void test_generate_election_key_pair() {
    ElectionKeyPair election_key_pair = generate_election_key_pair(NUMBER_OF_GUARDIANS, null);

    assertThat(election_key_pair).isNotNull();
    assertThat(election_key_pair.key_pair().public_key).isNotNull();
    assertThat(election_key_pair.key_pair().secret_key).isNotNull();
    assertThat(election_key_pair.polynomial()).isNotNull();
    assertThat(election_key_pair.proof().is_valid()).isTrue();
    for (SchnorrProof proof : election_key_pair.polynomial().coefficient_proofs) {
      assertThat(proof.is_valid()).isTrue();
    }
  }

  @Example
  public void test_generate_election_partial_key_backup() {
    ElectionKeyPair election_key_pair = generate_election_key_pair(QUORUM, null);
    Auxiliary.KeyPair auxiliary_key_pair = generate_rsa_auxiliary_key_pair();
    Auxiliary.PublicKey auxiliary_public_key = new Auxiliary.PublicKey(
            RECIPIENT_GUARDIAN_ID,
            RECIPIENT_SEQUENCE_ORDER,
            auxiliary_key_pair.public_key);

    Optional<ElectionPartialKeyBackup> backupO = generate_election_partial_key_backup(
            SENDER_GUARDIAN_ID,
            election_key_pair.polynomial(),
            auxiliary_public_key,
            identity_auxiliary_encrypt);
    assertThat(backupO).isPresent();
    ElectionPartialKeyBackup backup = backupO.get();

    assertThat(backup.designated_id()).isEqualTo(RECIPIENT_GUARDIAN_ID);
    assertThat(backup.designated_sequence_order()).isEqualTo(RECIPIENT_SEQUENCE_ORDER);
    assertThat(backup.encrypted_value()).isNotNull();
    assertThat(backup.coefficient_commitments().size()).isEqualTo(QUORUM);
    assertThat(backup.coefficient_proofs().size()).isEqualTo(QUORUM);
    for (SchnorrProof proof : backup.coefficient_proofs()) {
      assertThat(proof.is_valid()).isTrue();
    }
  }

  @Example
  public void test_verify_election_partial_key_backup() {
    Auxiliary.KeyPair recipient_auxiliary_key_pair = generate_rsa_auxiliary_key_pair();
    ElectionKeyPair sender_election_key_pair = generate_election_key_pair(QUORUM, null);
    Auxiliary.PublicKey recipient_auxiliary_public_key = new Auxiliary.PublicKey(
            RECIPIENT_GUARDIAN_ID,
            RECIPIENT_SEQUENCE_ORDER,
            recipient_auxiliary_key_pair.public_key);

    Optional<ElectionPartialKeyBackup> partial_key_backup = generate_election_partial_key_backup(
            SENDER_GUARDIAN_ID,
            sender_election_key_pair.polynomial(),
            recipient_auxiliary_public_key,
            identity_auxiliary_encrypt);

    ElectionPartialKeyVerification verification = verify_election_partial_key_backup(
            RECIPIENT_GUARDIAN_ID,
            partial_key_backup.orElseThrow(),
            recipient_auxiliary_key_pair,
            identity_auxiliary_decrypt);

    assertThat(verification).isNotNull();
    assertThat(verification.owner_id()).isEqualTo(SENDER_GUARDIAN_ID);
    assertThat(verification.designated_id()).isEqualTo(RECIPIENT_GUARDIAN_ID);
    assertThat(verification.verifier_id()).isEqualTo(RECIPIENT_GUARDIAN_ID);
    assertThat(verification.verified()).isTrue();
  }

  @Example
  public void test_generate_election_partial_key_challenge() {
    Auxiliary.KeyPair recipient_auxiliary_key_pair = generate_rsa_auxiliary_key_pair();
    ElectionKeyPair sender_election_key_pair = generate_election_key_pair(QUORUM, null);
    Auxiliary.PublicKey recipient_auxiliary_public_key = new Auxiliary.PublicKey(
            RECIPIENT_GUARDIAN_ID,
            RECIPIENT_SEQUENCE_ORDER,
            recipient_auxiliary_key_pair.public_key);
    Optional<ElectionPartialKeyBackup> partial_key_backup = generate_election_partial_key_backup(
            SENDER_GUARDIAN_ID,
            sender_election_key_pair.polynomial(),
            recipient_auxiliary_public_key,
            identity_auxiliary_encrypt);

    ElectionPartialKeyChallenge challenge = generate_election_partial_key_challenge(
            partial_key_backup.orElseThrow(), sender_election_key_pair.polynomial());

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
    Auxiliary.KeyPair recipient_auxiliary_key_pair = generate_rsa_auxiliary_key_pair();
    ElectionKeyPair sender_election_key_pair = generate_election_key_pair(QUORUM, null);
    Auxiliary.PublicKey recipient_auxiliary_public_key = new Auxiliary.PublicKey(
            RECIPIENT_GUARDIAN_ID,
            RECIPIENT_SEQUENCE_ORDER,
            recipient_auxiliary_key_pair.public_key);
    Optional<ElectionPartialKeyBackup> partial_key_backup = generate_election_partial_key_backup(
            SENDER_GUARDIAN_ID,
            sender_election_key_pair.polynomial(),
            recipient_auxiliary_public_key,
            identity_auxiliary_encrypt);

    ElectionPartialKeyChallenge challenge = generate_election_partial_key_challenge(
            partial_key_backup.orElseThrow(), sender_election_key_pair.polynomial());

    ElectionPartialKeyVerification verification = verify_election_partial_key_challenge(ALTERNATE_VERIFIER_GUARDIAN_ID, challenge);

    assertThat(verification).isNotNull();
    assertThat(verification.owner_id()).isEqualTo(SENDER_GUARDIAN_ID);
    assertThat(verification.designated_id()).isEqualTo(RECIPIENT_GUARDIAN_ID);
    assertThat(verification.verifier_id()).isEqualTo(ALTERNATE_VERIFIER_GUARDIAN_ID);
    assertThat(verification.verified()).isTrue();
  }

  @Example
  public void test_combine_election_public_keys() {
    ElectionKeyPair random_keypair = generate_election_key_pair(QUORUM, null);
    ElectionKeyPair random_keypair_two = generate_election_key_pair(QUORUM, null);
    Map<String, ElectionPublicKey> public_keys = new HashMap<>();
    public_keys.put(
            RECIPIENT_GUARDIAN_ID,
            ElectionPublicKey.create(
                    RECIPIENT_GUARDIAN_ID,
                    RECIPIENT_SEQUENCE_ORDER,
                    random_keypair.proof(),
                    random_keypair.key_pair().public_key));
    public_keys.put(
            SENDER_GUARDIAN_ID,
            ElectionPublicKey.create(
                    SENDER_GUARDIAN_ID,
                    SENDER_SEQUENCE_ORDER,
                    random_keypair_two.proof(),
                    random_keypair_two.key_pair().public_key));

    Group.ElementModP joint_key = combine_election_public_keys(public_keys);

    assertThat(joint_key).isNotNull();
    assertThat(joint_key).isNotEqualTo(random_keypair.key_pair().public_key);
    assertThat(joint_key).isNotEqualTo(random_keypair_two.key_pair().public_key);
  }

}
