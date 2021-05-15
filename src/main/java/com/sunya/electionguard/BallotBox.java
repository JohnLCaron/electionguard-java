package com.sunya.electionguard;

import com.google.common.collect.Iterables;
import com.google.common.flogger.FluentLogger;
import com.sunya.electionguard.publish.CloseableIterable;
import com.sunya.electionguard.publish.CloseableIterableAdapter;

import java.util.Optional;

/** A collection of ballots that have been either cast or spoiled. */
public class BallotBox {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  /** Enumeration indicating a ballot has been cast or spoiled. Ordering same as python. */
  public enum State {
    /** A ballot that has been explicitly cast */
    CAST,
    /** A ballot that has been explicitly spoiled */
    SPOILED,
    /** A ballot whose state is unknown to ElectionGuard and will not be included in any election results. */
    UNKNOWN
  }

  private final InternalManifest metadata;
  private final CiphertextElectionContext context;
  private final DataStore store;

  public BallotBox(Manifest election,
                   CiphertextElectionContext context) {
    this.metadata = new InternalManifest(election);
    this.context = context;
    this.store = new DataStore();
  }

  /** Cast a specific encrypted CiphertextBallot. */
  public Optional<SubmittedBallot> cast(CiphertextBallot ballot) {
    return accept_ballot(ballot, State.CAST);
  }

  /** Spoil a specific encrypted CiphertextBallot. */
  public Optional<SubmittedBallot> spoil(CiphertextBallot ballot) {
    return accept_ballot(ballot, State.SPOILED);
  }

  /**
   * Accept a ballot within the context of a specified election and against an existing data store.
   * Verify that the ballot is valid for the election and the ballot has not already been cast or spoiled.
   * @return a `SubmittedBallot` or `None` if there was an error
   */
  Optional<SubmittedBallot> accept_ballot(CiphertextBallot ballot, State state) {
    if (!BallotValidations.ballot_is_valid_for_election(ballot, this.metadata, context)) {
      return Optional.empty();
    }

    if (store.containsKey(ballot.object_id)) {
      SubmittedBallot existingBallot = store.get(ballot.object_id).orElseThrow(IllegalStateException::new);
      logger.atWarning().log("error accepting ballot, %s already exists with state: %s",
          ballot.object_id, existingBallot.state);
      return Optional.empty();
    }

    // TODO: ISSUE #56: check if the ballot includes the nonce, and regenerate the proofs
    // TODO: ISSUE #56: check if the ballot includes the proofs, if it does not include the nonce
    SubmittedBallot ballot_box_ballot = ballot.acceptWithState(state);
    store.put(ballot_box_ballot.object_id, ballot_box_ballot);
    return Optional.of(ballot_box_ballot);
  }

  /* LOOK python also returns state = None.
  def get_ballots(
    store: DataStore, state: Optional[BallotBoxState]
) -> Dict[str, SubmittedBallot]:
    return {
        ballot_id: ballot
        for (ballot_id, ballot) in store.items()
        if state is None or ballot.state == state
    }
   */

  /** Get all the ballots, cast or spoiled. */
  public Iterable<SubmittedBallot> getAllBallots() {
    return store;
  }

  /** Get just the cast ballots. */
  public Iterable<SubmittedBallot> getCastBallots() {
    return Iterables.filter(store, b -> b.state == State.CAST);
  }

  /** Get just the spoiled ballots. */
  public Iterable<SubmittedBallot> getSpoiledBallots() {
    return Iterables.filter(store, b -> b.state == State.SPOILED);
  }

  /** Return the SubmittedBallot with the given key, or empty if not exist. */
  Optional<SubmittedBallot> get(String key) {
    return store.get(key);
  }

  /** Get all the ballots as a CloseableIterable. */
  CloseableIterable<SubmittedBallot> getAcceptedBallotsAsCloseableIterable() {
    return CloseableIterableAdapter.wrap(store);
  }

  Iterable<SubmittedBallot> getAcceptedBallots() {
    return store;
  }
}
