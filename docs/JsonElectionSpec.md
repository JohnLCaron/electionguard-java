# 🗳 Election Record JSON serialization (proposed specification)
draft 2/17/2022

This covers only the election record, and not any serialized classes used in remote procedure calls.

# Data Types

| Data Type		 | JSON	                           | language type                                                  |
|-------------|---------------------------------|----------------------------------------------------------------|
| BigInteger  | string	                         | hex encoded integer, arbitrary number of digits.               |
| bool		      | boolean                         | boolean                                                        |
| isodate	    | string	                         | ISO 8601 formatted date/time                                   |
| timestamp	  | number	                         | 64 bit signed integer = secs since unix epoch  LOOK or millis? |
| Dict[K,V]	  | array of objects V with name K	 | Dict[K,V], Map\<K, V\>                                         |
| ElementModQ | object	                         | ElementModQ                                                    |
| ElementModP | object	                         | ElementModP                                                    |
| enum\<T\>	  | string                          | matches the name of an element of enum T                       |
| int		       | number	                         | 32 bit signed integer                                          |
| List[T]	    | array                           | 0 or more objects of type T                                    |
| long		      | number	                         | 64 bit signed integer                                          |
| str		       | string	                         | UTF-8 encoded string                                           |
| T		         | object	                         | T                                                              |

Notes

  1. Any field may be missing or null. Could try to document when fields must be present.
  2. Could document the filenames and directory layout.
  3. bool currently inconsistent.
  4. long only used for EncryptionDevice.device_id, could switch to str.
  5. Could use base64 encoding for BigInteger for compactness and possibly processing speed.
  6. Could remove Proof as a base class for all of the proofs, as its fields are unneeded.
  7. Could add add library/serialization version to allow independent evolution from specification version

## Election

### class CiphertextElectionContext
| Name		 | Type	 | Notes |
|--------|-------|-------|
|   number_of_guardians |int||
|    quorum| int||
|    elgamal_public_key| ElementModP||
|    commitment_hash| ElementModQ||
|    manifest_hash| ElementModQ||
|    crypto_base_hash| ElementModQ||
|    crypto_extended_base_hash| ElementModQ||
|    extended_data| Dict[str, str]  ||

### class ElectionConstants
| Name		      | Type	 | Notes |
|-------------|-------|-------|
| large_prime | BigInteger  ||
| small_prime | BigInteger  ||
| cofactor    | BigInteger  ||
| generator   | BigInteger  ||

### class ElementModQ, ElementModP
| Name		 | Type	       | Notes                         |
|--------|-------------|-------------------------------|
| data   | BigInteger	 |  |

### class EncryptionDevice
| Name		      | Type	 | Notes                                  |
|-------------|-------|----------------------------------------|
| device_id   | long	 | was uuid LOOK maybe just use a string? |
| session_id  | int	  |                                        |
| launch_code | int	  |                                        |
| location    | str   ||

### class LagrangeCoefficients
| Name		      | Type	                  | Notes                                  |
|-------------|------------------------|----------------------------------------|
| coefficients   | Dict[str,ElementModQ]	 | |

### class GuardianRecord
| Name		 | Type	 | Notes                       |
|--------|-------|-----------------------------|
|    guardian_id| GuardianId  ||
|    sequence_order| int  | change name to x_coordinate |
|    election_public_key| ElementModP  ||
|    election_commitments| List[ElementModP]  ||
|    election_proofs| List[SchnorrProof]  ||

### class ChaumPedersenProof
| Name		 | Type	                 | Notes |
|--------|-----------------------|-------|
|    name| str  		               | UNNEEDED |
|    usage| enum\<ProofUsage\>  	 | UNNEEDED |
|    pad| ElementModP           ||
|    data| ElementModP           ||
|    challenge| ElementModQ           ||
|    response| ElementModQ           ||

### class SchnorrProof
| Name		     | Type	                | Notes |
|------------|----------------------|-------|
| name       | str				              | UNNEEDED |
| usage      | enum\<ProofUsage\>		 | UNNEEDED |
| public_key | ElementModP          ||
| commitment | ElementModP          ||
| challenge  | ElementModQ          ||
| response   | ElementModQ          ||

### class ElGamalKeyPair
| Name		 | Type	 | Notes |
|--------|-------|-------|
|secret_key| ElementModQ  ||
|    public_key| ElementModP  ||

### class ElGamalCiphertext|
| Name		 | Type	 | Notes |
|--------|-------|-------|
|    pad| ElementModP  ||
|    data| ElementModP||

## Manifest

Could simplify to be just the fields needed by electionguard library.
Assume that there is an existing system that captures all the metadata that election software need, and that is a
superset of this.

Notes
  1. Could try to keep this record independent of the crypto.
  2. Could add manifest manifest schema version to allow independent evolution independent from the spec_version
  3. Could add manifest version for a specific election to allow independent evolution
  4. If write in candidates are known in advance, PlaintextBallot.ExtendedData is unneeded.

### class Manifest
| Name		 | Type	                    | Notes                                               |
|--------|--------------------------|-----------------------------------------------------|
|    election_scope_id| str                      ||
|    spec_version| str		                    | |
|    type| enum\<ElectionType\>     ||
|    start_date| isodate                  |                                                     |
|    end_date| isodate                  |                                                     |
|    geopolitical_units| List[GeopoliticalUnit]   ||
|    parties| List[Party]              ||
|    candidates| List[Candidate]          ||
|    contests| List[ContestDescription] ||
|    ballot_styles| List[BallotStyle]        ||
|    name| InternationalizedText    ||
|    contact_information| ContactInformation       ||

### class AnnotatedString
| Name		 | Type	 | Notes |
|--------|-------|-------|
|    annotation| str  ||
|    value| str  ||

### class BallotStyle
| Name		 | Type	 | Notes                                   |
|--------|-------|-----------------------------------------|
|    object_id| str  ||
|    geopolitical_unit_ids| List[str]  | matches GeoPoliticalUnit.object_id      |
|    party_ids| List[str]  | matches Party.object_id? LOOK How used? |
|    image_uri| str  ||

### class Candidate
| Name		 | Type	 | Notes                                     |
|--------|-------|-------------------------------------------|
|    object_id| str  ||
|    name| InternationalizedText  ||
|    party_id| str  | matches Party.object_id                   |
|    image_uri| str  ||
|    is_write_in| bool  | assume all write-ins are known in advance |

### class ContactInformation
| Name		 | Type	 | Notes |
|--------|-------|-------|
|    name| str  ||
|    address_line| List[str]  ||
|    email| List[str]  ||
|    phone| List[str]  ||

### class GeopoliticalUnit
| Name		 | Type	                     | Notes |
|--------|---------------------------|-------|
|    object_id| str                       ||
|    name| str                       ||
|    type| enum\<ReportingUnitType\> ||
|    contact_information| ContactInformation        ||

### class InternationalizedText
| Name		 | Type	          | Notes |
|--------|----------------|-------|
|    text| List[Language] ||

### class Language
| Name		 | Type	 | Notes |
|--------|-------|-------|
|    value| str  ||
|    language| str  ||

### class Party
| Name		 | Type	 | Notes |
|--------|-------|-------|
|    object_id| str  ||
|    name| InternationalizedText  ||
|    abbreviation| str  ||
|    color| str  ||
|    logo_uri| str  ||

### class ContestDescription
| Name		 | Type	                      | Notes                           |
|--------|----------------------------|---------------------------------|
|    object_id| str                        ||
|    sequence_order| int                        | deterministic sorting           |
|    electoral_district_id| str                        | matches GeoPoliticalUnit.object_id |
|    vote_variation| enum\<VoteVariationType\>  ||
|    number_elected| int                        ||
|    votes_allowed| int                        ||
|    name| str                        ||
|    ballot_selections| List[SelectionDescription] ||
|    ballot_title| InternationalizedText      ||
|    ballot_subtitle| InternationalizedText      ||
|    primary_party_ids| List[str]                  | Added from  CandidateContestDescription |

### class SelectionDescription
| Name		 | Type	 | Notes                        |
|--------|-------|------------------------------|
|    object_id| str  ||
|    sequence_order| int  | deterministic sorting        |
|    candidate_id| str  | matches Candidate.object_id  |


## PlaintextTally

### class PlaintextTally
| Name		 | Type	 | Notes                                                            |
|--------|-------|------------------------------------------------------------------|
|    object_id| str  | for decrypted spoiled ballots, matches SubmittedBallot.object_id |
|    contests| Dict[str, PlaintextTallyContest]  ||

### class PlaintextTallyContest
| Name		 | Type	 | Notes                                  |
|--------|-------|----------------------------------------|
|    object_id| str		| matches ContestDescription.object_id.  |
|    selections| Dict[str, PlaintextTallySelection]  ||

### class PlaintextTallySelection
| Name		 | Type	 | Notes |
|--------|-------|-------|
|    object_id| str  | matches SelectionDescription.object_id. |
|    tally| int  ||
|    value| ElementModP  ||
|    message| ElGamalCiphertext  ||
|    shares| List[CiphertextDecryptionSelection]  ||

### class CiphertextDecryptionSelection
| Name		 | Type	 | Notes                                           |
|--------|-------|-------------------------------------------------|
|object_id| str      | matches SelectionDescription.object_id UNNEEDED |
|    guardian_id| str  ||
|   share| ElementModP  ||
|   proof| ChaumPedersenProof  ||
|    recovered_parts| Dict[str, CiphertextCompensatedDecryptionSelection]  ||

### class CiphertextCompensatedDecryptionSelection(ElectionObjectBase)
| Name		 | Type	 | Notes    |
|--------|-------|----------|
|    object_id| str          | UNEEDED? |
|    guardian_id| str  ||
|    missing_guardian_id| str  ||
|    share| ElementModP  ||
|    recovery_key| ElementModP  ||
|    proof| ChaumPedersenProof  ||
 
## PublishedCiphertextTally

### class PublishedCiphertextTally
| Name		 | Type	 | Notes |
|--------|-------|-------|
|    object_id| str ||
|    contests| Dict[str, CiphertextTallyContest]    || 

### class CiphertextTallyContest
| Name		 | Type	 | Notes                                           |
|--------|-------|-------------------------------------------------|
|object_id| str  | matches ContestDescription.object_id |
|    sequence_order| int  ||
|    description_hash| ElementModQ  ||
|    selections| Dict[str, CiphertextTallySelection]  ||

### class CiphertextTallySelection|
| Name		 | Type	 | Notes                                   |
|--------|-------|-----------------------------------------|
|    object_id| str  | matches SelectionDescription.object_id  |
|    sequence_order| int  ||
|    description_hash| ElementModQ  ||
|    ciphertext| ElGamalCiphertext  ||
    
    
## PlaintextBallot

### class PlaintextBallot
| Name		 | Type	 | Notes                         |
|--------|-------|-------------------------------|
|    object_id| str  | unique input ballot id        |
|    style_id| str  | matches BallotStyle.object_id |
|    contests| List[PlaintextBallotContest]  |                               |

### class PlaintextBallotContest
| Name		 | Type	 | Notes                                          |
|--------|-------|------------------------------------------------|
|    object_id| str  | matches ContestDescription.object_id.  |
|    sequence_order| int  ||
|    ballot_selections| List[PlaintextBallotSelection]  ||

### class PlaintextBallotSelection
| Name		 | Type	 | Notes                                      |
|--------|-------|--------------------------------------------|
|    object_id| str  | matches SelectionDescription.object_id.    |
|    sequence_order| int  ||
|    vote| int  ||
|    is_placeholder_selection| bool  ||
|    extended_data| ExtendedData  | UNNEEDED                                   |

### class ExtendedData|
| Name		 | Type	 | Notes |
|--------|-------|-------|
|    value| str  ||
|    length| int                || 

## CyphertextBallot

### class SubmittedBallot
| Name		 | Type	                         | Notes                              |
|--------|-------------------------------|------------------------------------|
|    object_id| str                           | matches PlaintextBallot.object_id. |
|    style_id| str                           | matches BallotStyle.object_id.     |
|    manifest_hash| ElementModQ                   ||
|    code_seed| ElementModQ                   ||
|    code| ElementModQ                   ||
|    contests| List[CiphertextBallotContest] ||
|    timestamp| timestamp		                   |                                    |
|    crypto_hash| ElementModQ                   ||
|    nonce| ElementModQ                   ||
|    state| enum\<BallotBoxState\>        ||

### class CiphertextBallotContest
| Name		                  | Type	 | Notes                                        |
|-------------------------|-------|----------------------------------------------|
| object_id               | str  | matches ContestDescription.object_id |
| sequence_order          | int  ||
| description_hash        | ElementModQ  | rename contest_hash                          |                                                                     |
| ballot_selections       | List[CiphertextBallotSelection]  ||
| ciphertext_accumulation | ElGamalCiphertext  ||
| crypto_hash             | ElementModQ  ||
| nonce                   | ElementModQ			| UNNEEDED                                     |
| proof                   | ConstantChaumPedersenProof   ||

### class CiphertextBallotSelection
| Name		 | Type	 | Notes                                           |
|--------|-------|-------------------------------------------------|
|    object_id| str  | matches SelectionDescription.object_id |
|    sequence_order| int  ||
|    description_hash| ElementModQ  | rename selection_hash                           |
|    ciphertext| ElGamalCiphertext  ||
|    crypto_hash| ElementModQ  ||
|    is_placeholder_selection| bool  ||
|    nonce| ElementModQ			| UNNEEDED                                        |
|    proof| DisjunctiveChaumPedersenProof  ||
|    extended_data| ElGamalCiphertext  ||

### class ConstantChaumPedersenProof
| Name		 | Type	 | Notes |
|--------|-------|-------|
|    name| str				| UNNEEDED |
|    usage| enum\<ProofUsage\>		| UNNEEDED |
|    pad| ElementModP  ||
|    data| ElementModP  ||
|    challenge| ElementModQ  ||
|    response| ElementModQ  ||
|    constant| int  ||

### class DisjunctiveChaumPedersenProof
| Name		 | Type	 | Notes |
|--------|-------|-------|
|name| str				| UNNEEDED |
|    usage| enum\<ProofUsage\>		| UNNEEDED |
|    proof_zero_pad| ElementModP  ||
|    proof_zero_data| ElementModP  ||
|    proof_one_pad| ElementModP  ||
|    proof_one_data| ElementModP  ||
|    proof_zero_challenge| ElementModQ  ||
|    proof_one_challenge| ElementModQ  ||
|    challenge| ElementModQ  ||
|    proof_zero_response| ElementModQ  ||
|    proof_one_response| ElementModQ  ||