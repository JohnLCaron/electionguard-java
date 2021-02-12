package com.sunya.electionguard.workflow;

import com.sunya.electionguard.Guardian;

/**
 * An interface for providing the complete set of Guardians obtained during the key ceremony.
 * These are used in the decryption stage.
 */
public interface GuardiansProvider {
  Iterable<Guardian> guardians();
}
