package com.sunya.electionguard;

import net.jqwik.api.Example;

import java.io.IOException;

import static com.google.common.truth.Truth.assertThat;

public class TestManifest {

  @Example
  public void test_simple_election_is_valid() throws IOException {
    Manifest subject = ElectionFactory.get_simple_election_from_file();
    assertThat(subject.election_scope_id).isNotNull();
    assertThat(subject.election_scope_id).isEqualTo("jefferson-county-primary");
    assertThat(subject.is_valid()).isTrue();
  }

  @Example
  public void test_election_has_deterministic_hash() throws IOException {
    Manifest subject1 = ElectionFactory.get_simple_election_from_file();
    Manifest subject2 = ElectionFactory.get_simple_election_from_file();

    assertThat(subject1.crypto_hash()).isEqualTo(subject2.crypto_hash());
  }

  @Example
  public void test_election_from_file_generates_consistent_internal_description_contest_hashes() throws IOException {
    Manifest election = ElectionFactory.get_simple_election_from_file();
    InternalManifest metadata = new InternalManifest(election);

    assertThat(election.contests.size()).isEqualTo(election.contests.size());
    for (Manifest.ContestDescription expected : election.contests) {
      Manifest.ContestDescription actual = metadata.getContestById(expected.object_id).orElseThrow();
      assertThat(expected.crypto_hash()).isEqualTo(actual.crypto_hash());
    }
  }

}
