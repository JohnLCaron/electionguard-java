package com.sunya.electionguard.publish;

import com.sunya.electionguard.Group;

public interface ElectionContext {

  /** The number of guardians in the key ceremony. */
  int numberOfGuardians();

  /** The quorum of guardians needed to decrypt. */
  int quorum();

  /** The extended base hash, Qbar in the spec. */
  Group.ElementModQ extendedHash();

  /** Joint election key, K in the spec. */
  Group.ElementModP electionPublicKey();

}
