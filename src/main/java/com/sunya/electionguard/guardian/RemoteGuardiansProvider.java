package com.sunya.electionguard.guardian;

import com.google.common.collect.ImmutableList;
import com.sunya.electionguard.proto.TrusteeFromProto;

import java.io.IOException;

/**
 * A GuardiansProvider using serialized Guardian objects from a "secret" place on disk.
 */
public class RemoteGuardiansProvider implements ProxyGuardiansProvider {
  private final String location;
  private Iterable<RemoteTrustee.DecryptorProxy> guardians;

  public RemoteGuardiansProvider(String location) {
    this.location = location;
  }

  @Override
  public Iterable<RemoteTrustee.DecryptorProxy> guardians() {
    if (guardians == null) {
      guardians = read(location);
    }
    return guardians;
  }

  static ImmutableList<RemoteTrustee.DecryptorProxy> read(String location) {
    try {
      return TrusteeFromProto.readTrustees(location);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }
}