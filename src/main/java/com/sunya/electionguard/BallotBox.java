package com.sunya.electionguard;

import com.google.common.collect.Iterables;
import com.google.common.flogger.FluentLogger;
import com.sunya.electionguard.publish.CloseableIterable;
import com.sunya.electionguard.publish.CloseableIterableAdapter;

import java.util.Optional;

import static com.sunya.electionguard.Ballot.*;

/** A collection of ballots that have been either cast or spoiled. */
public class BallotBox {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final Election.ElectionDescription election;
  private final Election.CiphertextElectionContext context;
  private final DataStore store;
  private final ElectionWithPlaceholders metadata;

  public BallotBox(Election.ElectionDescription election,
                   Election.CiphertextElectionContext context,
                   DataStore store) {
    this.election = election;
    this.context = context;
    this.store = store;
    this.metadata = new ElectionWithPlaceholders(election);
  }

  public Optional<CiphertextAcceptedBallot> cast(CiphertextBallot ballot) {
    return accept_ballot(ballot, BallotBoxState.CAST);
  }

  /** Spoil a specific encrypted `CiphertextBallot` . */
  public Optional<CiphertextAcceptedBallot> spoil(CiphertextBallot ballot) {
    return accept_ballot(ballot, BallotBoxState.SPOILED);
  }

  /**
   * Accept a ballot within the context of a specified election and against an existing data store.
   * Verify that the ballot is valid for the election and the ballot has not already been cast or spoiled.
   * @return a `CiphertextAcceptedBallot` or `None` if there was an error
   */
  Optional<CiphertextAcceptedBallot> accept_ballot(CiphertextBallot ballot, BallotBoxState state) {
    if (!BallotValidations.ballot_is_valid_for_election(ballot, this.metadata, context)) {
      return Optional.empty();
    }

    if (store.containsKey(ballot.object_id)) {
      Ballot.CiphertextAcceptedBallot existingBallot = store.get(ballot.object_id).orElseThrow(IllegalStateException::new);
      logger.atWarning().log("error accepting ballot, %s already exists with state: %s",
          ballot.object_id, existingBallot.state);
      return Optional.empty();
    }

    // TODO: ISSUE #56: check if the ballot includes the nonce, and regenerate the proofs
    // TODO: ISSUE #56: check if the ballot includes the proofs, if it does not include the nonce
    CiphertextAcceptedBallot ballot_box_ballot = from_ciphertext_ballot(ballot, state);
    store.put(ballot_box_ballot.object_id, ballot_box_ballot);
    return Optional.of(ballot_box_ballot);
  }

  public Iterable<CiphertextAcceptedBallot> getAllBallots() {
    return store;
  }

  public Iterable<CiphertextAcceptedBallot> getCastBallots() {
    return Iterables.filter(store, b -> b.state == BallotBoxState.CAST);
  }

  public Iterable<CiphertextAcceptedBallot> getSpoiledBallots() {
    return Iterables.filter(store, b -> b.state == BallotBoxState.SPOILED);
  }

  /** Return the value for the given key, or empty. */
  Optional<Ballot.CiphertextAcceptedBallot> get(String key) {
    return store.get(key);
  }

  public CloseableIterable<CiphertextAcceptedBallot> accepted() {
    return CloseableIterableAdapter.wrap(store);
  }

}
