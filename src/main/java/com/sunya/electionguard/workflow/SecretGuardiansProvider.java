package com.sunya.electionguard.workflow;

import com.google.common.collect.ImmutableList;
import com.sunya.electionguard.Guardian;

/**
 * A GuardiansProvider using serialized Guardian objects from a "secret" place on disk.
 */
public class SecretGuardiansProvider implements GuardiansProvider {
  private final String location;
  private Iterable<Guardian> guardians;

  public SecretGuardiansProvider(String location) {
    this.location = location;
  }

  @Override
  public Iterable<Guardian> guardians() {
    if (guardians == null) {
      guardians = read(location);
    }
    return guardians;
  }

  static ImmutableList<Guardian> read(String location) {
      return null; // KeyCeremonyFromProto.readGuardians(location);
  }
}