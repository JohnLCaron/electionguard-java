# 🗳 ElectionGuard Java 

## DecryptBallots

The _com.sunya.electionguard.decrypting.DecryptingRemote_ command line program uses remote Guardians to perform a 
[decryption](https://www.electionguard.vote/spec/0.95.0/7_Verifiable_decryption/)
on an Election Record. 

The input Election Record comes from the output of an _EncryptBallots_ or _AccumulateTally_ program.

The results are written to the output Election Record, which can be input to an __ElectionGuard verifier__.

````
Usage: java -classpath electionguard-java-all.jar 
      com.sunya.electionguard.decrypting.DecryptingRemote [options]
  Options:
  * -in
      Directory containing input election record and encrypted ballots and 
      tally 
  * -out
      Directory where augmented election record is published
    -port
      The port to run the server on
      Default: 17711
    -h, --help
      Display this help and exit
````

The input directory containing the election record is required. It can be in Json or protobuf format. 
If it contains an encryptedTally, that step is skipped, otherwise it will also accumulate the cast 
encrypted ballots into an encrypted tally.

The output directory where the results of the decryption are written is required.
For safety in case of failure, the output directory should be different from the input directory.

The port to run the DecryptingRemote server may be given, otherwise the default is used.

Example:

````
java -classpath electionguard-java-all.jar com.sunya.electionguard.decrypting.DecryptingRemote \
    -in /data/electionguard/publishEncryption \
    -out /data/electionguard/publishDecryption
````

The program exits with a 0 on success, > 0 on failure.
Typical (successful) output looks like:

````
Command java -classpath build/libs/electionguard-java-0.9.1-SNAPSHOT-all.jar com.sunya.electionguard.decrypting.DecryptingRemote \
  -in /data/electionguard/publishEncryption -out /data/electionguard/publishDecryption
---StdOut---
DecryptingRemote startup at 2021-04-05T08:54:50.495486
DecryptingRemote quorum = 2 nguardians = 3
---- DecryptingRemote started, listening on 17711 ----
DecryptingRemote registerTrustee: remoteTrustee-1 url localhost:27001 
DecryptingRemote registerTrustee: remoteTrustee-2 url localhost:23256 

Accumulate tally
 done accumulating 7 ballots in the tally

Decrypt tally
 Guardian Present: remoteTrustee-1
 Guardian Present: remoteTrustee-2
Quorum of 2 reached
SpoiledBallotAndTally = 4
Done decrypting tally

DecryptingRemoteTrusteeProxy shutdown was success = true
*** DecryptBallots SUCCESS
---StdErr---
Apr 05, 2021 8:54:51 AM com.sunya.electionguard.decrypting.DecryptingRemote$DecryptingRegistrationService registerTrustee
INFO: DecryptingRemote registerTrustee remoteTrustee-2
Apr 05, 2021 8:54:51 AM com.sunya.electionguard.decrypting.DecryptingRemote$DecryptingRegistrationService registerTrustee
INFO: DecryptingRemote registerTrustee remoteTrustee-1
*** shutting down gRPC server since JVM is shutting down
*** server shut down
---Done status = true
````

## Security Issues

No secret information is transmitted to DecryptingRemote.

## Remote Decrypting Guardians and RPC calls, see electionguard Issue#87

**DecryptingGuardian state** that needs to be persisted between the key ceremony and the decryption:
````
class DecryptingGuardian {
  String id;
  int guardianXCoordinate; // aka sequence_order
  Rsa.PrivateKey rsa_private_key;

  /** The election (ElGamal) secret key */
  public final ElGamal.KeyPair election_keypair;

  /** Other guardians' partial key backups of this guardian's keys, keyed by other guardian id. */
  public final Map<String, KeyCeremony2.ElectionPartialKeyBackup> otherGuardianPartialKeyBackups;

  /** All guardians' public coefficient commitments, keyed by guardian id. */
  public final ImmutableMap<String, ImmutableList<Group.ElementModP>> guardianCommittments;
}
````

**Remote procedure calls** that are minimally needed.

````
service DecryptingTrusteeService {
  rpc partialDecrypt (PartialDecryptionRequest) returns (PartialDecryptionResponse) {}
  rpc compensatedDecrypt (CompensatedDecryptionRequest) returns (CompensatedDecryptionResponse) {}
}
````

````
message PartialDecryptionRequest {
  ElementModQ extended_base_hash = 1; // The election extended_base_hash.
  repeated ElGamalCiphertext text = 2; // The text(s) to decrypt.
}

message PartialDecryptionResponse {
  RemoteError error = 1; // non empty on error
  repeated PartialDecryptionResult results = 2;
}

message PartialDecryptionResult {
  ElementModP decryption = 1;
  ChaumPedersenProof proof = 2;
}
````
* Note: The DecryptingGuardian must return results in the order of the texts received. 

````
message CompensatedDecryptionRequest {
  ElementModQ extended_base_hash = 1; // The election extended_base_hash.
  string missing_guardian_id = 2; // The id of the guardian that's missing.
  repeated ElGamalCiphertext text = 3; // The text(s) to decrypt.
}

message CompensatedDecryptionResponse {
  RemoteError error = 1; // non empty on error
  repeated CompensatedDecryptionResult results = 2;
}

message CompensatedDecryptionResult {
  ElementModP decryption = 1;
  ChaumPedersenProof proof = 2;
  ElementModP recoveryPublicKey = 3;
}
````