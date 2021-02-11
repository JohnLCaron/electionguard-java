package com.sunya.electionguard.verifier;

import com.sunya.electionguard.ElGamal;
import com.sunya.electionguard.Group;
import com.sunya.electionguard.Hash;
import com.sunya.electionguard.KeyCeremony;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

import static com.sunya.electionguard.Group.ElementModP;
import static com.sunya.electionguard.Group.ElementModQ;

/** This verifies specification section "3. Election Public-Key Validation". */
public class ElectionPublicKeyVerifier {
  private final ElectionRecord electionRecord;

  ElectionPublicKeyVerifier(ElectionRecord electionRecord) {
    this.electionRecord = electionRecord;
  }

  boolean verify_public_keys() {
    ElementModP public_key = this.electionRecord.elgamal_key();
    ElementModP expected_public_key = computePublicKey();

    // Equation 3.B
    if (!public_key.equals(expected_public_key)) {
      System.out.printf(" ***Expected Public key does not match.%n");
      return false;
    }

    // Equation 3.A
    // The hashing is order dependent.
    List<KeyCeremony.CoefficientValidationSet> sorted = this.electionRecord.guardianCoefficients.stream()
            .sorted(Comparator.comparing(KeyCeremony.CoefficientValidationSet::owner_id))
            .collect(Collectors.toList());
    List<Group.ElementModP> commitments = new ArrayList<>();
    for (KeyCeremony.CoefficientValidationSet coeff : sorted) {
      commitments.addAll(coeff.coefficient_commitments());
    }
    ElementModQ commitment_hash = Hash.hash_elems(commitments);
    ElementModQ expectedExtendedHash = Hash.hash_elems(this.electionRecord.base_hash(), commitment_hash);

    if (!this.electionRecord.extended_hash().equals(expectedExtendedHash)) {
      System.out.printf(" ***Expected extended hash does not match.%n");
      return false;
    }
    System.out.printf(" Public key validation success.%n");
    return true;
  }

  ElementModP computePublicKey() {
    List<ElementModP> Ki = new ArrayList<>();
    for (KeyCeremony.CoefficientValidationSet coeff : this.electionRecord.guardianCoefficients) {
      // the first commitment is the public key for guardian i.
      Ki.add(coeff.coefficient_commitments().get(0));
    }
    return ElGamal.elgamal_combine_public_keys(Ki);
  }
}
