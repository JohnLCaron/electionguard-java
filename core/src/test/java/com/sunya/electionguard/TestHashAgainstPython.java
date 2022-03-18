package com.sunya.electionguard;

import com.google.common.collect.ImmutableList;
import com.sunya.electionguard.publish.ConvertFromJson;
import net.jqwik.api.Example;

import java.io.IOException;

import static com.google.common.truth.Truth.assertThat;

public class TestHashAgainstPython {
  public static final String topdirJsonPythonData = "src/test/data/python_data/";

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
  }
  @Example
  public void testBallotStyleHash() throws IOException {
    Group.setPrimes(ElectionConstants.LARGE_TEST_CONSTANTS);

    Manifest.BallotStyle bs = new Manifest.BallotStyle(
            "congress-district-7-hamilton-county",
            ImmutableList.of("hamilton-county", "congress-district-7"),
            ImmutableList.of(),
            null
            );

    System.out.printf("BallotStyle crypto_hash: %s%n", bs.cryptoHash().to_hex());

  }


}
