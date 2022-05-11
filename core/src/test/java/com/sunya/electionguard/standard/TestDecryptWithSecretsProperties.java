package com.sunya.electionguard.standard;

import com.sunya.electionguard.BallotFactory;
import com.sunya.electionguard.ChaumPedersen;
import com.sunya.electionguard.CiphertextBallot;
import com.sunya.electionguard.ElectionCryptoContext;
import com.sunya.electionguard.ElGamal;
import com.sunya.electionguard.ElectionBuilder;
import com.sunya.electionguard.ElectionFactory;
import com.sunya.electionguard.Encrypt;
import com.sunya.electionguard.InternalManifest;
import com.sunya.electionguard.Manifest;
import com.sunya.electionguard.PlaintextBallot;
import com.sunya.electionguard.TestProperties;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.ShrinkingMode;

import java.io.IOException;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth8.assertThat;
import static com.sunya.electionguard.Group.*;
import static com.sunya.electionguard.standard.DecryptWithSecrets.*;
import static com.sunya.electionguard.InternalManifest.ContestWithPlaceholders;

public class TestDecryptWithSecretsProperties extends TestProperties {

  //// TestDisjunctiveChaumPedersen

  @Property(tries = 10, shrinking = ShrinkingMode.OFF)
  public void test_decrypt_selection_valid_input_succeeds(
          @ForAll("selection_description") Manifest.SelectionDescription description,
          @ForAll("elgamal_keypairs") ElGamal.KeyPair keypair,
          @ForAll("elements_mod_q_no_zero") ElementModQ nonce_seed) {

    PlaintextBallot.Selection data = BallotFactory.get_random_selection_from(description);

    Optional<CiphertextBallot.Selection> subjectO = Encrypt.encrypt_selection(
            "test", data, description, keypair.public_key(), ONE_MOD_Q, nonce_seed, false, true);
    assertThat(subjectO).isPresent();
    CiphertextBallot.Selection subject = subjectO.get();

    Optional<PlaintextBallot.Selection> result_from_key =
            decrypt_selection_with_secret("test", subject, description, keypair.public_key(), keypair.secret_key(), ONE_MOD_Q, false);
    Optional<PlaintextBallot.Selection> result_from_nonce =
            decrypt_selection_with_nonce("test", subject, description, keypair.public_key(), ONE_MOD_Q, Optional.empty(), false);
    Optional<PlaintextBallot.Selection> result_from_nonce_seed =
            decrypt_selection_with_nonce("test", subject, description, keypair.public_key(), ONE_MOD_Q, Optional.of(nonce_seed), false);

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
          @ForAll("elements_mod_q_no_zero") ElementModQ seed) {

    Manifest.SelectionDescription description = ElectionFactory.get_selection_description_well_formed().selection_description;
    PlaintextBallot.Selection data = BallotFactory.get_random_selection_from(description);

    // Act
    Optional<CiphertextBallot.Selection> subjectO = Encrypt.encrypt_selection("test", data, description, keypair.public_key(), ONE_MOD_Q, seed, false, true);
    assertThat(subjectO).isPresent();
    CiphertextBallot.Selection subject = subjectO.get();

    // tamper with the encryption
    ElGamal.Ciphertext malformed_message = new ElGamal.Ciphertext(mult_p(subject.ciphertext().pad(), TWO_MOD_P),
            subject.ciphertext().data());
    CiphertextBallot.Selection malformed_encryption = new CiphertextBallot.Selection(
            subject.object_id(), subject.sequence_order(), TWO_MOD_Q, malformed_message,
            subject.crypto_hash, subject.is_placeholder_selection, subject.nonce,
            subject.proof, subject.extended_data);

    // tamper with the proof
    assertThat(subject.proof).isPresent();
    ElementModP altered_a0 = mult_p(subject.proof.get().proof0.pad, TWO_MOD_P);
    ChaumPedersen.DisjunctiveChaumPedersenProof proof = subject.proof.get();
    ChaumPedersen.DisjunctiveChaumPedersenProof malformed_disjunctive = new ChaumPedersen.DisjunctiveChaumPedersenProof(
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
            subject.object_id(), subject.sequence_order(), TWO_MOD_Q, malformed_message,
            subject.crypto_hash, subject.is_placeholder_selection, subject.nonce,
            Optional.of(malformed_disjunctive), subject.extended_data);

    Optional<PlaintextBallot.Selection> result_from_key_malformed_encryption = decrypt_selection_with_secret(
            "test", malformed_encryption,
            description,
            keypair.public_key(),
            keypair.secret_key(),
            ONE_MOD_Q, false);

    Optional<PlaintextBallot.Selection> result_from_key_malformed_proof = decrypt_selection_with_secret(
            "test", malformed_proof,
            description,
            keypair.public_key(),
            keypair.secret_key(),
            ONE_MOD_Q, false);

    Optional<PlaintextBallot.Selection> result_from_nonce_malformed_encryption = decrypt_selection_with_nonce(
            "test", malformed_encryption, description, keypair.public_key(), ONE_MOD_Q, Optional.empty(), false);
    Optional<PlaintextBallot.Selection> result_from_nonce_malformed_proof = decrypt_selection_with_nonce(
            "test", malformed_proof, description, keypair.public_key(), ONE_MOD_Q, Optional.empty(), false);

    // Assert
    assertThat(result_from_key_malformed_encryption).isEmpty();
    assertThat(result_from_key_malformed_proof).isEmpty();
    assertThat(result_from_nonce_malformed_encryption).isEmpty();
    assertThat(result_from_nonce_malformed_proof).isEmpty();
  }


  @Property(tries = 10, shrinking = ShrinkingMode.OFF)
  public void test_decrypt_selection_tampered_nonce_fails(
          @ForAll("elgamal_keypairs") ElGamal.KeyPair keypair,
          @ForAll("elements_mod_q_no_zero") ElementModQ nonce_seed) {

    Manifest.SelectionDescription description = ElectionFactory.get_selection_description_well_formed().selection_description;
    PlaintextBallot.Selection data = BallotFactory.get_random_selection_from(description);

    // Act
    Optional<CiphertextBallot.Selection> subjectO = Encrypt.encrypt_selection("test", data, description, keypair.public_key(), ONE_MOD_Q, nonce_seed, false, true);
    assertThat(subjectO).isPresent();
    CiphertextBallot.Selection subject = subjectO.get();

    // Tamper with the nonce by setting it to an arbitrary value
    CiphertextBallot.Selection bad_subject = new CiphertextBallot.Selection(
            subject.object_id(), subject.sequence_order(), subject.description_hash(), subject.ciphertext(),
            subject.crypto_hash, subject.is_placeholder_selection, Optional.of(nonce_seed),
            subject.proof, subject.extended_data);

    Optional<PlaintextBallot.Selection> result_from_nonce_seed =
            decrypt_selection_with_nonce("test", bad_subject, description, keypair.public_key(), ONE_MOD_Q, Optional.of(nonce_seed), false);

    assertThat(result_from_nonce_seed).isEmpty();
  }

  @Property(tries = 10, shrinking = ShrinkingMode.OFF)
  public void test_decrypt_contest_valid_input_succeeds(
          @ForAll("elgamal_keypairs") ElGamal.KeyPair keypair,
          @ForAll("elements_mod_q_no_zero") ElementModQ nonce_seed) {

    ContestWithPlaceholders contestp = ElectionFactory.get_contest_description_well_formed();
    Manifest.ContestDescription contest = contestp.contest;
    PlaintextBallot.Contest data = new BallotFactory().get_random_contest_from(contestp.contest, false, false);

    List<Manifest.SelectionDescription> placeholders = InternalManifest.generate_placeholder_selections_from(contest, contest.numberElected());
    ContestWithPlaceholders description_with_placeholders = new ContestWithPlaceholders(contestp.contest, placeholders);
    assertThat(description_with_placeholders.is_valid()).isTrue();

    Optional<CiphertextBallot.Contest> subjectO = Encrypt.encrypt_contest(
            "test", data,
            description_with_placeholders,
            keypair.public_key(),
            ONE_MOD_Q,
            nonce_seed, true);
    assertThat(subjectO).isPresent();
    CiphertextBallot.Contest subject = subjectO.get();

    // Decrypt the contest, but keep the placeholders so we can verify the selection count matches as expected in the test
    Optional<PlaintextBallot.Contest> result_from_key = decrypt_contest_with_secret(
            "test", subject,
            description_with_placeholders,
            keypair.public_key(),
            keypair.secret_key(),
            ONE_MOD_Q,
            false, false);
    Optional<PlaintextBallot.Contest> result_from_nonce = DecryptWithSecrets.decrypt_contest_with_nonce(
            "test", subject,
            description_with_placeholders,
            keypair.public_key(),
            ONE_MOD_Q,
            Optional.empty(), false, false);

    Optional<PlaintextBallot.Contest> result_from_nonce_seed = DecryptWithSecrets.decrypt_contest_with_nonce(
            "test", subject,
            description_with_placeholders,
            keypair.public_key(),
            ONE_MOD_Q,
            Optional.of(nonce_seed),
            false, false);


    assertThat(result_from_key).isPresent();
    assertThat(result_from_nonce).isPresent();
    assertThat(result_from_nonce_seed).isPresent();

    // The decrypted contest should include an entry for each possible selection// and placeholders for each seat
    int expected_entries = contest.selections().size() + contest.numberElected();
    assertThat(
            result_from_key.get().is_valid(
                    contest.contestId(),
                    expected_entries,
                    contest.numberElected(),
                    contest.votesAllowed())).isTrue();

    assertThat(
            result_from_nonce.get().is_valid(
                    contest.contestId(),
                    expected_entries,
                    contest.numberElected(),
                    contest.votesAllowed())).isTrue();

    assertThat(
            result_from_nonce_seed.get().is_valid(
                    contest.contestId(),
                    expected_entries,
                    contest.numberElected(),
                    contest.votesAllowed())).isTrue();

    // Assert the ballot selections sum to the expected number of selections
    int key_selected = result_from_key.get().selections.stream().mapToInt(s -> s.vote).sum();
    int nonce_selected = result_from_nonce.get().selections.stream().mapToInt(s -> s.vote).sum();
    int seed_selected = result_from_nonce_seed.get().selections.stream().mapToInt(s -> s.vote).sum();

    assertThat(key_selected).isEqualTo(nonce_selected);
    assertThat(seed_selected).isEqualTo(nonce_selected);
    assertThat(contest.numberElected()).isEqualTo(key_selected);

    //Assert each selection is valid
    for (Manifest.SelectionDescription selection_description : contest.selections()) {

      PlaintextBallot.Selection key_selection = result_from_key.get().selections.stream()
              .filter(s -> s.selectionId.equals(selection_description.selectionId())).findFirst().orElseThrow(IllegalStateException::new);

      PlaintextBallot.Selection nonce_selection = result_from_nonce.get().selections.stream()
              .filter(s -> s.selectionId.equals(selection_description.selectionId())).findFirst().orElseThrow(IllegalStateException::new);

      PlaintextBallot.Selection seed_selection = result_from_nonce_seed.get().selections.stream()
              .filter(s -> s.selectionId.equals(selection_description.selectionId())).findFirst().orElseThrow(IllegalStateException::new);

      List<PlaintextBallot.Selection> data_selections_exist = data.selections.stream()
              .filter(s -> s.selectionId.equals(selection_description.selectionId())).toList();


      // It 's possible there are no selections in the original data collection
      // since it is valid to pass in a ballot that is not complete
      if (data_selections_exist.size() > 0) {
        assertThat(data_selections_exist.get(0).vote).isEqualTo(key_selection.vote);
        assertThat(data_selections_exist.get(0).vote).isEqualTo(nonce_selection.vote);
        assertThat(data_selections_exist.get(0).vote).isEqualTo(seed_selection.vote);
      }

      // TODO: also check edge cases such as: placeholder selections are true for under votes

      assertThat(key_selection.is_valid(selection_description.selectionId())).isTrue();
      assertThat(nonce_selection.is_valid(selection_description.selectionId())).isTrue();
      assertThat(seed_selection.is_valid(selection_description.selectionId())).isTrue();
    }
  }

  @Property(tries = 10, shrinking = ShrinkingMode.OFF)
  public void test_decrypt_contest_invalid_input_fails(
          @ForAll("elgamal_keypairs") ElGamal.KeyPair keypair,
          @ForAll("elements_mod_q_no_zero") ElementModQ nonce_seed) {

    ContestWithPlaceholders contestp = ElectionFactory.get_contest_description_well_formed();
    Manifest.ContestDescription contest = contestp.contest;
    PlaintextBallot.Contest data = new BallotFactory().get_random_contest_from(contest, false, false);

    List<Manifest.SelectionDescription> placeholders = InternalManifest.generate_placeholder_selections_from(contest, contest.numberElected());
    ContestWithPlaceholders description_with_placeholders = new ContestWithPlaceholders(contest, placeholders);

    assertThat(description_with_placeholders.is_valid()).isTrue();

    Optional<CiphertextBallot.Contest> subjectO = Encrypt.encrypt_contest(
            "test", data,
            description_with_placeholders,
            keypair.public_key(),
            ONE_MOD_Q,
            nonce_seed, true);
    assertThat(subjectO).isPresent();
    CiphertextBallot.Contest subject = subjectO.get();

    // tamper with the nonce
    CiphertextBallot.Contest bad_subject = new CiphertextBallot.Contest(
            subject.object_id(), subject.sequence_order(), subject.contestHash,
            subject.selections, subject.crypto_hash,
            Optional.of(int_to_q_unchecked(BigInteger.ONE)), subject.proof);

    Optional<PlaintextBallot.Contest> result_from_nonce = DecryptWithSecrets.decrypt_contest_with_nonce(
            "test", bad_subject,
            description_with_placeholders,
            keypair.public_key(),
            ONE_MOD_Q,
            Optional.empty(),
            false, false);
    Optional<PlaintextBallot.Contest> result_from_nonce_seed = DecryptWithSecrets.decrypt_contest_with_nonce(
            "test", bad_subject,
            description_with_placeholders,
            keypair.public_key(),
            ONE_MOD_Q,
            Optional.of(nonce_seed),
            false, false);

    assertThat(result_from_nonce).isEmpty();
    assertThat(result_from_nonce_seed).isEmpty();

    // Tamper with the encryption
    CiphertextBallot.Selection org = subject.selections.get(0);
    CiphertextBallot.Selection bad_selection = new CiphertextBallot.Selection(
            org.object_id(), org.sequence_order(), org.description_hash(), new ElGamal.Ciphertext(TWO_MOD_P, TWO_MOD_P),
            org.crypto_hash, org.is_placeholder_selection, org.nonce,
            org.proof, org.extended_data);

    List<CiphertextBallot.Selection> bad_selections = new ArrayList<>(subject.selections);
    bad_selections.set(0, bad_selection);

    CiphertextBallot.Contest bad_contest = new CiphertextBallot.Contest(
            subject.object_id(), subject.sequence_order(), subject.contestHash,
            bad_selections, subject.crypto_hash,
            Optional.of(int_to_q_unchecked(BigInteger.ONE)), subject.proof);

    Optional<PlaintextBallot.Contest> result_from_key_tampered = decrypt_contest_with_secret(
            "test", bad_contest,
            description_with_placeholders,
            keypair.public_key(),
            keypair.secret_key(),
            ONE_MOD_Q,
            false, false);
    Optional<PlaintextBallot.Contest> result_from_nonce_tampered = DecryptWithSecrets.decrypt_contest_with_nonce(
            "test", bad_contest,
            description_with_placeholders,
            keypair.public_key(),
            ONE_MOD_Q,
            Optional.empty(),
            false, false);
    Optional<PlaintextBallot.Contest> result_from_nonce_seed_tampered = DecryptWithSecrets.decrypt_contest_with_nonce(
            "test", bad_contest,
            description_with_placeholders,
            keypair.public_key(),
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
          @ForAll("elements_mod_q_no_zero") ElementModQ seed) throws IOException {

    // TODO: Hypothesis test instead

    Manifest election = ElectionFactory.get_simple_election_from_file();
    ElectionBuilder.DescriptionAndContext celection = ElectionFactory.get_fake_ciphertext_election(election, keypair.public_key()).orElseThrow();
    InternalManifest metadata = celection.internalManifest;
    ElectionCryptoContext context = celection.context;

    PlaintextBallot data = new BallotFactory().get_simple_ballot_from_file();
    Encrypt.EncryptionDevice device = Encrypt.createDeviceForTest("Location");
    Encrypt.EncryptionMediator operator = new Encrypt.EncryptionMediator(metadata, context, device);

    // Act
    Optional<CiphertextBallot> subjectO = operator.encrypt(data);
    assertThat(subjectO).isPresent();
    CiphertextBallot subject = subjectO.get();

    Optional<PlaintextBallot> result_from_key = decrypt_ballot_with_secret(
            subject,
            metadata,
            context.cryptoExtendedBaseHash,
            keypair.public_key(),
            keypair.secret_key(),
            false, false);
    Optional<PlaintextBallot> result_from_nonce = decrypt_ballot_with_nonce(
            subject,
            metadata,
            context.cryptoExtendedBaseHash,
            keypair.public_key(),
            Optional.empty(),
            false, false);
    Optional<PlaintextBallot> result_from_nonce_seed = decrypt_ballot_with_nonce(
            subject,
            metadata,
            context.cryptoExtendedBaseHash,
            keypair.public_key(),
            subject.nonce,
            false, false);


    assertThat(result_from_key).isPresent();
    assertThat(result_from_nonce).isPresent();
    assertThat(result_from_nonce_seed).isPresent();
    assertThat(data.object_id()).isEqualTo(subject.object_id());
    assertThat(data.object_id()).isEqualTo(result_from_key.get().object_id());
    assertThat(data.object_id()).isEqualTo(result_from_nonce.get().object_id());
    assertThat(data.object_id()).isEqualTo(result_from_nonce_seed.get().object_id());

    for (ContestWithPlaceholders contestp : metadata.get_contests_for_style(data.ballotStyleId)) {
      Manifest.ContestDescription contest = contestp.contest;
      int expected_entries = contest.selections().size() + contest.numberElected();

      PlaintextBallot.Contest key_contest = result_from_key.get().contests.stream()
              .filter(c -> c.contestId.equals(contest.contestId())).findFirst().orElseThrow(IllegalStateException::new);

      PlaintextBallot.Contest nonce_contest = result_from_nonce.get().contests.stream()
              .filter(c -> c.contestId.equals(contest.contestId())).findFirst().orElseThrow(IllegalStateException::new);

      PlaintextBallot.Contest seed_contest = result_from_nonce_seed.get().contests.stream()
              .filter(c -> c.contestId.equals(contest.contestId())).findFirst().orElseThrow(IllegalStateException::new);

      List<PlaintextBallot.Contest> data_contest_exists = data.contests.stream()
              .filter(c -> c.contestId.equals(contest.contestId())).toList();


      // Contests may not be voted on the ballot
      PlaintextBallot.Contest data_contest = (data_contest_exists.size() > 0) ? data_contest_exists.get(0) : null;

      assertThat(
              key_contest.is_valid(
                      contest.contestId(),
                      expected_entries,
                      contest.numberElected(),
                      contest.votesAllowed())
      ).isTrue();
      assertThat(
              nonce_contest.is_valid(
                      contest.contestId(),
                      expected_entries,
                      contest.numberElected(),
                      contest.votesAllowed())
      ).isTrue();
      assertThat(
              seed_contest.is_valid(
                      contest.contestId(),
                      expected_entries,
                      contest.numberElected(),
                      contest.votesAllowed())
      ).isTrue();

      for (Manifest.SelectionDescription selection_description : contest.selections()) {

        PlaintextBallot.Selection key_selection = key_contest.selections.stream()
                .filter(s -> s.selectionId.equals(selection_description.selectionId())).findFirst().orElseThrow(IllegalStateException::new);

        PlaintextBallot.Selection nonce_selection = nonce_contest.selections.stream()
                .filter(s -> s.selectionId.equals(selection_description.selectionId())).findFirst().orElseThrow(IllegalStateException::new);

        PlaintextBallot.Selection seed_selection = seed_contest.selections.stream()
                .filter(s -> s.selectionId.equals(selection_description.selectionId())).findFirst().orElseThrow(IllegalStateException::new);

        // Selections may be undervoted for a specific contest
        List<PlaintextBallot.Selection> data_selection_exist = new ArrayList<>();

        if (data_contest != null) {
          data_selection_exist = data_contest.selections.stream()
                  .filter(s -> s.selectionId.equals(selection_description.selectionId())).toList();
        }

        if (!data_selection_exist.isEmpty()) {
          PlaintextBallot.Selection data_selection = data_selection_exist.get(0);
          assertThat(data_selection.vote).isEqualTo(key_selection.vote);
          assertThat(data_selection.vote).isEqualTo(nonce_selection.vote);
          assertThat(data_selection.vote).isEqualTo(seed_selection.vote);
        }

        // TODO: also check edge cases such as: placeholder selections are true for under votes

        assertThat(key_selection.is_valid(selection_description.selectionId())).isTrue();
        assertThat(nonce_selection.is_valid(selection_description.selectionId())).isTrue();
        assertThat(seed_selection.is_valid(selection_description.selectionId())).isTrue();
      }
    }
  }

  @Property(tries = 1, shrinking = ShrinkingMode.OFF)
  public void test_decrypt_ballot_valid_input_missing_nonce_fails(
          @ForAll("elgamal_keypairs") ElGamal.KeyPair keypair) throws IOException {

    Manifest election = ElectionFactory.get_simple_election_from_file();
    ElectionBuilder.DescriptionAndContext celection = ElectionFactory.get_fake_ciphertext_election(election, keypair.public_key()).orElseThrow();
    InternalManifest metadata = celection.internalManifest;
    ElectionCryptoContext context = celection.context;

    PlaintextBallot data = new BallotFactory().get_simple_ballot_from_file();
    Encrypt.EncryptionDevice device = Encrypt.createDeviceForTest("Location");
    Encrypt.EncryptionMediator operator = new Encrypt.EncryptionMediator(metadata, context, device);

    // Act
    Optional<CiphertextBallot> subjectO = operator.encrypt(data);
    assertThat(subjectO).isPresent();
    CiphertextBallot subject = subjectO.get();

    // munge
    CiphertextBallot bad_subject = new CiphertextBallot(
            subject.object_id(), subject.ballotStyleId, subject.manifestHash,
            subject.code_seed, subject.contests,
            subject.code, subject.timestamp, subject.crypto_hash,
            Optional.empty());

    Optional<ElementModQ> missing_nonce_value = Optional.empty();

    Optional<PlaintextBallot> result_from_nonce = decrypt_ballot_with_nonce(
            bad_subject,
            metadata,
            context.cryptoExtendedBaseHash,
            keypair.public_key(),
            Optional.empty(), false, true
    );
    // This test is the same as the one above
    Optional<PlaintextBallot> result_from_nonce_seed = decrypt_ballot_with_nonce(
            bad_subject,
            metadata,
            context.cryptoExtendedBaseHash,
            keypair.public_key(),
            missing_nonce_value, false, true
    );

    assertThat(result_from_nonce).isEmpty();
    assertThat(result_from_nonce_seed).isEmpty();
  }

}
