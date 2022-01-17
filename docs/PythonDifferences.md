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

### Classes Reorganized

1. __election.py__ -> (was broken out into) Election, ElectionConstants, CiphertextElectionContext, ElectionWithPlaceholders
2. __ballot.py__ -> SubmittedBallot, CiphertextBallot, PlaintextBallot
  * CiphertextBallot, CiphertextBallotContest, CiphertextBallotSelection(CiphertextSelection)
  * SubmittedBallot(CiphertextBallot)
  * PlaintextBallot, PlaintextBallotContest, PlaintextBallotSelection
3. __tally.py__ -> CiphertextTally, CiphertextTallyBuilder, PlaintextTally
  * CiphertextTally, CiphertextTallyContest, CiphertextTallySelection(CiphertextSelection)
  * PlaintextTally, PlaintextTallyContest, PlaintextTallySelection
4. BallotBox contains BallotBoxState, and covers DataStore

### Classes with added Builders

1. CiphertextTally, CiphertextTallyBuilder
2. Guardian, GuardianBuilder

### Classes Renamed

1. __InternalElectionDescription__ -> __ElectionDescriptionWithPlaceholders__
2. __PublishedCiphertextTally__ -> __CiphertextTally__
3. __CiphertextTally__ -> __CiphertextTallyBuilder__
4. __BallotDecryptionShare__ -> __DecryptionShare__
5. __CompensatedBallotDecryptionShare__ -> __CompensatedBallotDecryptionShare__

### Classes Renamed in Python

1. __CiphertextAcceptedBallot__ -> __SubmittedBallot__

### Not in Java

1. __CiphertextTally.spoiled_ballot_ids__

### Not in Python

1. __List<SpoiledBallotAndTally> DecryptionMediator.decrypt_spoiled_ballots()__
2. __List<SpoiledBallotAndTally> DecryptWithShares.decrypt_spoiled_ballots()__

---------------------------------------------------------------------------------
## 3/3 changes from PR#305

### CiphertextContest 
* added as a bridge to CiphertextTallyContest and CiphertextBallotContest.

### CiphertextTally
* CiphertextTally.Contest.tally_selections -> selections
* CiphertextTallyBuilder.Contest.tally_selections -> selections
* python: CiphertextTally.cast -> CiphertextTally.contests
* python: spoiled_ballots: Dict[BALLOT_ID, CiphertextAcceptedBallot] removed 
* python: spoiled_ballot_ids: Set[BALLOT_ID] = field(init=False) added

### PlaintextTally
* python: spoiled_ballots removed
* python: PublishedPlaintextTally removed

### DecryptionShare
* BallotDecryptionShare -> DecryptionShare, owner_id removed, spoiled_ballots removed, object_id is added
* CompensatedTallyDecryptionShare -> CompensatedDecryptionShare, owner_id removed, spoiled_ballots removed, object_id is added
* TallyDecryptionShare, CompensatedTallyDecryptionShare removed

### DecryptWithShares
* TallyDecryptionShare -> DecryptionShare
* decrypt_tally(), decrypt_ballot(), decrypt_selection_with_decryption_shares()
* decrypt_spoiled_ballots -> decrypt_ballots()
* decrypt_tally_contests_with_decryption_shares() -> decrypt_contest_with_decryption_shares()

### Decryptions
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

### DecryptionMediator
* java added List<SpoiledBallotAndTally> decrypt_spoiled_ballots(), to get both spoiled decrypted tally and ballot


