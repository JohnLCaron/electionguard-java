# 🗳 ElectionGuard Java 

## Perform Key Ceremony

The _com.sunya.electionguard.workflow.PerformKeyCeremony_ command line utility simulates
the creation of the election Guardians by performing a 
[key ceremony](https://www.electionguard.vote/spec/0.95.0/4_Key_generation/). 

The output is a set of serialized Guardians that are used in the _DecryptBallots_ workflow, along with the first
pieces of the Election Record: the election description, election context, and Guardians' coefficient validations.

````
Usage: java -classpath electionguard-java-all.jar 
      com.sunya.electionguard.workflow.PerformKeyCeremony [options]
  Options:
  * -in
      Directory containing input election description
  * -out
      Directory where Guardians and election context are written
    -coefficients
      CoefficientsProvider classname
    -nguardians
      Number of quardians to create (required if no coefficients)
      Default: 0
    -quorum
      Number of quardians that make a quorum (required if no coefficients)
      Default: 0
    -h, --help
      Display this help and exit
````

The input directory containing the election description is required. It can be in Json or Protobuf format. 
If Json, it must contain the file _description.json_. If Protobuf, it must contain the file _electionRecord.proto_, from
which only the election description is read.

Either a _CoefficientsProvider_ is provided (see below), or the number of guardians and quorum is provided. 
In the latter case, the Guardians' polynomial coefficients are generated at random.

The output directory where the Election Record is written is required. The first pieces of the record are
written: the election description, election context, and Guardians' coefficient validations.
The Guardians are written to that directory into the file _private/guardians.proto_.

Example:

````
java -classpath electionguard-java-all.jar com.sunya.electionguard.workflow.PerformKeyCeremony \
    -in /data/electionguard/cook_county/metadata \
    -out /data/electionguard/keyceremony \
    -nguardians 6 -quorum 5
````

The program exits with a 0 on success, > 0 on failure.
Typical (successful) output looks like:

````
KeyCeremony read election description from directory /data/electionguard/cook_county/metadata
  write Guardians to directory /data/electionguard/keyceremony
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
````

### CoefficentsProvider

Users can supply their own java class that implements _com.sunya.electionguard.workflow.CoefficentsProvider_,
which supplies the Guardians' polynomial coefficients. 
To do so, place a jar containing that class on the classpath. Example:

````
java -classpath electionguard-java-all.jar,my.jar com.sunya.electionguard.workflow.PerformKeyCeremony \
    -in /data/electionguard/cook_county/metadata \
    -out /data/electionguard/keyceremony \
    -coefficients my.package.MyCoefficentsProvider
````

The class _com.sunya.electionguard.workflow.RandomCoefficientsProvider_ is an implementation of CoefficientsProvider
you can use as an example to write your own.

## Security Issues

The output contains the Guardians' secret keys in plaintext, and so the entire key ceremony must be run in
a secure way that is unspecified here. For that reason, this program is just used for testing the workflow.

