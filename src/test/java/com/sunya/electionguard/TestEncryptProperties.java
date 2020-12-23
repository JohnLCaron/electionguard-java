package com.sunya.electionguard;

import com.google.common.collect.ImmutableList;
import com.google.common.truth.Truth;
import net.jqwik.api.Example;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.ShrinkingMode;
import net.jqwik.api.constraints.IntRange;
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

public class TestEncryptProperties extends TestProperties {
  static final ElementModQ SEED_HASH = new EncryptionDevice("Location").get_hash();
  static final ElectionFactory election_factory = new ElectionFactory();
  static final BallotFactory ballot_factory = new BallotFactory();

  @Example
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

  @Example
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

  @Property(tries = 10, shrinking = ShrinkingMode.OFF)
  public void test_encrypt_selection_valid_input_succeeds(
          @ForAll("selection_description") Election.SelectionDescription description,
          @ForAll("elgamal_keypairs") ElGamal.KeyPair keypair,
          @ForAll("elements_mod_q_no_zero") ElementModQ seed,
          @ForAll @IntRange(min = 0, max = 100) int random_seed) {
    Ballot.PlaintextBallotSelection subject = ballot_factory.get_random_selection_from(description);

    Optional<Ballot.CiphertextBallotSelection> result = encrypt_selection(subject, description, keypair.public_key, ONE_MOD_Q, seed, false, true);
    assertThat(result).isPresent();
    assertThat(result.get().ciphertext).isNotNull();
    assertThat(result.get().is_valid_encryption(description.crypto_hash(), keypair.public_key, ONE_MOD_Q)).isTrue();
  }

  @Property(tries = 10, shrinking = ShrinkingMode.OFF)
  public void test_encrypt_selection_valid_input_tampered_encryption_fails(
          @ForAll("selection_description") Election.SelectionDescription description,
          @ForAll("elgamal_keypairs") ElGamal.KeyPair keypair,
          @ForAll("elements_mod_q_no_zero") ElementModQ seed,
          @ForAll @IntRange(min = 0, max = 100) int random_seed) {
    Ballot.PlaintextBallotSelection subject = ballot_factory.get_random_selection_from(description);

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

  @Example
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

  @Property(tries = 10, shrinking = ShrinkingMode.OFF)
  public void test_encrypt_contest_valid_input_succeeds(
          @ForAll("contest_description_well_formed") Election.ContestDescriptionWithPlaceholders description,
          @ForAll("elgamal_keypairs") ElGamal.KeyPair keypair,
          @ForAll("elements_mod_q_no_zero") ElementModQ nonce_seed,
          @ForAll @IntRange(min = 0, max = 100) int random_seed) {

    Ballot.PlaintextBallotContest subject = ballot_factory.get_random_contest_from(description, false, false);

    // TODO want ContestDescriptionWithPlaceholders, has ContestDescription
    Optional<Ballot.CiphertextBallotContest> resultO = encrypt_contest(subject, description, keypair.public_key, ONE_MOD_Q, nonce_seed, true);
    assertThat(resultO).isPresent();
    Ballot.CiphertextBallotContest result = resultO.get();

    assertThat(result.is_valid_encryption(description.crypto_hash(), keypair.public_key, ONE_MOD_Q)).isTrue();

    // The encrypted contest should include an entry for each possible selection nd placeholders for each seat
    int expected_entries = description.ballot_selections.size() + description.number_elected;

    assertThat(result.ballot_selections.size()).isEqualTo(expected_entries);
  }

  /*
  @Property(tries = 10, shrinking = ShrinkingMode.OFF)
  public void test_encrypt_contest_valid_input_tampered_proof_fails(
          @ForAll("contest_description_well_formed") Election.ContestDescriptionWithPlaceholders description,
          @ForAll("elgamal_keypairs") ElGamal.KeyPair keypair,
          @ForAll("elements_mod_q_no_zero") ElementModQ nonce_seed,
          @ForAll @IntRange(min = 0, max = 100) int random_seed) {

    Ballot.PlaintextBallotContest subject = ballot_factory.get_random_contest_from(description, false, false);

    Optional<Ballot.CiphertextBallotContest> result = encrypt_contest(subject, description, keypair.public_key, ONE_MOD_Q, nonce_seed, true);
    assertThat(result.get().is_valid_encryption(description.crypto_hash(), keypair.public_key, ONE_MOD_Q)).isTrue();

    // tamper with the proof
    Ballot.CiphertextBallotContest malformed_proof = deepcopy(result)
    altered_a = mult_p(result.proof.pad, TWO_MOD_P)
    malformed_disjunctive = ConstantChaumPedersenProof(
            altered_a,
            malformed_proof.proof.data,
            malformed_proof.proof.challenge,
            malformed_proof.proof.response,
            malformed_proof.proof.constant,
            )
    malformed_proof.proof = malformed_disjunctive

    // remove the proof
    Ballot.CiphertextBallotContest missing_proof = deepcopy(result)
    missing_proof.proof = None

    assertThat(malformed_proof.is_valid_encryption(description.crypto_hash(), keypair.public_key, ONE_MOD_Q)).isFalse();
    assertThat(missing_proof.is_valid_encryption(description.crypto_hash(), keypair.public_key, ONE_MOD_Q)).isFalse();
  }

  @Property(tries = 10, shrinking = ShrinkingMode.OFF)
  public void test_encrypt_contest_overvote_fails(
          @ForAll("contest_description_well_formed") Election.ContestDescriptionWithPlaceholders description,
          @ForAll("elgamal_keypairs") ElGamal.KeyPair keypair,
          @ForAll("elements_mod_q_no_zero") ElementModQ seed,
          @ForAll @IntRange(min = 1, max = 6) int overvotes) {
    Ballot.PlaintextBallotContest subject = ballot_factory.get_random_contest_from(description, false, false);

    int highest_sequence = Math.max(1, description.ballot_selections.stream().mapToInt(s -> s.sequence_order).max().orElse(0));

    for (int i = 0; i < overvotes; i++) {
      Ballot.PlaintextBallotSelection extra = ballot_factory.get_random_selection_from(description.ballot_selections.get(0));
      extra.sequence_order = highest_sequence + i + 1;
      subject.ballot_selections.add(extra);
    }

    Optional<Ballot.CiphertextBallotContest> result = encrypt_contest(subject, description, keypair.public_key, ONE_MOD_Q, seed, true);
    assertThat(result).isEmpty();
  }

  @Example
  public void test_encrypt_contest_manually_formed_contest_description_valid_succeeds() {
    Election.ContestDescription description = new Election.ContestDescription(
            object_id = "0@A.com-contest",
            electoral_district_id = "0@A.com-gp-unit",
            sequence_order = 1,
            vote_variation = VoteVariationType.n_of_m,
            number_elected = 1,
            votes_allowed = 1,
            name = "",
            ballot_selections =[
            SelectionDescription(
                    object_id = "0@A.com-selection",
                    candidate_id = "0@A.com",
                    sequence_order = 0,
                    ),
    SelectionDescription(
            object_id = "0@B.com-selection",
            candidate_id = "0@B.com",
            sequence_order = 1,
            ),
            ],
    ballot_title = None,
            ballot_subtitle = None,
          );

    KeyPair keypair = elgamal_keypair_from_secret(TWO_MOD_Q).get();
    ElementModQ seed = ONE_MOD_Q;

    Ballot.PlaintextBallotContest data = ballot_factory.get_random_contest_from(description, false, false);

    placeholders = generate_placeholder_selections_from(description, description.number_elected);
    description_with_placeholders = contest_description_with_placeholders_from(description, placeholders);

    Optional<Ballot.CiphertextBallotContest> subject = encrypt_contest(
            data,
            description_with_placeholders,
            keypair.public_key,
            ONE_MOD_Q,
            seed,
            should_verify_proofs = True,
            );
    assertThat(subject).isPresent();
  }

  /** This is an example test of a failing test where the contest description is malformed.
  @Example
  public void test_encrypt_contest_duplicate_selection_object_ids_fails() {
    random_seed = 0

    Election.ContestDescription description = new Election.ContestDescription(
            object_id = "0@A.com-contest",
            electoral_district_id = "0@A.com-gp-unit",
            sequence_order = 1,
            vote_variation = VoteVariationType.n_of_m,
            number_elected = 1,
            votes_allowed = 1,
            name = "",
            ballot_selections =[
            SelectionDescription(
                    object_id = "0@A.com-selection",
                    candidate_id = "0@A.com",
                    sequence_order = 0,
                    ),
                #Note the selection description is the same as the first sequence element
    SelectionDescription(
            object_id = "0@A.com-selection",
            candidate_id = "0@A.com",
            sequence_order = 1,
            ),
            ],
                    );

    KeyPair keypair = elgamal_keypair_from_secret(TWO_MOD_Q).get();
    ElementModQ seed = ONE_MOD_Q;

    // Bypass checking the validity of the description
    Ballot.PlaintextBallotContest data = ballot_factory.get_random_contest_from(description, true, false);

    placeholders = generate_placeholder_selections_from(description, description.number_elected);
    description_with_placeholders = contest_description_with_placeholders_from(description, placeholders);

    subject = encrypt_contest(data, description_with_placeholders, keypair.public_key, ONE_MOD_Q, seed);
    assertThat(subject).isEmpty();
  }

  @Example
  public void test_encrypt_ballot_simple_succeeds() {

    KeyPair keypair = elgamal_keypair_from_secret(int_to_q(BigInteger.TWO).get()).get();
    Election.ElectionDescription election = election_factory.get_fake_election();
    ElectionBuilder.Tuple tuple = election_factory.get_fake_ciphertext_election(election, keypair.public_key).get();
    Election.InternalElectionDescription metadata = tuple.description;
    Election.CiphertextElectionContext context = tuple.context;
    ElementModQ nonce_seed = TWO_MOD_Q;

    // TODO:Ballot Factory
    Ballot.PlaintextBallot subject = election_factory.get_fake_ballot(metadata.description, null);
    assertThat(subject.is_valid(metadata.ballot_styles.get(0).object_id)).isTrue();

    Optional<Ballot.CiphertextBallot> resultO = encrypt_ballot(subject, metadata, context, SEED_HASH, Optional.empty(), true);
    assertThat(resultO).isPresent();
    Ballot.CiphertextBallot result = resultO.get();

    Optional<String> tracker_code = result.get_tracker_code();
    Optional<Ballot.CiphertextBallot> result_from_seed = encrypt_ballot(subject, metadata, context, SEED_HASH, Optional.of(nonce_seed), true);

    assertThat(result.tracking_hash).isPresent();
    assertThat(tracker_code).isPresent();
    assertThat(result_from_seed).isPresent();
    assertThat(
            result.is_valid_encryption(
                    metadata.description_hash,
                    keypair.public_key,
                    context.crypto_extended_base_hash,
                    )
    ).isTrue();
    assertThat(
            result_from_seed.is_valid_encryption(
                    metadata.description_hash,
                    keypair.public_key,
                    context.crypto_extended_base_hash,
                    )
    ).isTrue();
  }

  @Example
  public void test_encrypt_ballot_with_stateful_composer_succeeds() {
    keypair = elgamal_keypair_from_secret(int_to_q(2))
    election = election_factory.get_fake_election()
    metadata, context = election_factory.get_fake_ciphertext_election(
            election, keypair.public_key
    )

    data = election_factory.get_fake_ballot(metadata)
    assertThat(data.is_valid(metadata.ballot_styles[0].object_id)).isTrue();

    device = EncryptionDevice("Location")
    subject = EncryptionMediator(metadata, context, device)

    result = subject.encrypt(data)

    assertThat(result).isPresent();
    assertThat(
            result.is_valid_encryption(
                    metadata.description_hash,
                    keypair.public_key,
                    context.crypto_extended_base_hash,
                    )
    ).isTrue();
  }

  @Example
  public void test_encrypt_simple_ballot_from_files_succeeds() {
    keypair = elgamal_keypair_from_secret(int_to_q(2));
    election = election_factory.get_simple_election_from_file();
    metadata, context = election_factory.get_fake_ciphertext_election(election, keypair.public_key);

    data = ballot_factory.get_simple_ballot_from_file();
    assertThat(data.is_valid(metadata.ballot_styles[0].object_id)).isTrue();

    device = EncryptionDevice("Location");
    subject = EncryptionMediator(metadata, context, device);

    result = subject.encrypt(data);

    assertThat(result).isPresent();
    assertThat(data.object_id).isEqualTo(result.object_id);
    assertThat(
            result.is_valid_encryption(
                    metadata.description_hash,
                    keypair.public_key,
                    context.crypto_extended_base_hash,
                    )
    ).isTrue();
  }


   */

}
