package com.sunya.electionguard;

import com.google.common.base.Preconditions;

import javax.annotation.concurrent.Immutable;
import java.util.Objects;

/** An available Guardian when decrypting. */
@Immutable
public class AvailableGuardian {
  /** The guardian id. */
  public final String guardian_id;
  /** The guardian x coordinate value. */
  public final int sequence;
  /** Its lagrange coordinate when decrypting. */
  public final Group.ElementModQ lagrangeCoordinate;

  public AvailableGuardian(String guardianId, int sequence, Group.ElementModQ lagrangeCoordinate) {
    this.guardian_id = Preconditions.checkNotNull(guardianId);
    this.sequence = sequence;
    this.lagrangeCoordinate = Preconditions.checkNotNull(lagrangeCoordinate);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    AvailableGuardian that = (AvailableGuardian) o;
    return sequence == that.sequence &&
            guardian_id.equals(that.guardian_id) &&
            lagrangeCoordinate.equals(that.lagrangeCoordinate);
  }

  @Override
  public int hashCode() {
    return Objects.hash(guardian_id, sequence, lagrangeCoordinate);
  }
}
