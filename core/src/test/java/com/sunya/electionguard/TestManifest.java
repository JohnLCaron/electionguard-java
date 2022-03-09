package com.sunya.electionguard;

import net.jqwik.api.Example;

import java.io.IOException;

import static com.google.common.truth.Truth.assertThat;

public class TestManifest {

  @Example
  public void test_simple_election_is_valid() throws IOException {
    Manifest subject = ElectionFactory.get_simple_election_from_file();
    assertThat(subject.electionScopeId()).isNotNull();
    assertThat(subject.electionScopeId()).isEqualTo("jefferson-county-primary");
    assertThat(subject.is_valid()).isTrue();
  }

  @Example
  public void test_election_has_deterministic_hash() throws IOException {
    Manifest subject1 = ElectionFactory.get_simple_election_from_file();
    Manifest subject2 = ElectionFactory.get_simple_election_from_file();

    assertThat(subject1.cryptoHash()).isEqualTo(subject2.cryptoHash());
  }

  @Example
  public void test_election_from_file_generates_consistent_internal_description_contest_hashes() throws IOException {
    Manifest election = ElectionFactory.get_simple_election_from_file();
    InternalManifest metadata = new InternalManifest(election);

    assertThat(election.contests().size()).isEqualTo(election.contests().size());
    for (Manifest.ContestDescription expected : election.contests()) {
      InternalManifest.ContestWithPlaceholders actual = metadata.getContestById(expected.contestId()).orElseThrow();
      assertThat(actual.contest.cryptoHash()).isEqualTo(expected.cryptoHash());
    }
  }

}
