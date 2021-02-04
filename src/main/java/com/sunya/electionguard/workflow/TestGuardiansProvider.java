package com.sunya.electionguard.workflow;

import com.google.common.collect.ImmutableList;
import com.sunya.electionguard.Guardian;
import com.sunya.electionguard.proto.KeyCeremonyFromProto;

import java.io.IOException;

public class TestGuardiansProvider implements GuardiansProvider {
  private static final String WHERE = "/home/snake/tmp/electionguard/publishBallotEncryptor/private/guardians.proto";
  private Iterable<Guardian> guardians;

  @Override
  public Iterable<Guardian> guardians() {
    if (guardians == null) {
      guardians = read();
    }
    return guardians;
  }

  static ImmutableList<Guardian> read() {
    try {
      return KeyCeremonyFromProto.readGuardians(WHERE);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }
}