package com.sunya.electionguard.keyceremony;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;
import com.sunya.electionguard.ElectionPolynomial;
import com.sunya.electionguard.Group;
import com.sunya.electionguard.SchnorrProof;

import javax.annotation.Nullable;
import java.math.BigInteger;
import java.util.List;

import static com.sunya.electionguard.Group.ElementModP;

/** Value classes for the key ceremony. Replaces KeyCeremony. */
public class KeyCeremony2 {

  /** A Guardian's public key set of auxiliary and election keys, coefficient_commitments, and proofs. */
  @AutoValue
  public abstract static class PublicKeySet {
    /** guardian object_id. */
    public abstract String ownerId();
    /** guardian x coordinate (aka sequence_order). */
    public abstract int guardianXCoordinate();
    /** The election polynomial coefficients commitments and proofs. */
    public abstract ImmutableList<SchnorrProof> coefficientProofs();

    public static PublicKeySet create(String owner_id, int sequence_order,
                                      List<SchnorrProof> coefficient_proofs) {
      return new AutoValue_KeyCeremony2_PublicKeySet(
              owner_id, sequence_order,
              ImmutableList.copyOf(coefficient_proofs));
    }

    public ElementModP electionPublicKey() {
      return coefficientCommitments().get(0);
    }

    public ImmutableList<ElementModP> coefficientCommitments() {
      return coefficientProofs().stream().map(p -> p.public_key).collect(ImmutableList.toImmutableList());
    }

    public boolean isValid() {
      for (SchnorrProof proof : this.coefficientProofs()) {
        if (!proof.is_valid()) {
          return false;
        }
      }
      return true;
    }
  }

  /** A point on a secret polynomial, and commitments to verify this point for a designated guardian. */
  @AutoValue
  public abstract static class PartialKeyBackup {
    /** The Id of the guardian that generated this backup. */
    public abstract String generatingGuardianId();
    /** The Id of the guardian to receive this backup. */
    public abstract String designatedGuardianId();
    /** The x coordinate (aka sequence order) of the designated guardian. */
    public abstract int designatedGuardianXCoordinate();
    /** The coordinate of generatingGuardianId polynomial's value at designatedGuardianXCoordinate. */
    @Nullable public abstract Group.ElementModQ coordinate();

    public abstract String error(); // empty means no error

    public static PartialKeyBackup create(String owner_id,
                                          String designated_id,
                                          int designated_sequence_order,
                                          @Nullable Group.ElementModQ value,
                                          @Nullable String error) {
      if (error == null || error.trim().isEmpty()) {
        error = "";
      }
      return new AutoValue_KeyCeremony2_PartialKeyBackup(owner_id,
              designated_id,
              designated_sequence_order,
              value,
              error);
    }
  }

  /** Verification of election partial key used in key sharing. */
  @AutoValue
  public abstract static class PartialKeyVerification {
    public abstract String generatingGuardianId();
    public abstract String designatedGuardianId();
    public abstract String error(); // empty means no error

    public static PartialKeyVerification create(String owner_id, String designated_id, @Nullable String error) {
      if (error == null || error.trim().isEmpty()) {
        error = "";
      }
      return new AutoValue_KeyCeremony2_PartialKeyVerification(owner_id, designated_id, error);
    }
  }

  /** The response to a challenge to the ElectionPartialKeyVerification. */
  @AutoValue
  public abstract static class PartialKeyChallengeResponse {
    public abstract String generatingGuardianId();
    public abstract String designatedGuardianId();
    public abstract int designatedGuardianXCoordinate();
    /** The unencrypted coordinate of generatingGuardianId polynomial's value at designatedGuardianXCoordinate. */
    @Nullable public abstract Group.ElementModQ coordinate();
    public abstract String error(); // empty means no error

    public static PartialKeyChallengeResponse create(
            String owner_id, String designated_id, int designated_sequence_order,
            Group.ElementModQ value,
            String error) {
      if (error == null || error.trim().isEmpty()) {
        error = "";
      }
      return new AutoValue_KeyCeremony2_PartialKeyChallengeResponse(
              owner_id, designated_id, designated_sequence_order, value, error);
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

    return PartialKeyVerification.create(
            response.generatingGuardianId(),
            response.designatedGuardianId(),
            ok ? null : "verify_polynomial_coordinate failed");
  }
}
