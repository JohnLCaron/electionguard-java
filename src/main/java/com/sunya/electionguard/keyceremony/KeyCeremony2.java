package com.sunya.electionguard.keyceremony;

import com.google.auto.value.AutoValue;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.sunya.electionguard.Auxiliary;
import com.sunya.electionguard.ElectionPolynomial;
import com.sunya.electionguard.Group;
import com.sunya.electionguard.SchnorrProof;

import java.math.BigInteger;
import java.util.List;

import static com.sunya.electionguard.Group.ElementModP;

public class KeyCeremony2 {

  /** A Guardian's public key set of auxiliary and election keys, coefficient_commitments, and proofs. */
  @AutoValue
  public abstract static class PublicKeySet {
    /** guardian object_id. */
    public abstract String ownerId();
    /** guardian x coordinate (aka sequence_order). */
    public abstract int guardianXCoordinate();
    /** Auxiliary public key. */
    public abstract java.security.PublicKey auxiliaryPublicKey();
    /** The election polynomial coefficients commitments and proofs. */
    public abstract ImmutableList<SchnorrProof> coefficientProofs();

    public static PublicKeySet create(String owner_id, int sequence_order, java.security.PublicKey auxiliary_public_key,
                                      List<SchnorrProof> coefficient_proofs) {
      Preconditions.checkNotNull(auxiliary_public_key);
      return new AutoValue_KeyCeremony2_PublicKeySet(
              owner_id, sequence_order, auxiliary_public_key,
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
    /** The encrypted coordinate of generatingGuardianId polynomial's value at designatedGuardianXCoordinate. */
    public abstract Auxiliary.ByteString encryptedCoordinate();

    public static PartialKeyBackup create(String owner_id,
                                          String designated_id,
                                          int designated_sequence_order,
                                          Auxiliary.ByteString encrypted_value) {
      return new AutoValue_KeyCeremony2_PartialKeyBackup(owner_id,
              designated_id,
              designated_sequence_order,
              encrypted_value);
    }
  }

  /** Verification of election partial key used in key sharing. */
  @AutoValue
  public abstract static class PartialKeyVerification {
    public abstract String generatingGuardianId();
    public abstract String designatedGuardianId();
    public abstract boolean verified();

    public static PartialKeyVerification create(String owner_id, String designated_id, boolean verified) {
      return new AutoValue_KeyCeremony2_PartialKeyVerification(owner_id, designated_id, verified);
    }
  }

  /** The response to a challenge to the ElectionPartialKeyVerification. */
  @AutoValue
  public abstract static class PartialKeyChallengeResponse {
    public abstract String generatingGuardianId();
    public abstract String designatedGuardianId();
    public abstract int designatedGuardianXCoordinate();
    /** The unencrypted coordinate of generatingGuardianId polynomial's value at designatedGuardianXCoordinate. */
    public abstract Group.ElementModQ coordinate();

    public static PartialKeyChallengeResponse create(
            String owner_id, String designated_id, int designated_sequence_order,
            Group.ElementModQ value) {
      return new AutoValue_KeyCeremony2_PartialKeyChallengeResponse(
              owner_id, designated_id, designated_sequence_order, value);
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
            ok);
  }
}
