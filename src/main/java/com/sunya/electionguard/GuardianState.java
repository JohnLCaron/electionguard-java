package com.sunya.electionguard;

import com.google.auto.value.AutoValue;
import com.google.common.base.Preconditions;

/**
 * The state of the Guardian when decrypting: missing or available.
 */
@AutoValue
public abstract class GuardianState {
  public abstract String guardian_id();
  public abstract int sequence();
  public abstract boolean is_missing();

  public static GuardianState create(String guardianId, int sequence, boolean isMissing) {
    return new AutoValue_GuardianState(
            Preconditions.checkNotNull(guardianId),
            sequence,
            isMissing);
  }
}
