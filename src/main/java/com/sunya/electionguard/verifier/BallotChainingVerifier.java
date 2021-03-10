package com.sunya.electionguard.verifier;

import com.sunya.electionguard.Hash;

import java.util.HashSet;
import java.util.Set;

import com.sunya.electionguard.SubmittedBallot;
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
    for (SubmittedBallot ballot : electionRecord.acceptedBallots) {
        // 6.B For each ballot Bi , Hi = H(Hi−1, D, T, Bi) is satisfied. // LOOK what is D? is B_i == crypto_hash?
        ElementModQ crypto_hash = ballot.crypto_hash;
        ElementModQ prev_hash = ballot.previous_code;
        ElementModQ curr_hash = ballot.code;
        ElementModQ curr_hash_computed = Hash.hash_elems(prev_hash, ballot.timestamp, crypto_hash);
        if (!curr_hash.equals(curr_hash_computed)) {
          error = true;
          countFail++;
        }
        // ballot chaining
      if (ballot.previous_code != null) {
        prev_hashes.add(ballot.previous_code);
      }
      curr_hashes.add(ballot.code);
    }

    if (error) {
      System.out.printf(" ***Ballot Chaining failed on %d ballots.%n", countFail);
    } else {
      System.out.printf(" Ballot Chaining success.%n");
    }
    return !error;
  }

}
