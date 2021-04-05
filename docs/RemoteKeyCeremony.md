# 🗳 ElectionGuard Java 

## Perform Key Ceremony

The _com.sunya.electionguard.keyceremony.KeyCeremonyRemote_ command line program uses remote Guardians to perform a 
[key ceremony](https://www.electionguard.vote/spec/0.95.0/4_Key_generation/). 

The output are the first pieces of the Election Record: the election description, election context, and Guardians' coefficient validations.

````
Usage: java -classpath electionguard-java-all.jar 
      com.sunya.electionguard.keyceremony.KeyCeremonyRemote [options]
  Options:
  * -in
      Directory containing election manifest
    -out
      Directory where election record is written
  * -nguardians
      Number of Guardians that will be used
      Default: 0
  * -quorum
      Number of Guardians that make a quorum
      Default: 0
    -port
      The port to run the server on
      Default: 17111
    -h, --help
      Display this help and exit
````

The input directory containing the election manifest is required. It can be in Json or Protobuf format. 
If Json, it must contain the file _description.json_. If Protobuf, it must contain the file _electionRecord.proto_, from
which only the election description is read.

The output directory where the Election Record is written is required and must be writeable. When the key ceremony is successful,
the first parts of the record are written: the election manifest, election context, and Guardians' coefficient validations.

The number of guardians and quorum must be provided. The KeyCeremonyRemote is started and waits until nguardians
register with it, and then begins the key ceremony.

The server port may be provided, otherwise it defaults to 17111.

Example:

````
java -classpath electionguard-java-all.jar com.sunya.electionguard.keyceremony.KeyCeremonyRemote \
    -in /data/electionguard/cook_county/metadata \
    -out /data/electionguard/keyceremony \
    -nguardians 6 -quorum 5
````

The program exits with a 0 on success, > 0 on failure.
Typical (successful) output looks like:

````
Command java -classpath build/libs/electionguard-java-0.9.1-SNAPSHOT-all.jar com.sunya.electionguard.keyceremony.KeyCeremonyRemote 
   -in /data/electionguard/cook_county/metadata -out /data/electionguard/keyceremony -nguardians 3 -quorum 2
---StdOut---
---- KeyCeremonyRemoteService started, listening on 17111 ----
KeyCeremonyRemote registerTrustee remoteTrustee url localhost:23659 
KeyCeremonyRemote registerTrustee remoteTrustee url localhost:22016 
KeyCeremonyRemote registerTrustee remoteTrustee url localhost:17770 
  KeyCeremonyRemoteMediator 3 Guardians, quorum = 2
  Key Ceremony Round1: exchange public keys
  Key Ceremony Round2: exchange partial key backups
  Key Ceremony Round3: challenge and validate partial key backup responses 
  Key Ceremony Round4: compute and check JointKey agreement
  Key Ceremony makeCoefficientValidationSets
  Key Ceremony complete

Key Ceremony Trustees save state was success = true
Publish ElectionRecord to /data/electionguard/keyceremony
Key Ceremony Trustees finish was success = true
Key Ceremony Trustees shutdown was success = true
---StdErr---
Apr 05, 2021 8:12:08 AM com.sunya.electionguard.keyceremony.KeyCeremonyRemote$KeyCeremonyRemoteService registerTrustee
INFO: KeyCeremonyRemote registerTrustee registerTrustee remoteTrustee-3
Apr 05, 2021 8:12:08 AM com.sunya.electionguard.keyceremony.KeyCeremonyRemote$KeyCeremonyRemoteService registerTrustee
INFO: KeyCeremonyRemote registerTrustee registerTrustee remoteTrustee-2
Apr 05, 2021 8:12:08 AM com.sunya.electionguard.keyceremony.KeyCeremonyRemote$KeyCeremonyRemoteService registerTrustee
INFO: KeyCeremonyRemote registerTrustee registerTrustee remoteTrustee-1
*** shutting down gRPC server since JVM is shutting down
*** server shut down
---Done status = true
````

## Security Issues

No secret information is transmitted to KeyCeremonyRemote.

## Key Ceremony information exchange and messaging rounds. See electionguard Issue #84

**Round 1**. Each guardian shares their public keys with all the other guardians. Each guardian validates the other guardian's commitments against their proof.

````
  class PublicKeySet {
    String ownerId(); // guardian object_id
    int guardianXCoordinate(); // guardian x coordinate (aka sequence_order)
    Rsa.PublicKey auxiliaryPublicKey();  // Assume RSA?
    List<SchnorrProof> coefficientProofs();  // The election polynomial coefficients commitments and proofs
 } 
```` 
 Note: Having a pluggable auxilary encryption function adds complexity, so is not supported.
 Note: coefficientProofs contain the coefficient comittments, and the election public key and proof 	   
 
 **Round 2**. Each guardian shares partial key backups with each of the other guardians, each guardian verifies their own backups.
```` 
  class ElectionPartialKeyBackup {
    String generatingGuardianId(); 		// Id of the guardian that generated this backup.
    String designatedGuardianId(); 		// Id of the guardian to receive this backup
    int designatedGuardianXCoordinate(); // The x coordinate (aka sequence order) of the designated guardian

    Auxiliary.ByteString encryptedCoordinate(); // The encrypted coordinate of generatingGuardianId polynomial's value at designatedGuardianXCoordinate.
  } 
````  
 Note: coefficient comittments were already sent in the PublicKeySet.
 Note: The response to sending the partial key backup message can be a validation failure, so an extra round is not needed.
   

**Round 3**. For any partial backup verification failures, each challenged guardian broadcasts its response to the challenge.
         The mediator verifies the response. In point to point, each guardian would verify.
```` 
  class ElectionPartialKeyChallengeResponse {
    String generatingGuardianId();
    String designatedGuardianId();
    int designatedGuardianXCoordinate();
    
    Group.ElementModQ coordinate(); // The unencrypted coordinate of generatingGuardianId polynomial's value at designatedGuardianXCoordinate.
  }
````  
 Note: coefficient comittments were already sent in the PublicKeySet. Besides following the rule of minimizing copies of state, this
   also removes an attack point by not letting the challenged guardian modify its commitments.
   
   
**Round 4**. All guardians compute and send their joint election public key. If they agree, then the key ceremony is a success.

 Note: The guardians do not compute the extended base hash, so they dont need access to the election manifest at this stage.
