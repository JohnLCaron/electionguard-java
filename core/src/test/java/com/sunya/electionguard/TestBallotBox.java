package com.sunya.electionguard;

import com.sunya.electionguard.ballot.EncryptedBallot;
import net.jqwik.api.Example;

import java.math.BigInteger;
import java.util.Optional;

import static com.sunya.electionguard.Group.*;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth8.assertThat;

public class TestBallotBox {
  private static final ElementModQ SEED_HASH = Encrypt.createDeviceForTest("Location").get_hash();

  InternalManifest metadata;
  ElectionCryptoContext context;
  PlaintextBallot source;
  CiphertextBallot data;
  BallotBox ballotBox;

  public TestBallotBox() {
    ElGamal.KeyPair keypair = ElGamal.elgamal_keypair_from_secret(int_to_q_unchecked(BigInteger.TWO))
            .orElseThrow(RuntimeException::new);

    Manifest election = ElectionFactory.get_fake_manifest();
    ElectionBuilder.DescriptionAndContext tuple = ElectionFactory.get_fake_ciphertext_election(election, keypair.public_key()).orElseThrow();
    this.metadata = tuple.internalManifest;
    context = tuple.context;

    source = ElectionFactory.get_fake_ballot(election, null);
    assertThat(election.ballotStyles().isEmpty()).isFalse();
    assertThat(source.is_valid(election.ballotStyles().get(0).ballotStyleId())).isTrue();

    Optional<CiphertextBallot> dataO = Encrypt.encrypt_ballot(source, tuple.internalManifest, context, SEED_HASH, Optional.empty(), true);
    assertThat(dataO).isPresent();
    data = dataO.get();
    ballotBox = new BallotBox(election, context);
  }

  @Example
  public void test_ballot_box_cast_ballot() {
    assertThat(BallotValidations.ballot_is_valid_for_election(data, this.metadata, context)).isTrue();

    Optional<EncryptedBallot> resultO = ballotBox.cast(data);
    assertThat(resultO).isPresent();
    EncryptedBallot result = resultO.get();

    EncryptedBallot expected = ballotBox.get(source.object_id()).orElse(null);
    assertThat(expected).isNotNull();
    assertThat(expected.state).isEqualTo(BallotBox.State.CAST);
    assertThat(result.state).isEqualTo(BallotBox.State.CAST);
    assertThat(expected.object_id()).isEqualTo(result.object_id());

    // Test failure modes
    assertThat(ballotBox.cast(data)).isEmpty();  // cannot cast again
    assertThat(ballotBox.spoil(data)).isEmpty();  // cannot spoil a ballot already cast
  }

  @Example
  public void test_ballot_box_spoil_ballot() {
    Optional<EncryptedBallot> resultO = ballotBox.spoil(data);
    assertThat(resultO).isPresent();
    EncryptedBallot result = resultO.get();

    EncryptedBallot expected = ballotBox.get(source.object_id()).orElse(null);
    assertThat(expected).isNotNull();

    assertThat(expected.state).isEqualTo(BallotBox.State.SPOILED);
    assertThat(result.state).isEqualTo(BallotBox.State.SPOILED);
    assertThat(expected.object_id()).isEqualTo(result.object_id());

    //Test failure modes
    assertThat(ballotBox.spoil(data)).isEmpty();  //cannot spoil again
    assertThat(ballotBox.cast(data)).isEmpty();  //cannot cast a ballot already spoiled
  }

  @Example
  public void test_cast_ballot() {
    EncryptedBallot result = ballotBox.accept_ballot(data, BallotBox.State.CAST).orElse(null);
    assertThat(result).isNotNull();

    EncryptedBallot expected = ballotBox.get(source.object_id()).orElse(null);
    assertThat(expected).isNotNull();
    assertThat(expected.state).isEqualTo(BallotBox.State.CAST);
    assertThat(result.state).isEqualTo(BallotBox.State.CAST);
    assertThat(expected.object_id()).isEqualTo(result.object_id());

    //Test failure modes
    assertThat(ballotBox.accept_ballot(data, BallotBox.State.CAST)).isEmpty();  //cannot cast again
    assertThat(ballotBox.accept_ballot(data, BallotBox.State.SPOILED)).isEmpty();  //cannot spoil a ballot already cast
  }

  @Example
  public void test_spoil_ballot() {
    EncryptedBallot result = ballotBox.accept_ballot(data, BallotBox.State.SPOILED).orElse(null);
    assertThat(result).isNotNull();

    EncryptedBallot expected = ballotBox.get(source.object_id()).orElse(null);
    assertThat(expected).isNotNull();
    assertThat(expected.state).isEqualTo(BallotBox.State.SPOILED);
    assertThat(result.state).isEqualTo(BallotBox.State.SPOILED);
    assertThat(expected.object_id()).isEqualTo(result.object_id());

    //Test failure modes
    assertThat(ballotBox.accept_ballot(data, BallotBox.State.CAST)).isEmpty();  //cannot cast again
    assertThat(ballotBox.accept_ballot(data, BallotBox.State.SPOILED)).isEmpty();  //cannot spoil a ballot already cast
  }

}
