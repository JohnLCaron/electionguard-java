package com.sunya.electionguard;

import com.google.common.flogger.FluentLogger;

import javax.annotation.concurrent.Immutable;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * An encrypted ballot that is accepted for inclusion in election results. An accepted ballot is either cast or spoiled.
 * Note that this class is immutable, and the state cannot be changed.
 * Note this ballot includes all proofs but no nonces.
 */
@Immutable
public class SubmittedBallot extends CiphertextBallot {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  // python: from_ciphertext_ballot
  public static SubmittedBallot createFromCiphertextBallot(CiphertextBallot ballot, BallotBox.State state) {
    return create(ballot.object_id, ballot.style_id, ballot.manifest_hash, Optional.of(ballot.code_seed),
            ballot.contests, ballot.code, Optional.of(ballot.timestamp), state);
  }

  /**
   * Makes a `SubmittedBallot`, ensuring that no nonces are part of the contests.
   * python: make_ciphertext_submitted_ballot()
   * <p>
   *
   * @param object_id:               the object_id of this specific ballot
   * @param style_id:                The `object_id` of the `BallotStyle` in the `Manifest` Manifest
   * @param manifest_hash:           Hash of the election manifest
   * @param code_seedO:              Seed for ballot code
   * @param contests:                List of contests for this ballot
   * @param ballot_code:             This ballot's tracking hash, not Optional.
   * @param timestampO:              Timestamp at which the ballot encryption is generated in tick
   * @param state:                   ballot box state
   */
  static SubmittedBallot create(
          String object_id,
          String style_id,
          Group.ElementModQ manifest_hash,
          Optional<Group.ElementModQ> code_seedO,
          List<Contest> contests,
          Group.ElementModQ ballot_code,
          Optional<Long> timestampO,
          BallotBox.State state) { // default BallotBoxState.UNKNOWN,

    if (contests.isEmpty()) {
      logger.atInfo().log("ciphertext ballot with no contest: %s", object_id);
    }

    // contest_hashes = [contest.crypto_hash for contest in sequence_order_sort(contests)]
    List<Group.ElementModQ> contest_hashes = contests.stream()
            .sorted(Comparator.comparingInt(CiphertextBallot.Contest::sequence_order))
            .map(c -> c.crypto_hash)
            .collect(Collectors.toList());

    Group.ElementModQ contest_hash = Hash.hash_elems(object_id, manifest_hash, contest_hashes);

    long timestamp = timestampO.orElse(System.currentTimeMillis());
    Group.ElementModQ code_seed = code_seedO.orElse(manifest_hash); // LOOK spec #6.A says H0 = H(Qbar)

    // copy the contests and selections, removing all nonces
    List<Contest> new_contests = contests.stream().map(Contest::removeNonces).collect(Collectors.toList());

    return new SubmittedBallot(
            object_id,
            style_id,
            manifest_hash,
            code_seed,
            new_contests,
            ballot_code,
            timestamp,
            contest_hash,
            state);
  }

  //////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
  /** The accepted state: CAST or SPOILED. */
  public final BallotBox.State state;

  // public to allow proto serialization; use SubmittedBallot.create()
  public SubmittedBallot(String object_id,
                         String style_id,
                         Group.ElementModQ manifest_hash,
                         Group.ElementModQ code_seed,
                         List<Contest> contests,
                         Group.ElementModQ code,
                         long timestamp,
                         Group.ElementModQ crypto_hash,
                         BallotBox.State state) {
    super(object_id, style_id, manifest_hash, code_seed, contests, code, timestamp, crypto_hash, Optional.empty());
    this.state = state;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    if (!super.equals(o)) return false;
    SubmittedBallot that = (SubmittedBallot) o;
    return state == that.state;
  }

  @Override
  public int hashCode() {
    return Objects.hash(super.hashCode(), state);
  }

  @Override
  public String toString() {
    return "SubmittedBallot{" +
            "\n object_id    ='" + object_id + '\'' +
            "\n state        =" + state +
            "\n style_id     ='" + style_id + '\'' +
            "\n manifest_hash=" + manifest_hash +
            "\n code         =" + code +
            "\n code_seed    =" + code_seed +
            "\n tracking_hash=" + code +
            "\n timestamp    =" + timestamp +
            "\n crypto_hash  =" + crypto_hash +
            '}';
  }
}
