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
3. __tally.py__ -> CiphertextTally, CiphertextTallyBuilder, PlaintextTally

4. BallotBox contains BallotBoxState, and covers DataStore

### Classes with added Builders

1. CiphertextTally, CiphertextTallyBuilder
2. Guardian, GuardianBuilder

### Classes Renamed

1. __InternalElectionDescription__ -> ElectionDescriptionWithPlaceholders
2. __PublishedCiphertextTally__ -> CiphertextTally
3. __CiphertextTally__ -> CiphertextTallyBuilder
4. __PublishedPlaintextTally__ -> PlaintextTally
