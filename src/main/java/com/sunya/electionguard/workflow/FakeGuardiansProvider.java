package com.sunya.electionguard.workflow;

import com.google.common.collect.ImmutableList;
import com.sunya.electionguard.Guardian;
import com.sunya.electionguard.proto.KeyCeremonyFromProto;

import java.io.IOException;

/**
 * A GuardiansProvider using serialized Guardian objects from a "secret" place on disk.
 * Naive implementation for testing. Do not use in production.
 */
public class FakeGuardiansProvider implements GuardiansProvider {
  private static final String WHERE = "/home/snake/tmp/electionguard/publishWorkflowEncryptor/private/guardians.proto";
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