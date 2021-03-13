package com.sunya.electionguard;

public class DecryptionProofTuple {
  public final Group.ElementModP decryption;
  public final ChaumPedersen.ChaumPedersenProof proof;

  public DecryptionProofTuple(Group.ElementModP partial_decryption, ChaumPedersen.ChaumPedersenProof proof) {
    this.decryption = partial_decryption;
    this.proof = proof;
  }
}
