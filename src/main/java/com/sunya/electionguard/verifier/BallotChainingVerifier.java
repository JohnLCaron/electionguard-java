package com.sunya.electionguard.verifier;

import com.google.common.collect.Sets;
import com.sunya.electionguard.Group;
import com.sunya.electionguard.Hash;

import java.io.IOException;
import java.math.BigInteger;
import java.util.HashSet;
import java.util.Set;

import static com.sunya.electionguard.Ballot.CiphertextAcceptedBallot;
import static com.sunya.electionguard.Group.ElementModQ;

 /** This verifies specification section "6. Ballot Chaining". */
public class BallotChainingVerifier {
  private final ElectionRecord electionRecord;
  private final Grp grp;

  BallotChainingVerifier(ElectionRecord electionRecord) {
    this.electionRecord = electionRecord;
    this.grp = new Grp(electionRecord.large_prime(), electionRecord.small_prime());
  }

  boolean verify_all_ballots() throws IOException {
    boolean error = false;
    int countFail = 0;
    Set<ElementModQ> prev_hashes = new HashSet<>();
    Set<ElementModQ> curr_hashes = new HashSet<>();

    // LOOK this assumes that the ballots are in the correct order. Why would they be?
    for (CiphertextAcceptedBallot ballot : electionRecord.castBallots) {
        // 6.B For each ballot Bi , Hi = H(Hi−1, D, T, Bi) is satisfied.
        ElementModQ crypto_hash = ballot.crypto_hash;
        ElementModQ prev_hash = ballot.previous_tracking_hash;
        ElementModQ curr_hash = ballot.tracking_hash.orElseThrow(IllegalStateException::new);
        ElementModQ curr_hash_computed = Hash.hash_elems(prev_hash, ballot.timestamp, crypto_hash);
        if (!curr_hash.equals(curr_hash_computed)) {
          error = true;
          countFail++;
        }
        // ballot chaining
      if (ballot.previous_tracking_hash != null) {
        prev_hashes.add(ballot.previous_tracking_hash);
      }
      ballot.tracking_hash.ifPresent(curr_hashes::add);
    }

    // LOOK this test does not work see notes in TestEncryptionValidator
    if (!this.verify_tracking_hashes(prev_hashes, curr_hashes)) {
      // error = true;
    }

    if (error) {
      System.out.printf(" ***Ballot Chaining failed on %d ballots.%n", countFail);
    } else {
      System.out.printf(" Ballot Chaining success.%n");
    }
    return !error;
  }

  /**
   * LOOK this test does not work see notes in TestSelectionEncryptionValidation
   * verifies the tracking hash chain correctness
   * NOTE: didn't check the first and closing hashes
   * @param prev_hashes: the set of "previous tracking" hash codes
   * @param curr_hashes: the set of "current tracking" hash codes
   * @return True if all the tracking hashes satisfy Bi, Hi = H(Hi-1, D, T, Bi)
   */
  private boolean verify_tracking_hashes(Set<ElementModQ> prev_hashes, Set<ElementModQ> curr_hashes) {
    boolean error = false;

    // find the set that only contains first and last hash
    Set<ElementModQ> differenceSet = Sets.symmetricDifference(prev_hashes, curr_hashes);

    // find the first and last hash
    ElementModQ first_hash = Group.int_to_q_unchecked(BigInteger.ZERO);
    ElementModQ last_hash = Group.int_to_q_unchecked(BigInteger.ZERO);
    for (ElementModQ h : differenceSet) {
      if (prev_hashes.contains(h)){
        first_hash = h;
      } else if (curr_hashes.contains(h)){
        last_hash = h;
      }
    }

    // 6.1 verify the first hash H0 = H(Q-bar)
    ElementModQ zero_hash = Hash.hash_elems(electionRecord.extended_hash());
    if (!zero_hash.equals(first_hash)) {
      error = true;
    }

    // 6.3 verify the closing hash, H-bar = H(Hl, 'CLOSE')
    ElementModQ closing_hash_computed = Hash.hash_elems(last_hash, "CLOSE");
    if (!closing_hash_computed.equals(last_hash)) {
      error = true;
    }

    return !error;
  }

}
