package com.sunya.electionguard.decrypting;

import com.google.common.base.Preconditions;
import com.sunya.electionguard.ChaumPedersen;
import com.sunya.electionguard.Group;

public record DecryptionProofRecovery(
  Group.ElementModP decryption,
  ChaumPedersen.ChaumPedersenProof proof,
  Group.ElementModP recoveryPublicKey) {

  public DecryptionProofRecovery {
    Preconditions.checkNotNull(decryption);
    Preconditions.checkNotNull(proof);
    Preconditions.checkNotNull(recoveryPublicKey);
  }
}
