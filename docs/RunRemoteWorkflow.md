# 🗳 ElectionGuard Java 

## RunRemoteWorkflow

The _com.sunya.electionguard.workflow.RunRemoteWorkflow_ command line program simulates an
entire ElectionGuard workflow, calling each of the workflow programs in turn.
This workflow generates Remote Guardians for testing purposes, and so cannot be used in a real election.

````
Usage: java -classpath electionguard-java-all.jar 
      com.sunya.electionguard.workflow.RunRemoteWorkflow [options]
  Options:
Usage: java -classpath electionguard-java-all.jar 
      com.sunya.electionguard.workflow.RunRemoteWorkflow [options]
  Options:
  * -in
      Directory containing input election manifest
  * -nguardians
      Number of quardians to create
      Default: 6
  * -quorum
      Number of quardians that make a quorum
      Default: 5
  * -trusteeDir
      Directory containing Guardian serializations
  * -encryptDir
      Directory containing ballot encryption
  * -nballots
      number of ballots to generate
      Default: 0
  * -out
      Directory where complete election record is published
    -h, --help
      Display this help and exit
````

The input directory containing the election manifest is required. It is in Json (default) or Protobuf format. 
If Json, it must contain the file _description.json_. If Protobuf, it must contain the file _electionRecord.proto_, from
which only the election description is read.

You must specify the number of guardians and quorum.

The directory where the Guardian serializations are stored must be specified. Remote Guardians are automatically 
generated using this workflow, and so it cannot be used in a real election.

The _encryptDir_ is the output directory of _RemoteKeyCeremony_ and the input directory of _EncryptBallots_.
You should make this separate from the input directory.

The _out_ directory is the output directory of _EncryptBallots_ and the input directory of _RemoteDecryption_
and _VerifyElectionRecord_.
You should make this separate from the input and the encryptDir directory.

Example:

````
java -classpath electionguard-java-all.jar com.sunya.electionguard.workflow.RunRemoteWorkflow \
    -in /data/electionguard/cook_county/metadata \
    -nguardians 6 -quorum 5 \
    -trusteeDir /data/electionguard/trustees \
    -encryptDir /data/electionguard/publishEncryption \
    -nballots 111 \
    -out /data/electionguard/publishDecryption
````

The program exits with a 0 on success, > 0 on failure.
Typical (successful) output looks like:

````
1=============================================================
>Running command java -classpath build/libs/electionguard-java-0.9.1-SNAPSHOT-all.jar com.sunya.electionguard.keyceremony.KeyCeremonyRemote \ 
    -in /data/electionguard/cook_county/metadata -out /data/electionguard/publishDecryption -nguardians 6 -quorum 5
>Running command java -classpath build/libs/electionguard-java-0.9.1-SNAPSHOT-all.jar com.sunya.electionguard.keyceremony.KeyCeremonyRemoteTrustee \
    -name remoteTrustee -out /data/electionguard/trustees
>Running command java -classpath build/libs/electionguard-java-0.9.1-SNAPSHOT-all.jar com.sunya.electionguard.keyceremony.KeyCeremonyRemoteTrustee \
    -name remoteTrustee -out /data/electionguard/trustees
>Running command java -classpath build/libs/electionguard-java-0.9.1-SNAPSHOT-all.jar com.sunya.electionguard.keyceremony.KeyCeremonyRemoteTrustee \
    -name remoteTrustee -out /data/electionguard/trustees
...
*** keyCeremonyRemote elapsed = 6832 ms

2=============================================================
>Running command java -classpath build/libs/electionguard-java-0.9.1-SNAPSHOT-all.jar com.sunya.electionguard.workflow.EncryptBallots \
    -in /data/electionguard/publishEncryption -nballots 111 -out /data/electionguard/publishEncryption -device deviceName
*** encryptBallots elapsed = 8 sec

3=============================================================
>Running command java -classpath build/libs/electionguard-java-0.9.1-SNAPSHOT-all.jar com.sunya.electionguard.decrypting.DecryptingRemote \ 
    -in /data/electionguard/publishEncryption -out /data/electionguard/publishDecryption
>Running command java -classpath build/libs/electionguard-java-0.9.1-SNAPSHOT-all.jar com.sunya.electionguard.decrypting.DecryptingRemoteTrustee \ 
    -guardianFile /data/electionguard/trustees/remoteTrustee-1.protobuf
>Running command java -classpath build/libs/electionguard-java-0.9.1-SNAPSHOT-all.jar com.sunya.electionguard.decrypting.DecryptingRemoteTrustee \ 
    -guardianFile /data/electionguard/trustees/remoteTrustee-2.protobuf
...
*** decryptBallots elapsed = 30 sec

4=============================================================
>Running command java -classpath build/libs/electionguard-java-0.9.1-SNAPSHOT-all.jar com.sunya.electionguard.verifier.VerifyElectionRecord \ 
    -in /data/electionguard/publishDecryption
*** verifyElectionRecord elapsed = 4 sec
````

## Security Issues

This program is used only for testing the workflow. See notes in the individual workflow programs for details.

