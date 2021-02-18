package com.sunya.electionguard.proto;

import com.google.common.collect.Iterables;
import com.sunya.electionguard.Guardian;
import com.sunya.electionguard.publish.Consumer;
import com.sunya.electionguard.workflow.SecretGuardiansProvider;
import net.jqwik.api.Example;
import net.jqwik.api.lifecycle.BeforeContainer;

import java.io.IOException;

import static com.google.common.truth.Truth.assertThat;

public class TestKeyCeremonyProtoRoundtrip {
  private static final String WHERE = "/home/snake/tmp/electionguard/publishWorkflowEncryptor/private/guardians.proto";

  @Example
  public void testGuardiansProvider() {
    // just a sanity check that it doesnt barf
    SecretGuardiansProvider provider = new SecretGuardiansProvider(WHERE);
    Iterable<Guardian> guardians = provider.guardians();
    assertThat(Iterables.size(guardians)).isGreaterThan(0);
  }
}
