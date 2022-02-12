package com.sunya.electionguard;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;

import java.util.List;

/**
 * Published record per Guardian used in verification processes.
 */
public record GuardianRecord(
        String guardian_id, // Unique identifier of the guardian.
        int sequence_order, // Actually the x coordinate, must be > 0
        Group.ElementModP election_public_key, // Guardian's election public key for encrypting election objects.
        List<Group.ElementModP> election_commitments,
        // Commitment for each coefficient of the guardians secret polynomial. First commitment is the election_public_key.
        List<SchnorrProof> election_proofs) // Proofs for each commitment for each coefficient of the guardians secret polynomial.
{
  public GuardianRecord {
    Preconditions.checkArgument(!Strings.isNullOrEmpty(guardian_id));
    Preconditions.checkNotNull(election_commitments);
    Preconditions.checkNotNull(election_proofs);
    Preconditions.checkArgument(election_proofs.size() == election_commitments.size());
    Preconditions.checkArgument(election_proofs.size() > 0);
    Preconditions.checkArgument(sequence_order > 0);

    if (election_public_key == null) {
      election_public_key = election_commitments.get(0);
    } else {
      Preconditions.checkArgument(election_public_key.equals(election_commitments.get(0)));
    }
  }

}
