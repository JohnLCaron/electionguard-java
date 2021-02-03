package com.sunya.electionguard.workflow;

import com.sunya.electionguard.Guardian;

/** Externally provided guardians. */
public interface GuardianProvider {
  int quorum();
  Iterable<Guardian> guardians();
}
