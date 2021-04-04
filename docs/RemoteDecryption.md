# 🗳 ElectionGuard Java 

## DecryptBallots

The _com.sunya.electionguard.workflow.DecryptBallots_ command line utility simulates
[ballot decryption](https://www.electionguard.vote/spec/0.95.0/7_Verifiable_decryption/)
on a single __encrypted ballot chain__ in an Election Record. 

The input Election Record comes from the output of an _EncryptBallots_ or _AccumulateTally_ program.

The output Election Record is complete, and can be input to an __ElectionGuard verifier__.

````
Usage: java -classpath electionguard-java-all.jar 
      com.sunya.electionguard.workflow.DecryptBallots [options]
  Options:
  * -in
      Directory containing input election record and ballot encryptions
    -guardiansLocation
      location of serialized guardian files
    -guardians
      GuardianProvider classname
  * -out
      Directory where augmented election record is published
    -h, --help
      Display this help and exit
````

The input directory containing the election record is required. It can be in Json or protobuf format. 
In the workflow simulation, the output of the _EncryptBallots_ is used. If it contains an encryptedTally,
that step is skipped, otherwise it will also accumulate the accepted encrypted ballots into an encrypted tally.

You must specify a _GuardianProvider_ (see below), or a location of the serialized guardian file, for example 
from the output of the _PerformKeyCeremony_. Note that only the Protobuf guardian file is currently supported, 
and that you specify the filename, not the directory name.

The output directory where the results of the decryption are written is required.
For safety in case of failure, the output directory should be different from the input directory.

Example:

````
java -classpath electionguard-java-all.jar com.sunya.electionguard.workflow.DecryptBallots \
    -in /data/electionguard/publishEncryption \
    -guardiansLocation /data/electionguard/keyceremony/private/guardians.proto \
    -out /data/electionguard/publishDecryption
````

The program exits with a 0 on success, > 0 on failure.
Typical (successful) output looks like:

````
 BallotDecryptor read from /home/snake/tmp/electionguard/publishWorkflowEncryptor
 Write to /data/electionguard/publishDecryption

Ready to decrypt

Accumulate tally
 done accumulating 3 ballots in the tally

Decrypt tally
 Guardian Present: guardian_1
 Guardian Present: guardian_2
 Guardian Present: guardian_3
 Guardian Present: guardian_4
 Guardian Present: guardian_5
Quorum of 5 reached
Done decrypting tally

Contest justice-supreme-court
   write-in-selection                       = 1
   john-adams-selection                     = 1
   benjamin-franklin-selection              = 2
   john-hancock-selection                   = 2
   Total votes                              = 6
Contest referendum-pineapple
   referendum-pineapple-negative-selection  = 0
   referendum-pineapple-affirmative-selection = 0
   Total votes                              = 0

*** DecryptBallots SUCCESS
````

### GuardianProvider

Users may supply their own java class that implements _com.sunya.electionguard.workflow.GuardianProvider_,
which supplies a quorum of Guardians needed to do the decryption. 
To do so, place a jar containing that class on the classpath. Example:

````
java -classpath electionguard-java-all.jar,my.jar com.sunya.electionguard.workflow.DecryptBallots \
    -in /data/electionguard/publishEncryption \
    -guardians my.package.MyGuardiansProvider
    -out /data/electionguard/publishDecryption
````

The class _com.sunya.electionguard.workflow.SecretGuardiansProvider_ is an implementation of GuardiansProvider
you can use as an example to write your own. It reads serialized Guardians, for example from the _PerformKeyCeremony_
stage of the workflow. 

## Security Issues

The input and output are published (non-secret) Election Records.

The issue with decryption is keeping the Guardian secret keys safe. So DecryptBallots must be run in
a secure way that is unspecified here. For that reason, this program is just used for testing the workflow.

## Remote Decrypting Guardians and RPC calls #87

This is part 2 of the Remote Guardian refactor (also see https://github.com/microsoft/electionguard/discussions/84), documenting my take on the minimal state and message exchange needed by the Guardian during decryption. 

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
In what follows, I assume it may be advantageous to ask for multiple decryptions in a single message, rather than sending separate messages, eg one for each selection. Its up to the DecryptingGuardian to return these in the order received, though one could add an identifier if needed. 

**Remote procedure calls** that are minimally needed. I'm using grpc/protobuf notation, but I think the ideas should be clear for any implementation:

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
* Note: its not clear if we need to be able to send a nonce. Im assuming the nonce is only needed for unit testing, which is best done separate from the RPC. I've left it out, so a random nonce is always used.

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
* Note: I've combined fetching the recoveryPublicKey into the same call as the compensated decryption, since they are always done together. (This could be placed into CompensatedDecryptionResponse, since its the same for all results in the response).
* Note: I assume a nonce is not needed in the RPC.