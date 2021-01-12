package com.sunya.electionguard.verifier;

import com.sunya.electionguard.publish.Consumer;
import net.jqwik.api.Example;

import java.io.IOException;

import static com.google.common.truth.Truth.assertThat;

public class TestParameterValidator {

  @Example
  public void testJavaGenerated() throws IOException {
    String topdir = "src/test/data/python-modified";

    // set up
    Consumer consumer = new Consumer(topdir);
    ElectionParameters electionParameters = new ElectionParameters(consumer);
    System.out.println("set up finished. ");

    // baseline parameter check
    System.out.println(" ------------ [box 1] baseline parameter check ------------");
    ParameterVerifier blv = new ParameterVerifier(electionParameters);
    boolean blvOk = blv.verify_all_params();
    assertThat(blvOk).isTrue();
  }
}
