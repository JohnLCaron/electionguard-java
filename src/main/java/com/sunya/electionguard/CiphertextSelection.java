package com.sunya.electionguard;

import com.google.common.base.Preconditions;

import javax.annotation.concurrent.Immutable;

/**
 * Superclass for encrypted selections.
 */
@Immutable
class CiphertextSelection extends ElectionObjectBase {
  public final Group.ElementModQ description_hash; // Election.SelectionDescription.crypto_hash().
  private final ElGamal.Ciphertext ciphertext; // only accessed through ciphertext(), so subclass can override

  CiphertextSelection(String object_id, Group.ElementModQ description_hash, ElGamal.Ciphertext ciphertext) {
    super(object_id);
    this.description_hash = Preconditions.checkNotNull(description_hash);
    this.ciphertext = Preconditions.checkNotNull(ciphertext);
  }

  public ElGamal.Ciphertext ciphertext() {
    return ciphertext;
  }
}