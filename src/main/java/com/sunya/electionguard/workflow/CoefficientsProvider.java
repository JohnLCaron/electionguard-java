package com.sunya.electionguard.workflow;

import static com.sunya.electionguard.KeyCeremony.CoefficientSet;

/**
 * An interface for providing the Guardian's secret polynomial coefficients to electionguard.
 * The Guardian objects are created from these.
 */
public interface CoefficientsProvider {
  int quorum();
  Iterable<CoefficientSet> guardianCoefficients();
}
