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

  private final ElectionWithPlaceholders metadata;
  private final CiphertextElectionContext context;
  private final DataStore store;

  public BallotBox(Election.ElectionDescription election,
                   CiphertextElectionContext context) {
    this.metadata = new ElectionWithPlaceholders(election);
    this.context = context;
    this.store = new DataStore();
  }

  public Optional<CiphertextAcceptedBallot> cast(CiphertextBallot ballot) {
    return accept_ballot(ballot, State.CAST);
  }

  /** Spoil a specific encrypted `CiphertextBallot` . */
  public Optional<CiphertextAcceptedBallot> spoil(CiphertextBallot ballot) {
    return accept_ballot(ballot, State.SPOILED);
  }

  /**
   * Accept a ballot within the context of a specified election and against an existing data store.
   * Verify that the ballot is valid for the election and the ballot has not already been cast or spoiled.
   * @return a `CiphertextAcceptedBallot` or `None` if there was an error
   */
  Optional<CiphertextAcceptedBallot> accept_ballot(CiphertextBallot ballot, State state) {
    if (!BallotValidations.ballot_is_valid_for_election(ballot, this.metadata, context)) {
      return Optional.empty();
    }

    if (store.containsKey(ballot.object_id)) {
      CiphertextAcceptedBallot existingBallot = store.get(ballot.object_id).orElseThrow(IllegalStateException::new);
      logger.atWarning().log("error accepting ballot, %s already exists with state: %s",
          ballot.object_id, existingBallot.state);
      return Optional.empty();
    }

    // TODO: ISSUE #56: check if the ballot includes the nonce, and regenerate the proofs
    // TODO: ISSUE #56: check if the ballot includes the proofs, if it does not include the nonce
    CiphertextAcceptedBallot ballot_box_ballot = ballot.acceptWithState(state);
    store.put(ballot_box_ballot.object_id, ballot_box_ballot);
    return Optional.of(ballot_box_ballot);
  }

  public Iterable<CiphertextAcceptedBallot> getAllBallots() {
    return store;
  }

  public Iterable<CiphertextAcceptedBallot> getCastBallots() {
    return Iterables.filter(store, b -> b.state == State.CAST);
  }

  public Iterable<CiphertextAcceptedBallot> getSpoiledBallots() {
    return Iterables.filter(store, b -> b.state == State.SPOILED);
  }

  /** Return the value for the given key, or empty. */
  Optional<CiphertextAcceptedBallot> get(String key) {
    return store.get(key);
  }

  public CloseableIterable<CiphertextAcceptedBallot> getAcceptedBallots() {
    return CloseableIterableAdapter.wrap(store);
  }
}
