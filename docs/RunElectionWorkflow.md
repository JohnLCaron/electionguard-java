# 🗳 ElectionGuard Java 

## RunElectionWorkflow

The _com.sunya.electionguard.workflow.RunElectionWorkflow_ command line utility simulates an
entire ElectionGuard workflow, calling each of the workflow programs in turn. It is used for testing,
it cannot be used in an actual election.

It generates random Guardians, and random ballots, encrypts them, then decrypts the election record and 
publishes the final record.

````
Usage: java -classpath electionguard-java-all.jar 
      com.sunya.electionguard.workflow.RunElectionWorkflow [options]
  Options:
  * -in
      Directory containing input election description
    --proto
      Input election record is in proto format
      Default: false
  * -nguardians
      Number of quardians to create
      Default: 6
  * -quorum
      Number of quardians that make a quorum
      Default: 5
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

The input directory containing the election description is required. It is in Json (default) or Protobuf format. 
If Json, it must contain the file _description.json_. If Protobuf, it must contain the file _electionRecord.proto_, from
which only the election description is read.

You must specify the number of guardians and quorum, and the Guardian's polynomial coefficients are generated at random.

The _encryptDir_ is the output directory of _PerformKeyCeremony_ and the input directory of _EncryptBallots_.
You should make this separate from the input directory.

The _out_ directory is the output directory of _EncryptBallots_ and the input directory of _DecryptBallots _
and _VerifyElectionRecord_.
You should make this separate from the input and the encryptDir directory.

Example:

````
java -classpath electionguard-java-all.jar com.sunya.electionguard.workflow.RunElectionWorkflow \
    -in /data/electionguard/cook_county/metadata \
    -nguardians 6 -quorum 5
    -encryptDir /data/electionguard/publishEncryption
    -nballots 111
    -out /data/electionguard/publishDecryption
````

The program exits with a 0 on success, > 0 on failure.
Typical (successful) output looks like:

````
> java -classpath build/libs/electionguard-java-0.9-all.jar com.sunya.electionguard.workflow.PerformKeyCeremony -in /home/snake/tmp/electionguard/inputKeyCeremony -out /home/snake/tmp/electionguard/publishWorkflowEncryptor -nguardians 6 -quorum 5
---StdOut---
KeyCeremony read election description from directory /home/snake/tmp/electionguard/inputKeyCeremony
  write Guardians to directory /home/snake/tmp/electionguard/publishWorkflowEncryptor
  generate random Guardian coefficients
  Create 6 Guardians, quorum = 5

Key Ceremony
 Confirm all guardians have shared their public keys
 Execute the key exchange between guardians
 Confirm all guardians have shared their partial key backups
 Confirm all guardians truthfully executed the ceremony
 Confirm all guardians have submitted a verification of the backups of all other guardians
 Confirm all guardians have verified the backups of all other guardians
 Create the Joint Election Key
*** KeyCeremony SUCCESS
---StdErr---
---Done status = 0
*** elapsed = 8608 ms

==============================================================
> java -classpath build/libs/electionguard-java-0.9-all.jar com.sunya.electionguard.workflow.EncryptBallots -in /home/snake/tmp/electionguard/publishWorkflowEncryptor --proto -nballots 111 -out /home/snake/tmp/electionguard/publishWorkflowEncryptor -device deviceName
---StdOut---
 BallotEncryptor read context from /home/snake/tmp/electionguard/publishWorkflowEncryptor
 Ballots from null
 Write to /home/snake/tmp/electionguard/publishWorkflowEncryptor

Ready to encrypt at location: 'deviceName'

Publish cast = 57 spoiled = 54 failed = 0 total = 111

*** EncryptBallots SUCCESS
---StdErr---
---Done status = 0
*** elapsed = 84 sec

==============================================================
> java -classpath build/libs/electionguard-java-0.9-all.jar com.sunya.electionguard.workflow.DecryptBallots -in /home/snake/tmp/electionguard/publishWorkflowEncryptor -guardiansLocation /home/snake/tmp/electionguard/publishWorkflowEncryptor/private/guardians.proto -out /home/snake/tmp/electionguard/publishWorkflowDecryptor
---StdOut---
 BallotDecryptor read from /home/snake/tmp/electionguard/publishWorkflowEncryptor
 Write to /home/snake/tmp/electionguard/publishWorkflowDecryptor

Ready to decrypt

Accumulate tally
 done accumulating 57 ballots in the tally

Decrypt tally
 Guardian Present: guardian_1
 Guardian Present: guardian_2
 Guardian Present: guardian_3
 Guardian Present: guardian_4
 Guardian Present: guardian_5
Quorum of 5 reached
Done decrypting tally

Contest justice-supreme-court
   write-in-selection                       = 10
   john-adams-selection                     = 27
   benjamin-franklin-selection              = 29
   john-hancock-selection                   = 28
   Total votes                              = 94
Contest referendum-pineapple
   referendum-pineapple-negative-selection  = 0
   referendum-pineapple-affirmative-selection = 0
   Total votes                              = 0

*** DecryptBallots SUCCESS
---StdErr---
---Done status = 0
*** elapsed = 174 sec

==============================================================
> java -classpath build/libs/electionguard-java-0.9-all.jar com.sunya.electionguard.verifier.VerifyElectionRecord -in /home/snake/tmp/electionguard/publishWorkflowDecryptor --proto
---StdOut---
 VerifyElectionRecord read from /home/snake/tmp/electionguard/publishWorkflowDecryptor isProto = true
============ Ballot Verification =========================
------------ [box 1] Parameter Validation ------------
 Baseline parameter check success
------------ [box 2] Guardian Public-Key Validation ------------
 All guardians: key generation verification success. 
------------ [box 3] Election Public-Key Validation ------------
 Public key validation success.
------------ [box 4] Selection Encryption Validation ------------
 All Selection Encryptions validate: success.
------------ [box 5] Contest Vote Limits Validation ------------
 Adherence to Vote Limits success.
------------ [box 6] Ballot Chaining Validation ------------
 Ballot Chaining success.

============ Decryption Verification =========================
------------ [box 7] Ballot Aggregation Validation ------------
  No ballots for talley key referendum-pineapple.referendum-pineapple-negative-selection
  No ballots for talley key referendum-pineapple.referendum-pineapple-affirmative-selection
 Ballot Aggregation Validation success.
------------ [box 8, 9] Correctness of Decryptions ------------
 Decryptions of cast ballots success. 
------------ [box 10] Correctness of Replacement Partial Decryptions ------------
 Replacement Partial Decryptions success. 
------------ [box 11] Correctness of Decryption of Tallies ------------
 Tally Decryption Validation success.
------------ [box 12] Correct Decryption of Spoiled Ballots ------------
 12.A Spoiled ballot decryption success. 
 12.B Spoiled PlaintextBallot Names Validation success.

===== ALL OK! ===== 
---StdErr---
---Done status = 0
*** elapsed = 69 sec

*** All took = 5 min
````

## Security Issues

This program is used only for testing the workflow. See notes in the individual workflow programs for details.

