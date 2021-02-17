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
  private static Consumer consumer;

  @BeforeContainer
  public static void setUp() throws IOException {
    consumer = new Consumer(TestElectionDescriptionToProtoRoundtrip.testElectionRecord);
  }

  @Example
  public void testGuardiansProvider() throws IOException {
    // just a sanity check that it doesnt barf
    SecretGuardiansProvider provider = new SecretGuardiansProvider();
    Iterable<Guardian> guardians = provider.guardians();
    assertThat(Iterables.size(guardians)).isGreaterThan(0);
  }
}
