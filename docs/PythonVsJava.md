# 🗳 ElectionGuard Python vs. Java
_last changed: May 18, 2021_

Generally the classes in **com.sunya.electionguard** are direct ports from the Python library. There are some 
modifications and renaming, but the intention is to keep it easy to port changes from Python as they occur.

The remote workflow uses gRPC to keep secret Guardian information from being trivially discoverable from the other 
Guardians. These are extensively refactored; the intention is that they must be interoperable with the Python
election record output.

Extensions to the Python library are kept in subdirectories of **com.sunya.electionguard**:
* **decrypting**: Decrypting with remote Guardians aka Trustees.
* **input**: Validation of input manifest, ballots and tallies. 
* **keyceremony**: Run the Key Ceremony with remote Guardians aka Trustees.
* **proto**: Protobuf serialization helper classes.
* **publish**: JSON helper serialization classes, and top level publisher/consumer for both protobuf and JSON serialization.
* **verifier**: The electionguard-java verifier.
* **viz**: Simple visualization UI to examine the election record.
* **workflow**: Command line utilities to run the entire electionguard remote workflow, including in production settings.
The non-remote workflow can also be run for testing purposes.

## Classes

* Python: Python class name
* standard Java: Java class equivalent to Python class
* remote Java: Java class refactored for remote Guardians

### Guardian

| Python | standard Java | remote Java |
| --- | --- | --- |
| Guardian | Guardian | KeyCeremonyTrustee, DecryptingTrustee |
| GuardianRecord | GuardianRecord | same |
| GuardianPrivateRecord | GuardianPrivateRecord | DecryptingTrustee |

### KeyCeremony

| Python | standard Java | remote Java |
| --- | --- | --- |
| key_ceremony | KeyCeremony | KeyCeremony2 |
| KeyCeremonyHelper | KeyCeremonyHelper | - |
| KeyCeremonyMediator | KeyCeremonyMediator | KeyCeremonyRemoteMediator |
| PublicKeySet | KeyCeremony.PublicKeySet | KeyCeremonyTrustee |

### Decryption

| Python | standard Java | remote Java |
| --- | --- | --- |
| decryption | Decryptions | RemoteDecryptions |
| DecryptionHelper | DecryptionHelper | - |
| DecryptionMediator | DecryptionMediator | DecryptingMediator |
| decrypt_with_shares | DecryptWithShares | same |
| DecryptionShare | DecryptionShare | same |
| ElectionPublicKey | KeyCeremony.ElectionPublicKey | DecryptingTrustee |

### Workflow

| Python | standard Java | remote Java           |
| --- | --- |-----------------------|
| TestEndToEndElection | RunElectionWorkflow | RunRemoteWorkflow     |
| KeyCeremonyMediator | PerformKeyCeremony | KeyCeremonyRemote     |
| EncryptionMediator | EncryptBallots | same                  |
| - | AccumulateTally | AccumulateTally       |
| DecryptionMediator | DecryptBallots | RunDecryptingMediator |
| - | VerifyElectionRecord | same                  |

## Serialization from Java

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

