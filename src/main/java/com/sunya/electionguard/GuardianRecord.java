package com.sunya.electionguard;

import com.google.auto.value.AutoValue;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;

import javax.annotation.Nullable;
import java.util.List;

/** Published record per Guardian used in verification processes. */
@AutoValue
public abstract class GuardianRecord {

  /** Unique identifier of the guardian. */
  public abstract String guardian_id();

  /** Unique sequence order of the guardian indicating the order in which the guardian should be processed. */
  public abstract int sequence_order();

  /** Guardian's election public key for encrypting election objects. */
  public abstract Group.ElementModP election_public_key();

  /** Commitment for each coefficient of the guardians secret polynomial. First commitment is the election_public_key. */
  public abstract List<Group.ElementModP> election_commitments();

  /** Proofs for each commitment for each coefficient of the guardians secret polynomial. */
  public abstract List<SchnorrProof> election_proofs();

  public static GuardianRecord create(String guardian_id,
                                      int sequence_order,
                                      @Nullable Group.ElementModP election_public_key,
                                      List<Group.ElementModP> election_commitments,
                                      List<SchnorrProof> election_proofs) {

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

    return new AutoValue_GuardianRecord(
            guardian_id,
            sequence_order,
            election_public_key,
            election_commitments,
            election_proofs);
  }


  /**
   * Published record containing all required information per Guardian
   * for Election record used in verification processes
   *
   * @param election_public_key: Guardian's election public key
   */
  public static GuardianRecord publish_guardian_record(KeyCeremony.ElectionPublicKey election_public_key) {
    return GuardianRecord.create(
            election_public_key.owner_id(),
            election_public_key.sequence_order(),
            election_public_key.key(),
            election_public_key.coefficient_commitments(),
            election_public_key.coefficient_proofs()
            );
  }

}
