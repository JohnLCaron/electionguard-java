package com.sunya.electionguard.workflow;

import com.sunya.electionguard.Guardian;

/** Externally provided guardians, used in the decryption stage. */
public interface GuardiansProvider {
  Iterable<Guardian> guardians();
}
