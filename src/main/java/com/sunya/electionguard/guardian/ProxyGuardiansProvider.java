package com.sunya.electionguard.guardian;

/**
 * An interface for providing the complete set of Guardians obtained during the key ceremony.
 * These are used in the decryption stage.
 */
public interface ProxyGuardiansProvider {
  Iterable<DecryptingTrusteeProxy> guardians();
}
