package com.sunya.electionguard;

import com.google.common.base.Preconditions;

import javax.annotation.concurrent.Immutable;
import java.util.Objects;

/**
 * An available Guardian when decrypting.
 * @param guardian_id The guardian id
 * @param sequence the guardian x coordinate value
 * @param lagrangeCoordinate the lagrange coordinate when decrypting
 */
public record AvailableGuardian(
  String guardian_id,
  Integer sequence,
  Group.ElementModQ lagrangeCoordinate) {

  public AvailableGuardian {
    Preconditions.checkNotNull(guardian_id);
    Preconditions.checkArgument(sequence > 0);
    Preconditions.checkNotNull(lagrangeCoordinate);
  }
}
