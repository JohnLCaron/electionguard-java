package com.sunya.electionguard.ballot;

import com.google.common.base.Preconditions;
import com.sunya.electionguard.Group;

/**
 * An available Guardian when decrypting.
 * @param guardianId The guardian id
 * @param xCoordinate the guardian x coordinate value
 * @param lagrangeCoefficient the lagrange coefficient used for compensated decrypting
 */
public record DecryptingGuardian(
  String guardianId,
  Integer xCoordinate,
  Group.ElementModQ lagrangeCoefficient) {

  public DecryptingGuardian {
    Preconditions.checkNotNull(guardianId);
    Preconditions.checkArgument(xCoordinate > 0);
  }

}
