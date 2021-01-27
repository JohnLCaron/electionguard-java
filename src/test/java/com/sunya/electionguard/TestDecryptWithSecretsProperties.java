package com.sunya.electionguard;

import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.ShrinkingMode;

import java.io.IOException;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth8.assertThat;
import static com.sunya.electionguard.Group.*;
import static com.sunya.electionguard.DecryptWithSecrets.*;


public class TestDecryptWithSecretsProperties extends TestProperties {

  //// TestDisjunctiveChaumPedersen

  @Property(tries = 10, shrinking = ShrinkingMode.OFF)
  public void test_decrypt_selection_valid_input_succeeds(
          @ForAll("selection_description") Election.SelectionDescription description,
          @ForAll("elgamal_keypairs") ElGamal.KeyPair keypair,
          @ForAll("elements_mod_q_no_zero") Group.ElementModQ nonce_seed) {

    Ballot.PlaintextBallotSelection data = BallotFactory.get_random_selection_from(description);

    Optional<Ballot.CiphertextBallotSelection> subjectO = Encrypt.encrypt_selection(
            data, description, keypair.public_key, ONE_MOD_Q, nonce_seed, false, true);
    assertThat(subjectO).isPresent();
    Ballot.CiphertextBallotSelection subject = subjectO.get();

    Optional<Ballot.PlaintextBallotSelection> result_from_key =
            decrypt_selection_with_secret(subject, description, keypair.public_key, keypair.secret_key, ONE_MOD_Q, false);
    Optional<Ballot.PlaintextBallotSelection> result_from_nonce =
            decrypt_selection_with_nonce(subject, description, keypair.public_key, ONE_MOD_Q, Optional.empty(), false);
    Optional<Ballot.PlaintextBallotSelection> result_from_nonce_seed =
            decrypt_selection_with_nonce(subject, description, keypair.public_key, ONE_MOD_Q, Optional.of(nonce_seed), false);

    assertThat(result_from_key).isPresent();
    assertThat(result_from_nonce).isPresent();
    assertThat(result_from_nonce_seed).isPresent();
    assertThat(data.vote).isEqualTo(result_from_key.get().vote);
    assertThat(data.vote).isEqualTo(result_from_nonce.get().vote);
    assertThat(data.vote).isEqualTo(result_from_nonce_seed.get().vote);
  }

  @Property(tries = 10, shrinking = ShrinkingMode.OFF)
  public void test_decrypt_selection_valid_input_tampered_fails(
          @ForAll("elgamal_keypairs") ElGamal.KeyPair keypair,
          @ForAll("elements_mod_q_no_zero") Group.ElementModQ seed) {

    Election.SelectionDescription description = ElectionFactory.get_selection_description_well_formed().selection_description;
    Ballot.PlaintextBallotSelection data = BallotFactory.get_random_selection_from(description);

    // Act
    Optional<Ballot.CiphertextBallotSelection> subjectO = Encrypt.encrypt_selection(data, description, keypair.public_key, ONE_MOD_Q, seed, false, true);
    assertThat(subjectO).isPresent();
    Ballot.CiphertextBallotSelection subject = subjectO.get();

    // tamper with the encryption
    ElGamal.Ciphertext malformed_message = new ElGamal.Ciphertext(mult_p(subject.ciphertext().pad, TWO_MOD_P),
            subject.ciphertext().data);
    Ballot.CiphertextBallotSelection malformed_encryption = new Ballot.CiphertextBallotSelection(
            subject.object_id, TWO_MOD_Q, malformed_message,
            subject.crypto_hash, subject.is_placeholder_selection, subject.nonce,
            subject.proof, subject.extended_data);

    // tamper with the proof
    assertThat(subject.proof).isPresent();
    ElementModP altered_a0 = mult_p(subject.proof.get().proof_zero_pad, TWO_MOD_P);
    ChaumPedersen.DisjunctiveChaumPedersenProof proof = subject.proof.get();
    ChaumPedersen.DisjunctiveChaumPedersenProof malformed_disjunctive = new ChaumPedersen.DisjunctiveChaumPedersenProof(
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
            subject.object_id, TWO_MOD_Q, malformed_message,
            subject.crypto_hash, subject.is_placeholder_selection, subject.nonce,
            Optional.of(malformed_disjunctive), subject.extended_data);

    Optional<Ballot.PlaintextBallotSelection> result_from_key_malformed_encryption = decrypt_selection_with_secret(
            malformed_encryption,
            description,
            keypair.public_key,
            keypair.secret_key,
            ONE_MOD_Q, false);

    Optional<Ballot.PlaintextBallotSelection> result_from_key_malformed_proof = decrypt_selection_with_secret(
            malformed_proof,
            description,
            keypair.public_key,
            keypair.secret_key,
            ONE_MOD_Q, false);

    Optional<Ballot.PlaintextBallotSelection> result_from_nonce_malformed_encryption = decrypt_selection_with_nonce(
            malformed_encryption, description, keypair.public_key, ONE_MOD_Q, Optional.empty(), false);
    Optional<Ballot.PlaintextBallotSelection> result_from_nonce_malformed_proof = decrypt_selection_with_nonce(
            malformed_proof, description, keypair.public_key, ONE_MOD_Q, Optional.empty(), false);

    // Assert
    assertThat(result_from_key_malformed_encryption).isEmpty();
    assertThat(result_from_key_malformed_proof).isEmpty();
    assertThat(result_from_nonce_malformed_encryption).isEmpty();
    assertThat(result_from_nonce_malformed_proof).isEmpty();
  }


  @Property(tries = 10, shrinking = ShrinkingMode.OFF)
  public void test_decrypt_selection_tampered_nonce_fails(
          @ForAll("elgamal_keypairs") ElGamal.KeyPair keypair,
          @ForAll("elements_mod_q_no_zero") Group.ElementModQ nonce_seed) {

    Election.SelectionDescription description = ElectionFactory.get_selection_description_well_formed().selection_description;
    Ballot.PlaintextBallotSelection data = BallotFactory.get_random_selection_from(description);

    // Act
    Optional<Ballot.CiphertextBallotSelection> subjectO = Encrypt.encrypt_selection(data, description, keypair.public_key, ONE_MOD_Q, nonce_seed, false, true);
    assertThat(subjectO).isPresent();
    Ballot.CiphertextBallotSelection subject = subjectO.get();

    // Tamper with the nonce by setting it to an arbitrary value
    Ballot.CiphertextBallotSelection bad_subject = new Ballot.CiphertextBallotSelection(
            subject.object_id, subject.description_hash, subject.ciphertext(),
            subject.crypto_hash, subject.is_placeholder_selection, Optional.of(nonce_seed),
            subject.proof, subject.extended_data);

    Optional<Ballot.PlaintextBallotSelection> result_from_nonce_seed =
            decrypt_selection_with_nonce(bad_subject, description, keypair.public_key, ONE_MOD_Q, Optional.of(nonce_seed), false);

    assertThat(result_from_nonce_seed).isEmpty();
  }

  @Property(tries = 10, shrinking = ShrinkingMode.OFF)
  public void test_decrypt_contest_valid_input_succeeds(
          @ForAll("elgamal_keypairs") ElGamal.KeyPair keypair,
          @ForAll("elements_mod_q_no_zero") Group.ElementModQ nonce_seed) {

    Election.ContestDescriptionWithPlaceholders description = ElectionFactory.get_contest_description_well_formed();
    Ballot.PlaintextBallotContest data = new BallotFactory().get_random_contest_from(description, false, false);

    List<Election.SelectionDescription> placeholders = Election.generate_placeholder_selections_from(description, description.number_elected);
    Election.ContestDescriptionWithPlaceholders description_with_placeholders = Election.contest_description_with_placeholders_from(description, placeholders);
    assertThat(description_with_placeholders.is_valid()).isTrue();

    Optional<Ballot.CiphertextBallotContest> subjectO = Encrypt.encrypt_contest(
            data,
            description_with_placeholders,
            keypair.public_key,
            ONE_MOD_Q,
            nonce_seed, true);
    assertThat(subjectO).isPresent();
    Ballot.CiphertextBallotContest subject = subjectO.get();

    // Decrypt the contest, but keep the placeholders so we can verify the selection count matches as expected in the test
    Optional<Ballot.PlaintextBallotContest> result_from_key = decrypt_contest_with_secret(
            subject,
            description_with_placeholders,
            keypair.public_key,
            keypair.secret_key,
            ONE_MOD_Q,
            false, false);
    Optional<Ballot.PlaintextBallotContest> result_from_nonce = DecryptWithSecrets.decrypt_contest_with_nonce(
            subject,
            description_with_placeholders,
            keypair.public_key,
            ONE_MOD_Q,
            Optional.empty(), false, false);

    Optional<Ballot.PlaintextBallotContest> result_from_nonce_seed = DecryptWithSecrets.decrypt_contest_with_nonce(
            subject,
            description_with_placeholders,
            keypair.public_key,
            ONE_MOD_Q,
            Optional.of(nonce_seed),
            false, false);


    assertThat(result_from_key).isPresent();
    assertThat(result_from_nonce).isPresent();
    assertThat(result_from_nonce_seed).isPresent();

    // The decrypted contest should include an entry for each possible selection// and placeholders for each seat
    int expected_entries = description.ballot_selections.size() + description.number_elected;
    assertThat(
            result_from_key.get().is_valid(
                    description.object_id,
                    expected_entries,
                    description.number_elected,
                    description.votes_allowed)).isTrue();

    assertThat(
            result_from_nonce.get().is_valid(
                    description.object_id,
                    expected_entries,
                    description.number_elected,
                    description.votes_allowed)).isTrue();

    assertThat(
            result_from_nonce_seed.get().is_valid(
                    description.object_id,
                    expected_entries,
                    description.number_elected,
                    description.votes_allowed)).isTrue();

    // Assert the ballot selections sum to the expected number of selections
    int key_selected = result_from_key.get().ballot_selections.stream().mapToInt(s -> s.to_int()).sum();
    int nonce_selected = result_from_nonce.get().ballot_selections.stream().mapToInt(s -> s.to_int()).sum();
    int seed_selected = result_from_nonce_seed.get().ballot_selections.stream().mapToInt(s -> s.to_int()).sum();

    assertThat(key_selected).isEqualTo(nonce_selected);
    assertThat(seed_selected).isEqualTo(nonce_selected);
    assertThat(description.number_elected).isEqualTo(key_selected);

    //Assert each selection is valid
    for (Election.SelectionDescription selection_description : description.ballot_selections) {

      Ballot.PlaintextBallotSelection key_selection = result_from_key.get().ballot_selections.stream()
              .filter(s -> s.selection_id.equals(selection_description.object_id)).findFirst().orElseThrow(IllegalStateException::new);

      Ballot.PlaintextBallotSelection nonce_selection = result_from_nonce.get().ballot_selections.stream()
              .filter(s -> s.selection_id.equals(selection_description.object_id)).findFirst().orElseThrow(IllegalStateException::new);

      Ballot.PlaintextBallotSelection seed_selection = result_from_nonce_seed.get().ballot_selections.stream()
              .filter(s -> s.selection_id.equals(selection_description.object_id)).findFirst().orElseThrow(IllegalStateException::new);

      List<Ballot.PlaintextBallotSelection> data_selections_exist = data.ballot_selections.stream()
              .filter(s -> s.selection_id.equals(selection_description.object_id)).collect(Collectors.toList());


      // It 's possible there are no selections in the original data collection
      // since it is valid to pass in a ballot that is not complete
      if (data_selections_exist.size() > 0) {
        assertThat(data_selections_exist.get(0).to_int()).isEqualTo(key_selection.to_int());
        assertThat(data_selections_exist.get(0).to_int()).isEqualTo(nonce_selection.to_int());
        assertThat(data_selections_exist.get(0).to_int()).isEqualTo(seed_selection.to_int());
      }

      // TODO: also check edge cases such as: placeholder selections are true for under votes

      assertThat(key_selection.is_valid(selection_description.object_id)).isTrue();
      assertThat(nonce_selection.is_valid(selection_description.object_id)).isTrue();
      assertThat(seed_selection.is_valid(selection_description.object_id)).isTrue();
    }
  }

  @Property(tries = 10, shrinking = ShrinkingMode.OFF)
  public void test_decrypt_contest_invalid_input_fails(
          @ForAll("elgamal_keypairs") ElGamal.KeyPair keypair,
          @ForAll("elements_mod_q_no_zero") Group.ElementModQ nonce_seed) {

    Election.ContestDescriptionWithPlaceholders description = ElectionFactory.get_contest_description_well_formed();
    Ballot.PlaintextBallotContest data = new BallotFactory().get_random_contest_from(description, false, false);

    List<Election.SelectionDescription> placeholders = Election.generate_placeholder_selections_from(description, description.number_elected);
    Election.ContestDescriptionWithPlaceholders description_with_placeholders = Election.contest_description_with_placeholders_from(description, placeholders);

    assertThat(description_with_placeholders.is_valid()).isTrue();

    Optional<Ballot.CiphertextBallotContest> subjectO = Encrypt.encrypt_contest(
            data,
            description_with_placeholders,
            keypair.public_key,
            ONE_MOD_Q,
            nonce_seed, true);
    assertThat(subjectO).isPresent();
    Ballot.CiphertextBallotContest subject = subjectO.get();

    // tamper with the nonce
    Ballot.CiphertextBallotContest bad_subject = new Ballot.CiphertextBallotContest(
            subject.object_id, subject.description_hash,
            subject.ballot_selections, subject.crypto_hash,
            new ElGamal.Ciphertext(TWO_MOD_P, TWO_MOD_P),
            Optional.of(int_to_q_unchecked(BigInteger.ONE)), subject.proof);

    Optional<Ballot.PlaintextBallotContest> result_from_nonce = DecryptWithSecrets.decrypt_contest_with_nonce(
            bad_subject,
            description_with_placeholders,
            keypair.public_key,
            ONE_MOD_Q,
            Optional.empty(),
            false, false);
    Optional<Ballot.PlaintextBallotContest> result_from_nonce_seed = DecryptWithSecrets.decrypt_contest_with_nonce(
            bad_subject,
            description_with_placeholders,
            keypair.public_key,
            ONE_MOD_Q,
            Optional.of(nonce_seed),
            false, false);

    assertThat(result_from_nonce).isEmpty();
    assertThat(result_from_nonce_seed).isEmpty();

    // Tamper with the encryption
    Ballot.CiphertextBallotSelection org = subject.ballot_selections.get(0);
    Ballot.CiphertextBallotSelection bad_selection = new Ballot.CiphertextBallotSelection(
            org.object_id, org.description_hash, new ElGamal.Ciphertext(TWO_MOD_P, TWO_MOD_P),
            org.crypto_hash, org.is_placeholder_selection, org.nonce,
            org.proof, org.extended_data);

    List<Ballot.CiphertextBallotSelection> bad_selections = new ArrayList<>(subject.ballot_selections);
    bad_selections.set(0, bad_selection);

    Ballot.CiphertextBallotContest bad_contest = new Ballot.CiphertextBallotContest(
            subject.object_id, subject.description_hash,
            bad_selections, subject.crypto_hash, new ElGamal.Ciphertext(TWO_MOD_P, TWO_MOD_P),
            Optional.of(int_to_q_unchecked(BigInteger.ONE)), subject.proof);

    Optional<Ballot.PlaintextBallotContest> result_from_key_tampered = decrypt_contest_with_secret(
            bad_contest,
            description_with_placeholders,
            keypair.public_key,
            keypair.secret_key,
            ONE_MOD_Q,
            false, false);
    Optional<Ballot.PlaintextBallotContest> result_from_nonce_tampered = DecryptWithSecrets.decrypt_contest_with_nonce(
            bad_contest,
            description_with_placeholders,
            keypair.public_key,
            ONE_MOD_Q,
            Optional.empty(),
            false, false);
    Optional<Ballot.PlaintextBallotContest> result_from_nonce_seed_tampered = DecryptWithSecrets.decrypt_contest_with_nonce(
            bad_contest,
            description_with_placeholders,
            keypair.public_key,
            ONE_MOD_Q,
            Optional.of(nonce_seed),
            false, false);

    assertThat(result_from_key_tampered).isEmpty();
    assertThat(result_from_nonce_tampered).isEmpty();
    assertThat(result_from_nonce_seed_tampered).isEmpty();
  }

  /**
   * Check that decryption works as expected by encrypting a ballot using the stateful `EncryptionMediator`
   * and then calling the various decrypt functions.
   */
  @Property(tries = 1, shrinking = ShrinkingMode.OFF)
  public void test_decrypt_ballot_valid_input_succeeds(
          @ForAll("elgamal_keypairs") ElGamal.KeyPair keypair,
          @ForAll("elements_mod_q_no_zero") Group.ElementModQ seed) throws IOException {

    // TODO: Hypothesis test instead

    Election.ElectionDescription election = ElectionFactory.get_simple_election_from_file();
    ElectionBuilder.DescriptionAndContext celection = ElectionFactory.get_fake_ciphertext_election(election, keypair.public_key).orElseThrow();
    Election.InternalElectionDescription metadata = celection.metadata;
    Election.CiphertextElectionContext context = celection.context;

    Ballot.PlaintextBallot data = new BallotFactory().get_simple_ballot_from_file();
    Encrypt.EncryptionDevice device = new Encrypt.EncryptionDevice("Location");
    Encrypt.EncryptionMediator operator = new Encrypt.EncryptionMediator(metadata, context, device);

    // Act
    Optional<Ballot.CiphertextBallot> subjectO = operator.encrypt(data);
    assertThat(subjectO).isPresent();
    Ballot.CiphertextBallot subject = subjectO.get();

    Optional<Ballot.PlaintextBallot> result_from_key = decrypt_ballot_with_secret(
            subject,
            metadata,
            context.crypto_extended_base_hash,
            keypair.public_key,
            keypair.secret_key,
            false, false);
    Optional<Ballot.PlaintextBallot> result_from_nonce = decrypt_ballot_with_nonce(
            subject,
            metadata,
            context.crypto_extended_base_hash,
            keypair.public_key,
            Optional.empty(),
            false, false);
    Optional<Ballot.PlaintextBallot> result_from_nonce_seed = decrypt_ballot_with_nonce(
            subject,
            metadata,
            context.crypto_extended_base_hash,
            keypair.public_key,
            subject.nonce,
            false, false);


    assertThat(result_from_key).isPresent();
    assertThat(result_from_nonce).isPresent();
    assertThat(result_from_nonce_seed).isPresent();
    assertThat(data.object_id).isEqualTo(subject.object_id);
    assertThat(data.object_id).isEqualTo(result_from_key.get().object_id);
    assertThat(data.object_id).isEqualTo(result_from_nonce.get().object_id);
    assertThat(data.object_id).isEqualTo(result_from_nonce_seed.get().object_id);

    for (Election.ContestDescriptionWithPlaceholders description : metadata.get_contests_for(data.ballot_style)) {
      int expected_entries = description.ballot_selections.size() + description.number_elected;

      Ballot.PlaintextBallotContest key_contest = result_from_key.get().contests.stream()
              .filter(c -> c.contest_id.equals(description.object_id)).findFirst().orElseThrow(IllegalStateException::new);

      Ballot.PlaintextBallotContest nonce_contest = result_from_nonce.get().contests.stream()
              .filter(c -> c.contest_id.equals(description.object_id)).findFirst().orElseThrow(IllegalStateException::new);

      Ballot.PlaintextBallotContest seed_contest = result_from_nonce_seed.get().contests.stream()
              .filter(c -> c.contest_id.equals(description.object_id)).findFirst().orElseThrow(IllegalStateException::new);

      List<Ballot.PlaintextBallotContest> data_contest_exists = data.contests.stream()
              .filter(c -> c.contest_id.equals(description.object_id)).collect(Collectors.toList());


      // Contests may not be voted on the ballot
      Ballot.PlaintextBallotContest data_contest = (data_contest_exists.size() > 0) ? data_contest_exists.get(0) : null;

      assertThat(
              key_contest.is_valid(
                      description.object_id,
                      expected_entries,
                      description.number_elected,
                      description.votes_allowed)
      ).isTrue();
      assertThat(
              nonce_contest.is_valid(
                      description.object_id,
                      expected_entries,
                      description.number_elected,
                      description.votes_allowed)
      ).isTrue();
      assertThat(
              seed_contest.is_valid(
                      description.object_id,
                      expected_entries,
                      description.number_elected,
                      description.votes_allowed)
      ).isTrue();

      for (Election.SelectionDescription selection_description : description.ballot_selections) {

        Ballot.PlaintextBallotSelection key_selection = key_contest.ballot_selections.stream()
                .filter(s -> s.selection_id.equals(selection_description.object_id)).findFirst().orElseThrow(IllegalStateException::new);

        Ballot.PlaintextBallotSelection nonce_selection = nonce_contest.ballot_selections.stream()
                .filter(s -> s.selection_id.equals(selection_description.object_id)).findFirst().orElseThrow(IllegalStateException::new);

        Ballot.PlaintextBallotSelection seed_selection = seed_contest.ballot_selections.stream()
                .filter(s -> s.selection_id.equals(selection_description.object_id)).findFirst().orElseThrow(IllegalStateException::new);

        // Selections may be undervoted for a specific contest
        List<Ballot.PlaintextBallotSelection> data_selection_exist = new ArrayList<>();

        if (data_contest != null) {
          data_selection_exist = data_contest.ballot_selections.stream()
                  .filter(s -> s.selection_id.equals(selection_description.object_id)).collect(Collectors.toList());
        }

        if (!data_selection_exist.isEmpty()) {
          Ballot.PlaintextBallotSelection data_selection = data_selection_exist.get(0);
          assertThat(data_selection.to_int()).isEqualTo(key_selection.to_int());
          assertThat(data_selection.to_int()).isEqualTo(nonce_selection.to_int());
          assertThat(data_selection.to_int()).isEqualTo(seed_selection.to_int());
        }

        // TODO: also check edge cases such as: placeholder selections are true for under votes

        assertThat(key_selection.is_valid(selection_description.object_id)).isTrue();
        assertThat(nonce_selection.is_valid(selection_description.object_id)).isTrue();
        assertThat(seed_selection.is_valid(selection_description.object_id)).isTrue();
      }
    }
  }

  @Property(tries = 1, shrinking = ShrinkingMode.OFF)
  public void test_decrypt_ballot_valid_input_missing_nonce_fails(
          @ForAll("elgamal_keypairs") ElGamal.KeyPair keypair) throws IOException {

    Election.ElectionDescription election = ElectionFactory.get_simple_election_from_file();
    ElectionBuilder.DescriptionAndContext celection = ElectionFactory.get_fake_ciphertext_election(election, keypair.public_key).orElseThrow();
    Election.InternalElectionDescription metadata = celection.metadata;
    Election.CiphertextElectionContext context = celection.context;

    Ballot.PlaintextBallot data = new BallotFactory().get_simple_ballot_from_file();
    Encrypt.EncryptionDevice device = new Encrypt.EncryptionDevice("Location");
    Encrypt.EncryptionMediator operator = new Encrypt.EncryptionMediator(metadata, context, device);

    // Act
    Optional<Ballot.CiphertextBallot> subjectO = operator.encrypt(data);
    assertThat(subjectO).isPresent();
    Ballot.CiphertextBallot subject = subjectO.get();

    // munge
    Ballot.CiphertextBallot bad_subject = new Ballot.CiphertextBallot(
            subject.object_id, subject.ballot_style, subject.description_hash,
            subject.previous_tracking_hash, subject.contests,
            subject.tracking_hash, subject.timestamp, subject.crypto_hash,
            Optional.empty());

    Optional<ElementModQ> missing_nonce_value = Optional.empty();

    Optional<Ballot.PlaintextBallot> result_from_nonce = decrypt_ballot_with_nonce(
            bad_subject,
            metadata,
            context.crypto_extended_base_hash,
            keypair.public_key,
            Optional.empty(), false, true
    );
    // This test is the same as the one above
    Optional<Ballot.PlaintextBallot> result_from_nonce_seed = decrypt_ballot_with_nonce(
            bad_subject,
            metadata,
            context.crypto_extended_base_hash,
            keypair.public_key,
            missing_nonce_value, false, true
    );

    assertThat(result_from_nonce).isEmpty();
    assertThat(result_from_nonce_seed).isEmpty();
  }

}
