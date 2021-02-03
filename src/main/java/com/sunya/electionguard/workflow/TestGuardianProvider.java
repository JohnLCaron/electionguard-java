package com.sunya.electionguard.workflow;

import com.sunya.electionguard.Guardian;

import java.util.ArrayList;

public class TestGuardianProvider implements GuardianProvider {
  private static final int NUMBER_OF_GUARDIANS = 6;
  private static final int QUORUM = 5;

  @Override
  public int quorum() {
    return QUORUM;
  }

  @Override
  public Iterable<Guardian> guardians() {
    ArrayList<Guardian> guardians = new ArrayList<>();
    for (int i = 0; i < NUMBER_OF_GUARDIANS; i++) {
      int sequence = i + 1;
      guardians.add(Guardian.createForTesting("guardian_" + sequence, sequence, NUMBER_OF_GUARDIANS, QUORUM, null));
    }
    return guardians;
  }
}
