package com.sunya.electionguard;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;

import java.util.List;

/**
 * Published record per Guardian used in verification processes.
 */
public record GuardianRecord(
        String guardianId, // Unique identifier of the guardian.
        int xCoordinate, // Actually the x coordinate, must be > 0
        Group.ElementModP guardianPublicKey, // Guardian's election public key for encrypting election objects.
        List<Group.ElementModP> coefficientCommitments,
        // Commitment for each coefficient of the guardians secret polynomial. First commitment is the election_public_key.
        List<SchnorrProof> coefficientProofs) // Proofs for each commitment for each coefficient of the guardians secret polynomial.
{
  public GuardianRecord {
    Preconditions.checkArgument(!Strings.isNullOrEmpty(guardianId));
    Preconditions.checkNotNull(coefficientCommitments);
    Preconditions.checkNotNull(coefficientProofs);
    Preconditions.checkArgument(coefficientProofs.size() == coefficientCommitments.size());
    Preconditions.checkArgument(coefficientProofs.size() > 0);
    Preconditions.checkArgument(xCoordinate > 0);

    if (guardianPublicKey == null) {
      guardianPublicKey = coefficientCommitments.get(0);
    } else {
      Preconditions.checkArgument(guardianPublicKey.equals(coefficientCommitments.get(0)));
    }
  }

}
