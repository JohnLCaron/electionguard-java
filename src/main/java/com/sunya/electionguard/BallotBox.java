package com.sunya.electionguard;

import java.util.Optional;

import static com.sunya.electionguard.Ballot.*;

/**
 * A stateful convenience wrapper to cache election data.
 */
public class BallotBox {
  private Election.InternalElectionDescription _metadata;
  private Election.CiphertextElectionContext _context;
  private DataStore _store;

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

  /**
   * Spoil a specific encrypted `CiphertextBallot` .
   */
  Optional<CiphertextAcceptedBallot> spoil(CiphertextBallot ballot) {
    return accept_ballot(ballot, BallotBoxState.SPOILED);
  }

  /**
   * Accept a ballot within the context of a specified election and against an existing data store
   * Verified that the ballot is valid for the election `metadata` and `context` and
   * that the ballot has not already been cast or spoiled.
   * :return: a `CiphertextAcceptedBallot` or `None` if there was an error
   */
  Optional<CiphertextAcceptedBallot> accept_ballot(CiphertextBallot ballot, BallotBoxState state) {
    if (!BallotValidator.ballot_is_valid_for_election(ballot, _metadata, _context)) {
      return Optional.empty();
    }

    if (_store.containsKey(ballot.object_id)) {
      // log_warning(f"error accepting ballot, {ballot.object_id} already exists with state: {existing_ballot.state}")
      return Optional.empty();
    }

    // TODO: ISSUE #56: check if the ballot includes the nonce, and regenerate the proofs
    // TODO: ISSUE #56: check if the ballot includes the proofs, if it does not include the nonce
    CiphertextAcceptedBallot ballot_box_ballot = from_ciphertext_ballot(ballot, state);

    _store.put(ballot_box_ballot.object_id, ballot_box_ballot);
    return Optional.of(ballot_box_ballot);
  }
}
