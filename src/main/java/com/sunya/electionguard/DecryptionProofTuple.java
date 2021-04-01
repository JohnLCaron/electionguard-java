package com.sunya.electionguard;

import com.google.common.base.Preconditions;

public class DecryptionProofTuple {
  public final Group.ElementModP decryption;
  public final ChaumPedersen.ChaumPedersenProof proof;

  public DecryptionProofTuple(Group.ElementModP partial_decryption, ChaumPedersen.ChaumPedersenProof proof) {
    this.decryption = Preconditions.checkNotNull(partial_decryption);
    this.proof = Preconditions.checkNotNull(proof);
  }
}
