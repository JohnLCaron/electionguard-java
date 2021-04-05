# 🗳 ElectionGuard Java 

## DecryptBallots

The _com.sunya.electionguard.decrypting.DecryptingRemoteTrustee_ command line program instantiates the
same _Remote Guardian_ that was created in the Key Ceremony.

The input Election Record comes from the output of an _EncryptBallots_ or _AccumulateTally_ program.

The output Election Record is complete, and can be input to an __ElectionGuard verifier__.

````
Usage: java -classpath electionguard-java-all.jar 
      com.sunya.electionguard.decrypting.DecryptingRemoteTrustee [options]
  Options:
  * -guardianFile
      location of serialized guardian file
    -port
      This DecryptingRemoteTrustee port
      Default: 0
    -serverPort
      The DecryptingRemote server port
      Default: 17711
    -h, --help
      Display this help and exit
````

The guardianFile containing the serialized Guardian record generated durring the Key Ceremony is required. 

The port which this DecryptingRemoteTrustee will run on may be assigned, otherwise a random one will be used.

The port which the DecryptingRemote will run on may be assigned, otherwise the default port will be used.

Example:

````
java -classpath electionguard-java-all.jar com.sunya.electionguard.decrypting.DecryptingRemoteTrustee \
    -guardianFile /local/secret/place/JohnLCaron-3.protobuf
````

The program exits with a 0 on success, > 0 on failure.
Typical (successful) output looks like:

````
Command java -classpath build/libs/electionguard-java-0.9.1-SNAPSHOT-all.jar com.sunya.electionguard.decrypting.DecryptingRemoteTrustee \ 
    -guardianFile /local/secret/place/JohnLCaron-3.protobuf
---StdOut---
*** DecryptingRemoteTrustee from file /local/secret/place/JohnLCaron-3.protobuf url localhost:27001 server localhost:17711
    registered with DecryptingRemote 
---- DecryptingRemoteTrustee started, listening on 27001 ----
---StdErr---
Apr 05, 2021 8:55:07 AM com.sunya.electionguard.decrypting.DecryptingRemoteTrustee partialDecrypt
INFO: DecryptingRemoteTrustee partialDecrypt remoteTrustee-1
...
Apr 05, 2021 8:55:12 AM com.sunya.electionguard.decrypting.DecryptingRemoteTrustee compensatedDecrypt
INFO: DecryptingRemoteTrustee compensatedDecrypt remoteTrustee-3
...
---Done status = true
````

## Security Issues

This process must be run in a secure way that is unspecified here.

