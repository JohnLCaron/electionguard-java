package com.sunya.electionguard;

import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.math.BigInteger;
import java.util.Optional;

import static com.sunya.electionguard.Ballot.*;
import static com.sunya.electionguard.Election.*;
import static com.sunya.electionguard.Group.*;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth8.assertThat;

public class TestBallotBox {
  private static final ElementModQ SEED_HASH = new Encrypt.EncryptionDevice("Location").get_hash();
  static ElectionFactory election_factory;

  @BeforeClass
  public static void setup() {
    election_factory = new ElectionFactory();
  }

  InternalElectionDescription metadata;
  CiphertextElectionContext context;
  PlaintextBallot source;
  CiphertextBallot data;
  DataStore store;
  BallotBox subject;

  @Before
  public void setupElection() {
    // Arrange
    ElGamal.KeyPair keypair = ElGamal.elgamal_keypair_from_secret(int_to_q_unchecked(BigInteger.TWO))
            .orElseThrow(() -> new RuntimeException());

    ElectionDescription election = election_factory.get_fake_election();
    Optional<ElectionBuilder.Tuple> tupleO = election_factory.get_fake_ciphertext_election(election, keypair.public_key);
    assertThat(tupleO.isEmpty()).isFalse();
    metadata = tupleO.get().description;
    context = tupleO.get().context;

    source = election_factory.get_fake_ballot(metadata.description, null);
    assertThat(metadata.ballot_styles.isEmpty()).isFalse();
    assertThat(source.is_valid(metadata.ballot_styles.get(0).object_id)).isTrue();

    Optional<CiphertextBallot> dataO = Encrypt.encrypt_ballot(source, metadata, context, SEED_HASH, Optional.empty(), true);
    assertThat(dataO).isPresent();
    data = dataO.get();
    store = new DataStore();
    subject = new BallotBox(metadata, context, store);
  }

  @Test
  public void test_ballot_box_cast_ballot() {
    // Act
    assertThat(BallotValidator.ballot_is_valid_for_election(data, metadata, context)).isTrue();

    Optional<CiphertextAcceptedBallot> resultO = subject.cast(data);
    assertThat(resultO).isPresent();
    CiphertextAcceptedBallot result = resultO.get();

    // Assert
    CiphertextAcceptedBallot expected = store.get(source.object_id).orElse(null);
    assertThat(expected).isNotNull();
    assertThat(expected.state).isEqualTo(BallotBoxState.CAST);
    assertThat(result.state).isEqualTo(BallotBoxState.CAST);
    assertThat(expected.object_id).isEqualTo(result.object_id);

    // Test failure modes
    assertThat(subject.cast(data)).isEmpty();  // cannot cast again
    assertThat(subject.spoil(data)).isEmpty();  // cannot spoil a ballot already cast
  }

  @Test
  public void test_ballot_box_spoil_ballot() {
          //Act
    Optional<CiphertextAcceptedBallot> resultO = subject.spoil(data);
    assertThat(resultO).isPresent();
    CiphertextAcceptedBallot result = resultO.get();

          //Assert
    CiphertextAcceptedBallot expected = store.get(source.object_id).orElse(null);;
    assertThat(expected).isNotNull();

    assertThat(expected.state).isEqualTo(BallotBoxState.SPOILED);
    assertThat(result.state).isEqualTo(BallotBoxState.SPOILED);
    assertThat(expected.object_id).isEqualTo(result.object_id);

          //Test failure modes
    assertThat(subject.spoil(data)).isEmpty();  //cannot spoil again
    assertThat(subject.cast(data)).isEmpty();  //cannot cast a ballot already spoiled
  }

  @Test
  public void test_cast_ballot() {
    //Act
    CiphertextAcceptedBallot result = subject.accept_ballot(data, BallotBoxState.CAST).orElse(null);
    assertThat(result).isNotNull();

        //Assert
    CiphertextAcceptedBallot expected = store.get(source.object_id).orElse(null);
    assertThat(expected).isNotNull();
    assertThat(expected.state).isEqualTo(BallotBoxState.CAST);
    assertThat(result.state).isEqualTo(BallotBoxState.CAST);
    assertThat(expected.object_id).isEqualTo(result.object_id);

          //Test failure modes
    assertThat(subject.accept_ballot(data, BallotBoxState.CAST)).isEmpty();  //cannot cast again
    assertThat(subject.accept_ballot(data, BallotBoxState.SPOILED)).isEmpty();  //cannot spoil a ballot already cast
  }

  @Test
  public void test_spoil_ballot() {
          //Act
    CiphertextAcceptedBallot result = subject.accept_ballot(data, BallotBoxState.SPOILED).orElse(null);
    assertThat(result).isNotNull();

        //Assert
    CiphertextAcceptedBallot expected = store.get(source.object_id).orElse(null);
    assertThat(expected).isNotNull();
    assertThat(expected.state).isEqualTo(BallotBoxState.SPOILED);
    assertThat(result.state).isEqualTo(BallotBoxState.SPOILED);
    assertThat(expected.object_id).isEqualTo(result.object_id);

    //Test failure modes
    assertThat(subject.accept_ballot(data, BallotBoxState.CAST)).isEmpty();  //cannot cast again
    assertThat(subject.accept_ballot(data, BallotBoxState.SPOILED)).isEmpty();  //cannot spoil a ballot already cast
  }


}