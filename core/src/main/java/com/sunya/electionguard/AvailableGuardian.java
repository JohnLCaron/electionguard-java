package com.sunya.electionguard;

import com.google.common.base.Preconditions;

/**
 * An available Guardian when decrypting.
 * @param guardianId The guardian id
 * @param xCoordinate the guardian x coordinate value
 * @param lagrangeCoordinate the lagrange coordinate when decrypting
 */
public record AvailableGuardian(
  String guardianId,
  Integer xCoordinate,
  Group.ElementModQ lagrangeCoordinate) {

  public AvailableGuardian {
    Preconditions.checkNotNull(guardianId);
    Preconditions.checkArgument(xCoordinate > 0);
    Preconditions.checkNotNull(lagrangeCoordinate);
  }
}
