package com.sunya.electionguard;

import com.google.common.flogger.FluentLogger;

import java.util.Optional;

import static com.sunya.electionguard.Ballot.*;

/** A stateful convenience wrapper to cache election data. */
public class BallotBox {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final Election.InternalElectionDescription _metadata;
  private final Election.CiphertextElectionContext _context;
  private final DataStore _store;

  public BallotBox(Election.InternalElectionDescription _metadata,
                   Election.CiphertextElectionContext _context,
                   DataStore _store) {
    this._metadata = _metadata;
    this._context = _context;
    this._store = _store;
  }

  Optional<CiphertextAcceptedBallot> cast(CiphertextBallot ballot) {
    return accept_ballot(ballot, BallotBoxState.CAST);
  }

  /** Spoil a specific encrypted `CiphertextBallot` . */
  Optional<CiphertextAcceptedBallot> spoil(CiphertextBallot ballot) {
    return accept_ballot(ballot, BallotBoxState.SPOILED);
  }

  /**
   * Accept a ballot within the context of a specified election and against an existing data store.
   * Verify that the ballot is valid for the election and the ballot has not already been cast or spoiled.
   * @return a `CiphertextAcceptedBallot` or `None` if there was an error
   */
  Optional<CiphertextAcceptedBallot> accept_ballot(CiphertextBallot ballot, BallotBoxState state) {
    if (!BallotValidator.ballot_is_valid_for_election(ballot, _metadata, _context)) {
      return Optional.empty();
    }

    if (_store.containsKey(ballot.object_id)) {
      Ballot.CiphertextAcceptedBallot existingBallot = _store.get(ballot.object_id).orElseThrow(IllegalStateException::new);
      logger.atWarning().log("error accepting ballot, %s already exists with state: %s",
          ballot.object_id, existingBallot.state);
      return Optional.empty();
    }

    // TODO: ISSUE #56: check if the ballot includes the nonce, and regenerate the proofs
    // TODO: ISSUE #56: check if the ballot includes the proofs, if it does not include the nonce
    CiphertextAcceptedBallot ballot_box_ballot = from_ciphertext_ballot(ballot, state);

    _store.put(ballot_box_ballot.object_id, ballot_box_ballot);
    return Optional.of(ballot_box_ballot);
  }
}
