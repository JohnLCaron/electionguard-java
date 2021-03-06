package com.sunya.electionguard;

import net.jqwik.api.Example;
import java.util.Optional;

import static com.google.common.truth.Truth.assertThat;

public class TestCompactBallot {

  PlaintextBallot plaintext_ballot;
  Group.ElementModQ ballot_nonce;
  SubmittedBallot submitted_ballot;
  InternalManifest internal_manifest;
  CiphertextElectionContext context;

  public TestCompactBallot() {
    ElGamal.KeyPair keypair = ElGamal.elgamal_keypair_from_secret(Group.TWO_MOD_Q).orElseThrow();
    Manifest manifest = ElectionFactory.get_fake_manifest();
    ElectionBuilder.DescriptionAndContext metadata = ElectionFactory.get_fake_ciphertext_election(manifest, keypair.public_key).orElseThrow();
    this.internal_manifest = metadata.internalManifest;
    this.context = metadata.context;

    Group.ElementModQ device_hash = Encrypt.EncryptionDevice.createForTest("device").get_hash();

    // Arrange ballots
    this.plaintext_ballot = ElectionFactory.get_fake_ballot(manifest, "fake_ballot_id");
    CiphertextBallot ciphertext_ballot = Encrypt.encrypt_ballot(this.plaintext_ballot, this.internal_manifest, this.context, device_hash,
            Optional.empty(), true).orElseThrow();
    this.ballot_nonce = ciphertext_ballot.nonce.orElseThrow();

    this.submitted_ballot = new SubmittedBallot(
            ciphertext_ballot,
            BallotBox.State.CAST);
  }

  @Example
  public void test_compact_plaintext_ballot() {
    CompactPlaintextBallot compact_ballot = CompactPlaintextBallot.compress_plaintext_ballot(this.plaintext_ballot);

    assertThat(compact_ballot).isNotNull();
    assertThat(this.plaintext_ballot.object_id).isEqualTo(compact_ballot.object_id);

    PlaintextBallot expanded_ballot = CompactPlaintextBallot.expand_compact_plaintext_ballot(compact_ballot, this.internal_manifest);
    assertThat(expanded_ballot).isNotNull();
    assertThat(this.plaintext_ballot).isEqualTo(expanded_ballot);
  }

  @Example
  public void test_compact_submitted_ballot() {
    CompactSubmittedBallot compact_ballot = CompactSubmittedBallot.compress_submitted_ballot(
            this.submitted_ballot, this.plaintext_ballot, this.ballot_nonce);

    assertThat(compact_ballot).isNotNull();
    assertThat(this.submitted_ballot.object_id).isEqualTo(compact_ballot.compact_plaintext_ballot.object_id);

    SubmittedBallot roundtrip = CompactSubmittedBallot.expand_compact_submitted_ballot(
            compact_ballot, this.internal_manifest, this.context);

    assertThat(roundtrip).isNotNull();
    assertThat(roundtrip).isEqualTo(this.submitted_ballot);
  }


}
