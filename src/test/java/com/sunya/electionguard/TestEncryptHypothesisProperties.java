package com.sunya.electionguard;

import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.ShrinkingMode;

import java.util.*;
import java.util.stream.Collectors;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth8.assertThat;

public class TestEncryptHypothesisProperties extends TestProperties {
  private static final Group.ElementModQ SEED_HASH = new Encrypt.EncryptionDevice("Location").get_hash();

  /**
   * Tests that our Hypothesis election strategies generate "valid" output, also exercises the full stack
   * of `is_valid` methods.
   */
  @Property(tries = 10, shrinking = ShrinkingMode.OFF)
  public void test_generators_yield_valid_output(
          @ForAll("election_description") Election.ElectionDescription ed) {

    assertThat(ed.is_valid()).isTrue();
  }

  /**
   * Tests that decryption is the inverse of encryption over arbitrarily generated elections and ballots.
   * <p>
   * This test uses an abitrarily generated dataset with a single public-private keypair for the election
   * encryption context.  It also manually verifies that homomorphic accumulation works as expected.
   */
  @Property(tries = 5, shrinking = ShrinkingMode.OFF)
  public void test_accumulation_encryption_decryption(
          @ForAll("elections_and_ballots") ElectionTestHelper.EverythingTuple everything,
          @ForAll("elements_mod_q") Group.ElementModQ nonce) {

    Election.InternalElectionDescription metadata = everything.internal_election_description;
    List<Ballot.PlaintextBallot> ballots = everything.ballots;
    Group.ElementModQ secret_key = everything.secret_key;
    Election.CiphertextElectionContext context = everything.context;

    // Tally the plaintext ballots for comparison later
    Map<String, Integer> plaintext_tallies = TallyTestHelper.accumulate_plaintext_ballots(ballots);
    int num_ballots = ballots.size();
    int num_contests = metadata.contests.size();

    Nonces nonce_gen = new Nonces(nonce);
    Group.ElementModQ zero_nonce = nonce_gen.get(0);
    List<Group.ElementModQ> nonces = new ArrayList<>();
    for (int i=0; i<num_ballots; i++) {
      nonces.add(nonce_gen.get(i));
    }
    assertThat(metadata.contests.size() > 0).isTrue();

    // Generate a valid encryption of zero
    Optional<ElGamal.Ciphertext> encrypted_zero = ElGamal.elgamal_encrypt(0, zero_nonce, context.elgamal_public_key);
    assertThat(encrypted_zero).isPresent();

    List<Ballot.CiphertextBallot> encrypted_ballots = new ArrayList<>();

    // encrypt each ballot
    for (int i = 0; i < num_ballots; i++) {
      Optional<Ballot.CiphertextBallot> encrypted_ballotO = Encrypt.encrypt_ballot(
              ballots.get(i), metadata, context, SEED_HASH, Optional.of(nonces.get(i)), true);
      assertThat(encrypted_ballotO).isPresent();
      Ballot.CiphertextBallot encrypted_ballot = encrypted_ballotO.get();

      // sanity check the encryption
      assertThat(num_contests).isEqualTo(encrypted_ballot.contests.size());
      encrypted_ballots.add(encrypted_ballot);

      // decrypt the ballot with secret and verify it matches the plaintext
      Optional<Ballot.PlaintextBallot> decrypted_ballot = DecryptWithSecrets.decrypt_ballot_with_secret(
              encrypted_ballot,
              metadata,
              context.crypto_extended_base_hash,
              context.elgamal_public_key,
               secret_key,
              false, true);
      assertThat(decrypted_ballot).isPresent();
      assertThat(ballots.get(i)).isEqualTo(decrypted_ballot.get());
    }

    // homomorphically accumulate the encrypted ballot representations
    Map<String, ElGamal.Ciphertext> encrypted_tallies = _accumulate_encrypted_ballots(encrypted_zero.get(), encrypted_ballots);

    Map<String, Integer> decrypted_tallies = new HashMap<>();
    for (String object_id : encrypted_tallies.keySet()) {
      decrypted_tallies.put(object_id, encrypted_tallies.get(object_id).decrypt(secret_key));
    }

    // loop through the contest descriptions and verify
    // the decrypted tallies match the plaintext tallies
    for (Election.ContestDescriptionWithPlaceholders contest : metadata.contests) {
      // Sanity check the generated data
      assertThat(contest.ballot_selections).isNotEmpty();
      assertThat(contest.placeholder_selections).isNotEmpty();

      List<Integer> decrypted_selection_tallies = contest.ballot_selections.stream()
              .map(s -> decrypted_tallies.get(s.object_id)).collect(Collectors.toList());

      List<Integer> decrypted_placeholder_tallies = contest.placeholder_selections.stream()
              .map(p -> decrypted_tallies.get(p.object_id)).collect(Collectors.toList());

      List<Integer> plaintext_tally_values = contest.ballot_selections.stream()
              .map(s -> plaintext_tallies.get(s.object_id)).collect(Collectors.toList());

      // verify the plaintext tallies match the decrypted tallies
      assertThat(decrypted_selection_tallies).isEqualTo(plaintext_tally_values);

      // validate the right number of selections including placeholders across all ballots
      Integer total = decrypted_selection_tallies.stream().mapToInt(Integer::intValue).sum() +
                      decrypted_placeholder_tallies.stream().mapToInt(Integer::intValue).sum();
      assertThat(contest.number_elected * num_ballots).isEqualTo(total);
    }
  }


  /**
   * Internal helper function for testing: takes a list of encrypted ballots as input,
   * digs into all of the individual selections and then accumulates them, using
   * their `object_id` fields as keys. This function only knows what to do with
   * `n_of_m` elections. It's not a general-purpose tallying mechanism for other
   * election types.
   * <p>
   * Note that the output will include both "normal" and "placeholder" selections.
   * <p>
   * @param encrypted_zero: an encrypted zero, used for the accumulation
   * @param ballots: a list of encrypted ballots
   * @return a dict from selection object_id's to `ElGamalCiphertext` totals
   */
  private Map<String, ElGamal.Ciphertext> _accumulate_encrypted_ballots(
          ElGamal.Ciphertext encrypted_zero, List<Ballot.CiphertextBallot> ballots) {

    Map<String, ElGamal.Ciphertext> tally = new HashMap<>();

    for (Ballot.CiphertextBallot ballot : ballots) {
      for (Ballot.CiphertextBallotContest contest : ballot.contests) {
        for (Ballot.CiphertextBallotSelection selection : contest.ballot_selections) {
          String desc_id = (selection.object_id); // this should be the same as in the PlaintextBallot!
          if (!tally.containsKey(desc_id)) {
            tally.put(desc_id, encrypted_zero);
          }
          tally.put(desc_id, ElGamal.elgamal_add(tally.get(desc_id), selection.ciphertext()));
        }
      }
    }
    return tally;
  }

}
