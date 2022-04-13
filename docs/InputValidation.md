# 🗳 ElectionGuard Input Validation
_last changed: April 12, 2022_

The election manifest and each input plaintext ballot are expected to be validated before being passed to the 
electionguard library. Both the encrypted and decrypted tallies may also be validated against the manifest,
and against each other for consistency. This document summarizes the expected (non-crypto) validations.

A specific validation is referenced as, eg, Manifest.B.5 and Ballot.A.2.1

## Manifest

### A. Referential integrity

1. Referential integrity of BallotStyle's geopoliticalUnitIds.
    * For each BallotStyle, all geopoliticalUnitIds reference a GeopoliticalUnit in Manifest.geopoliticalUnitIds

2. Referential integrity of Candidate partyId.
    * For each Candidate, the partyId is null or references a Party in Manifest.parties
    
3. Referential integrity of ContestDescription geopoliticalUnitId.
    * For each ContestDescription, the geopoliticalUnitId references a GeopoliticalUnit in Manifest.geopoliticalUnitIds    

4. Referential integrity of SelectionDescription candidateId.
    * For each SelectionDescription, the candidateId references a Candidate in Manifest.candidates    

5. Every ContestDescription geopoliticalUnitId exists in some BallotStyle
   * For each ContestDescription, the geopoliticalUnitId exists in at least one Manifest.ballotStyles.geopoliticalUnitIds

### B. Duplication

1. All ContestDescription have a unique object_id.   

2. All ContestDescription have a unique sequence_order.  

3. Within a ContestDescription, all SelectionDescription have a unique selectionId.

4. Within a ContestDescription, all SelectionDescription have a unique sequenceOrder.

5. Within a ContestDescription, all SelectionDescription have a unique candidateIdd.

6. All SelectionDescription have a unique object_id within the election (see get_shares_for_selection in decryption_share)

### C. Contest VoteVariationType

1. A ContestDescription has VoteVariationType = n_of_m, one_of_m, or approval.

2. For all contests, votes_allowed == number_elected.

3. A one_of_m contest has votes_allowed == 1.

4. A n_of_m contest has 0 < votes_allowed <= number of selections in the contest. 

5. An approval contest has votes_allowed == number of selections in the contest.


## Input Ballot

### A. Referential integrity

1. A PlaintextBallot's ballotStyleId must match a BallotStyle in Manifest.ballotStyles.

2. For each PlaintextBallotContest on the ballot, the contestId must match a ContestDescription.contestId in Manifest.contests.
   
   2.1 Within the PlaintextBallotContest and matching ContestDescription, each PlaintextBallotSelection.selectionId must match a SelectionDescription.selectionId.

3. PlaintextBallot.Contest's geopoliticalUnitId must be listed in the PlaintextBallot's BallotStyle geopoliticalUnitIds.

### B. Duplication

1. All PlaintextBallotContest have a unique contest_id.   

2. Within a PlaintextBallotContest, all PlaintextBallotSelection have a unique selectionId.

3Within a PlaintextBallotContest, all PlaintextBallotSelection have a unique sequenceOrder.

### C. Voting limits

1. All PlaintextBallotSelection must have a vote whose value is 0 or 1.

2. Within a PlaintextBallotContest, the sum of the PlaintextBallotSelection votes must be <= ContestDescription.votes_allowed.


## Ciphertext Tally

### A. Referential integrity

1. For each CiphertextTally.Contest in the tally, the contestId must match a ContestDescription.contestId in Manifest.contests.

   1.1 CiphertextTally.Contest description_hash must match the ContestDescription.crypto_hash.
   
2. Within the CiphertextTally.Contest and matching ContestDescription, each CiphertextTally.Selection.selectionId must match a SelectionDescription.selectionId.
   
   2.1 CiphertextTally.Selection description_hash must match the SelectionDescription.crypto_hash.

### B. Duplication

1. All CiphertextTally.Contest must have a unique contestId.

   1.1 In the contests map, the key must match the value.object_id. (JSON only)

2. Within a CiphertextTally.Contest, all CiphertextTally.Selection have a unique object_id. 

   2.1 In the selections map, the key must match the value.object_id.  (JSON only)


## Plaintext Tally

### A. Referential integrity with Manifest

1. For each PlaintextTally.Contest in the tally, the object_id must match a ContestDescription.object_id in Manifest.contests.
   
2. Within the PlaintextTally.Contest and matching ContestDescription, each PlaintextTally.Selection.object_id must match a SelectionDescription.object_id.

### B. Referential integrity with Ciphertext Tally

1. For each PlaintextTally.Contest in the tally, the contestId must match a CiphertextTally.Contest.contestId.
   
2. Within the PlaintextTally.Contest and matching CiphertextTally.Contest, each PlaintextTally.Selection.selectionId must match a CiphertextTally.Selection.selectionId.

  2.1 The PlaintextTally.Selection.message must match the CiphertextTally.Selection.message.
   
### C. Duplication

1. All PlaintextTally.Contest must have a unique contest_id. In the contests map, the key must match the value.object_id.  

2. Within a PlaintextTally.Contest, all PlaintextTally.Selection have a unique object_id. In the selections map, the key must match the value.object_id.

### D. Shares

1. All Selection shares have an object_id matching the Selection object_id.

2. Within a selection, the selection shares have a unique guardian_id. 

3 There are _nguardian_ shares.

### E. Recovered Shares

1. For each CiphertextDecryptionSelection share with recovered_parts, each recovered_part has an object_id matching the share object_id.

2. Each recovered_part has a unique guardian_id, matching the key in the share's map. 

3. There are _navailable_ recovered_parts. Note that navailable may be greater than quorum.

4. Each recovered_part has a missing_guardian_id matching the share's guardian_id



 