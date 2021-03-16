# 🗳 ElectionGuard Input Validation

The Election Manifest and each input plaintext ballot are expected to be validated before being passed to the 
electionguard library. This document summarizes the expected validations.

A specific validation is referenced as, eg, Manifest.B.5 and Ballot.A.2.1

## Manifest

### A. Referential integrity

1. Referential integrity of BallotStyle's geopolitical_unit_ids.
    * For each BallotStyle, all geopolitical_unit_ids reference a GeopoliticalUnit in Manifest.geopolitical_units

2. Referential integrity of Candidate party_id.
    * For each Candidate, the party_id references a Party in Manifest.parties
    
3. Referential integrity of ContestDescription electoral_district_id.
    * For each ContestDescription, the electoral_district_id references a GeopoliticalUnit in Manifest.geopolitical_units    

4. Referential integrity of SelectionDescription candidate_id.
    * For each SelectionDescription, the candidate_id references a Candidate in Manifest.candidates    
    
### B. Duplication

1. All ContestDescription have a unique object_id.   

2. All ContestDescription have a unique sequence_order .  

3. Within a ContestDescription, all SelectionDescription have a unique object_id.

4. Within a ContestDescription, all SelectionDescription have a unique sequence_order.

5. Within a ContestDescription, all SelectionDescription have a unique candidate_id.

## Input Ballot

### A. Referential integrity

1. A ballot's style_id must match a BallotStyle object_id in Manifest.ballot_styles.

2. For each PlaintextBallot.Contest on the ballot, the contest_id must match a ContestDescription.object_id in Manifest.contests.
   
   2.1 Within the Contest and matching ContestDescription, each PlaintextBallot.Selection.selection_id must match a SelectionDescription.object_id.
   
### B. Duplication

1. All PlaintextBallot.Contest have a unique contest_id.   

2. Within a Contest, all PlaintextBallot.Selection have a unique selection_id.

### C. Voting limits

1. All PlaintextBallot.Selection must have a vote whose value is 0 or 1.

2. Within a Contest, the sum of the PlaintextBallot.Selection votes must be <= ContestDescription.votes_allowed.

