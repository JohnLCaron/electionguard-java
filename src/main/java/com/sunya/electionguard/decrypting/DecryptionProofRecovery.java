package com.sunya.electionguard.decrypting;

import com.google.common.base.Preconditions;
import com.sunya.electionguard.ChaumPedersen;
import com.sunya.electionguard.Group;

import javax.annotation.concurrent.Immutable;

@Immutable
public class DecryptionProofRecovery {
  public final Group.ElementModP decryption;
  public final ChaumPedersen.ChaumPedersenProof proof;
  public final Group.ElementModP recoveryPublicKey;

  public DecryptionProofRecovery(Group.ElementModP partial_decryption, ChaumPedersen.ChaumPedersenProof proof,
                                 Group.ElementModP recoveryPublicKey) {
    this.decryption = Preconditions.checkNotNull(partial_decryption);
    this.proof = Preconditions.checkNotNull(proof);
    this.recoveryPublicKey = Preconditions.checkNotNull(recoveryPublicKey);
  }
}
