package com.sunya.electionguard;

import com.google.common.collect.ImmutableList;
import com.sunya.electionguard.publish.ConvertFromJson;
import com.sunya.electionguard.publish.Publisher;
import net.jqwik.api.Example;
import net.jqwik.api.lifecycle.AfterContainer;

import java.io.IOException;

import static com.google.common.truth.Truth.assertThat;
import static com.sunya.electionguard.publish.Publisher.Mode.createIfMissing;

public class TestHashAgainstPython {
  public static final String topdirJsonPythonData = "src/test/data/python_data/";
  public static final String output = "/home/snake/tmp/electionguard/TestHashAgainstPython/";
  private static final boolean writeOut = true;

  @AfterContainer
  public static void cleanup() {
    Group.setPrimes(ElectionConstants.STANDARD_CONSTANTS);
  }

  @Example
  public void testManifestHash() throws IOException {
    Group.setPrimes(ElectionConstants.LARGE_TEST_CONSTANTS);
    Manifest subject = ConvertFromJson.readManifest(
            topdirJsonPythonData + "hamilton-county/election_manifest.json");
    assertThat(subject).isNotNull();
    assertThat(subject.is_valid()).isTrue();

    System.out.printf("Manifest crypto_hash: %s%n", subject.cryptoHash().to_hex());
    System.out.printf("     start_date: %s%n", subject.startDate().format(Utils.dtf));
    System.out.printf("     end_date: %s%n", subject.endDate().format(Utils.dtf));

    for (Manifest.ContestDescription contest : subject.contests()) {
      System.out.printf("  Contest %s crypto_hash: %s%n",
              contest.contestId(), contest.cryptoHash().to_hex());
      for (Manifest.SelectionDescription selection : contest.selections()) {
        System.out.printf("    Selection %s crypto_hash: %s%n",
                selection.selectionId(), selection.cryptoHash().to_hex());
      }
    }
    assertThat(subject.cryptoHash().to_hex()).isEqualTo("0369");
  }

  @Example
  public void testWriteManifestHash() throws IOException {
    ElectionConstants constants = ElectionConstants.STANDARD_CONSTANTS;
    Group.setPrimes(constants);
    Manifest subject = ConvertFromJson.readManifest(
            topdirJsonPythonData + "hamilton-county/election_manifest.json");
    assertThat(subject).isNotNull();
    assertThat(subject.is_valid()).isTrue();

    System.out.printf("Manifest crypto_hash: %s%n", subject.cryptoHash().to_hex());
    System.out.printf("     start_date: %s%n", subject.startDate().format(Utils.dtf));
    System.out.printf("     end_date: %s%n", subject.endDate().format(Utils.dtf));

    for (Manifest.ContestDescription contest : subject.contests()) {
      System.out.printf("  Contest %s crypto_hash: %s%n",
              contest.contestId(), contest.cryptoHash().to_hex());
      for (Manifest.SelectionDescription selection : contest.selections()) {
        System.out.printf("    Selection %s crypto_hash: %s%n",
                selection.selectionId(), selection.cryptoHash().to_hex());
      }
    }

    if (writeOut) {
      Publisher publisher = new Publisher(output, createIfMissing, false);
      publisher.writeStartingProto(subject, constants);
    }
  }

  @Example
  public void testBallotStyleHash() {
    Group.setPrimes(ElectionConstants.LARGE_TEST_CONSTANTS);
    Manifest.BallotStyle bs = new Manifest.BallotStyle(
            "congress-district-7-hamilton-county",
            ImmutableList.of("hamilton-county", "congress-district-7"),
            ImmutableList.of(),
            null
            );

    System.out.printf("BallotStyle crypto_hash: %s%n", bs.cryptoHash().to_hex());
    assertThat(bs.cryptoHash().to_hex()).isEqualTo("586E");
  }


}
