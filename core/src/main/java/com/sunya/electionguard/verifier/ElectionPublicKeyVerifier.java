package com.sunya.electionguard.verifier;

import com.sunya.electionguard.ElGamal;
import com.sunya.electionguard.Group;
import com.sunya.electionguard.Hash;
import com.sunya.electionguard.publish.ElectionRecord;
import electionguard.ballot.Guardian;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static com.sunya.electionguard.Group.ElementModP;
import static com.sunya.electionguard.Group.ElementModQ;

/**
 * This verifies specification section "3 Manifest Public-Key Validation".
 * @see <a href="https://www.electionguard.vote/spec/0.95.0/9_Verifier_construction/#election-public-key-validation">Manifest public key validation</a>
 */
public class ElectionPublicKeyVerifier {
  private final ElectionRecord electionRecord;

  ElectionPublicKeyVerifier(ElectionRecord electionRecord) {
    this.electionRecord = electionRecord;
  }

  boolean verify_public_keys() {
    ElementModP public_key = this.electionRecord.electionPublicKey();
    ElementModP expected_public_key = computePublicKey();

    // Equation 3.A
    if (!public_key.equals(expected_public_key)) {
      System.out.printf(" ***3.A Public key does not match expected.%n");
      return false;
    }

    // Equation 3.B
    // The hashing is order dependent, use the sequence_order to sort.
    List<Guardian> sorted = this.electionRecord.guardians().stream()
            .sorted(Comparator.comparing(Guardian::getXCoordinate)).toList();
    List<Group.ElementModP> commitments = new ArrayList<>();
    for (Guardian coeff : sorted) {
      commitments.addAll(coeff.getCoefficientCommitments());
    }
    ElementModQ commitment_hash = Hash.hash_elems(commitments);
    ElementModQ expectedExtendedHash = Hash.hash_elems(this.electionRecord.baseHash(), commitment_hash);

    if (!this.electionRecord.extendedHash().equals(expectedExtendedHash)) {
      System.out.printf(" ***3.B. extended hash does not match expected.%n");
      return false;
    }
    System.out.printf(" Manifest public key validation for %d guardians success.%n", this.electionRecord.guardians().size());
    return true;
  }

  ElementModP computePublicKey() {
    List<ElementModP> Ki = new ArrayList<>();
    for (Guardian coeff : this.electionRecord.guardians()) {
      Ki.add(coeff.publicKey());
    }
    return ElGamal.elgamal_combine_public_keys(Ki);
  }
}
