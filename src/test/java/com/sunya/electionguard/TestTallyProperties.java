package com.sunya.electionguard;

import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.ShrinkingMode;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth8.assertThat;

import static com.sunya.electionguard.Ballot.*;
import static com.sunya.electionguard.Tally.*;


public class TestTallyProperties extends TestProperties {

  @Property(tries = 3, shrinking = ShrinkingMode.OFF)
  public void test_tally_cast_ballots_accumulates_valid_tally(
          @ForAll("elections_and_ballots") ElectionTestHelper.EverythingTuple everything) {

    // election_description, metadata, ballots, secret_key, context = everything
    // Tally the plaintext ballots for comparison later
    Map<String, BigInteger> plaintext_tallies = TallyTestHelper.accumulate_plaintext_ballots(everything.ballots);

    // encrypt each ballot
    DataStore store = new DataStore();
    Group.ElementModQ seed_hash = new Encrypt.EncryptionDevice("Location").get_hash();
    for (PlaintextBallot ballot : everything.ballots) {
      Optional<CiphertextBallot> encrypted_ballotO = Encrypt.encrypt_ballot(
              ballot, everything.internal_election_description, everything.context, seed_hash, Optional.empty(), true);
      assertThat(encrypted_ballotO).isPresent();
      CiphertextBallot encrypted_ballot = encrypted_ballotO.get();
      seed_hash = encrypted_ballot.tracking_hash.get();
      // add to the ballot store
      store.put(encrypted_ballot.object_id, from_ciphertext_ballot(encrypted_ballot, BallotBoxState.CAST));
    }

    Optional<CiphertextTally> result = tally_ballots(store, everything.internal_election_description, everything.context);
    assertThat(result).isPresent();

    Map<String, BigInteger> decrypted_tallies = this._decrypt_with_secret(result.get(), everything.secret_key);
    assertThat(plaintext_tallies).isEqualTo(decrypted_tallies);
  }

  @Property(tries = 3, shrinking = ShrinkingMode.OFF)
  public void test_tally_spoiled_ballots_accumulates_valid_tally(
          @ForAll("elections_and_ballots") ElectionTestHelper.EverythingTuple everything) {

    // election_description, metadata, ballots, secret_key, context = everything
    //  Tally the plaintext ballots for comparison later
    Map<String, BigInteger> plaintext_tallies = TallyTestHelper.accumulate_plaintext_ballots(everything.ballots);
    System.out.printf("%n test_tally_spoiled_ballots_accumulates_valid_tally expected %s%n", plaintext_tallies);

    // encrypt each ballot
    DataStore store = new DataStore();
    Group.ElementModQ seed_hash = new Encrypt.EncryptionDevice("Location").get_hash();
    for (PlaintextBallot ballot : everything.ballots) {
      Optional<CiphertextBallot> encrypted_ballotO = Encrypt.encrypt_ballot(
              ballot, everything.internal_election_description, everything.context, seed_hash, Optional.empty(), true);
      assertThat(encrypted_ballotO).isPresent();
      CiphertextBallot encrypted_ballot = encrypted_ballotO.get();
      seed_hash = encrypted_ballot.tracking_hash.get();
      // add to the ballot store
      store.put(encrypted_ballot.object_id, from_ciphertext_ballot(encrypted_ballot, BallotBoxState.SPOILED));
    }

    Optional<CiphertextTally> result = tally_ballots(store, everything.internal_election_description, everything.context);
    assertThat(result).isPresent();

    Map<String, BigInteger> decrypted_tallies = this._decrypt_with_secret(result.get(), everything.secret_key);
    System.out.printf("%n test_tally_spoiled_ballots_accumulates_valid_tally decrypted_tallies %s%n%n", decrypted_tallies);

    // This cant be true since all ballots are spoiled ans so have a count of 0
    // assertThat(plaintext_tallies).containsExactlyEntriesIn(decrypted_tallies);

    // self.assertCountEqual(plaintext_tallies, decrypted_tallies)
    assertThat(plaintext_tallies.keySet()).isEqualTo(decrypted_tallies.keySet());

    for (BigInteger value : decrypted_tallies.values()) {
      assertThat(value).isEqualTo(BigInteger.ZERO);
    }
    assertThat(result.get().spoiled_ballots().size()).isEqualTo(everything.ballots.size());
  }

  /*
  @Property(tries = 3, shrinking = ShrinkingMode.OFF)
  public void test_tally_ballot_invalid_input_fails(
          @ForAll("elections_and_ballots") ElectionTestHelper.EverythingTuple everything) {

    // election_description, metadata, ballots, secret_key, context = everything

    // encrypt each ballot
    DataStore store = new DataStore();
    Group.ElementModQ seed_hash = new Encrypt.EncryptionDevice("Location").get_hash();
    for (PlaintextBallot ballot : everything.ballots) {
      Optional<CiphertextBallot> encrypted_ballotO = Encrypt.encrypt_ballot(
              ballot, everything.internal_election_description, everything.context, seed_hash, Optional.empty(), true);
      assertThat(encrypted_ballotO).isPresent();
      CiphertextBallot encrypted_ballot = encrypted_ballotO.get();
      seed_hash = encrypted_ballot.tracking_hash.get();
      // add to the ballot store
      store.put(encrypted_ballot.object_id, from_ciphertext_ballot(encrypted_ballot, BallotBoxState.CAST));
    }

    CiphertextTally subject = new CiphertextTally("my-tally", everything.internal_election_description, everything.context);

    CiphertextAcceptedBallot first_ballot = store.iterator().next();
    first_ballot.state = BallotBoxState.UNKNOWN;

    //  verify an UNKNOWN state ballot fails
    assertThat(Tally.tally_ballot(first_ballot, subject)).isEmpty();
    assertThat(subject.append(first_ballot)).isFalse();

    //  cast a ballot
    first_ballot.state = BallotBoxState.CAST;
    assertThat(subject.append(first_ballot)).isTrue();

    //  try to append a spoiled ballot
    first_ballot.state = BallotBoxState.SPOILED;
    assertThat(subject.append(first_ballot)).isFalse();

    //  Verify accumulation fails if the selection collection is empty
    if (first_ballot.state == BallotBoxState.CAST) {
      assertThat(subject.cast.get(first_ballot.object_id).accumulate_contest(new ArrayList<>())).isFalse();
    }

    // LOOK Im guessing this means remove the ballot with the given id, so that it can be added again
    //  LOOK pop the cast ballot
    subject._cast_ballot_ids.remove(first_ballot.object_id);

    //  reset to cast
    first_ballot.state = BallotBoxState.CAST;

    assertThat(_cannot_erroneously_mutate_state(subject, first_ballot, BallotBoxState.CAST)).isTrue();
    assertThat(_cannot_erroneously_mutate_state(subject, first_ballot, BallotBoxState.SPOILED)).isTrue();
    assertThat(_cannot_erroneously_mutate_state(subject, first_ballot, BallotBoxState.UNKNOWN)).isTrue();

    //  verify a spoiled ballot cannot be added twice
    first_ballot.state = BallotBoxState.SPOILED;
    assertThat(subject.append(first_ballot)).isTrue();
    assertThat(subject.append(first_ballot)).isFalse();

    //  verify an already spoiled ballot cannot be cast
    first_ballot.state = BallotBoxState.CAST;
    assertThat(subject.append(first_ballot)).isFalse();

    // TODO Im guessing this means remove the ballot with the given id, so that it can be added again
    //  pop the spoiled ballot
    subject.spoiled_ballots.remove(first_ballot.object_id);

    //  verify a cast ballot cannot be added twice
    first_ballot.state = BallotBoxState.CAST;
    assertThat(subject.append(first_ballot)).isTrue();
    assertThat(subject.append(first_ballot)).isFalse();

    //  verify an already cast ballot cannot be spoiled
    first_ballot.state = BallotBoxState.SPOILED;
    assertThat(subject.append(first_ballot)).isFalse();
  } */


  /**
   * Demonstrates how to decrypt a tally with a known secret key.
   */
  private Map<String, BigInteger> _decrypt_with_secret(CiphertextTally tally, Group.ElementModQ secret_key) {
    Map<String, BigInteger> plaintext_selections = new HashMap<>();
    for (CiphertextTallyContest contest : tally.cast().values()) {
      for (Map.Entry<String, CiphertextTallySelection> entry : contest.tally_selections().entrySet()) {
        BigInteger plaintext_tally = entry.getValue().ciphertext().decrypt(secret_key);
        plaintext_selections.put(entry.getKey(), plaintext_tally);
      }
    }
    return plaintext_selections;
  }

  // LOOK this assumes mutability, fix
  private boolean _cannot_erroneously_mutate_state(
          CiphertextTally subject,
          CiphertextAcceptedBallot ballot,
          BallotBoxState state_to_test) {

    /*
    BallotBoxState input_state = ballot.state;
    ballot.state = state_to_test;

    // remove the first selection
    CiphertextBallotContest first_contest = ballot.contests.get(0);
    CiphertextBallotSelection first_selection = first_contest.ballot_selections.get(0);
    first_contest.ballot_selections.remove(first_selection);

    assertThat(tally_ballot(ballot, subject)).isEmpty();
    assertThat(subject.append(ballot)).isFalse();

    // Verify accumulation fails if the selection count does not match
    if (ballot.state == BallotBoxState.CAST) {
      CiphertextTallyContest first_tally = subject.cast.get(first_contest.object_id);
      assertThat(first_tally.accumulate_contest(first_contest.ballot_selections)).isFalse();

      Tally.Tuple tuple = first_tally._accumulate_selections(
              first_selection.object_id,
              first_tally.tally_selections.get(first_selection.object_id),
              first_contest.ballot_selections);
      assertThat(tuple).isNull();
    }

    first_contest.ballot_selections.add(0, first_selection);

    // modify the contest description hash
    Group.ElementModQ first_contest_hash = first_contest.description_hash;
    first_contest.description_hash = Group.ONE_MOD_Q;
    assertThat(tally_ballot(ballot, subject)).isEmpty();
    assertThat(subject.append(ballot)).isFalse();

    first_contest.description_hash = first_contest_hash;

    // modify a contest object id
    String first_contest_object_id = first_contest.object_id;
    first_contest.object_id = "a-bad-object-id";
    assertThat(tally_ballot(ballot, subject)).isEmpty();
    assertThat(subject.append(ballot)).isFalse();

    first_contest.object_id = first_contest_object_id;

    //modify a selection object id
    String first_contest_selection_object_id = first_selection.object_id;
    first_selection.object_id = "another-bad-object-id";

    assertThat(tally_ballot(ballot, subject)).isEmpty();
    assertThat(subject.append(ballot));

    //Verify accumulation fails if the selection object id does not match
    if (ballot.state == BallotBoxState.CAST) {
      assertThat(
              subject.cast.get(first_contest.object_id).accumulate_contest(
                      first_contest.ballot_selections))
              .isFalse();
    }

    first_selection.object_id = first_contest_selection_object_id;

    // LOOK mutablity
    /* modify the ballot's hash
    Group.ElementModQ first_ballot_hash = ballot.description_hash;
    ballot.description_hash = Group.ONE_MOD_Q;
    assertThat(tally_ballot(ballot, subject)).isEmpty();
    assertThat(subject.append(ballot)).isFalse();

    ballot.description_hash = first_ballot_hash;
    ballot.state = input_state; */

    return true;
  }
}
