# 🗳 Election Record serialization (proposed specification)
draft 2/22/2022

This covers only the election record, and not any serialized classes used in remote procedure calls.

Notes

  1. Any field may be missing or null. Could try to document when fields must be present.
  2. Could document the filenames and directory layout.

## Common

### class ChaumPedersenProof
| Name		    | JSON Name	 | Type               | Notes   |
|-----------|------------|--------------------|---------|
|           | name       | string  		         | removed |
|           | usage      | enum ProofUsage  	 | removed |
| pad       |            | ElementModP        |         |
| data      |            | ElementModP        |         |
| challenge |            | ElementModQ        |         |
| response  |            | ElementModQ        |         |

### class ElementModQ, ElementModP
| Name		 | JSON Name		 | Type   | Notes |
|--------|-------------|--------|-------|
| value  | data        | bytes	 |       |

### class ElGamalCiphertext|
| Name		 | JSON Name		 | Type        | Notes |
|--------|-------------|-------------|-------|
| pad    |             | ElementModP ||
| data   |             | ElementModP ||

### class ElGamalKeyPair LOOK
| Name		     | JSON Name		 | Type        | Notes |
|------------|-------------|-------------|-------|
| secret_key |             | ElementModQ ||
| public_key |             | ElementModP ||

### class SchnorrProof
| Name		     | JSON Name		 | Type              | Notes   |
|------------|-------------|-------------------|---------|
|            | name        | string				        | removed |
|            | usage       | enum ProofUsage		 | removed |
| public_key |             | ElementModP       ||
| commitment |             | ElementModP       ||
| challenge  |             | ElementModQ       ||
| response   |             | ElementModQ       ||


## Election

### class ElectionRecord

| Name		              | Type                      | Notes            |
|---------------------|---------------------------|------------------|
| version             | string                    | key ceremony     |
| constants           | ElectionConstants         | key ceremony     |
| manifest            | Manifest                  | key ceremony     |
| context             | ElectionContext           | key ceremony     |
| guardian_records    | List\<GuardianRecord\>    | key ceremony     |
| devices             | List\<EncryptionDevice\>  | key ceremony ??  |
| ciphertext_tally    | CiphertextTally           | accumulate tally |
| plaintext_tally     | PlaintextTally            | decrypt tally    |
| available_guardians | List\<AvailableGuardian\> | decrypt tally    |

### class AvailableGuardian
| Name                | Type        | Notes                          |
|---------------------|-------------|--------------------------------|
| guardian_id         | string      |                                |
| x_coordinate        | string      | x_coordinate in the polynomial |
| lagrange_coordinate | ElementModQ |                                |

### class ElectionConstants
| Name        | JSON Name  | Type  | Notes |
|-------------|------------|-------|-------|
| large_prime |            | bytes ||
| small_prime |            | bytes ||
| cofactor    |            | bytes ||
| generator   |            | bytes ||

### class ElectionContext

| Name		                    | JSON Name	 | Type                  | Notes |
|---------------------------|------------|-----------------------|-------|
| number_of_guardians       |            | uint32                |       |
| quorum                    |            | uint32                |       |
| elgamal_public_key        |            | ElementModP           |       |
| commitment_hash           |            | ElementModQ           |       |
| manifest_hash             |            | ElementModQ           |       |
| crypto_base_hash          |            | ElementModQ           |       |
| crypto_extended_base_hash |            | ElementModQ           |       |
| extended_data             |            | map\<string, string\> |       |

### class EncryptionDevice
| Name        | JSON Name  | Type   | Notes                                  |
|-------------|------------|--------|----------------------------------------|
| device_id   |            | int64	 | was uuid LOOK maybe just use a string? |
| session_id  |            | int64	 |                                        |
| launch_code |            | int64	 |                                        |
| location    |            | string |                                        |

### class GuardianRecord
| Name                 | JSON Name	 | Type                 | Notes                          |
|----------------------|------------|----------------------|--------------------------------|
| guardian_id          |            | string               |                                |
| x_coordinate         |            | uint32               | x_coordinate in the polynomial |
| election_public_key  |            | ElementModP          |                                |
| election_commitments |            | List\<ElementModP\>  |                                |
| election_proofs      |            | List\<SchnorrProof\> |                                |

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
| Name		              | JSON Name		 | Type	                      | Notes                        |
|---------------------|-------------|----------------------------|------------------------------|
| election_scope_id   |             | string                     |                              |
| spec_version        |             | string                     |                              |
| election_type       | type        | enum ElectionType          |                              |
| start_date          |             | string                     | ISO 8601 formatted date/time |
| end_date            |             | string                     | ISO 8601 formatted date/time |
| geopolitical_units  |             | List\<GeopoliticalUnit\>   |                              |
| parties             |             | List\<Party\>              |                              |
| candidates          |             | List\<Candidate\>          |                              |
| contests            |             | List\<ContestDescription\> |                              |
| ballot_styles       |             | List\<BallotStyle\>        |                              |
| name                |             | InternationalizedText      |                              |
| contact_information |             | ContactInformation         |                              |

### class AnnotatedString
| Name		     | JSON Name		 | Type	  | Notes |
|------------|-------------|--------|-------|
| annotation |             | string |       |
| value      |             | string |       |

### class BallotStyle
| Name	                 | JSON Name  | Type           | Notes                                         |
|-----------------------|------------|----------------|-----------------------------------------------|
| ballot_style_id       | object_id  | string         |                                               |
| geopolitical_unit_ids |            | List\<string\> | matches GeoPoliticalUnit.geopolitical_unit_id |
| party_ids             |            | List\<string\> | matches Party.party_id                        |
| image_uri             |            | string         |                                               |

### class Candidate
| Name		       | JSON Name		 | Type	                 | Notes                                      |
|--------------|-------------|-----------------------|--------------------------------------------|
| candidate_id | object_id   | string                |                                            |
| name         |             | InternationalizedText |                                            |
| party_id     |             | string                | matches Party.party_id                     |
| image_uri    |             | string                |                                            |
| is_write_in  |             | bool                  | assumes all write-ins are known in advance |

### class ContactInformation
| Name		       | JSON Name		 | Type	          | Notes |
|--------------|-------------|----------------|-------|
| name         |             | string         |       |
| address_line |             | List\<string\> |       |
| email        |             | List\<string\> |       |
| phone        |             | List\<string\> |       |

### class GeopoliticalUnit
| Name		               | JSON Name		 | Type	                  | Notes |
|----------------------|-------------|------------------------|-------|
| geopolitical_unit_id | object_id   | string                 |       |
| name                 |             | string                 |       |
| type                 |             | enum ReportingUnitType |       |
| contact_information  |             | ContactInformation     |       |

### class InternationalizedText
| Name		 | JSON Name		 | Type	            | Notes |
|--------|-------------|------------------|-------|
| text   |             | List\<Language\> |       |

### class Language
| Name		   | JSON Name		 | Type	  | Notes |
|----------|-------------|--------|-------|
| value    |             | string |       |
| language |             | string |       |

### class Party
| Name		       | JSON Name		 | Type	                 | Notes |
|--------------|-------------|-----------------------|-------|
| party_id     | object_id   | string                |       |
| name         |             | InternationalizedText |       |
| abbreviation |             | string                |       |
| color        |             | string                |       |
| logo_uri     |             | string                |       |

### class ContestDescription
| Name		               | JSON Name		           | Type	                        | Notes                                              |
|----------------------|-----------------------|------------------------------|----------------------------------------------------|
| contest_id           | object_id             | string                       |                                                    |
| sequence_order       |                       | uint32                       | deterministic sorting                              |
| geopolitical_unit_id | electoral_district_id | string                       | matches GeoPoliticalUnit.geopolitical_unit_id LOOK |
| vote_variation       |                       | enum VoteVariationType       |                                                    |
| number_elected       |                       | uint32                       |                                                    |
| votes_allowed        |                       | uint32                       |                                                    |
| name                 |                       | string                       |                                                    |
| selections           |                       | List\<SelectionDescription\> |                                                    |
| ballot_title         |                       | InternationalizedText        |                                                    |
| ballot_subtitle      |                       | InternationalizedText        |                                                    |
| primary_party_ids    |                       | List\<string\>               | matches Party.party_id LOOK                        |

### class SelectionDescription
| Name		         | JSON Name		 | Type	  | Notes                          |
|----------------|-------------|--------|--------------------------------|
| selection_id   | object_id   | string |                                |
| sequence_order |             | uint32 | deterministic sorting          |
| candidate_id   |             | string | matches Candidate.candidate_id |


## PlaintextTally

### class PlaintextTally
| Name		   | JSON Name		 | Type	                         | Notes                                                             |
|----------|-------------|-------------------------------|-------------------------------------------------------------------|
| tally_id | object_id   | string                        | when decrypted spoiled ballots, matches SubmittedBallot.ballot_id |
| contests |             | List\<PlaintextTallyContest\> |                                                                   |

### class PlaintextTallyContest
| Name		     | JSON Name		 | Type	                           | Notes                                  |
|------------|-------------|---------------------------------|----------------------------------------|
| contest_id | object_id   | string                          | matches ContestDescription.contest_id. |
| selections |             | List\<PlaintextTallySelection\> | removed unneeded map                   |

### class PlaintextTallySelection
| Name		       | JSON Name		 | Type	                                 | Notes                                      |
|--------------|-------------|---------------------------------------|--------------------------------------------|
| selection_id | object_id   | string                                | matches SelectionDescription.selection_id  |
| tally        |             | int                                   |                                            |
| value        |             | ElementModP                           |                                            |
| message      |             | ElGamalCiphertext                     |                                            |
| shares       |             | List\<CiphertextDecryptionSelection\> | removed unneeded map                       |

### class CiphertextDecryptionSelection
| Name	           | JSON Name | Type                                                    | Notes                           |
|-----------------|-----------|---------------------------------------------------------|---------------------------------|
| selection_id    | object_id | string                                                  | get_tally_shares_for_selection2 |
| guardian_id     |           | string                                                  |                                 |
| share           |           | ElementModP                                             |                                 |
| proof           |           | ChaumPedersenProof                                      |                                 |
| recovered_parts |           | map\<string, CiphertextCompensatedDecryptionSelection\> |                                 |

### class CiphertextCompensatedDecryptionSelection(ElectionObjectBase)
| Name		              | JSON Name | Type      	        | Notes    |
|---------------------|-----------|--------------------|----------|
| selection_id        | object_id | string             | unneeded |
| guardian_id         |           | string             |          |
| missing_guardian_id |           | string             |          |
| share               |           | ElementModP        |          |
| recovery_key        |           | ElementModP        |          |
| proof               |           | ChaumPedersenProof |          |
 
## CiphertextTally

### class CiphertextTally
| Name		   | JSON Name | Type		                         | Notes                |
|----------|-----------|--------------------------------|----------------------|
| tally_id | object_id | string                         |                      |
| contests |           | List\<CiphertextTallyContest\> | removed unneeded map | 

### class CiphertextTallyContest
| Name		                   | JSON Name | Type    		                       | Notes                                  |
|--------------------------|-----------|----------------------------------|----------------------------------------|
| contest_id               | object_id | string                           | matches ContestDescription.contest_id  |
| sequence_order           |           | uint32                           |                                        |
| contest_description_hash |           | ElementModQ                      | matches ContestDescription.crypto_hash |
| selections               |           | List\<CiphertextTallySelection\> | removed unneeded map                   |

### class CiphertextTallySelection|
| Name		                     | JSON Name | Type    	         | Notes                                     |
|----------------------------|-----------|-------------------|-------------------------------------------|
| selection_id               | object_id | string            | matches SelectionDescription.selection_id |
| sequence_order             |           | uint32            |                                           |
| selection_description_hash |           | ElementModQ       | matches SelectionDescription.crypto_hash  |
| ciphertext                 |           | ElGamalCiphertext |                                           |
    
    
## PlaintextBallot

### class PlaintextBallot
| Name		          | JSON Name | Type  		                       | Notes                               |
|-----------------|-----------|--------------------------------|-------------------------------------|
| ballot_id       | object_id | string                         | unique input ballot id              |
| ballot_style_id | style_id  | string                         | matches BallotStyle.ballot_style_id |
| contests        |           | List\<PlaintextBallotContest\> |                                     |

### class PlaintextBallotContest
| Name		         | JSON Name | Type  		                         | Notes                                  |
|----------------|-----------|----------------------------------|----------------------------------------|
| contest_id     | object_id | string                           | matches ContestDescription.contest_id. |
| sequence_order |           | uint32                           |                                        |
| selections     |           | List\<PlaintextBallotSelection\> |                                        |

### class PlaintextBallotSelection
| Name		                   | JSON Name | Type  		     | Notes                                      |
|--------------------------|-----------|--------------|--------------------------------------------|
| selection_id             | object_id | string       | matches SelectionDescription.selection_id. |
| sequence_order           |           | uint32       |                                            |
| vote                     |           | uint32       |                                            |
| is_placeholder_selection |           | bool         |                                            |
| extended_data            |           | ExtendedData | unused                                     |

### class ExtendedData|
| Name		 | JSON Name | Type  		 | Notes |
|--------|-----------|----------|-------|
| value  |           | string   |       |
| length |           | uint32   | why?  | 


## CyphertextBallot

### class SubmittedBallot
| Name		                 | JSON Name | Type  		                        | Notes                                |
|------------------------|-----------|---------------------------------|--------------------------------------|
| ballot_id              | object_id | string                          | matches PlaintextBallot.ballot_id.   |
| ballot_style_id        | style_id  | string                          | matches BallotStyle.ballot_style_id. |
| manifest_hash          |           | ElementModQ                     |                                      |
| previous_tracking_hash | code_seed | ElementModQ                     |                                      |
| tracking_hash          | code      | ElementModQ                     |                                      |
| contests               |           | List\<CiphertextBallotContest\> |                                      |
| timestamp              |           | int64                           | seconds since the unix epoch UTC     |
| crypto_hash            |           | ElementModQ                     |                                      |
|                        | nonce     | ElementModQ                     | removed                              |
| state                  |           | enum BallotState                | CAST, SPOILED                        |

### class CiphertextBallotContest
| Name		                  | JSON Name         | Type  		                          | Notes                                          |
|-------------------------|-------------------|-----------------------------------|------------------------------------------------|
| contest_id              | object_id         | string                            | matches ContestDescription.contest_id  REMOVE? |
| sequence_order          |                   | uint32                            |                                                |
| contest_hash            | description_hash  | ElementModQ                       | matches ContestDescription.crypto_hash         |                                                                     |
| selections              | ballot_selections | List\<CiphertextBallotSelection\> |                                                |
| ciphertext_accumulation |                   | ElGamalCiphertext                 |                                                |
| crypto_hash             |                   | ElementModQ                       |                                                |
|                         | nonce             | ElementModQ                       | removed                                        |
| proof                   |                   | ConstantChaumPedersenProof        |                                                |

### class CiphertextBallotSelection
| Name		                   | JSON Name        | Type  		                      | Notes                                             |
|--------------------------|------------------|-------------------------------|---------------------------------------------------|
| selection_id             | object_id        | string                        | matches SelectionDescription.selection_id REMOVE? |
| sequence_order           |                  | uint32                        |                                                   |
| selection_hash           | description_hash | ElementModQ                   | matches SelectionDescription.crypto_hash          |
| ciphertext               |                  | ElGamalCiphertext             |                                                   |
| crypto_hash              |                  | ElementModQ                   |                                                   |
| is_placeholder_selection |                  | bool                          |                                                   |
|                          | nonce            | ElementModQ                   | removed                                           |
| proof                    |                  | DisjunctiveChaumPedersenProof |                                                   |
| extended_data            |                  | ElGamalCiphertext             |                                                   |

### class ConstantChaumPedersenProof
| Name		    | JSON Name | Type  		        | Notes   |
|-----------|-----------|-----------------|---------|
|           | name      | string          | removed |
|           | usage     | enum ProofUsage | removed |
| pad       |           | ElementModP     |         |
| data      |           | ElementModP     |         |
| challenge |           | ElementModQ     |         |
| response  |           | ElementModQ     |         |
| constant  |           | int32           |         |

### class DisjunctiveChaumPedersenProof
| Name                 | JSON Name | Type            | Notes   |
|----------------------|-----------|-----------------|---------|
|                      | name      | string          | removed |
|                      | usage     | enum ProofUsage | removed |
| proof_zero_pad       |           | ElementModP     |         |
| proof_zero_data      |           | ElementModP     |         |
| proof_zero_challenge |           | ElementModQ     |         |
| proof_zero_response  |           | ElementModQ     |         |
| proof_one_pad        |           | ElementModP     |         |
| proof_one_data       |           | ElementModP     |         |
| proof_one_challenge  |           | ElementModQ     |         |
| proof_one_response   |           | ElementModQ     |         |
| challenge            |           | ElementModQ     |         |
