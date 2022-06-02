# 🗳 Election Record serialization (proposed specification)

draft 5/13/2022 for proto_version = 1.0.0 (MAJOR.MINOR.PATCH)

This covers only the election record, and not any serialized messages used in remote procedure calls.

Notes

1. All fields must be present unless marked as optional.
2. A missing (optional) String should be internally encoded as null (not empty string), to agree with python hashing.
3. Proto_version uses [semantic versioning](https://semver.org/)

## common.proto

### message ElementModQ

| Name  | JSON Name | Type  | Notes                                           |
|-------|-----------|-------|-------------------------------------------------|
| value | data      | bytes | unsigned, big-endian, 0 left-padded to 32 bytes |

### message ElementModP

| Name  | JSON Name | Type  | Notes                                            |
|-------|-----------|-------|--------------------------------------------------|
| value | data      | bytes | unsigned, big-endian, 0 left-padded to 512 bytes |

### message ElGamalCiphertext

| Name | JSON Name | Type        | Notes |
|------|-----------|-------------|-------|
| pad  |           | ElementModP |       |
| data |           | ElementModP |       |

### message GenericChaumPedersenProof

| Name      | JSON Name | Type            | Notes   |
|-----------|-----------|-----------------|---------|
|           | name      | string          | removed |
|           | usage     | enum ProofUsage | removed |
| pad       |           | ElementModP     |         |
| data      |           | ElementModP     |         |
| challenge |           | ElementModQ     |         |
| response  |           | ElementModQ     |         |

### message HashedElGamalCiphertext

| Name     | Type        | Notes |
|----------|-------------|-------|
| c0       | ElementModP |       |
| c1       | bytes       |       |
| c2       | UInt256     |       |
| numBytes | uint32      |       |

### message SchnorrProof

| Name       | JSON Name | Type            | Notes   |
|------------|-----------|-----------------|---------|
|            | name      | string          | removed |
|            | usage     | enum ProofUsage | removed |
| public_key |           | ElementModP     |         |
| commitment |           | ElementModP     |         |
| challenge  |           | ElementModQ     |         |
| response   |           | ElementModQ     |         |

### message UInt256

JSON uses "byte array".

| Name  | Type  | Notes                                       |
|-------|-------|---------------------------------------------|
| value | bytes | unsigned, big-endian, 0 padded to 32 bytes  |


## election_record.proto

### message ElectionRecord

There is no python SDK version of this message.

| Name                | Type                      | Notes                |
|---------------------|---------------------------|----------------------|
| proto_version       | string                    | proto schema version |
| constants           | ElectionConstants         | key ceremony         |
| manifest            | Manifest                  | key ceremony         |
| context             | ElectionContext           | key ceremony         |
| guardian_records    | List\<GuardianRecord\>    | key ceremony         |
| devices             | List\<EncryptionDevice\>  | key ceremony ??      |
| ciphertext_tally    | CiphertextTally           | accumulate tally     |
| plaintext_tally     | PlaintextTally            | decrypt tally        |
| available_guardians | List\<AvailableGuardian\> | decrypt tally        |

### message ElectionConstants

| Name        | JSON Name | Type   | Notes                             |
|-------------|-----------|--------|-----------------------------------|
| name        |           | string | not present in JSON               |
| large_prime |           | bytes  | bigint is unsigned and big-endian |
| small_prime |           | bytes  | bigint is unsigned and big-endian |
| cofactor    |           | bytes  | bigint is unsigned and big-endian |
| generator   |           | bytes  | bigint is unsigned and big-endian |

### message ElectionContext

| Name                      | JSON Name          | Type                  | Notes    |
|---------------------------|--------------------|-----------------------|----------|
| number_of_guardians       |                    | uint32                |          |
| quorum                    |                    | uint32                |          |
| joint_public_key          | elgamal public key | ElementModP           |          |
| manifest_hash             |                    | UInt256               |          |
| crypto_base_hash          |                    | UInt256               |          |
| crypto_extended_base_hash |                    | UInt256               |          |
| commitment_hash           |                    | UInt256               |          |
| extended_data             |                    | map\<string, string\> | optional |

### message EncryptionDevice

| Name        | JSON Name | Type   | Notes                                  |
|-------------|-----------|--------|----------------------------------------|
| device_id   |           | int64  | was uuid LOOK maybe just use a string? |
| session_id  |           | int64  |                                        |
| launch_code |           | int64  |                                        |
| location    |           | string |                                        |

### message GuardianRecord

| Name                    | JSON Name            | Type                 | Notes                           |
|-------------------------|----------------------|----------------------|---------------------------------|
| guardian_id             |                      | string               |                                 |
| x_coordinate            | sequence_order       | uint32               | x_coordinate in the polynomial  |
| guardian_public_key     | election_public_key  | ElementModP          | not needed = commitment0        |
| coefficient_commitments | election_commitments | List\<ElementModP\>  | not needed, in proof.public_key |
| coefficient_proofs      | election_proofs      | List\<SchnorrProof\> |                                 |

### message AvailableGuardian

There is no python SDK version of this message. Can be constructed from the LagrangeCoefficients JSON.

| Name                | Type        | Notes                          |
|---------------------|-------------|--------------------------------|
| guardian_id         | string      |                                |
| x_coordinate        | string      | x_coordinate in the polynomial |
| lagrange_coordinate | ElementModQ |                                |


## manifest.proto

When the optional crypto_hash are passed, they are verified. If not passed in, they are recomputed.

### message Manifest

| Name                | JSON Name     | Type                       | Notes                        |
|---------------------|---------------|----------------------------|------------------------------|
| election_scope_id   |               | string                     |                              |
| spec_version        |               | string                     | the reference SDK version    |
| election_type       | type          | enum ElectionType          |                              |
| start_date          |               | string                     | ISO 8601 formatted date/time |
| end_date            |               | string                     | ISO 8601 formatted date/time |
| geopolitical_units  |               | List\<GeopoliticalUnit\>   |                              |
| parties             |               | List\<Party\>              |                              |
| candidates          |               | List\<Candidate\>          |                              |
| contests            |               | List\<ContestDescription\> |                              |
| ballot_styles       |               | List\<BallotStyle\>        |                              |
| name                |               | InternationalizedText      | optional                     |
| contact_information |               | ContactInformation         | optional                     |
| crypto_hash         | not present   |  UInt256                   | optional                     |

### message AnnotatedString

| Name       | JSON Name | Type   | Notes |
|------------|-----------|--------|-------|
| annotation |           | string |       |
| value      |           | string |       |

### message BallotStyle

| Name                  | JSON Name | Type           | Notes                                         |
|-----------------------|-----------|----------------|-----------------------------------------------|
| ballot_style_id       | object_id | string         |                                               |
| geopolitical_unit_ids |           | List\<string\> | matches GeoPoliticalUnit.geopolitical_unit_id |
| party_ids             |           | List\<string\> | optional matches Party.party_id               |
| image_uri             |           | string         | optional                                      |

### message Candidate

| Name         | JSON Name | Type                  | Notes                           |
|--------------|-----------|-----------------------|---------------------------------|
| candidate_id | object_id | string                |                                 |
| name         |           | InternationalizedText |                                 |
| party_id     |           | string                | optional matches Party.party_id |
| image_uri    |           | string                | optional                        |
| is_write_in  |           | bool                  |                                 |

### message ContactInformation

| Name         | JSON Name | Type           | Notes    |
|--------------|-----------|----------------|----------|
| name         |           | string         | optional |
| address_line |           | List\<string\> | optional |
| email        |           | List\<string\> | optional |
| phone        |           | List\<string\> | optional |

### message GeopoliticalUnit

| Name                 | JSON Name | Type                   | Notes    |
|----------------------|-----------|------------------------|----------|
| geopolitical_unit_id | object_id | string                 |          |
| name                 |           | string                 |          |
| type                 |           | enum ReportingUnitType |          |
| contact_information  |           | ContactInformation     | optional |

### message InternationalizedText

| Name | JSON Name | Type             | Notes |
|------|-----------|------------------|-------|
| text |           | List\<Language\> |       |

### message Language

| Name     | JSON Name | Type   | Notes |
|----------|-----------|--------|-------|
| value    |           | string |       |
| language |           | string |       |

### message Party

| Name         | JSON Name | Type                  | Notes    |
|--------------|-----------|-----------------------|----------|
| party_id     | object_id | string                |          |
| name         |           | InternationalizedText |          |
| abbreviation |           | string                | optional |
| color        |           | string                | optional |
| logo_uri     |           | string                | optional |

### message ContestDescription

| Name                 | JSON Name             | Type                         | Notes                                         |
|----------------------|-----------------------|------------------------------|-----------------------------------------------|
| contest_id           | object_id             | string                       |                                               |
| sequence_order       |                       | uint32                       | deterministic sorting                         |
| geopolitical_unit_id | electoral_district_id | string                       | GeoPoliticalUnit.geopolitical_unit_id |
| vote_variation       |                       | enum VoteVariationType       |                                               |
| number_elected       |                       | uint32                       |                                               |
| votes_allowed        |                       | uint32                       |                                               |
| name                 |                       | string                       |                                               |
| selections           | ballot_selections     | List\<SelectionDescription\> |                                               |
| ballot_title         |                       | InternationalizedText        | optional                                      |
| ballot_subtitle      |                       | InternationalizedText        | optional                                      |
| primary_party_ids    |                       | List\<string\>               | optional, match Party.party_id                |
| crypto_hash          | not present           | UInt256                      | optional                                      |

### message SelectionDescription

| Name           | JSON Name    | Type     | Notes                          |
|----------------|--------------|----------|--------------------------------|
| selection_id   | object_id    | string   |                                |
| sequence_order |              | uint32   | deterministic sorting          |
| candidate_id   |              | string   | matches Candidate.candidate_id |
| crypto_hash    | not present  | UInt256  | optional                       |

## plaintext_ballot.proto

### message PlaintextBallot

| Name            | JSON Name   | Type                           | Notes                               |
|-----------------|-------------|--------------------------------|-------------------------------------|
| ballot_id       | object_id   | string                         | unique input ballot id              |
| ballot_style_id | style_id    | string                         | matches BallotStyle.ballot_style_id |
| contests        |             | List\<PlaintextBallotContest\> |                                     |
| errors          | not present | string                         | optional eg an invalid ballot       |

### message PlaintextBallotContest

| Name           | JSON Name | Type                             | Notes                                     |
|----------------|-----------|----------------------------------|-------------------------------------------|
| contest_id     | object_id | string                           | matches ContestDescription.contest_id     |
| sequence_order |           | uint32                           | matches ContestDescription.sequence_order |
| selections     |           | List\<PlaintextBallotSelection\> |                                           |

### message PlaintextBallotSelection

| Name                     | JSON Name | Type   | Notes                                       |
|--------------------------|-----------|--------|---------------------------------------------|
| selection_id             | object_id | string | matches SelectionDescription.selection_id   |
| sequence_order           |           | uint32 | matches SelectionDescription.sequence_order |
| vote                     |           | uint32 |                                             |
| extended_data            |           | string | optional                                    |

## ciphertext_ballot.proto

### message SubmittedBallot

| Name              | JSON Name | Type                            | Notes                               |
|-------------------|-----------|---------------------------------|-------------------------------------|
| ballot_id         | object_id | string                          | matches PlaintextBallot.ballot_id   |
| ballot_style_id   | style_id  | string                          | matches BallotStyle.ballot_style_id |
| manifest_hash     |           | UInt256                         | matches Manifest.crypto_hash        |
| code_seed         |           | UInt256                         |                                     |
| code              |           | UInt256                         |                                     |
| contests          |           | List\<CiphertextBallotContest\> |                                     |
| timestamp         |           | int64                           | seconds since the unix epoch UTC    |
| crypto_hash       |           | UInt256                         |                                     |
|                   | nonce     | ElementModQ                     | removed                             |
| state             |           | enum BallotState                | CAST, SPOILED                       |

### message CiphertextBallotContest

| Name           | JSON Name               | Type                              | Notes                                     |
|----------------|-------------------------|-----------------------------------|-------------------------------------------|
| contest_id     | object_id               | string                            | matches ContestDescription.contest_id     |
| sequence_order |                         | uint32                            | matches ContestDescription.sequence_order |
| contest_hash   | description_hash        | UInt256                           | matches ContestDescription.crypto_hash    |                                                                     |
| selections     | ballot_selections       | List\<CiphertextBallotSelection\> |                                           |
|                | ciphertext_accumulation | ElGamalCiphertext                 | removed                                   |
| crypto_hash    |                         | UInt256                           |                                           |
|                | nonce                   | ElementModQ                       | removed                                   |
| proof          |                         | ConstantChaumPedersenProof        |                                           |

### message CiphertextBallotSelection

| Name                     | JSON Name        | Type                          | Notes                                       |
|--------------------------|------------------|-------------------------------|---------------------------------------------|
| selection_id             | object_id        | string                        | matches SelectionDescription.selection_id   |
| sequence_order           |                  | uint32                        | matches SelectionDescription.sequence_order |
| selection_hash           | description_hash | UInt256                       | matches SelectionDescription.crypto_hash    |
| ciphertext               |                  | ElGamalCiphertext             |                                             |
| crypto_hash              |                  | UInt256                       |                                             |
| is_placeholder_selection |                  | bool                          |                                             |
|                          | nonce            | ElementModQ                   | removed                                     |
| proof                    |                  | DisjunctiveChaumPedersenProof |                                             |
| extended_data            |                  | HashedElGamalCiphertext       | optional                                    |

### message ConstantChaumPedersenProof

| Name      | JSON Name | Type            | Notes   |
|-----------|-----------|-----------------|---------|
|           | name      | string          | removed |
|           | usage     | enum ProofUsage | removed |
| pad       |           | ElementModP     |         |
| data      |           | ElementModP     |         |
| challenge |           | ElementModQ     |         |
| response  |           | ElementModQ     |         |
| constant  |           | uint32          |         |

### message DisjunctiveChaumPedersenProof

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

## ciphertext_tally.proto

### message CiphertextTally

| Name     | JSON Name | Type                           | Notes                                                             |
|----------|-----------|--------------------------------|-------------------------------------------------------------------|
| tally_id | object_id | string                         | when decrypted spoiled ballots, matches SubmittedBallot.ballot_id |
| contests |           | List\<CiphertextTallyContest\> | removed unneeded map                                              | 

### message CiphertextTallyContest

| Name                     | JSON Name        | Type                             | Notes                             |
|--------------------------|------------------|----------------------------------|-----------------------------------|
| contest_id               | object_id        | string                           | ContestDescription.contest_id     |
| sequence_order           |                  | uint32                           | ContestDescription.sequence_order |
| contest_description_hash | description_hash | UInt256                          | ContestDescription.crypto_hash    |
| selections               |                  | List\<CiphertextTallySelection\> | removed unneeded map              |

### message CiphertextTallySelection

| Name                       | JSON Name        | Type              | Notes                               |
|----------------------------|------------------|-------------------|-------------------------------------|
| selection_id               | object_id        | string            | SelectionDescription.selection_id   |
| sequence_order             |                  | uint32            | SelectionDescription.sequence_order |
| selection_description_hash | description_hash | UInt256           | SelectionDescription.crypto_hash    |
| ciphertext                 |                  | ElGamalCiphertext |                                     |

## plaintext_tally.proto

### message PlaintextTally

| Name     | JSON Name | Type                          | Notes                                                             |
|----------|-----------|-------------------------------|-------------------------------------------------------------------|
| tally_id | object_id | string                        | when decrypted spoiled ballots, matches SubmittedBallot.ballot_id |
| contests |           | List\<PlaintextTallyContest\> |                                                                   |

### message PlaintextTallyContest

| Name       | JSON Name | Type                            | Notes                          |
|------------|-----------|---------------------------------|--------------------------------|
| contest_id | object_id | string                          | ContestDescription.contest_id  |
| selections |           | List\<PlaintextTallySelection\> | removed unneeded map           |

### message PlaintextTallySelection

| Name         | JSON Name | Type                        | Notes                               |
|--------------|-----------|-----------------------------|-------------------------------------|
| selection_id | object_id | string                      | SelectionDescription.selection_id   |
| tally        |           | int                         |                                     |
| value        |           | ElementModP                 |                                     |
| message      |           | ElGamalCiphertext           | CiphertextTallySelection.ciphertext |
| shares       |           | List\<PartialDecryption\>   | removed unneeded map                |

### message PartialDecryption (JSON "selection_share")

| Name            | JSON Name | Type                               | Notes                          |
|-----------------|-----------|------------------------------------|--------------------------------|
| selection_id    | object_id | string                             | get_tally_shares_for_selection |
| guardian_id     |           | string                             |                                |
| share           |           | ElementModP                        |                                |
| proof           |           | GenericChaumPedersenProof          | proof or recovered_parts       |
| recovered_parts |           | List\<MissingPartialDecryption\>   | removed unneeded map           |

### message MissingPartialDecryption (JSON "recovered_part")

| Name                   | JSON Name   | Type                       | Notes   |
|------------------------|-------------|----------------------------|---------|
|                        | object_id   | string                     | removed |
| decrypting_guardian_id | guardian_id | string                     |         |
| missing_guardian_id    |             | string                     |         |
| share                  |             | ElementModP                |         |
| recovery_key           |             | ElementModP                |         |
| proof                  |             | GenericChaumPedersenProof  |         |