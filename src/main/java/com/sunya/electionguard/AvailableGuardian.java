package com.sunya.electionguard;

import com.google.auto.value.AutoValue;
import com.google.common.base.Preconditions;

/**
 * An available Guardian when decrypting.
 */
@AutoValue
public abstract class AvailableGuardian {
  /** The guardian id. */
  public abstract String guardian_id();
  /** The guardian x coordinate value. */
  public abstract int sequence();
  /** Its lagrange coordinate when decrypting. */
  public abstract Group.ElementModQ lagrangeCoordinate();

  public static AvailableGuardian create(String guardianId, int sequence, Group.ElementModQ lagrangeCoordinate) {
    return new AutoValue_AvailableGuardian(
            Preconditions.checkNotNull(guardianId),
            sequence,
            lagrangeCoordinate);
  }
}
