package com.sunya.electionguard.standard;

/**
 * An interface for providing the complete set of Guardians obtained during the key ceremony.
 * These are used in the decryption stage.
 */
public interface GuardiansProvider {
  Iterable<Guardian> guardians();
}
