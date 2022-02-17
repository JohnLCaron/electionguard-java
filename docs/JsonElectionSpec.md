# 🗳 Election Record JSON serialization (proposed specification)
draft 2/17/2022

This covers only the election record, and not any serialized classes used in remote procedure calls.

# Data Types

| Data Type		 | JSON	                           | language type                                                          |
|------------|---------------------------------|------------------------------------------------------------------------|
| BigInteger | string	                         | hex encoded integer, arbitrary number of digits. |
| bool		     | boolean                         | boolean                                                                |
| isodate	   | string	                         | ISO 8601 formatted date/time                                           |
| timestamp	 | number	                         | 64 bit signed integer = secs since unix epoch  LOOK or millis?         |
| Dict[K,V]	 | array of objects V with name K	 | Dict[K,V], Map<K, V>                                                   |
| ElementModQ | object	                         | ElementModQ                                                            |
| ElementModP | object	                         | ElementModP                                                            |
| enum<T>	   | string                          | matches the name of an element of enum T                               |
| int		      | number	                         | 32 bit signed integer                                                  |
| List[T]	   | array                           | 0 or more objects of type T                                            |
| long		     | number	                         | 64 bit signed integer                                                  |
| str		      | string	                         | UTF-8 encoded string                                                   |
| T		        | object	                         | T                                                                      |

Notes

  1. Any field may be missing or null. Could try to document when fields must be present 
  2. Could document the filenames and directory layout
  3. bool currently inconsistent
  4. long only used for EncryptionDevice.device_id, could switch to str.
  5. could use timestamp everywhere instead of isodate
  6. could use base64 encoding for BigInteger for compactness and possibly processing speed

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

### class SchnorrProof
| Name		     | Type	 | Notes |
|------------|-------|-------|
| name       | str				| UNNEEDED |
| usage      | enum<ProofUsage>		| UNNEEDED |
| public_key | ElementModP  ||
| commitment | ElementModP  ||
| challenge  | ElementModQ  ||
| response   | ElementModQ  ||

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

### class ChaumPedersenProof|
| Name		 | Type	 | Notes |
|--------|-------|-------|
|    name| str  		| UNNEEDED |
|    usage| enum<ProofUsage>  	| UNNEEDED |
|    pad| ElementModP  ||
|    data| ElementModP  ||
|    challenge| ElementModQ  ||
|    response| ElementModQ  ||
   
                
## PublishedCiphertextTally

### class PublishedCiphertextTally
| Name		 | Type	 | Notes |
|--------|-------|-------|
|    object_id| str ||
|    contests| Dict[str, CiphertextTallyContest]    || 

### class CiphertextTallyContest
| Name		 | Type	 | Notes                                           |
|--------|-------|-------------------------------------------------|
|object_id| str  | matches ContestDescription.object_id, UNNEEDED? |
|    sequence_order| int  ||
|    description_hash| ElementModQ  ||
|    selections| Dict[str, CiphertextTallySelection]  ||

### class CiphertextTallySelection|
| Name		 | Type	 | Notes                                              |
|--------|-------|----------------------------------------------------|
|    object_id| str  | matches SelectionDescription.object_id, UNNEEDED? |
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
| Name		 | Type	 | Notes                                                          |
|--------|-------|----------------------------------------------------------------|
|    object_id| str  | matches ContestDescription.object_id. |
|    sequence_order| int  ||
|    ballot_selections| List[PlaintextBallotSelection]  ||

### class PlaintextBallotSelection
| Name		 | Type	 | Notes                                                              |
|--------|-------|--------------------------------------------------------------------|
|    object_id| str  | matches SelectionDescription.object_id. |
|    sequence_order| int  ||
|    vote| int  ||
|    is_placeholder_selection| bool  ||
|    extended_data| ExtendedData  ||
    

## SubmittedBallot

### class SubmittedBallot
| Name		 | Type	 | Notes                              |
|--------|-------|------------------------------------|
|    object_id| str  | matches PlaintextBallot.object_id. |
|    style_id| str  | matches BallotStyle.object_id.     |
|    manifest_hash| ElementModQ  ||
|    code_seed| ElementModQ  ||
|    code| ElementModQ  ||
|    contests| List[CiphertextBallotContest]  ||
|    timestamp| timestamp		|                                    |
|    crypto_hash| ElementModQ  ||
|    nonce| ElementModQ  ||
|    state| enum<BallotBoxState>  ||

### class CiphertextBallotContest
| Name		                  | Type	 | Notes                                                                            |
|-------------------------|-------|----------------------------------------------------------------------------------|
| object_id               | str  | matches ContestDescription.object_id: UNNEEDED, use sequence_order or int hash?. |
| sequence_order          | int  ||
| description_hash        | ElementModQ  | rename contest_hash                                                              |                                                                     |
| ballot_selections       | List[CiphertextBallotSelection]  ||
| ciphertext_accumulation | ElGamalCiphertext  ||
| crypto_hash             | ElementModQ  ||
| nonce                   | ElementModQ			| UNNEEDED                                                                         |
| proof                   | ConstantChaumPedersenProof   ||

### class CiphertextBallotSelection
| Name		 | Type	 | Notes                                                                             |
|--------|-------|-----------------------------------------------------------------------------------|
|    object_id| str  | matches SelectionDescription.object_id UNNEEDED, use sequence_order or int hash?. |
|    sequence_order| int  ||
|    description_hash| ElementModQ  | rename selection_hash                                                             |
|    ciphertext| ElGamalCiphertext  ||
|    crypto_hash| ElementModQ  ||
|    is_placeholder_selection| bool  ||
|    nonce| ElementModQ			| UNNEEDED                                                                          |
|    proof| DisjunctiveChaumPedersenProof  ||
|    extended_data| ElGamalCiphertext  ||

### class ConstantChaumPedersenProof
| Name		 | Type	 | Notes |
|--------|-------|-------|
|    name| str				| UNNEEDED |
|    usage| enum<ProofUsage>		| UNNEEDED |
|    pad| ElementModP  ||
|    data| ElementModP  ||
|    challenge| ElementModQ  ||
|    response| ElementModQ  ||
|    constant| int  ||

### class DisjunctiveChaumPedersenProof
| Name		 | Type	 | Notes |
|--------|-------|-------|
|name| str				| UNNEEDED |
|    usage| enum<ProofUsage>		| UNNEEDED |
|    proof_zero_pad| ElementModP  ||
|    proof_zero_data| ElementModP  ||
|    proof_one_pad| ElementModP  ||
|    proof_one_data| ElementModP  ||
|    proof_zero_challenge| ElementModQ  ||
|    proof_one_challenge| ElementModQ  ||
|    challenge| ElementModQ  ||
|    proof_zero_response| ElementModQ  ||
|    proof_one_response| ElementModQ  ||

### class ExtendedData|
| Name		 | Type	 | Notes |
|--------|-------|-------|
|    value| str  ||
|    length| int                || 
