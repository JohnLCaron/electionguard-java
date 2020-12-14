package sunya.electionguard;

import java.math.BigInteger;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static sunya.electionguard.Group.*;

class Tally {

  /**
   * A plaintext Tally Selection is a decrypted selection of a contest.
   */
  static class PlaintextTallySelection extends ElectionObjectBase {
    // g^tally or M in the spec
    final BigInteger tally;
    final ElementModP value;

    final ElGamal.Ciphertext message;

    final List<DecryptionShare.CiphertextDecryptionSelection> shares;

    public PlaintextTallySelection(String objectId, BigInteger tally, ElementModP value,
                                   ElGamal.Ciphertext message, List<DecryptionShare.CiphertextDecryptionSelection> shares) {
      super(objectId);
      this.tally = tally;
      this.value = value;
      this.message = message;
      this.shares = shares;
    }
  }

  /**
   * a CiphertextTallySelection is a homomorphic accumulation of all of the
   * CiphertextBallotSelection instances for a specific selection in an election.
   */
  static class CiphertextTallySelection extends Ballot.CiphertextSelection {
    ElementModQ description_hash; // The SelectionDescription hash

    ElGamal.Ciphertext ciphertext; // default=ElGamalCiphertext(ONE_MOD_P, ONE_MOD_P)
    // The encrypted representation of the total of all ballots for this selection

    public CiphertextTallySelection(String object_id, ElementModQ description_hash, ElGamal.Ciphertext ciphertext,
                                    ElementModQ description_hash1, ElGamal.Ciphertext ciphertext1) {
      super(object_id, description_hash, ciphertext);
      this.description_hash = description_hash1;
      this.ciphertext = ciphertext1;
    }
  }

  /**
   * A plaintext Tally Contest is a collection of plaintext selections
   */
  static class PlaintextTallyContest extends ElectionObjectBase {
    final Map<String, PlaintextTallySelection> selections;

    public PlaintextTallyContest(String object_id, Map<String, PlaintextTallySelection> selections) {
      super(object_id);
      this.selections = selections;
    }
  }

  /**
   * A CiphertextTallyContest is a container for associating a collection of CiphertextTallySelection
   * to a specific ContestDescription
   */
  static class CiphertextTallyContest extends ElectionObjectBase {
    final ElementModQ description_hash; // The ContestDescription hash

    final Map<String, CiphertextTallySelection> tally_selections; // A collection of CiphertextTallySelection mapped by SelectionDescription.object_id

    public CiphertextTallyContest(String object_id, ElementModQ description_hash, Map<String, CiphertextTallySelection> tally_selections) {
      super(object_id);
      this.description_hash = description_hash;
      this.tally_selections = tally_selections;
    }
  }

  /**
   * A `CiphertextTally` accepts cast and spoiled ballots and accumulates a tally on the cast ballots.
   */
  static class CiphertextTally extends ElectionObjectBase {
    private final Election.InternalElectionDescription metadata;
    private final Election.CiphertextElectionContext encryption;

    // A local cache of ballots id's that have already been cast
    private Set<String> cast_ballot_ids;

    //    A collection of each contest and selection in an election.
    //    Retains an encrypted representation of a tally for each selection
    Map<String, CiphertextTallyContest> cast;
    // All of the ballots marked spoiled in the election
    Map<String, Ballot.CiphertextAcceptedBallot> spoiled_ballots;

    public CiphertextTally(String object_id, Election.InternalElectionDescription metadata, Election.CiphertextElectionContext encryption) {
      super(object_id);
      this.metadata = metadata;
      this.encryption = encryption;
    }
  }

  /**
   * The plaintext representation of all contests in the election
   */
  static class PlaintextTally extends ElectionObjectBase {
    Map<String, PlaintextTallyContest> contests;
    Map<String, Map<String, PlaintextTallyContest>> spoiled_ballots;

    public PlaintextTally(String object_id, Map<String, PlaintextTallyContest> contests,
                          Map<String, Map<String, PlaintextTallyContest>> spoiled_ballots) {
      super(object_id);
      this.contests = contests;
      this.spoiled_ballots = spoiled_ballots;
    }
  }

}
