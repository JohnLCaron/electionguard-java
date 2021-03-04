# 🗳 ElectionGuard Java

## Differences with Python

### PlaintextTally has added fields for issue #281

Missing lagrange coefficients to validate spec 10. #281 https://github.com/microsoft/electionguard-python/issues/281

PlaintextTally has added fields:

````
  /** The lagrange coefficients w_ij for verification of section 10. */
  public final ImmutableMap<String, Group.ElementModQ> lagrange_coefficients;

  /** The state of the Guardian when decrypting: missing or available. */
  public final ImmutableList<GuardianState> guardianStates;
````

Probably need to reconcile when python makes fix.

### Json serialization

1. Optional fields

    * Python: Optional fields that are empty have _"None"_ in the serialization.
    * Java: Optional fields that are empty have a _null_ or are left out.

2. Spoiled plaintext tallies

    * Python: Each spoiled ballot tally is stored inside the election plaintext tally. 
      Java library does not currently have access to those, anticipate PR #305 will fix.
    * Java: Each spoiled ballot tally stored in a separate file in *spoiled_tallies/ballot_<ballot_id>*
      Placeholder selections are removed.

3. Spoiled plaintext ballots

    * Python: These are not currently serialized to the election record. 
      Spoiled ciphertext ballots are mistakenly being written instead, anticipate PR #305 will fix.
    * Java: Each spoiled ballot stored in a separate file in *spoiled_ballots/ballot_<ballot_id>*
      Placeholder selections are removed.

### Classes Reorganized

1. __election.py__ -> (was broken out into) Election, ElectionConstants, CiphertextElectionContext, ElectionWithPlaceholders

2. __ballot.py__ -> CiphertextAcceptedBallot, CiphertextBallot, PlaintextBallot
    CiphertextBallot, CiphertextBallotContest, CiphertextBallotSelection(CiphertextSelection)
    CiphertextAcceptedBallot(CiphertextBallot)
    PlaintextBallot, PlaintextBallotContest, PlaintextBallotSelection
    
3. __tally.py__ -> CiphertextTally, CiphertextTallyBuilder, PlaintextTally
    CiphertextTally, CiphertextTallyContest, CiphertextTallySelection(CiphertextSelection)
    PlaintextTally, PlaintextTallyContest, PlaintextTallySelection
    
4. BallotBox contains BallotBoxState, and covers DataStore

### Classes with added Builders

1. CiphertextTally, CiphertextTallyBuilder
2. Guardian, GuardianBuilder

### Classes Renamed

1. __InternalElectionDescription__ -> ElectionDescriptionWithPlaceholders
2. __PublishedCiphertextTally__ -> CiphertextTally
3. __CiphertextTally__ -> CiphertextTallyBuilder
4. __BallotDecryptionShare__ -> __DecryptionShare__
5. __CompensatedBallotDecryptionShare__ -> __CompensatedBallotDecryptionShare__

---------------------------------------------------------------------------------
3/3 changes from PR#305

CiphertextContest 
* added as a bridge to CiphertextTallyContest and CiphertextBallotContest.

CiphertextTally
* CiphertextTally.Contest.tally_selections -> selections
* CiphertextTallyBuilder.Contest.tally_selections -> selections
* python: CiphertextTally.cast -> CiphertextTally.contests
* python: spoiled_ballots: Dict[BALLOT_ID, CiphertextAcceptedBallot] removed 
* python: spoiled_ballot_ids: Set[BALLOT_ID] = field(init=False) added

PlaintextTally
* python: spoiled_ballots is removed
* python: PublishedPlaintextTally removed

DecryptionShare
* BallotDecryptionShare -> DecryptionShare, owner_id removed, spoiled_ballots removed
* CompensatedTallyDecryptionShare -> CompensatedDecryptionShare, owner_id removed, spoiled_ballots removed
* python: ballot_id is removed, so these can be used for Tally or Ballot. We had added owner_id for that reason.
   Note that object_id is added.
* python: TallyDecryptionShare, CompensatedTallyDecryptionShare is removed

DecryptWithShares
* TallyDecryptionShare -> DecryptionShare
* decrypt_tally(), decrypt_ballot(), decrypt_selection_with_decryption_shares()
* decrypt_spoiled_ballots -> decrypt_ballots()
* decrypt_tally_contests_with_decryption_shares -> decrypt_contest_with_decryption_shares()

Decryptions
* TallyDecryptionShare -> DecryptionShare
* CompensatedTallyDecryptionShare -> CompensatedDecryptionShare
* compute_decryption_share(), compute_compensated_decryption_share(), compute_decryption_share_for_ballot(),
  compute_decryption_share_for_selection(), compute_compensated_decryption_share_for_selection(),
  compute_lagrange_coefficients_for_guardian(s), 
* compute_decryption_share_for_cast_contests() 
* compute_compensated_decryption_share_for_cast_contests()
* compute_compensated_decryption_share_for_spoiled_ballots()
* compute_compensated_decryption_share_for_ballot
* reconstruct_decryption_contests() -> reconstruct_decryption_share()
* reconstruct_decryption_ballots() -> reconstruct_decryption_shares_for_ballots()
* reconstruct_decryption_ballot -> reconstruct_decryption_share_for_ballot()

DecryptionMediator


