# 🗳 ElectionGuard Java 
_last changed: April 4, 2021_

_This document may slightly lag both the electionguard-python and electionguard-java library._

## The Election Record

The **_Election Record_** is the publicly available record of the ElectionGuard process. 
The reference electionguard-python library produces a JSON serialization of the election record. 
The electionguard-java library supports JSON serialization and also protobuf serialization. 
In this document simple Java value classes are used to document the election record objects. 
See the implementing libraries for the language specific API. 

Everything here references the [Election Guard specification](https://www.electionguard.vote/spec/0.95.0/).

### The Election Manifest

Before the election starts, the election officials create an Election Manifest for it, describing all
the candidates, contests, and possible ballots. The manifest model is derived from the 
[Civics Common Standard Data Specification](https://developers.google.com/elections-data/reference/election).

````
/** Helper class for conversion of Manifest description to/from Json, using python's object model and naming. */
class ManifestPojo {
  InternationalizedText name;
  String election_scope_id;
  String type;
  String start_date; // ISO-8601 Local or UTC? Assume local has zone offset
  String end_date; // ISO-8601 Local or UTC? Assume local has zone offset
  ContactInformation contact_information;

  List<GeopoliticalUnit> geopolitical_units;
  List<Party> parties;
  List<Candidate> candidates;
  List<ContestDescription> contests;
  List<BallotStyle> ballot_styles;

  class AnnotatedString {
    String annotation;
    String value;
  }

  class BallotStyle extends ElectionObjectBase {
    List<String> geopolitical_unit_ids;
    List<String> party_ids;
    String image_uri;
  }

  class Candidate extends ElectionObjectBase {
    InternationalizedText name;
    String party_id;
    String image_uri;
    Boolean is_write_in;
  }

  class ContestDescription extends ElectionObjectBase {
    String electoral_district_id;
    int sequence_order;
    String vote_variation;
    int number_elected;
    int votes_allowed;
    String name;
    List<SelectionDescription> ballot_selections;
    InternationalizedText ballot_title;
    InternationalizedText ballot_subtitle;
  }

  class ContactInformation {
    List<String> address_line;
    List<AnnotatedString> email;
    List<AnnotatedString> phone;
    String name;
  }

  class ElectionObjectBase {
    String object_id;
  }

  class GeopoliticalUnit extends ElectionObjectBase {
    String name;
    String type;
    ContactInformation contact_information;
  }

  class InternationalizedText {
    List<Language> text;
  }

  class Language {
    String value;
    String language;
  }

  class Party extends ElectionObjectBase {
    InternationalizedText ballot_name;
    String abbreviation;
    String color;
    String logo_uri;
  }

  class SelectionDescription extends ElectionObjectBase {
    String candidate_id;
    int sequence_order;
  }
````

### Key Ceremony

The Key Ceremony takes the manifest as input, as well as the total number of Guardians and the mininum number
of Guardians required for decryption. It adds the **_ElectionConstants_**, **_CiphertextElectionContext_**,
and a **_CoefficientValidationSet_** for each Guardian, to the Election Record.

During the Key Ceremony, the Guardian state needed for decryption is also created, but that is private state 
and not part of the election record.

````
  class ElectionConstants {
    BigInteger large_prime; /** large prime or p. */
    BigInteger small_prime; /** small prime or q. */
    BigInteger cofactor; /** cofactor or r. */
    BigInteger generator; /** generator or g. */
  }
````

````
  class CiphertextElectionContext {
    int number_of_guardians; /** The number of guardians necessary to generate the key. */
    int quorum; /** The quorum of guardians necessary to decrypt an election.  Must be less than number_of_guardians. */
    Group.ElementModP elgamal_public_key; /** The joint key (K) in the ElectionGuard Spec. */
    Group.ElementModQ description_hash; /** The Manifest crypto_hash. */
    Group.ElementModQ crypto_base_hash; /** The base hash code (𝑄) in the ElectionGuard Spec. */
    Group.ElementModQ crypto_extended_base_hash; /** The extended base hash code (𝑄') in the ElectionGuard Spec. */
  }
````

````
  class CoefficientValidationSet {
    String owner_id; /** Guardian.object_id. */
    List<ElementModP> coefficient_commitments; /** The Kij of the specification. */
    List<SchnorrProof> coefficient_proofs; /** The proof of knowledge for the coefficient commitments. */
  }

  class SchnorrProof {
    ElementModP public_key; /** K in the spec. The commitment {"keys K_ij") generated from the election polynomial coefficients. */
    ElementModP commitment; /** h in the spec */
    ElementModQ challenge; /** c in the spec */
    ElementModQ response; /** u in the spec */
  }
````

### Plaintext Ballots

A voter's ballot is converted to a **_PlaintextBallot_** for input to Election Guard:

````
class PlaintextBallotPojo {
  String object_id;
  String style_id;
  List<PlaintextBallotContest> contests;

  class PlaintextBallotContest {
    String object_id;
    List<PlaintextBallotSelection> ballot_selections;
  }

  class PlaintextBallotSelection {
    String object_id;
    int vote;
    boolean is_placeholder_selection;
    String extra_data;
  }
````

### Encryption 

During encryption, one or more _PlaintextBallots_ are given to the encryption device, which are
encrypted into **_CiphertextBallots_**. The ballot is then either cast or spoiled, and the CiphertextBallot
is added to the election record as a **_Submitted Ballot_**. The plaintext ballots are not part of the election record, 
but presumably kept safe and secret by the election authorities, for recounts and audits.

````
/** Conversion between SubmittedBallot and Json, using python's object model and naming. */
class SubmittedBallotPojo {
  String object_id;
  String style_id;
  ElementModQ manifest_hash;
  ElementModQ code;
  ElementModQ previous_code;
  List<CiphertextBallotContestPojo> contests;
  long timestamp;
  ElementModQ crypto_hash;
  ElementModQ nonce; // LOOK ignored until Optional serialization gets fixed
  BallotBox.State state;

  class CiphertextBallotContestPojo {
    String object_id;
    ElementModQ description_hash;
    List<CiphertextBallotSelectionPojo> ballot_selections;
    ElementModQ crypto_hash;
    ElGamalCiphertextPojo ciphertext_accumulation;
    ElementModQ nonce;
    ConstantChaumPedersenProofPojo proof;
  }

  class CiphertextBallotSelectionPojo {
    String object_id;
    ElementModQ description_hash;
    ElGamalCiphertextPojo ciphertext;
    ElementModQ crypto_hash;
    boolean is_placeholder_selection;
    ElementModQ nonce;
    DisjunctiveChaumPedersenProofPojo proof;
    ElGamalCiphertextPojo extended_data;
  }

  class ConstantChaumPedersenProofPojo {
    ElementModP pad;
    ElementModP data;
    ElementModQ challenge;
    ElementModQ response;
    int constant;
  }

  class DisjunctiveChaumPedersenProofPojo {
    ElementModP proof_zero_pad;
    ElementModP proof_zero_data;
    ElementModP proof_one_pad;
    ElementModP proof_one_data;
    ElementModQ proof_zero_challenge;
    ElementModQ proof_one_challenge;
    ElementModQ challenge;
    ElementModQ proof_zero_response;
    ElementModQ proof_one_response;
  }

  class ElGamalCiphertextPojo {
    ElementModP pad;
    ElementModP data;
  }
````

The encrypting device information is also added to the election record:

````
  class EncryptionDevice {
    long uuid; /** Unique identifier for device. */
    String session_id; /** Used to identify session and protect the timestamp. */
    int launch_code; /** Election initialization value. */
    String location; /** Arbitrary string to designate the location of the device. */
}
````

### Tally Accumulation 

During Tally Accumulation, all of the Encrypted Ballots are added together in a way that does not
require the ballots to be decrypted first. The result is an **_EncryptedTally_** that is added to the
election record:

````
class CiphertextTallyPojo {
  String object_id;
  Map<String, CiphertextTallyContestPojo> contests;

  class CiphertextTallyContestPojo {
    String object_id;
    Group.ElementModQ description_hash;
    Map<String, CiphertextTallySelectionPojo> selections;
  }

  class CiphertextTallySelectionPojo {
    String object_id;
    Group.ElementModQ description_hash;
    ElGamal.Ciphertext ciphertext;
  }
````

### Tally Decryption 

During Tally Decryption, the _EncryptedTally_ is decrypted into a **_PlaintextTally_** by a quorum number of Guardians. 
This is the count of the election results for whatever set of ballots were accumulated in the election record.
The PlaintextTally as well as the list of **_AvailableGuardians_** are added to the election record:

````
class PlaintextTallyPojo {
  String object_id;
  Map<String, PlaintextTallyContestPojo> contests;

  class PlaintextTallyContestPojo {
    String object_id;
    Map<String, PlaintextTallySelectionPojo> selections;
  }

  class PlaintextTallySelectionPojo {
    String object_id;
    Integer tally;
    Group.ElementModP value;
    ElGamal.Ciphertext message;
    List<CiphertextDecryptionSelectionPojo> shares;
  }

  class CiphertextDecryptionSelectionPojo {
    String object_id;
    String guardian_id;
    Group.ElementModP share;
    ChaumPedersenProofPojo proof; 
    Map<String, CiphertextCompensatedDecryptionSelectionPojo> recovered_parts;
  }

  class CiphertextCompensatedDecryptionSelectionPojo {
    String object_id;
    String guardian_id;
    String missing_guardian_id;
    Group.ElementModP share;
    Group.ElementModP recovery_key;
    ChaumPedersenProofPojo proof;
  }

  class ChaumPedersenProofPojo {
    Group.ElementModP pad;
    Group.ElementModP data;
    Group.ElementModQ challenge;
    Group.ElementModQ response;
  }
````

````
class AvailableGuardian {
  String guardian_id; /** The guardian id. */
  int x_coordinate; /** The guardian x coordinate value, aka sequence_order. */
  Group.ElementModQ lagrangeCoordinate; /** Its lagrange coordinate when decrypting. */
}
````

### Spoiled Ballot Decryption

During this step, which may be done at the same time as the Tally Decryption, the Encrypted Ballots that were marked
as spoiled (but not the cast ballots!) are decrypted by a quorum number of Guardians. For each spoiled SubmittedBallot,
a PlaintextBallot and a PlaintextTally (that is just for that one spoiled SubmittedBallot) are produced. These are added 
to the Election Record. These objects are the same as the ones already documented.

### Election Validation

The entire Election Record may be submitted to an ElectionGuard Validator, which uses just the Election Record
to validate with all the checks that are in the [Validator specification](https://www.electionguard.vote/spec/0.95.0/9_Verifier_construction/);

The complete set of objects looks like:

````
  class ElectionRecord {
    Manifest election;
  
    // KeyCeremony
    ElectionConstants constants;
    CiphertextElectionContext context;
    List<CoefficientValidationSet> guardianCoefficients;
  
    // Encyption
    List<EncryptionDevice> devices; 
    List<SubmittedBallot> acceptedBallots; // All ballots, cast and spoiled
  
    // Tally Accumulation
    CiphertextTally encryptedTally;
  
    // Decryption
    PlaintextTally decryptedTally;
    List<PlaintextBallot> spoiledBallots; 
    List<PlaintextTally> spoiledTallies; 
    List<AvailableGuardian> availableGuardians; 
  }
````

