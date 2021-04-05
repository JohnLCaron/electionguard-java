# 🗳 ElectionGuard Java 

## Perform Key Ceremony

The _com.sunya.electionguard.keyceremony.KeyCeremonyRemoteTrustee_ command line program creates a _Remote Guardian_ in a 
process separate from the KeyCeremonyRemote, in order to keep the Guardian's secret information safe.

````
Usage: java -classpath electionguard-java-all.jar 
      com.sunya.electionguard.keyceremony.KeyCeremonyRemoteTrustee [options]
  Options:
  * -name
      Guardian name
    -port
      This KeyCeremonyRemoteTrustee port
      Default: 0
    -serverPort
      The KeyCeremonyRemote server port
      Default: 17111
  * -out
      Directory where the Guardian state is written
    -h, --help
      Display this help and exit
````

The Guardian name is required and must be unique among all the Guardians used in this election.

The port which this KeyCeremonyRemoteTrustee will run on may be assigned, otherwise a random one will be used.

The port which the KeyCeremonyRemote will run on may be assigned, otherwise the default port will be used.

The output directory where the Guardian state needed for decryption will be written. This contains secret information
and must be kept secure.

Example:

````
java -classpath electionguard-java-all.jar com.sunya.electionguard.keyceremony.KeyCeremonyRemoteTrustee \
    -name JohnLCaron \
    -out /local/secret/place
````

The program exits with a 0 on success, > 0 on failure.
Typical (successful) output looks like:

````
Command java -classpath build/libs/electionguard-java-0.9.1-SNAPSHOT-all.jar com.sunya.electionguard.keyceremony.KeyCeremonyRemoteTrustee \
    -name JohnLCaron -out /local/secret/place
---StdOut---
*** KeyCeremonyRemote localhost:17111 with args JohnLCaron localhost:22458
    response JohnLCaron-3 3 2 
---- KeyCeremonyRemoteService started, listening on 22458 ----
---StdErr---
Apr 05, 2021 8:38:39 AM com.sunya.electionguard.keyceremony.KeyCeremonyRemoteTrustee receivePublicKeys
INFO: KeyCeremonyRemoteTrustee receivePublicKeys remoteTrustee-1
Apr 05, 2021 8:38:39 AM com.sunya.electionguard.keyceremony.KeyCeremonyRemoteTrustee receivePublicKeys
INFO: KeyCeremonyRemoteTrustee receivePublicKeys remoteTrustee-2
Apr 05, 2021 8:38:39 AM com.sunya.electionguard.keyceremony.KeyCeremonyRemoteTrustee sendPublicKeys
INFO: KeyCeremonyRemoteTrustee sendPublicKeys JohnLCaron-3
Apr 05, 2021 8:38:39 AM com.sunya.electionguard.keyceremony.KeyCeremonyRemoteTrustee verifyPartialKeyBackup
INFO: KeyCeremonyRemoteTrustee verifyPartialKeyBackup remoteTrustee-1
Apr 05, 2021 8:38:39 AM com.sunya.electionguard.keyceremony.KeyCeremonyRemoteTrustee verifyPartialKeyBackup
INFO: KeyCeremonyRemoteTrustee verifyPartialKeyBackup remoteTrustee-2
Apr 05, 2021 8:38:39 AM com.sunya.electionguard.keyceremony.KeyCeremonyRemoteTrustee sendPartialKeyBackup
INFO: KeyCeremonyRemoteTrustee sendPartialKeyBackup remoteTrustee-1
Apr 05, 2021 8:38:39 AM com.sunya.electionguard.keyceremony.KeyCeremonyRemoteTrustee sendPartialKeyBackup
INFO: KeyCeremonyRemoteTrustee sendPartialKeyBackup remoteTrustee-2
Apr 05, 2021 8:38:39 AM com.sunya.electionguard.keyceremony.KeyCeremonyRemoteTrustee sendJointPublicKey
INFO: KeyCeremonyRemoteTrustee sendJointPublicKey JohnLCaron-3
Apr 05, 2021 8:38:39 AM com.sunya.electionguard.keyceremony.KeyCeremonyRemoteTrustee saveState
INFO: KeyCeremonyRemoteTrustee saveState JohnLCaron-3
Apr 05, 2021 8:38:39 AM com.sunya.electionguard.keyceremony.KeyCeremonyRemoteTrustee finish
INFO: KeyCeremonyRemoteTrustee finish ok = true
*** shutting down gRPC server since JVM is shutting down
*** server shut down
---Done status = true
````

## Security Issues

The output contains the Guardians' secret keys in plaintext, and so this process must be run in
a secure way that is unspecified here.

