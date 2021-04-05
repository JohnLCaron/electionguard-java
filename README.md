# 🗳 ElectionGuard Java
_last changed: April 4, 2021_

This is a port of https://github.com/microsoft/electionguard-python/ to Java.

Also see:
 * [ElectionGuard official site](https://www.electionguard.vote/) 
 * [ElectionGuard Discussions](https://github.com/microsoft/electionguard/)
 * [ElectionGuard Specification](https://www.electionguard.vote/spec/0.95.0/1_Overview/)

## Election Verification

To verify an __Election Record__:

*   [VerifyElectionRecord](https://github.com/JohnLCaron/electionguard-java/blob/master/docs/VerifyElectionRecord.md)

The full [Verification Specification](https://www.electionguard.vote/spec/0.95.0/9_Verifier_construction/) is implemented.

## Workflow

### Workflow using Remote Guardians

The following command line programs run the entire workflow needed to run an election:

1.   [RemoteKeyCeremony](https://github.com/JohnLCaron/electionguard-java/blob/master/docs/RemoteKeyCeremony.md)

2.   [RemoteKeyCeremonyTrustee](https://github.com/JohnLCaron/electionguard-java/blob/master/docs/RemoteKeyCeremonyTrustee.md)

3.   [EncryptBallots](https://github.com/JohnLCaron/electionguard-java/blob/master/docs/EncryptBallots.md)

4.   [AccumulateTally](https://github.com/JohnLCaron/electionguard-java/blob/master/docs/AccumulateTally.md)

5.   [RemoteDecryption](https://github.com/JohnLCaron/electionguard-java/blob/master/docs/RemoteDecryption.md)

6.   [RemoteDecryptionTrustee](https://github.com/JohnLCaron/electionguard-java/blob/master/docs/RemoteDecryptionTrustee.md)

The entire workflow, consisting of the above steps plus verification can be run from a single command:

*   [RunRemoteWorkflow](https://github.com/JohnLCaron/electionguard-java/blob/master/docs/RunRemoteWorkflow.md)

### Workflow Simulation

The following command line programs simulate the pieces of the workflow needed to run an election:

1.   [PerformKeyCeremony](https://github.com/JohnLCaron/electionguard-java/blob/master/docs/PerformKeyCeremony.md)

2.   [EncryptBallots](https://github.com/JohnLCaron/electionguard-java/blob/master/docs/EncryptBallots.md)

2.   [AccumulateTally](https://github.com/JohnLCaron/electionguard-java/blob/master/docs/AccumulateTally.md)

4.   [DecryptBallots](https://github.com/JohnLCaron/electionguard-java/blob/master/docs/DecryptBallots.md)

The entire workflow, consisting of the above steps plus verification can be run from a single command:

*   [RunElectionWorkflow](https://github.com/JohnLCaron/electionguard-java/blob/master/docs/RunElectionWorkflow.md)

## Election Record

*   [Election record](https://github.com/JohnLCaron/electionguard-java/blob/master/docs/ElectionRecord.md)

### Election Record Visualization

A simple Swing-based visualization of the Election Record is available. This is a debugging tool for developers, 
not a polished tool for end-users (eg election officials). No documentation, no warentees, 
void where taxed or prohibited by law.

*   [Visualization](https://github.com/JohnLCaron/electionguard-java/blob/master/docs/Visualization.md)

## Input Validation

Election Manifest and Input Ballot validation can be done by classes in the **com.sunya.electionguard.input** package.
These must be checked before starting encryption. 

*   [InputValidation](https://github.com/JohnLCaron/electionguard-java/blob/master/docs/InputValidation.md)

## Library Use

ElectionGuard-Java is a full port of the ElectionGuard python library, and can be used in your own projects.
[Contributions](https://opensource.guide/how-to-contribute/) here and to the python library are welcome!

### Building from source

````
git clone https://github.com/JohnLCaron/electionguard-java.git
cd electionguard-java
./gradlew clean assemble fatJar myJavadocs
````

The javadocs will be in _build/docs/javadoc/index.html_.
The jars will be in _build/libs_. 
[Good luck, Jim](https://en.wikiquote.org/wiki/Mission:_Impossible).

### Protobuf definitions

These may be interesting to implement in other libraries:

*   [Protobuf definitions](https://github.com/JohnLCaron/electionguard-java/tree/master/src/main/proto/com/sunya/electionguard/proto)

## Notes

 * Assumes Java 11.
 * Most classes are immutable. Builders are used where needed to separate mutable and immutable uses. 
 * Uses Java's BigInteger for cryptographic computations.
 * Uses Gson for JSON serialization. 
 * Also serializes using protobuf.
 * Uses AutoValue for (some) immutable value classes.
 * Uses Flogger for logging.
 * Uses JUnit5 / jqwik library for property based testing.
 * Uses gradle for building.
 
## TODO

  * Not yet compatible with python library JSON serialization, due to different ways to serialize Optional.
    It incorporates a workaround which should work for now, however.
  * Review error logging.
  * Measure computational performance.
  * Investigate parallelization strategies.


