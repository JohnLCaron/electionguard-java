package com.sunya.electionguard.verifier;

import com.sunya.electionguard.publish.Consumer;
import net.jqwik.api.Example;

import java.io.IOException;

import static com.google.common.truth.Truth.assertThat;

public class TestParameterVerifier {
  // public static final String topdir = "src/test/data/testEndToEnd/";
  public static final String topdir ="/home/snake/tmp/testEndToEnd";

  @Example
  public void testJavaGenerated() throws IOException {
    Consumer consumer = new Consumer(topdir);
    ParameterVerifier blv = new ParameterVerifier(consumer.getElectionRecord());
    boolean blvOk = blv.verify_all_params();
    assertThat(blvOk).isTrue();
  }
}
