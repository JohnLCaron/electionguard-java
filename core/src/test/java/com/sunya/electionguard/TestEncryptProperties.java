package com.sunya.electionguard;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import net.jqwik.api.Example;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.ShrinkingMode;
import net.jqwik.api.constraints.IntRange;

import java.io.IOException;
import java.math.BigInteger;
import java.util.List;
import java.util.Optional;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth8.assertThat;

import static com.sunya.electionguard.ChaumPedersen.*;
import static com.sunya.electionguard.Encrypt.*;
import static com.sunya.electionguard.ElGamal.*;
import static com.sunya.electionguard.Group.*;
import static com.sunya.electionguard.InternalManifest.ContestWithPlaceholders;

public class TestEncryptProperties extends TestProperties {
  static final ElementModQ SEED_HASH = Encrypt.createDeviceForTest("Location").get_hash();
  static final BallotFactory ballot_factory = new BallotFactory();

  @Example
  public void test_encrypt_simple_selection_succeeds() {
    KeyPair keypair = elgamal_keypair_from_secret(int_to_q_unchecked(BigInteger.TWO)).orElseThrow();
    ElementModQ nonce = TestUtils.elements_mod_q();

    Manifest.SelectionDescription metadata = new Manifest.SelectionDescription(
            "some-selection-object-id", 1, "some-candidate-id", null);
    ElementModQ hash_context = metadata.cryptoHash();

    PlaintextBallot.Selection subject = selection_from(metadata, false, false);
    assertThat(subject.is_valid(metadata.selectionId())).isTrue();

    Optional<CiphertextBallot.Selection> result = encrypt_selection("test", subject, metadata, keypair.public_key(), ONE_MOD_Q, nonce, false, true);

    // Assert
    assertThat(result).isPresent();
    assertThat(result.get().ciphertext()).isNotNull();
    assertThat(result.get().is_valid_encryption("test", hash_context, keypair.public_key(), ONE_MOD_Q)).isTrue();
  }

  @Example
  public void test_encrypt_simple_selection_malformed_data_fails() {
    KeyPair keypair = elgamal_keypair_from_secret(int_to_q_unchecked(BigInteger.TWO)).orElseThrow();
    ElementModQ nonce = TestUtils.elements_mod_q();
    Manifest.SelectionDescription metadata = new Manifest.SelectionDescription(
            "some-selection-object-id", 1, "some-candidate-id", null);
    ElementModQ hash_context = metadata.cryptoHash();
    PlaintextBallot.Selection subject = selection_from(metadata, false, false);
    assertThat(subject.is_valid(metadata.selectionId())).isTrue();

    Optional<CiphertextBallot.Selection> resultO = encrypt_selection("test", subject, metadata, keypair.public_key(), ONE_MOD_Q, nonce, false, true);
    assertThat(resultO).isPresent();
    CiphertextBallot.Selection result = resultO.get();

    // tamper with the description_hash
    // CiphertextBallotSelection malformed_description_hash = result.toBuilder().setDescriptionHash(TWO_MOD_Q).build();
    CiphertextBallot.Selection malformed_description_hash = new CiphertextBallot.Selection(
            result.object_id(), result.sequence_order(), TWO_MOD_Q, result.ciphertext(),
            result.crypto_hash, result.is_placeholder_selection, result.nonce,
            result.proof, result.extended_data);

    // remove the proof
    // CiphertextBallotSelection missing_proof = result.toBuilder().clearProof().build();
    CiphertextBallot.Selection missing_proof = new CiphertextBallot.Selection(
            result.object_id(), result.sequence_order(), result.description_hash(), result.ciphertext(),
            result.crypto_hash, result.is_placeholder_selection, result.nonce,
            Optional.empty(), result.extended_data);

    assertThat(malformed_description_hash.is_valid_encryption("test", hash_context, keypair.public_key(), ONE_MOD_Q)).isFalse();
    assertThat(missing_proof.is_valid_encryption("test", hash_context, keypair.public_key(), ONE_MOD_Q)).isFalse();
  }

  @Property(tries = 10, shrinking = ShrinkingMode.OFF)
  public void test_encrypt_selection_valid_input_succeeds(
          @ForAll("selection_description") Manifest.SelectionDescription description,
          @ForAll("elgamal_keypairs") ElGamal.KeyPair keypair,
          @ForAll("elements_mod_q_no_zero") ElementModQ seed) {
    PlaintextBallot.Selection subject = BallotFactory.get_random_selection_from(description);

    Optional<CiphertextBallot.Selection> result = encrypt_selection("test", subject, description, keypair.public_key(), ONE_MOD_Q, seed, false, true);
    assertThat(result).isPresent();
    assertThat(result.get().ciphertext()).isNotNull();
    assertThat(result.get().is_valid_encryption("test", description.cryptoHash(), keypair.public_key(), ONE_MOD_Q)).isTrue();
  }

  @Property(tries = 10, shrinking = ShrinkingMode.OFF)
  public void test_encrypt_selection_valid_input_tampered_encryption_fails(
          @ForAll("selection_description") Manifest.SelectionDescription description,
          @ForAll("elgamal_keypairs") ElGamal.KeyPair keypair,
          @ForAll("elements_mod_q_no_zero") ElementModQ seed,
          @ForAll @IntRange(min = 0, max = 100) int random_seed) {
    PlaintextBallot.Selection subject = BallotFactory.get_random_selection_from(description);

    Optional<CiphertextBallot.Selection> resultO = encrypt_selection("test", subject, description, keypair.public_key(), ONE_MOD_Q, seed, false, true);
    assertThat(resultO).isPresent();
    assertThat(resultO.get().is_valid_encryption("test", description.cryptoHash(), keypair.public_key(), ONE_MOD_Q)).isTrue();
    CiphertextBallot.Selection result = resultO.get();

    // tamper with the encryption
    Ciphertext malformed_message = new Ciphertext(mult_p(result.ciphertext().pad(), TWO_MOD_P), result.ciphertext().data());
    CiphertextBallot.Selection malformed_encryption = new CiphertextBallot.Selection(
            result.object_id(), result.sequence_order(), TWO_MOD_Q, malformed_message,
            result.crypto_hash, result.is_placeholder_selection, result.nonce,
            result.proof, result.extended_data);

    // tamper with the proof
    ElementModP altered_a0 = mult_p(result.proof.orElseThrow().proof0.pad, TWO_MOD_P);
    DisjunctiveChaumPedersenProof proof = result.proof.get();
    DisjunctiveChaumPedersenProof malformed_disjunctive = new DisjunctiveChaumPedersenProof(
            altered_a0,
            proof.proof0.data,
            proof.proof1.pad,
            proof.proof1.data,
            proof.proof0.challenge,
            proof.proof1.challenge,
            proof.challenge,
            proof.proof0.response,
            proof.proof1.response
    );
    CiphertextBallot.Selection malformed_proof = new CiphertextBallot.Selection(
            result.object_id(), result.sequence_order(), TWO_MOD_Q, malformed_message,
            result.crypto_hash, result.is_placeholder_selection, result.nonce,
            Optional.of(malformed_disjunctive), result.extended_data);


    assertThat(malformed_encryption.is_valid_encryption("test", description.cryptoHash(), keypair.public_key(), ONE_MOD_Q)).isFalse();
    assertThat(malformed_proof.is_valid_encryption("test", description.cryptoHash(), keypair.public_key(), ONE_MOD_Q)).isFalse();
  }

  @Example
  public void test_encrypt_simple_contest_referendum_succeeds() {
    KeyPair keypair = elgamal_keypair_from_secret(int_to_q_unchecked(BigInteger.TWO)).orElseThrow();
    ElementModQ nonce = TestUtils.elements_mod_q();

    Manifest.SelectionDescription desc1 = new Manifest.SelectionDescription(
            "some-object-id-affirmative", 0, "some-candidate-id-affirmative", null);
    Manifest.SelectionDescription desc2 = new Manifest.SelectionDescription(
            "some-object-id-negative", 1, "some-candidate-id-negative", null);
    Manifest.SelectionDescription descp = new Manifest.SelectionDescription(
            "some-object-id-placeholder", 2, "some-candidate-id-placeholder", null);

    Manifest.ContestDescription contest = new Manifest.ContestDescription(
            "some-contest-object-id",
            0,
            "some-electoral-district-id",
            Manifest.VoteVariationType.one_of_m,
            1,
            1,
            "some-referendum-contest-name",
            ImmutableList.of(desc1, desc2),
            null,
            null,
            ImmutableList.of(),
            null);

    ContestWithPlaceholders metadata = new ContestWithPlaceholders(contest, ImmutableList.of(descp));
    ElementModQ hash_context = contest.cryptoHash();
    PlaintextBallot.Contest subject = contest_from(contest);

    assertThat(subject.is_valid(contest.contestId(), contest.selections().size(), contest.numberElected(), contest.votesAllowed())).isTrue();
    Optional<CiphertextBallot.Contest> result = encrypt_contest("test", subject, metadata, keypair.public_key(), ONE_MOD_Q, nonce, true);

    assertThat(result).isPresent();
    assertThat(result.get().is_valid_encryption("test", hash_context, keypair.public_key(), ONE_MOD_Q)).isTrue();
  }

  @Property(tries = 10, shrinking = ShrinkingMode.OFF)
  public void test_encrypt_contest_valid_input_succeeds(
          @ForAll("contest_description_well_formed") ContestWithPlaceholders description,
          @ForAll("elgamal_keypairs") ElGamal.KeyPair keypair,
          @ForAll("elements_mod_q_no_zero") ElementModQ nonce_seed,
          @ForAll @IntRange(min = 0, max = 100) int random_seed) {

    PlaintextBallot.Contest subject = ballot_factory.get_random_contest_from(description.contest, false, false);
    Optional<CiphertextBallot.Contest> resultO = encrypt_contest("test", subject, description, keypair.public_key(), ONE_MOD_Q, nonce_seed, true);
    assertThat(resultO).isPresent();
    CiphertextBallot.Contest result = resultO.get();

    assertThat(result.is_valid_encryption("test", description.contest.cryptoHash(), keypair.public_key(), ONE_MOD_Q)).isTrue();

    // The encrypted contest should include an entry for each possible selection nd placeholders for each seat
    int expected_entries = description.contest.selections().size() + description.contest.numberElected();

    assertThat(result.selections.size()).isEqualTo(expected_entries);
  }

  @Property(tries = 10, shrinking = ShrinkingMode.OFF)
  public void test_encrypt_contest_valid_input_tampered_proof_fails(
          @ForAll("contest_description_well_formed") ContestWithPlaceholders description,
          @ForAll("elgamal_keypairs") ElGamal.KeyPair keypair,
          @ForAll("elements_mod_q_no_zero") ElementModQ nonce_seed,
          @ForAll @IntRange(min = 0, max = 100) int random_seed) {

    PlaintextBallot.Contest subject = ballot_factory.get_random_contest_from(description.contest, false, false);

    Optional<CiphertextBallot.Contest> resultO = encrypt_contest(
            "test", subject, description, keypair.public_key(), ONE_MOD_Q, nonce_seed, true);
    assertThat(resultO).isPresent();
    CiphertextBallot.Contest result = resultO.get();
    assertThat(result.is_valid_encryption("test", description.contest.cryptoHash(), keypair.public_key(), ONE_MOD_Q)).isTrue();

    // tamper with the proof
    assertThat(result.proof).isPresent();
    ChaumPedersen.ConstantChaumPedersenProof proof = result.proof.get();
    ElementModP altered_a = mult_p(proof.pad, TWO_MOD_P);
    ConstantChaumPedersenProof malformed_disjunctive = new ConstantChaumPedersenProof(
            altered_a, proof.data, proof.challenge, proof.response, proof.constant);

    CiphertextBallot.Contest malformed_proof = new CiphertextBallot.Contest(
            result.object_id(), result.sequence_order(), result.contestHash,
            result.selections, result.crypto_hash,
            result.nonce,
            Optional.of(malformed_disjunctive));
    assertThat(malformed_proof.is_valid_encryption("test", description.contest.cryptoHash(), keypair.public_key(), ONE_MOD_Q)).isFalse();

    // remove the proof
    CiphertextBallot.Contest missing_proof = new CiphertextBallot.Contest(
            result.object_id(), result.sequence_order(), result.contestHash,
            result.selections, result.crypto_hash,
            result.nonce, Optional.empty());
    assertThat(missing_proof.is_valid_encryption("test", description.contest.cryptoHash(), keypair.public_key(), ONE_MOD_Q)).isFalse();
  }

  /*  Fails - Dont know what the fix is.
  @Property(tries = 10, shrinking = ShrinkingMode.OFF)
  public void test_encrypt_contest_overvote_fails(
          @ForAll("contest_description_well_formed") Manifest.ContestDescriptionWithPlaceholders description,
          @ForAll("elgamal_keypairs") ElGamal.KeyPair keypair,
          @ForAll("elements_mod_q_no_zero") ElementModQ seed,
          @ForAll @IntRange(min = 1, max = 6) int overvotes) {

    PlaintextBallotContest subject = ballot_factory.get_random_contest_from(description, false, false);
    //  highest_sequence = max( *[selection.sequence_order for selection in description.ballot_selections], 1, )
    int highest_sequence = Math.max(1, description.ballot_selections.stream().mapToInt(s -> s.sequence_order).max().orElse(0));

    List<PlaintextBallotSelection> extra_ballot_selections = new ArrayList<>(subject.ballot_selections);
    for (int i = 0; i < overvotes; i++) {
      // extra = ballot_factory.get_random_selection_from( description.ballot_selections[0], random )
      PlaintextBallotSelection extra = ballot_factory.get_random_selection_from(description.ballot_selections.get(0));
      // PlaintextBallotSelection extraModified = new Manifest.SelectionDescription(extra.object_id, extra.vote, extra.is_placeholder_selection, extra.extended_data);
      // extra.sequence_order = highest_sequence + i + 1 // TODO there is no extra.sequence_order field
      extra_ballot_selections.add(extra);
    }

    Optional<CiphertextBallotContest> result = encrypt_contest(subject, description, keypair.public_key(), ONE_MOD_Q, seed, true);
    assertThat(result).isEmpty();
  } */

  @Example
  public void test_encrypt_contest_manually_formed_contest_description_valid_succeeds() {
    Manifest.ContestDescription description = new Manifest.ContestDescription(
            "0@A.com-contest",
            1,
            "0@A.com-gp-unit",
            Manifest.VoteVariationType.n_of_m,
            1,
            1,
            "",
            ImmutableList.of(
                    new Manifest.SelectionDescription(
                            "0@A.com-selection",
                            0,
                            "0@A.com",
                            null),
                    new Manifest.SelectionDescription(
                            "0@B.com-selection",
                            1,
                            "0@B.com",
                            null)),
            null, null, ImmutableList.of(),
            null);

    KeyPair keypair = elgamal_keypair_from_secret(TWO_MOD_Q).orElseThrow();

    PlaintextBallot.Contest data = ballot_factory.get_random_contest_from(description, false, false);

    List<Manifest.SelectionDescription> placeholders = InternalManifest.generate_placeholder_selections_from(description, description.numberElected());
    ContestWithPlaceholders contestp = new ContestWithPlaceholders(description, placeholders);

    Optional<CiphertextBallot.Contest> subject = encrypt_contest(
            "test",
            data,
            contestp,
            keypair.public_key(),
            ONE_MOD_Q,
            ONE_MOD_Q,
            true);
    assertThat(subject).isPresent();
  }

  /**
   * This is an example test of a failing test where the contest description is malformed.
   */
  @Example
  public void test_encrypt_contest_duplicate_selection_object_ids_fails() {

    Manifest.ContestDescription description = new Manifest.ContestDescription(
            "0@A.com-contest",
            1,
            "0@A.com-gp-unit",
            Manifest.VoteVariationType.n_of_m,
            1,
            1,
            "",
            ImmutableList.of(
                    new Manifest.SelectionDescription(
                            "0@A.com-selection",
                            0,
                            "0@A.com",
                            null),
                    // Note the selection description is the same as the first sequence element
                    new Manifest.SelectionDescription(
                            "0@A.com-selection",
                            1,
                            "0@A.com",
                            null)),
            null, null, ImmutableList.of(),
            null);

    KeyPair keypair = elgamal_keypair_from_secret(TWO_MOD_Q).orElseThrow();

    // Bypass checking the validity of the description
    PlaintextBallot.Contest data = ballot_factory.get_random_contest_from(description, true, false);

    List<Manifest.SelectionDescription> placeholders = InternalManifest.generate_placeholder_selections_from(description, description.numberElected());
    ContestWithPlaceholders contestp = new ContestWithPlaceholders(description, placeholders);

    Optional<CiphertextBallot.Contest> subject = encrypt_contest(
            "test",
            data,
            contestp,
            keypair.public_key(),
            ONE_MOD_Q,
            ONE_MOD_Q,
            true);
    assertThat(subject).isEmpty();
  }

  @Example
  public void test_encrypt_ballot_simple_succeeds() {
    KeyPair keypair = elgamal_keypair_from_secret(int_to_q_unchecked(BigInteger.TWO)).orElseThrow();
    Manifest election = ElectionFactory.get_fake_manifest();
    ElectionBuilder.DescriptionAndContext tuple = ElectionFactory.get_fake_ciphertext_election(election, keypair.public_key()).orElseThrow();
    ElectionContext context = tuple.context;

    PlaintextBallot subject = ElectionFactory.get_fake_ballot(election, null);
    assertThat(subject.is_valid(election.ballotStyles().get(0).ballotStyleId())).isTrue();

    Optional<CiphertextBallot> resultO = encrypt_ballot(subject, tuple.internalManifest, context, SEED_HASH, Optional.empty(), true);
    assertThat(resultO).isPresent();
    CiphertextBallot result = resultO.get();

    Optional<CiphertextBallot> result_from_seed = encrypt_ballot(subject, tuple.internalManifest, context, SEED_HASH, Optional.of(TWO_MOD_Q), true);

    assertThat(result.code).isNotNull();
    assertThat(result_from_seed).isPresent();
    assertThat(
            result.is_valid_encryption(
                    election.cryptoHash(),
                    keypair.public_key(),
                    context.cryptoExtendedBaseHash)).isTrue();
    assertThat(
            result_from_seed.get().is_valid_encryption(
                    election.cryptoHash(),
                    keypair.public_key(),
                    context.cryptoExtendedBaseHash)).isTrue();
  }


  @Example
  public void test_encrypt_ballot_with_stateful_composer_succeeds() {
    KeyPair keypair = elgamal_keypair_from_secret(int_to_q_unchecked(BigInteger.TWO)).orElseThrow();
    Manifest election = ElectionFactory.get_fake_manifest();
    ElectionBuilder.DescriptionAndContext tuple = ElectionFactory.get_fake_ciphertext_election(election, keypair.public_key()).orElseThrow();
    ElectionContext context = tuple.context;

    PlaintextBallot data = ElectionFactory.get_fake_ballot(election, null);
    assertThat(data.is_valid(election.ballotStyles().get(0).ballotStyleId())).isTrue();

    EncryptionDevice device = Encrypt.createDeviceForTest("Location");
    EncryptionMediator subject = new EncryptionMediator(tuple.internalManifest, context, device);

    Optional<CiphertextBallot> result = subject.encrypt(data);
    assertThat(result).isPresent();
    assertThat(result.get().is_valid_encryption(election.cryptoHash(), keypair.public_key(), context.cryptoExtendedBaseHash)).isTrue();
  }

  @Example
  public void test_encrypt_simple_ballot_from_files_succeeds() throws IOException {
    KeyPair keypair = elgamal_keypair_from_secret(int_to_q_unchecked(BigInteger.TWO)).orElseThrow();
    Manifest election = ElectionFactory.get_simple_election_from_file();
    ElectionBuilder.DescriptionAndContext tuple = ElectionFactory.get_fake_ciphertext_election(election, keypair.public_key()).orElseThrow();
    ElectionContext context = tuple.context;

    PlaintextBallot data = ballot_factory.get_simple_ballot_from_file();
    assertThat(data.is_valid(election.ballotStyles().get(0).ballotStyleId())).isTrue();

    EncryptionDevice device = Encrypt.createDeviceForTest("Location");
    EncryptionMediator subject = new EncryptionMediator(tuple.internalManifest, context, device);

    Optional<CiphertextBallot> result = subject.encrypt(data);
    assertThat(result).isPresent();
    assertThat(data.object_id()).isEqualTo(result.get().object_id());
    assertThat(result.get().is_valid_encryption(election.cryptoHash(), keypair.public_key(), context.cryptoExtendedBaseHash)).isTrue();
  }

  /**         This test verifies that we can regenerate the contest and selection proofs from the cached nonce values. */
   @Property(tries = 10, shrinking = ShrinkingMode.OFF)
  public void test_encrypt_ballot_with_derivative_nonces_regenerates_valid_proofs(
          @ForAll("elgamal_keypairs") ElGamal.KeyPair keypair) throws IOException {

     // TODO: Hypothesis test instead

     Manifest election = ElectionFactory.get_simple_election_from_file();
     ElectionBuilder.DescriptionAndContext tuple = ElectionFactory.get_fake_ciphertext_election(election, keypair.public_key()).orElseThrow();
     ElectionContext context = tuple.context;

     PlaintextBallot data = ballot_factory.get_simple_ballot_from_file();
     assertThat(data.is_valid(election.ballotStyles().get(0).ballotStyleId())).isTrue();

     EncryptionDevice device = Encrypt.createDeviceForTest("Location");
     EncryptionMediator subject = new EncryptionMediator(tuple.internalManifest, context, device);

     Optional<CiphertextBallot> resultO = subject.encrypt(data);
     assertThat(resultO).isPresent();
     CiphertextBallot result = resultO.get();
     assertThat(result.is_valid_encryption(election.cryptoHash(), keypair.public_key(), context.cryptoExtendedBaseHash)).isTrue();

     for (CiphertextBallot.Contest contest : result.contests) {
       // Find the contest description
       ContestWithPlaceholders contestp = tuple.internalManifest.getContestById(contest.contestId).orElseThrow();

       // Homomorpically accumulate the selection encryptions
       List<ElGamal.Ciphertext> ciphertexts = contest.selections.stream().map(s -> s.ciphertext()).toList();
       Ciphertext elgamal_accumulation = elgamal_add(Iterables.toArray(ciphertexts, ElGamal.Ciphertext.class));

       // accumulate the selection nonce's
       List<ElementModQ> nonces = contest.selections.stream().map(s -> s.nonce.orElseThrow()).toList();
       ElementModQ aggregate_nonce = add_q(Iterables.toArray(nonces, ElementModQ.class));

       ConstantChaumPedersenProof regenerated_constant = make_constant_chaum_pedersen(
               elgamal_accumulation,
               contestp.contest.numberElected(),
               aggregate_nonce,
               keypair.public_key(),
               add_q(contest.nonce.orElseThrow(), TWO_MOD_Q),
               context.cryptoExtendedBaseHash);

       assertThat(
               regenerated_constant.is_valid(
                       elgamal_accumulation,
                       keypair.public_key(),
                       context.cryptoExtendedBaseHash)).isTrue();

       for (CiphertextBallot.Selection selection : contest.selections) {
         // Since we know the nonce, we can decrypt the plaintext
         Integer representation = selection.ciphertext().decrypt_known_nonce(keypair.public_key(), selection.nonce.orElseThrow());

         // one could also decrypt with the secret key:
         // representation = selection.message.decrypt(keypair.secret_key)

         DisjunctiveChaumPedersenProof regenerated_disjuctive = make_disjunctive_chaum_pedersen(
                 selection.ciphertext(),
                 selection.nonce.get(),
                 keypair.public_key(),
                 context.cryptoExtendedBaseHash,
                 add_q(selection.nonce.get(), TWO_MOD_Q),
                 representation);

         assertThat(regenerated_disjuctive.is_valid(selection.ciphertext(), keypair.public_key(), context.cryptoExtendedBaseHash))
                 .isTrue();
       }
     }
   }

}
