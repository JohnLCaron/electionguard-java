package com.sunya.electionguard.standard;

import com.google.common.collect.ImmutableList;

/**
 * A GuardiansProvider using serialized Guardian objects from a "secret" place on disk.
 */
class SecretGuardiansProvider implements GuardiansProvider {
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