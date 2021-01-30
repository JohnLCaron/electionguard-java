package com.sunya.electionguard.verifier;

import com.sunya.electionguard.Hash;

import java.util.HashSet;
import java.util.Set;

import static com.sunya.electionguard.Ballot.CiphertextAcceptedBallot;
import static com.sunya.electionguard.Group.ElementModQ;

 /** This verifies specification section "6. Ballot Chaining". */
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
    for (CiphertextAcceptedBallot ballot : electionRecord.castBallots) {
        // 6.B For each ballot Bi , Hi = H(Hi−1, D, T, Bi) is satisfied. // LOOK what is D? is B_i == crypto_hash?
        ElementModQ crypto_hash = ballot.crypto_hash;
        ElementModQ prev_hash = ballot.previous_tracking_hash;
        ElementModQ curr_hash = ballot.tracking_hash;
        ElementModQ curr_hash_computed = Hash.hash_elems(prev_hash, ballot.timestamp, crypto_hash);
        if (!curr_hash.equals(curr_hash_computed)) {
          error = true;
          countFail++;
        }
        // ballot chaining
      if (ballot.previous_tracking_hash != null) {
        prev_hashes.add(ballot.previous_tracking_hash);
      }
      curr_hashes.add(ballot.tracking_hash);
    }

    if (error) {
      System.out.printf(" ***Ballot Chaining failed on %d ballots.%n", countFail);
    } else {
      System.out.printf(" Ballot Chaining success.%n");
    }
    return !error;
  }

}
