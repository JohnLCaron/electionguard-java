package com.sunya.electionguard.workflow;

import static com.sunya.electionguard.KeyCeremony.CoefficientSet;

/** Externally provided guardians. */
public interface CoefficientsProvider {
  int quorum();
  Iterable<CoefficientSet> guardianCoefficients();
}
