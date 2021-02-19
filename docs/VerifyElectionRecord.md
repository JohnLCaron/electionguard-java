# 🗳 ElectionGuard Java 

## Verify Election Record

The ElectionGuard output (aka __Election Record__) can be validated using the
_com.sunya.electionguard.verifier.VerifyElectionRecord_ command line utility.

The full [Verification Specification](https://www.electionguard.vote/spec/0.95.0/9_Verifier_construction/) is implemented.

````
Usage: java -jar electionguard-java-all.jar [options]
  Options:
  * -in
      Directory containing input election record
    -h, --help
      Display this help and exit
````

The input directory name is required. It can be in Json or protobuf format. 

Example:

````
java -jar electionguard-java-0.9-all.jar -in /data/electionguard/workflow_output
````

The program exits with a 0 on success, 1 on failure.
Typical (successful) output looks like:

````
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
````


