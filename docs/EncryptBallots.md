# 🗳 ElectionGuard Java 

## EncryptBallots

The _com.sunya.electionguard.workflow.EncryptBallots_ command line utility simulates batch
[ballot encryption]("https://www.electionguard.vote/spec/0.95.0/5_Ballot_encryption/")
on a single _encryption device_, for example a voting machine. 

The input ballots come from a BallotProvider that you supply, or from randomly generated ballots
generated against the election description. 
An __Election Record__ containing the election description and context is also needed as input.

The ballots are randomly selected to be cast or spoiled.

The output is the encrypted ballots, stored in the output Election Record. These are cryptographically
chained together to prevent tampering with the output, and so these encrypted ballots are called the
__encrypted ballot chain__.

````
Usage: java -classpath electionguard-java-all.jar 
      com.sunya.electionguard.workflow.EncryptBallots [options]
  Options:
  * -in
      Directory containing input election record
    --proto
      Input election record is in proto format
      Default: false
  * -out
      Directory to write output election record
    -ballots
      BallotProvider classname
    -nballots
      number of ballots to generate
      Default: 0
  * -device
      Name of this device
    --save
      Save the original ballots for debugging
    -h, --help
      Display this help and exit
````

The input election record containing the description and the context is required. In the workflow simulation, the output 
of the _PerformKeyCeremony_ is used (but not the private Guardians).

Either a _BallotProvider_ is provided (see below), or _nballots_ fake ballots are generated against the election description.

The output directory where the Guardians are written is required. The file private/guardians.proto is written there. 
For safety in case of failure, the output directory should be different from the input directory.

The name of the device is required. It can be any unique name.

If the --save flag is specified, the input ballots are saved in Json serialized form in _private/plaintext/_.

Example:

````
java -classpath electionguard-java-all.jar com.sunya.electionguard.workflow.EncryptBallots \
    -in /data/electionguard/keyceremony --proto \
    -out /data/electionguard/publishEncryption \
    -nballots 11 \
    -device CountyCook-precinct079-device24358 \
    --save
````

The program exits with a 0 on success, > 0 on failure.
Typical (successful) output looks like:

````
 BallotEncryptor read context from /data/electionguard/keyceremony
 Ballots from com.sunya.electionguard.workflow.FakeBallotProvider
 Write to /data/electionguard/publishEncryption

 Ready to encrypt at location: 'CountyCook-precinct079-device24358'
 Save private ballot in /data/electionguard/publishEncryption/private/plaintext

 Publish cast = 7 spoiled = 4 failed = 0 total = 11

*** EncryptBallots SUCCESS
````

### BallotProvider

Users can supply their own java class that implements _com.sunya.electionguard.workflow.BallotProvider_,
which supplies the input plaintext ballots. 
To do so, place a jar containing that class on the classpath. Example:

````
java -classpath electionguard-java-all.jar,my.jar com.sunya.electionguard.workflow.EncryptBallots \
    -in /data/electionguard/keyceremony --proto \
    -out /data/electionguard/publishEncryption \
    -ballots my.package.MyBallotsProvider \
    -device CountyCook-precinct079-device24358 \
    --save
````

The class _com.sunya.electionguard.workflow.FakeBallotProvider_ is an implementation of BallotProvider
you can use as an example to write your own.

## Security Issues

This input uses only the published __election joint key__ and the output is part of the published Election Record,
so there are no security problems in the encryption. 

The input ballots must be kept private, which is the job of the BallotProvider. So the _save_ flag should only
be used for testing and debugging.

