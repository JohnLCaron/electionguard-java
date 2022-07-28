package com.sunya.electionguard.ballot;

import com.google.common.flogger.FluentLogger;
import com.sunya.electionguard.BallotBox;
import com.sunya.electionguard.CiphertextBallot;
import com.sunya.electionguard.Group;
import com.sunya.electionguard.Hash;

import javax.annotation.concurrent.Immutable;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * An encrypted ballot that is accepted for inclusion in election results. An accepted ballot is either cast or spoiled.
 * Note that this class is immutable, and the state cannot be changed.
 * Note this ballot includes all proofs but no nonces.
 */
@Immutable
public class EncryptedBallot extends CiphertextBallot {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  // python: from_ciphertext_ballot
  public static EncryptedBallot createFromCiphertextBallot(CiphertextBallot ballot, BallotBox.State state) {
    return create(ballot.object_id(), ballot.ballotStyleId, ballot.manifestHash, Optional.of(ballot.code_seed),
            ballot.contests, ballot.code, Optional.of(ballot.timestamp), state);
  }

  /**
   * Makes a `SubmittedBallot`, ensuring that no nonces are part of the contests.
   * python: make_ciphertext_submitted_ballot()
   * <p>
   *
   * @param ballotId:               the object_id of this specific ballot
   * @param ballotStyleId:                The `object_id` of the `BallotStyle` in the `Manifest` Manifest
   * @param manifest_hash:           Hash of the election manifest
   * @param code_seedO:              Seed for ballot code
   * @param contests:                List of contests for this ballot
   * @param ballot_code:             This ballot's tracking hash, not Optional.
   * @param timestampO:              Timestamp at which the ballot encryption is generated in seconds since the epoch UTC
   * @param state:                   ballot state
   */
  public static EncryptedBallot create(
          String ballotId,
          String ballotStyleId,
          Group.ElementModQ manifest_hash,
          Optional<Group.ElementModQ> code_seedO,
          List<Contest> contests,
          Group.ElementModQ ballot_code,
          Optional<Long> timestampO,
          BallotBox.State state) { // default BallotBoxState.UNKNOWN,

    if (contests.isEmpty()) {
      logger.atInfo().log("ciphertext ballot with no contest: %s", ballotId);
    }

    // contest_hashes = [contest.crypto_hash for contest in sequence_order_sort(contests)]
    List<Group.ElementModQ> contest_hashes = contests.stream()
            .sorted(Comparator.comparingInt(CiphertextBallot.Contest::sequence_order))
            .map(c -> c.crypto_hash)
            .toList();

    Group.ElementModQ crypto_hash = Hash.hash_elems(ballotId, manifest_hash, contest_hashes);

    long timestamp = timestampO.orElse(System.currentTimeMillis());
    Group.ElementModQ code_seed = code_seedO.orElse(manifest_hash); // LOOK spec #6.A says H0 = H(Qbar)

    // copy the contests and selections, removing all nonces
    List<Contest> new_contests = contests.stream().map(Contest::removeNonces).toList();

    return new EncryptedBallot(
            ballotId,
            ballotStyleId,
            manifest_hash,
            code_seed,
            new_contests,
            ballot_code,
            timestamp,
            crypto_hash,
            state);
  }

  //////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
  /** The accepted state: CAST or SPOILED. */
  public final BallotBox.State state;

  // public to allow proto serialization; use SubmittedBallot.create()
  public EncryptedBallot(String ballotId,
                         String ballotStyleId,
                         Group.ElementModQ manifest_hash,
                         Group.ElementModQ code_seed,
                         List<Contest> contests,
                         Group.ElementModQ code,
                         long timestamp,
                         Group.ElementModQ crypto_hash,
                         BallotBox.State state) {
    super(ballotId, ballotStyleId, manifest_hash, code_seed, contests, code, timestamp, crypto_hash, Optional.empty());
    this.state = state;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    if (!super.equals(o)) return false;
    EncryptedBallot that = (EncryptedBallot) o;
    return timestamp == that.timestamp &&
            ballotId.equals(that.ballotId) &&
            ballotStyleId.equals(that.ballotStyleId) &&
            manifestHash.equals(that.manifestHash) &&
            code_seed.equals(that.code_seed) &&
            contests.equals(that.contests) &&
            code.equals(that.code) &&
            crypto_hash.equals(that.crypto_hash) &&
            state.equals(that.state);
  }

  @Override
  public int hashCode() {
    return Objects.hash(ballotId, ballotStyleId, manifestHash, code_seed, contests, code, timestamp, crypto_hash, state);
  }

  @Override
  public String toString() {
    return "SubmittedBallot{" +
            "state=" + state +
            ", ballotId='" + ballotId + '\'' +
            ", ballotStyleId='" + ballotStyleId + '\'' +
            ", manifestHash=" + manifestHash +
            ", code_seed=" + code_seed +
            ", contests=" + contests +
            ", code=" + code +
            ", timestamp=" + timestamp +
            ", crypto_hash=" + crypto_hash +
            '}';
  }
}