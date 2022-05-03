package com.sunya.electionguard.verifier;

import com.sunya.electionguard.Hash;

import java.util.HashSet;
import java.util.Set;

import com.sunya.electionguard.SubmittedBallot;
import com.sunya.electionguard.publish.ElectionRecord;

import static com.sunya.electionguard.Group.ElementModQ;

/**
 * This verifies specification section "6 Ballot Chaining".
 * @see <a href="https://www.electionguard.vote/spec/0.95.0/9_Verifier_construction/#validation-of-ballot-chaining">Ballot chaining validation</a>
 */
public class BallotChainingVerifier {
  private final ElectionRecord electionRecord;

  BallotChainingVerifier(ElectionRecord electionRecord) {
    this.electionRecord = electionRecord;
  }

  boolean verify_all_ballots() {
    boolean error = false;
    int countFail = 0;
    Set<ElementModQ> prev_hashes = new HashSet<>();
    Set<ElementModQ> curr_hashes = new HashSet<>();

    // LOOK this assumes that the ballots are in the correct order. Why would they be?
    int nballots = 0;
    for (SubmittedBallot ballot : electionRecord.submittedBallots()) {
      nballots++;
        // 6.B For each ballot Bi , Hi = H(Hi−1, D, T, Bi) is satisfied. // LOOK what is D? is B_i == crypto_hash?
        ElementModQ hashChain = Hash.hash_elems(ballot.code_seed, ballot.timestamp, ballot.crypto_hash);
        if (!ballot.code.equals(hashChain)) {
          error = true;
          countFail++;
        }
        // ballot chaining
      if (ballot.code_seed != null) {
        prev_hashes.add(ballot.code_seed);
      }
      curr_hashes.add(ballot.code);
    }

    if (error) {
      System.out.printf(" ***Ballot Chaining failed on %d ballots.%n", countFail);
    } else {
      System.out.printf(" Ballot Chaining success for %d ballots.%n", nballots);
    }
    return !error;
  }

}
