package com.sunya.electionguard;

import com.google.common.collect.Iterables;

import javax.annotation.Nullable;
import java.math.BigInteger;
import java.util.*;
import java.util.stream.Collectors;

import static com.sunya.electionguard.Ballot.*;
import static com.sunya.electionguard.Group.*;

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
  static class CiphertextTallySelection extends CiphertextSelection {
    // Note not immutable - override superclass
    ElGamal.Ciphertext ciphertext; // default=ElGamalCiphertext(ONE_MOD_P, ONE_MOD_P)

    public CiphertextTallySelection(String object_id, ElementModQ description_hash, @Nullable ElGamal.Ciphertext ciphertext) {
      super(object_id, description_hash, ciphertext == null ? new ElGamal.Ciphertext(ONE_MOD_P, ONE_MOD_P) : ciphertext);
      this.ciphertext = (ciphertext == null) ? new ElGamal.Ciphertext(ONE_MOD_P, ONE_MOD_P) : ciphertext;
    }

    /**
     * Homomorphically add the specified value to the message.
     */
    ElGamal.Ciphertext elgamal_accumulate(ElGamal.Ciphertext elgamal_ciphertext) {
      // Note not immutable
      this.ciphertext = ElGamal.elgamal_add(this.ciphertext, elgamal_ciphertext);
      return this.ciphertext;
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
    private final Election.InternalElectionDescription _metadata;
    private final Election.CiphertextElectionContext _encryption;

    // A local cache of ballots id's that have already been cast
    private Set<String> _cast_ballot_ids;

    //    A collection of each contest and selection in an election.
    //    Retains an encrypted representation of a tally for each selection
    Map<String, CiphertextTallyContest> cast;
    // All of the ballots marked spoiled in the election
    Map<String, Ballot.CiphertextAcceptedBallot> spoiled_ballots;

    public CiphertextTally(String object_id, Election.InternalElectionDescription metadata, Election.CiphertextElectionContext encryption) {
      super(object_id);
      this._metadata = metadata;
      this._encryption = encryption;
      this.spoiled_ballots = new HashMap<>();

      this._cast_ballot_ids = new HashSet<>();
      this.cast = this._build_tally_collection(this._metadata);
    }

    private int len() {
      return this._cast_ballot_ids.size() + this.spoiled_ballots.size();
    }

    private boolean contains(Object item) {
      if (!(item instanceof CiphertextAcceptedBallot)) {
        return false;
      }
      CiphertextAcceptedBallot ballot = (CiphertextAcceptedBallot) item;
      return this._cast_ballot_ids.contains(ballot.object_id) || this.spoiled_ballots.containsKey(ballot.object_id);
    }


    /**
     * Append a collection of Ballots to the tally and recalculate.
     */
    boolean batch_append(Iterable<CiphertextAcceptedBallot> ballots, Optional<Scheduler> scheduler) {
      Map<String, Map<String, ElGamal.Ciphertext>> cast_ballot_selections = new HashMap<>();

      for (CiphertextAcceptedBallot ballot : ballots) {
        if (!this.contains(ballot) &&
                BallotValidator.ballot_is_valid_for_election(ballot, this._metadata, this._encryption)) {
          if (ballot.state == BallotBoxState.CAST) {
            // collect the selections so they can can be accumulated in parallel
            for (CiphertextBallotContest contest : ballot.contests) {
              for (CiphertextBallotSelection selection : contest.ballot_selections) {
                if (!cast_ballot_selections.containsKey(selection.object_id)) {
                  cast_ballot_selections.put(selection.object_id, new HashMap<>());
                }
                // cast_ballot_selections[selection.object_id][ ballot_value.object_id] = selection.ciphertext
                cast_ballot_selections.get(selection.object_id).put(ballot.object_id, selection.ciphertext);
              }
            }

            // just append the spoiled ballots
          } else if (ballot.state == BallotBoxState.SPOILED) {
            this._add_spoiled(ballot);
          }
        }
      }

      // cache the cast ballot id's so they are not double counted
      if (this._execute_accumulate(cast_ballot_selections, scheduler)) {
        for (CiphertextAcceptedBallot ballot : ballots) {
          if (ballot.state == BallotBoxState.CAST) {
            this._cast_ballot_ids.add(ballot.object_id);
          }
          return true;
        }
      }
      return false;
    }


    /**
     * Add a spoiled ballot.
     */
    boolean _add_spoiled(CiphertextAcceptedBallot ballot) {
      this.spoiled_ballots.put(ballot.object_id, ballot);
      return true;
    }

    /**
     * Build the object graph for the tally from the InternalElectionDescription.
     */
    static Map<String, CiphertextTallyContest> _build_tally_collection(Election.InternalElectionDescription description) {

      Map<String, CiphertextTallyContest> cast_collection = new HashMap<>();
      for (Election.ContestDescriptionWithPlaceholders contest : description.contests) {
        // build a collection of valid selections for the contest description
        // note: we explicitly ignore the Placeholder Selections.
        Map<String, CiphertextTallySelection> contest_selections = new HashMap<>();
        for (Election.SelectionDescription selection : contest.ballot_selections) {
          contest_selections.put(selection.object_id,
                  new CiphertextTallySelection(selection.object_id, selection.crypto_hash(), null));
        }
        cast_collection.put(contest.object_id, new CiphertextTallyContest(contest.object_id, contest.crypto_hash(), contest_selections));
      }
      return cast_collection;
    }

    // Allow _accumulate to be run in parellel
    static class Tuple1 {
      final String id;
      final ElGamal.Ciphertext ciphertext;

      public Tuple1(String id, ElGamal.Ciphertext ciphertext) {
        this.id = id;
        this.ciphertext = ciphertext;
      }
    }

    static class RunAccumulate implements Runnable {
      final String id;
      final Map<String, ElGamal.Ciphertext> ballot_selections;
      Tuple1 result;

      public RunAccumulate(String id, Map<String, ElGamal.Ciphertext> ballot_selections) {
        this.id = id;
        this.ballot_selections = ballot_selections;
      }

      @Override
      public void run() {
        result = _accumulate(id, ballot_selections);
      }
    }

    static Tuple1 _accumulate(String id, Map<String, ElGamal.Ciphertext> ballot_selections) {
      //  *[ciphertext for ciphertext in ballot_selections.values()]
      return new Tuple1(id, ElGamal.elgamal_add(Iterables.toArray(ballot_selections.values(), ElGamal.Ciphertext.class)));
    }

    boolean _execute_accumulate(
            Map<String, Map<String, ElGamal.Ciphertext>> ciphertext_selections_by_selection_id,
            Optional<Scheduler> schedulerO) {
      Scheduler scheduler = schedulerO.orElse(new Scheduler());

      List<RunAccumulate> tasks =
              ciphertext_selections_by_selection_id.entrySet().stream()
                      .map(entry -> new RunAccumulate(entry.getKey(), entry.getValue()))
                      .collect(Collectors.toList());
      List<Tuple1> result_set = scheduler.schedule(tasks, true);

      Map<String, ElGamal.Ciphertext> result_dict = result_set.stream()
              .collect(Collectors.toMap(t -> t.id, t -> t.ciphertext));

      for (CiphertextTallyContest contest : this.cast.values()) {
        for (Map.Entry<String, CiphertextTallySelection> entry2 : contest.tally_selections.entrySet()) {
          String selection_id = entry2.getKey();
          CiphertextTallySelection selection = entry2.getValue();
          if (result_dict.containsKey(selection_id)) {
            selection.elgamal_accumulate(result_dict.get(selection_id));
          }
        }
      }

      return true;
    }

  } // class CiphertextTally

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
  } // PlaintextTally

  /**
   * Tally all of the ballots in the ballot store.
   * :return: a CiphertextTally or None if there is an error
   */
  static Optional<CiphertextTally> tally_ballots(
          Iterable<CiphertextAcceptedBallot> store,
          Election.InternalElectionDescription metadata,
          Election.CiphertextElectionContext context) {

    // TODO: ISSUE //14: unique Id for the tally
    CiphertextTally tally = new CiphertextTally("election-results", metadata, context);
    if (tally.batch_append(store, Optional.empty())) {
      return Optional.of(tally);
    }
    return Optional.empty();
  }

}
