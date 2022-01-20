# 🗳 ElectionGuard Java

### JSON Election Record Serialization (1.4.0) <publish_dir>/election_record/

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

### Private Serialization <publish_dir>/election_private_data/


| Class                      | ConvertToJson             | JSON                                         |
|----------------------------|---------------------------|----------------------------------------------|
| GuardianRecordPrivate      | GuardianRecordPrivatePojo | private_guardians/private_guardian_<id>.json |
