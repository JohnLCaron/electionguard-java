package com.sunya.electionguard;

import com.google.common.collect.ImmutableList;
import com.google.common.truth.Truth;
import org.junit.Test;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth8.assertThat;

import static com.sunya.electionguard.ChaumPedersen.*;
import static com.sunya.electionguard.Encrypt.*;
import static com.sunya.electionguard.ElGamal.*;
import static com.sunya.electionguard.Group.*;
import static org.junit.Assert.fail;

public class TestEncrypt {

  static ElectionFactory election_factory = new ElectionFactory();
  static BallotFactory ballot_factory = new BallotFactory();

  @Test
  public void test_encrypt_simple_selection_succeeds() {
    KeyPair keypair = elgamal_keypair_from_secret(int_to_q(BigInteger.TWO).get()).get();
    ElementModQ nonce = TestUtils.elements_mod_q();

    Election.SelectionDescription metadata = new Election.SelectionDescription("some-selection-object-id", "some-candidate-id", 1);
    ElementModQ hash_context = metadata.crypto_hash();

    Ballot.PlaintextBallotSelection subject = selection_from(metadata, false, false);
    assertThat(subject.is_valid(metadata.object_id)).isTrue();

    // Act
    Optional<Ballot.CiphertextBallotSelection> result = encrypt_selection(subject, metadata, keypair.public_key, ONE_MOD_Q, nonce, false, true);

    // Assert
    assertThat(result).isPresent();
    assertThat(result.get().ciphertext).isNotNull();
    assertThat(result.get().is_valid_encryption(hash_context, keypair.public_key, ONE_MOD_Q)).isTrue();
  }

  @Test
  public void test_encrypt_simple_selection_malformed_data_fails() {
    KeyPair keypair = elgamal_keypair_from_secret(int_to_q(BigInteger.TWO).get()).get();
    ElementModQ nonce = TestUtils.elements_mod_q();
    Election.SelectionDescription metadata = new Election.SelectionDescription("some-selection-object-id", "some-candidate-id", 1);
    ElementModQ hash_context = metadata.crypto_hash();
    Ballot.PlaintextBallotSelection subject = selection_from(metadata, false, false);
    assertThat(subject.is_valid(metadata.object_id)).isTrue();

    Optional<Ballot.CiphertextBallotSelection> resultO = encrypt_selection(subject, metadata, keypair.public_key, ONE_MOD_Q, nonce, false, true);
    assertThat(resultO).isPresent();
    Ballot.CiphertextBallotSelection result = resultO.get();

    // tamper with the description_hash
    // Ballot.CiphertextBallotSelection malformed_description_hash = result.toBuilder().setDescriptionHash(TWO_MOD_Q).build();
    Ballot.CiphertextBallotSelection malformed_description_hash = new Ballot.CiphertextBallotSelection(
            result.object_id, TWO_MOD_Q, result.ciphertext,
            result.crypto_hash, result.is_placeholder_selection, result.nonce,
            result.proof, result.extended_data);

    // remove the proof
    // Ballot.CiphertextBallotSelection missing_proof = result.toBuilder().clearProof().build();
    Ballot.CiphertextBallotSelection missing_proof = new Ballot.CiphertextBallotSelection(
            result.object_id, result.description_hash, result.ciphertext,
            result.crypto_hash, result.is_placeholder_selection, result.nonce,
            Optional.empty(), result.extended_data);

    assertThat(malformed_description_hash.is_valid_encryption(hash_context, keypair.public_key, ONE_MOD_Q)).isFalse();
    assertThat(missing_proof.is_valid_encryption(hash_context, keypair.public_key, ONE_MOD_Q)).isFalse();
  }

  @Test
  public void test_encrypt_selection_valid_input_succeeds() {
    ElectionFactory.SelectionTuple tuple = ElectionFactory.get_selection_description_well_formed();
    KeyPair keypair = elgamal_keypair_from_secret(int_to_q(BigInteger.TWO).get()).get();
    ElementModQ seed = TestUtils.elements_mod_q_no_zero();

    Election.SelectionDescription description = tuple.selection_description;
    Ballot.PlaintextBallotSelection subject = ballot_factory.get_random_selection_from(description, new Random(), false);

    Optional<Ballot.CiphertextBallotSelection> result = encrypt_selection(subject, description, keypair.public_key, ONE_MOD_Q, seed, false, true);
    assertThat(result).isPresent();
    assertThat(result.get().ciphertext).isNotNull();
    assertThat(result.get().is_valid_encryption(description.crypto_hash(), keypair.public_key, ONE_MOD_Q)).isTrue();
  }

  @Test
  public void test_encrypt_selection_valid_input_tampered_encryption_fails() {
    ElectionFactory.SelectionTuple tuple = ElectionFactory.get_selection_description_well_formed();
    KeyPair keypair = elgamal_keypair_from_secret(int_to_q(BigInteger.TWO).get()).get();
    ElementModQ seed = TestUtils.elements_mod_q_no_zero();

    Election.SelectionDescription description = tuple.selection_description;
    Ballot.PlaintextBallotSelection subject = ballot_factory.get_random_selection_from(description, new Random(), false);

    Optional<Ballot.CiphertextBallotSelection> resultO = encrypt_selection(subject, description, keypair.public_key, ONE_MOD_Q, seed, false, true);
    assertThat(resultO).isPresent();
    assertThat(resultO.get().is_valid_encryption(description.crypto_hash(), keypair.public_key, ONE_MOD_Q)).isTrue();
    Ballot.CiphertextBallotSelection result = resultO.get();

    // tamper with the encryption
    Ciphertext malformed_message = new Ciphertext(mult_p(result.ciphertext.pad, TWO_MOD_P), result.ciphertext.data);
    Ballot.CiphertextBallotSelection malformed_encryption = new Ballot.CiphertextBallotSelection(
            result.object_id, TWO_MOD_Q, malformed_message,
            result.crypto_hash, result.is_placeholder_selection, result.nonce,
            result.proof, result.extended_data);

    // tamper with the proof
    ElementModP altered_a0 = mult_p(result.proof.get().proof_zero_pad, TWO_MOD_P);
    DisjunctiveChaumPedersenProof proof = result.proof.get();
    DisjunctiveChaumPedersenProof malformed_disjunctive = new DisjunctiveChaumPedersenProof(
            altered_a0,
            proof.proof_zero_data,
            proof.proof_one_pad,
            proof.proof_one_data,
            proof.proof_zero_challenge,
            proof.proof_one_challenge,
            proof.challenge,
            proof.proof_zero_response,
            proof.proof_one_response
    );
    Ballot.CiphertextBallotSelection malformed_proof = new Ballot.CiphertextBallotSelection(
            result.object_id, TWO_MOD_Q, malformed_message,
            result.crypto_hash, result.is_placeholder_selection, result.nonce,
            Optional.of(malformed_disjunctive), result.extended_data);


    assertThat(malformed_encryption.is_valid_encryption(description.crypto_hash(), keypair.public_key, ONE_MOD_Q)).isFalse();
    assertThat(malformed_proof.is_valid_encryption(description.crypto_hash(), keypair.public_key, ONE_MOD_Q)).isFalse();
  }

  @Test
  public void test_encrypt_simple_contest_referendum_succeeds() {
    KeyPair keypair = elgamal_keypair_from_secret(int_to_q(BigInteger.TWO).get()).get();
    ElementModQ nonce = TestUtils.elements_mod_q();

    Election.SelectionDescription desc1 = new Election.SelectionDescription("some-object-id-affirmative", "some-candidate-id-affirmative", 0);
    Election.SelectionDescription desc2 = new Election.SelectionDescription("some-object-id-negative", "some-candidate-id-negative", 1);
    Election.SelectionDescription descp = new Election.SelectionDescription("some-object-id-placeholder", "some-candidate-id-placeholder", 2);

    Election.ContestDescriptionWithPlaceholders metadata = new Election.ContestDescriptionWithPlaceholders(
            "some-contest-object-id",
            "some-electoral-district-id",
            0,
            Election.VoteVariationType.one_of_m,
            1,
            1,
            "some-referendum-contest-name",
            ImmutableList.of(desc1, desc2),
            null,
            null,
            ImmutableList.of(descp));

    ElementModQ hash_context = metadata.crypto_hash();
    Ballot.PlaintextBallotContest subject = contest_from(metadata);

    assertThat(subject.is_valid(metadata.object_id, metadata.ballot_selections.size(), metadata.number_elected, metadata.votes_allowed)).isTrue();
    Optional<Ballot.CiphertextBallotContest> result = encrypt_contest(subject, metadata, keypair.public_key, ONE_MOD_Q, nonce, true);

    assertThat(result).isPresent();
    assertThat(result.get().is_valid_encryption(hash_context, keypair.public_key, ONE_MOD_Q)).isTrue();
  }

  @Test
  public void test_encrypt_contest_valid_input_succeeds() {
    ElectionFactory.ContestTuple tuple = ElectionFactory.get_contest_description_well_formed();
    KeyPair keypair = TestUtils.elgamal_keypairs();
    ElementModQ nonce_seed = TestUtils.elements_mod_q_no_zero();

    Election.ContestDescriptionWithPlaceholders description = tuple.contest_description;
    Ballot.PlaintextBallotContest subject = ballot_factory.get_random_contest_from(description, new Random(), false, false);

    // TODO want ContestDescriptionWithPlaceholders, has ContestDescription
    Optional<Ballot.CiphertextBallotContest> resultO = encrypt_contest(subject, description, keypair.public_key, ONE_MOD_Q, nonce_seed, true);
    assertThat(resultO).isPresent();
    Ballot.CiphertextBallotContest result = resultO.get();

    assertThat(result.is_valid_encryption(description.crypto_hash(), keypair.public_key, ONE_MOD_Q)).isTrue();

    // The encrypted contest should include an entry for each possible selection nd placeholders for
    // each seat
    int expected_entries = description.ballot_selections.size() + description.number_elected;

    assertThat(result.ballot_selections.size()).isEqualTo(expected_entries);
  }

  @settings(
          deadline=timedelta(milliseconds=4000),
          suppress_health_check=[HealthCheck.too_slow],
          max_examples=10,
  )
  @given(
          ElectionFactory.get_contest_description_well_formed(),
          elgamal_keypairs(),
          elements_mod_q_no_zero(),
          integers(),
  )
  def test_encrypt_contest_valid_input_succeeds(
          self,
          contest_description: ContestDescription,
          keypair: ElGamalKeyPair,
          nonce_seed: ElementModQ,
          random_seed: int,
          ):

          # Arrange
          _, description = contest_description
  random = Random(random_seed)
  subject = ballot_factory.get_random_contest_from(description, random)

          # Act
          result = encrypt_contest(
          subject, description, keypair.public_key, ONE_MOD_Q, nonce_seed
  )

        # Assert
        self.assertIsNotNone(result)
          self.assertTrue(
          result.is_valid_encryption(
          description.crypto_hash(), keypair.public_key, ONE_MOD_Q
            )
                    )

                    # The encrypted contest should include an entry for each possible selection
        # and placeholders for each seat
  expected_entries = (
  len(description.ballot_selections) + description.number_elected
        )
                self.assertEqual(len(result.ballot_selections), expected_entries)


}
