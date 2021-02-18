# 🗳 ElectionGuard Java 

##DecryptBallots

The _com.sunya.electionguard.workflow.DecryptBallots_ command line utility simulates
[ballot decryption](https://www.electionguard.vote/spec/0.95.0/7_Verifiable_decryption/)
on a single __encrypted ballot chain__ in an Election Record. 

The input Election Record comes from the output of an _EncryptBallots_ program.

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

The input directory containing the election record is required. In the workflow simulation, the output 
of the _EncryptBallots_ is used.

You must specifiy a _GuardianProvider_ (see below), or a location of the serialized guardian file, for example 
from the output of the _PerformKeyCeremony_. Note that only the Protobuf form is currently supported, and that
you specify the filename, not the directory name.

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

