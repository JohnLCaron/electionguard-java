package com.sunya.electionguard;

import com.google.common.base.Preconditions;

import javax.annotation.concurrent.Immutable;
import java.util.List;
import java.util.Optional;

import static com.sunya.electionguard.Group.ElementModQ;

/** A compact submitted ballot minimized for data size. */
@Immutable
public class CompactSubmittedBallot {
  final CompactPlaintextBallot compact_plaintext_ballot;
  final long timestamp;
  final ElementModQ ballot_nonce; // LOOK why not optional?
  final ElementModQ code_seed;
  final ElementModQ code;
  final BallotBox.State ballot_box_state;

  public CompactSubmittedBallot(CompactPlaintextBallot compact_plaintext_ballot, long timestamp,
                                ElementModQ ballot_nonce, ElementModQ code_seed, ElementModQ code,
                                BallotBox.State ballot_box_state) {
    this.compact_plaintext_ballot = Preconditions.checkNotNull(compact_plaintext_ballot);
    this.timestamp = timestamp;
    this.ballot_nonce = Preconditions.checkNotNull(ballot_nonce);
    this.code_seed = Preconditions.checkNotNull(code_seed);
    this.code = Preconditions.checkNotNull(code);
    this.ballot_box_state = Preconditions.checkNotNull(ballot_box_state);
  }

  /** Compress a submitted ballot into a compact submitted ballot. */
  static CompactSubmittedBallot compress_submitted_ballot(
          SubmittedBallot ballot,
          PlaintextBallot plaintext_ballot, // LOOK surprising, what is the use case?
          ElementModQ ballot_nonce) {

    return new CompactSubmittedBallot(
            CompactPlaintextBallot.compress_plaintext_ballot(plaintext_ballot),
            ballot.timestamp,
            ballot_nonce,
            ballot.code_seed,
            ballot.code,
            ballot.state);
  }

  /** Expand a compact submitted ballot using context and the election manifest into a submitted ballot. */
  static SubmittedBallot expand_compact_submitted_ballot(
          CompactSubmittedBallot compact_ballot,
          InternalManifest internal_manifest,
          CiphertextElectionContext context) {

    // Expand ballot and encrypt & hash contests
    PlaintextBallot plaintext_ballot = CompactPlaintextBallot.expand_compact_plaintext_ballot(
            compact_ballot.compact_plaintext_ballot, internal_manifest);

    ElementModQ nonce_seed = CiphertextBallot.nonce_seed(
            internal_manifest.manifest.crypto_hash,
            compact_ballot.compact_plaintext_ballot.object_id,
            compact_ballot.ballot_nonce);

    List<CiphertextBallot.Contest> contests =
            Encrypt.encrypt_ballot_contests(
                    plaintext_ballot, internal_manifest, context, nonce_seed).orElseThrow();

    ElementModQ crypto_hash = CiphertextBallot.create_ballot_hash(
            plaintext_ballot.object_id, internal_manifest.manifest.crypto_hash, contests);

    return new SubmittedBallot(
            plaintext_ballot.object_id,
            plaintext_ballot.style_id,
            internal_manifest.manifest.crypto_hash,
            compact_ballot.code_seed,
            contests,
            compact_ballot.code,
            compact_ballot.timestamp,
            crypto_hash,
            Optional.of(compact_ballot.ballot_nonce),
            compact_ballot.ballot_box_state);
  }

}
