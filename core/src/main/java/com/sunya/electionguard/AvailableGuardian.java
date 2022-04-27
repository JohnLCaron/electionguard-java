package com.sunya.electionguard;

import com.google.common.base.Preconditions;

/**
 * An available Guardian when decrypting.
 * @param guardianId The guardian id
 * @param xCoordinate the guardian x coordinate value
 * @param lagrangeCoefficient the lagrange coefficient used for compensated decrypting
 */
public record AvailableGuardian(
  String guardianId,
  Integer xCoordinate,
  Group.ElementModQ lagrangeCoefficient) {

  public AvailableGuardian {
    Preconditions.checkNotNull(guardianId);
    Preconditions.checkArgument(xCoordinate > 0);
  }

}
