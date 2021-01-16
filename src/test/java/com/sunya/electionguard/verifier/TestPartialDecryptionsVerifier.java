package com.sunya.electionguard.verifier;

import com.sunya.electionguard.publish.Consumer;
import net.jqwik.api.Example;
import net.jqwik.api.lifecycle.BeforeContainer;

import java.io.IOException;

import static com.google.common.truth.Truth.assertThat;

public class TestPartialDecryptionsVerifier {
  static ElectionParameters electionParameters;
  static Consumer consumer;
  static PartialDecryptionsVerifier validator;
  static Grp grp;

  @BeforeContainer
  public static void setUp() throws IOException {
    String topdir = "src/test/data/python-modified";
    // String topdir = "/home/snake/tmp/testEndToEnd";

    // set up
    consumer = new Consumer(topdir);
    electionParameters = new ElectionParameters(consumer);
    System.out.println("set up finished. ");

    System.out.println(" ------------ [box 3, 4, 5] ballot encryption check ------------");
    validator = new PartialDecryptionsVerifier(electionParameters, consumer);
    grp = new Grp(electionParameters.large_prime(), electionParameters.small_prime());
  }

  @Example
  public void testSelectionEncryptionValidation() throws IOException {
    boolean sevOk = validator.verify_cast_ballot_tallies();
    assertThat(sevOk).isTrue();
  }

  @Example
  public void testSelectionSpoiledBallots() throws IOException {
    boolean sevOk = validator.verify_spoiled_ballots();
    assertThat(sevOk).isTrue();
  }
}