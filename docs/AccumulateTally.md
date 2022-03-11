# 🗳 ElectionGuard Java 

## AccumulateTally

The _com.sunya.electionguard.workflow.AccumulateTally_ command line utility simulates
[Ballot Aggregation](https://www.electionguard.vote/spec/0.95.0/6_Ballot_aggregation/)
on a single __encrypted ballot chain__ in an Election Record. 

The input Election Record comes from the output of an _EncryptBallots_ program.

````
Usage: java -classpath electionguard-java-all.jar 
      com.sunya.electionguard.workflow.AccumulateTally [options]
  Options:
  * -in
      Directory containing input election record and ballot encryptions
  * -out
      Directory where encrypted tally is published
    -h, --help
      Display this help and exit
````

The input directory containing the election record is required. It can be in Json or protobuf format. 
In the workflow simulation, the output of the _EncryptBallots_ is used.

The output directory where the encrypted tally is written is required.
For safety in case of failure, the output directory should be different from the input directory.

Example:

````
java -classpath electionguard-java-all.jar com.sunya.electionguard.workflow.DecryptBallots \
-in /home/snake/tmp/electionguard/publishBallotEncryptor \
-out /home/snake/tmp/electionguard/publishEncryptedTally
````

The program exits with a 0 on success, > 0 on failure.
Typical (successful) output looks like:

````
 AccumulateTally read from /home/snake/tmp/electionguard/publishBallotEncryptor
 Write to /home/snake/tmp/electionguard/publishEncryptedTally

Ready to decrypt

Accumulate tally
 done accumulating 7 ballots in the tally
*** AccumulateTally SUCCESS
````

## Security Issues

The input and output are published (non-secret) Election Records.
