package com.sunya.electionguard.verifier;

import com.sunya.electionguard.ElGamal;
import com.sunya.electionguard.Hash;
import com.sunya.electionguard.KeyCeremony;

import java.util.ArrayList;
import java.util.List;

import static com.sunya.electionguard.Group.ElementModP;
import static com.sunya.electionguard.Group.ElementModQ;

/** This verifies specification section "3. Election Public-Key Validation". */
public class ElectionPublicKeyVerifier {
  private final ElectionParameters electionParameters;
  private final Grp grp;

  ElectionPublicKeyVerifier(ElectionParameters electionParameters) {
    this.electionParameters = electionParameters;
    this.grp = new Grp(electionParameters.large_prime(), electionParameters.small_prime());
  }

  boolean verify_public_keys() {
    ElementModP public_key = this.electionParameters.elgamal_key();
    ElementModP expected_public_key = computePublicKey();

    // Equation 3.B
    if (!public_key.equals(expected_public_key)) {
      System.out.printf(" ***Expected Public key does not match.%n");
      return false;
    }

    // Equation 3.A LOOK probably wrong, see issue #279.
    ElementModQ expectedExtendedHash = Hash.hash_elems(this.electionParameters.base_hash(), public_key);
    if (!this.electionParameters.extended_hash().equals(expectedExtendedHash)) {
      System.out.printf(" ***Expected extended hash does not match.%n");
      return false;
    }
    System.out.printf(" Public key validation success.%n");
    return true;
  }

  ElementModP computePublicKey() {
    List<ElementModP> Ki = new ArrayList<>();
    for (KeyCeremony.CoefficientValidationSet coeff : this.electionParameters.coefficients()) {
      // the first commitment is the public key for guardian i.
      Ki.add(coeff.coefficient_commitments().get(0));
    }
    return ElGamal.elgamal_combine_public_keys(Ki);
  }
}
