package com.sunya.electionguard;

import net.jqwik.api.Example;

import java.math.BigInteger;
import java.util.Optional;

import static com.sunya.electionguard.Ballot.*;
import static com.sunya.electionguard.Election.*;
import static com.sunya.electionguard.Group.*;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth8.assertThat;

public class TestBallotBox {
  private static final ElementModQ SEED_HASH = new Encrypt.EncryptionDevice("Location").get_hash();

  ElectionWithPlaceholders metadata;
  CiphertextElectionContext context;
  PlaintextBallot source;
  CiphertextBallot data;
  BallotBox ballotBox;

  public TestBallotBox() {
    ElGamal.KeyPair keypair = ElGamal.elgamal_keypair_from_secret(int_to_q_unchecked(BigInteger.TWO))
            .orElseThrow(RuntimeException::new);

    ElectionDescription election = ElectionFactory.get_fake_election();
    ElectionBuilder.DescriptionAndContext tuple = ElectionFactory.get_fake_ciphertext_election(election, keypair.public_key).orElseThrow();
    this.metadata = tuple.metadata;
    context = tuple.context;

    source = ElectionFactory.get_fake_ballot(election, null);
    assertThat(election.ballot_styles.isEmpty()).isFalse();
    assertThat(source.is_valid(election.ballot_styles.get(0).object_id)).isTrue();

    Optional<CiphertextBallot> dataO = Encrypt.encrypt_ballot(source, tuple.metadata, context, SEED_HASH, Optional.empty(), true);
    assertThat(dataO).isPresent();
    data = dataO.get();
    ballotBox = new BallotBox(election, context);
  }

  @Example
  public void test_ballot_box_cast_ballot() {
    assertThat(BallotValidations.ballot_is_valid_for_election(data, this.metadata, context)).isTrue();

    Optional<CiphertextAcceptedBallot> resultO = ballotBox.cast(data);
    assertThat(resultO).isPresent();
    CiphertextAcceptedBallot result = resultO.get();

    CiphertextAcceptedBallot expected = ballotBox.get(source.object_id).orElse(null);
    assertThat(expected).isNotNull();
    assertThat(expected.state).isEqualTo(BallotBoxState.CAST);
    assertThat(result.state).isEqualTo(BallotBoxState.CAST);
    assertThat(expected.object_id).isEqualTo(result.object_id);

    // Test failure modes
    assertThat(ballotBox.cast(data)).isEmpty();  // cannot cast again
    assertThat(ballotBox.spoil(data)).isEmpty();  // cannot spoil a ballot already cast
  }

  @Example
  public void test_ballot_box_spoil_ballot() {
    Optional<CiphertextAcceptedBallot> resultO = ballotBox.spoil(data);
    assertThat(resultO).isPresent();
    CiphertextAcceptedBallot result = resultO.get();

    CiphertextAcceptedBallot expected = ballotBox.get(source.object_id).orElse(null);
    assertThat(expected).isNotNull();

    assertThat(expected.state).isEqualTo(BallotBoxState.SPOILED);
    assertThat(result.state).isEqualTo(BallotBoxState.SPOILED);
    assertThat(expected.object_id).isEqualTo(result.object_id);

    //Test failure modes
    assertThat(ballotBox.spoil(data)).isEmpty();  //cannot spoil again
    assertThat(ballotBox.cast(data)).isEmpty();  //cannot cast a ballot already spoiled
  }

  @Example
  public void test_cast_ballot() {
    CiphertextAcceptedBallot result = ballotBox.accept_ballot(data, BallotBoxState.CAST).orElse(null);
    assertThat(result).isNotNull();

    CiphertextAcceptedBallot expected = ballotBox.get(source.object_id).orElse(null);
    assertThat(expected).isNotNull();
    assertThat(expected.state).isEqualTo(BallotBoxState.CAST);
    assertThat(result.state).isEqualTo(BallotBoxState.CAST);
    assertThat(expected.object_id).isEqualTo(result.object_id);

    //Test failure modes
    assertThat(ballotBox.accept_ballot(data, BallotBoxState.CAST)).isEmpty();  //cannot cast again
    assertThat(ballotBox.accept_ballot(data, BallotBoxState.SPOILED)).isEmpty();  //cannot spoil a ballot already cast
  }

  @Example
  public void test_spoil_ballot() {
    CiphertextAcceptedBallot result = ballotBox.accept_ballot(data, BallotBoxState.SPOILED).orElse(null);
    assertThat(result).isNotNull();

    CiphertextAcceptedBallot expected = ballotBox.get(source.object_id).orElse(null);
    assertThat(expected).isNotNull();
    assertThat(expected.state).isEqualTo(BallotBoxState.SPOILED);
    assertThat(result.state).isEqualTo(BallotBoxState.SPOILED);
    assertThat(expected.object_id).isEqualTo(result.object_id);

    //Test failure modes
    assertThat(ballotBox.accept_ballot(data, BallotBoxState.CAST)).isEmpty();  //cannot cast again
    assertThat(ballotBox.accept_ballot(data, BallotBoxState.SPOILED)).isEmpty();  //cannot spoil a ballot already cast
  }

}
