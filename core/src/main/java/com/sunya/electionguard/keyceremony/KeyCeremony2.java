package com.sunya.electionguard.keyceremony;

import com.google.common.collect.ImmutableList;
import com.sunya.electionguard.ElectionPolynomial;
import com.sunya.electionguard.Group;
import com.sunya.electionguard.SchnorrProof;

import javax.annotation.Nullable;
import java.math.BigInteger;
import java.util.List;

import static com.sunya.electionguard.Group.ElementModP;

/** Value classes for the key ceremony. Replaces standard.KeyCeremony. */
public class KeyCeremony2 {

  /**
   * A Guardian's public key set of auxiliary and election keys, coefficient_commitments, and proofs.
   * @param ownerId guardian object_id
   * @param guardianXCoordinate guardian x coordinate (aka sequence_order)
   * @param coefficientProofs The election polynomial coefficients commitments and proofs
   */
  public record PublicKeySet(
    String ownerId,
    int guardianXCoordinate,
    List<SchnorrProof> coefficientProofs) {

    public PublicKeySet {
      coefficientProofs = List.copyOf(coefficientProofs);
    }

    public ElementModP electionPublicKey() {
      return coefficientCommitments().get(0);
    }

    public List<ElementModP> coefficientCommitments() {
      return coefficientProofs().stream().map(p -> p.publicKey).collect(ImmutableList.toImmutableList());
    }

    public boolean isValid() {
      for (SchnorrProof proof : this.coefficientProofs()) {
        if (!proof.isValidVer1()) {
          return false;
        }
      }
      return true;
    }
  }

  /**
   * A point on a secret polynomial, and commitments to verify this point for a designated guardian.
   * @param generatingGuardianId The Id of the guardian that generated this backup
   * @param designatedGuardianId The Id of the guardian to receive this backup
   * @param designatedGuardianXCoordinate TThe x coordinate (aka sequence order) of the designated guardian
   * @param coordinate TThe coordinate of generatingGuardianId polynomial's value at designatedGuardianXCoordinate
   */
  public record PartialKeyBackup(
      String generatingGuardianId,
      String designatedGuardianId,
      int designatedGuardianXCoordinate,
      @Nullable Group.ElementModQ coordinate,
      @Nullable String error) {

    public PartialKeyBackup {
      if (error == null || error.trim().isEmpty()) {
        error = "";
      }
    }
  }

  /** Verification of election partial key used in key sharing. */
  public record PartialKeyVerification(
    String generatingGuardianId,
    String designatedGuardianId,
    @Nullable String error) {

    public PartialKeyVerification {
      if (error == null || error.trim().isEmpty()) {
        error = "";
      }
    }
  }

  /**
   * The response to a challenge to the ElectionPartialKeyVerification.
   *
   * @param coordinate The unencrypted coordinate of generatingGuardianId polynomial's value at designatedGuardianXCoordinate.
   */
  public record PartialKeyChallengeResponse(
          String generatingGuardianId,
          String designatedGuardianId,
          int designatedGuardianXCoordinate,
          @Nullable Group.ElementModQ coordinate,
          @Nullable String error) {

    public PartialKeyChallengeResponse {
      if (error == null || error.trim().isEmpty()) {
        error = "";
      }
    }
  }

  /**
   * Verify a response to a challenge to a partial key backup.
   * @param response: The response to a partial key backup challenge
   */
  public static PartialKeyVerification verifyElectionPartialKeyChallenge(
          PartialKeyChallengeResponse response, List<ElementModP> coefficient_commitments) {

    boolean ok = ElectionPolynomial.verify_polynomial_coordinate(
            response.coordinate(),
            BigInteger.valueOf(response.designatedGuardianXCoordinate()),
            coefficient_commitments);

    return new PartialKeyVerification(
            response.generatingGuardianId(),
            response.designatedGuardianId(),
            ok ? null : "verify_polynomial_coordinate failed");
  }
}
