# 🗳 ElectionGuard Java

### JSON Election Record Serialization (1.4.0)

| Class                      | ConvertToJson        | JSON                                                |
|----------------------------|----------------------|-----------------------------------------------------|
| Coefficients               | CoefficientsPojo     | coefficients.json                                   |
| ElectionConstants          |                      | constants.json                                      | 
| CiphertextElectionContext  |                      | context.json                                        |
| CiphertextTally            | CiphertextTallyPojo  | encrypted_tally.json                                |
| Manifest                   | ManifestPojo         | manifest.json                                       |
| PlaintextTally             | PlaintextTallyPojo   | tally.json                                          |
| Encrypt.EncryptionDevice   | EncryptionDevicePojo | encryption_devices/device_<device_id>.json          |
| GuardianRecord             | GuardianRecordPojo   | guardians/guardian_<guardian_id>.json               |
| PlaintextTally             | PlaintextTallyPojo   | spoiled_ballots/spoiled_ballot_<ballot_id>.json     |
| SubmittedBallot            | SubmittedBallotPojo  | submitted_ballots/submitted_ballot_<ballot_id>.json | 


### Json serialization

1. Spoiled plaintext ballots

   Note decrypted spoiled_ballots are stored as a PlaintextTally. Is that ok?

    * Python: Each spoiled ballot tally is stored inside the election plaintext tally.
      Java library does not currently have access to those, anticipate PR #305 will fix.
    * Java: Each spoiled ballot tally stored in a separate file in *spoiled_ballots/ballot_<ballot_id>*
      Placeholder selections are removed.
   
* ## Serialization from Java

### Election Record Serialization

| Class | ConvertToJson | JSON | proto writer | election_record.proto |
| --- | --- | --- | --- | --- |
| AvailableGuardian | writeAvailableGuardian | lagrange_coordinates/available_guardian_<guardian_id>.json | ElectionRecordToProto.convertAvailableGuardian | ElectionRecord.AvailableGuardian |
| ElectionConstants | writeConstants | constants.json | ElectionRecordToProto.convertConstants | ElectionRecord.Constants |
| CiphertextElectionContext | writeContext | context.json | ElectionRecordToProto.convertContext | ElectionRecord.ElectionContext |
| Encrypt.EncryptionDevice | writeDevice | devices/device_<device_id>.json | ElectionRecordToProto.convertDevice | ElectionRecord.EncryptionDevice |
| GuardianRecord | writeGuardianRecord | guardians/coefficient_validation_set_<guardian_id>.json | ElectionRecordToProto.convertGuardianRecord | ElectionRecord.GuardianRecord |
| Manifest | writeElection | manifest.json | ManifestToProto.translateToProto | manifest.proto |
| CiphertextTally | writeCiphertextTally | encrypted_tally.json | CiphertextTallyToProto.translateToProto | ciphertext_tally.proto |
| PlaintextTally | writePlaintextTally | tally.json | PlaintextTallyToProto.translateToProto | plaintext_tally.proto |

### Spoiled Ballots and Tallies Serialization

| Class | ConvertToJson | JSON | proto writer | protobuf |
| --- | --- | --- | --- | --- |
| PlaintextBallot | writePlaintextBallot | - | PlaintextBallotToProto.translateToProto | spoiledPlaintextBallot.protobuf |
| PlaintextTally | writePlaintextTally | spoiled_ballots/ballot_<ballot_id>.json | PlaintextTallyToProto.translateToProto | spoiledPlaintextTally.protobuf |
| PlaintextBallot | writeSubmittedBallot | encrypted_ballots/ballot_<ballot_id>.json | CiphertextBallotToProto.translateToProto | submittedBallot.protobuf |

### Private Serialization

#### Python: publish.publish_private_data()

| Class | what | JSON | 
| --- | --- | --- | 
| PlaintextBallot | input ballots | private/plaintext/plaintext_ballot_<ballot_id>.json | 
| CiphertextBallot | ? | private/encrypted/ballot_<ballot_id>.json | 
| GuardianRecord | ? | private/guardian_<guardian_id> | 

#### Java:

| Class | proto | writer | protobuf |
| --- | --- | --- | --- | 
| KeyCeremonyTrustee | DecryptingTrustee | Publisher.overwriteTrusteeProto | <output_dir>/<guardian_id>.protobuf |  