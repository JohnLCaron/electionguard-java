package com.sunya.electionguard;

import com.google.common.base.Preconditions;
import com.google.common.flogger.FluentLogger;

import javax.annotation.concurrent.Immutable;
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
public class CiphertextAcceptedBallot extends CiphertextBallot {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  /**
   * Makes a `CiphertextAcceptedBallot`, ensuring that no nonces are part of the contests.
   * <p>
   *
   * @param object_id:               the object_id of this specific ballot
   * @param ballot_style:            The `object_id` of the `BallotStyle` in the `Election` Manifest
   * @param description_hash:        Hash of the election description
   * @param previous_tracking_hashO: Previous tracking hash or seed hash
   * @param contests:                List of contests for this ballot
   * @param tracking_hash:           This ballot's tracking hash
   * @param timestampO:              Timestamp at which the ballot encryption is generated in tick
   * @param state:                   ballot box state
   */
  static CiphertextAcceptedBallot create(
          String object_id,
          String ballot_style,
          Group.ElementModQ description_hash,
          Optional<Group.ElementModQ> previous_tracking_hashO,
          List<Contest> contests,
          Group.ElementModQ tracking_hash,
          Optional<Long> timestampO,
          BallotBox.State state) { // default BallotBoxState.UNKNOWN,

    if (contests.isEmpty()) {
      logger.atInfo().log("ciphertext ballot with no contest: %s", object_id);
    }

    List<Group.ElementModQ> contest_hashes = contests.stream().map(c -> c.crypto_hash).collect(Collectors.toList());
    Group.ElementModQ contest_hash = Hash.hash_elems(object_id, description_hash, contest_hashes);

    long timestamp = timestampO.orElse(System.currentTimeMillis());
    Group.ElementModQ previous_tracking_hash = previous_tracking_hashO.orElse(description_hash); // LOOK spec #6.A says H0 = H(Qbar)

    // copy the contests and selections, removing all nonces
    List<Contest> new_contests = contests.stream().map(Contest::removeNonces).collect(Collectors.toList());

    return new CiphertextAcceptedBallot(
            object_id,
            ballot_style,
            description_hash,
            previous_tracking_hash,
            new_contests,
            tracking_hash,
            timestamp,
            contest_hash,
            Optional.empty(),
            state);
  }

  //////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
  /** The accepted state: CAST or SPOILED. */
  public final BallotBox.State state;

  public CiphertextAcceptedBallot(CiphertextBallot ballot, BallotBox.State state) {
    super(ballot.object_id, ballot.ballot_style, ballot.description_hash, ballot.previous_tracking_hash, ballot.contests,
            ballot.tracking_hash, ballot.timestamp, ballot.crypto_hash, ballot.nonce);
    this.state = Preconditions.checkNotNull(state);
  }

  public CiphertextAcceptedBallot(String object_id,
                                  String ballot_style,
                                  Group.ElementModQ description_hash,
                                  Group.ElementModQ previous_tracking_hash,
                                  List<Contest> contests,
                                  Group.ElementModQ tracking_hash,
                                  long timestamp,
                                  Group.ElementModQ crypto_hash,
                                  Optional<Group.ElementModQ> nonce,
                                  BallotBox.State state) {
    super(object_id, ballot_style, description_hash, previous_tracking_hash, contests, tracking_hash, timestamp, crypto_hash, nonce);
    this.state = state;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    if (!super.equals(o)) return false;
    CiphertextAcceptedBallot that = (CiphertextAcceptedBallot) o;
    return state == that.state;
  }

  @Override
  public int hashCode() {
    return Objects.hash(super.hashCode(), state);
  }

  @Override
  public String toString() {
    return "CiphertextAcceptedBallot{" +
            "state=" + state +
            "} " + super.toString();
  }
}
