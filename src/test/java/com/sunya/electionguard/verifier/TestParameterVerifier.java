package com.sunya.electionguard.verifier;

import com.sunya.electionguard.publish.Consumer;
import net.jqwik.api.Example;

import java.io.IOException;

import static com.google.common.truth.Truth.assertThat;

public class TestParameterVerifier {
  // public static final String topdir = "~/tmp/testEndToEnd";
  public static final String topdir = "src/test/data/python-modified";

  @Example
  public void testJavaGenerated() throws IOException {
    Consumer consumer = new Consumer(topdir);
    ElectionParameters electionParameters = new ElectionParameters(consumer);
    ParameterVerifier blv = new ParameterVerifier(electionParameters);
    boolean blvOk = blv.verify_all_params();
    assertThat(blvOk).isTrue();
  }
}
