package com.sunya.electionguard;

import com.google.common.collect.ImmutableList;
import net.jqwik.api.Example;

import java.io.IOException;

import static com.google.common.truth.Truth.assertThat;
import static com.sunya.electionguard.Election.*;

public class TestElection {

  @Example
  public void test_simple_election_is_valid() throws IOException {
    ElectionDescription subject = ElectionFactory.get_simple_election_from_file();
    assertThat(subject.election_scope_id).isNotNull();
    assertThat(subject.election_scope_id).isEqualTo("jefferson-county-primary");
    assertThat(subject.is_valid()).isTrue();
  }

  @Example
  public void test_election_has_deterministic_hash() throws IOException {
    ElectionDescription subject1 = ElectionFactory.get_simple_election_from_file();
    ElectionDescription subject2 = ElectionFactory.get_simple_election_from_file();

    assertThat(subject1.crypto_hash()).isEqualTo(subject2.crypto_hash());
  }

  @Example
  public void test_election_from_file_generates_consistent_internal_description_contest_hashes() throws IOException {
    ElectionDescription comparator = ElectionFactory.get_simple_election_from_file();
    InternalElectionDescription subject = new InternalElectionDescription(comparator);

    assertThat(comparator.contests.size()).isEqualTo(subject.contests.size());

    for (ContestDescription expected : comparator.contests) {
      for (ContestDescription actual : subject.contests) {
        if (expected.object_id.equals(actual.object_id)) {
          assertThat(expected.crypto_hash()).isEqualTo(actual.crypto_hash());
        }
      }
    }
  }

  @Example
  public void test_contest_description_valid_input_succeeds() {
    ContestDescriptionWithPlaceholders description = new ContestDescriptionWithPlaceholders(
            "0@A.com-contest",
            "0@A.com-gp-unit",
            1,
            VoteVariationType.n_of_m,
            1,
            1,
            "",
            ImmutableList.of(
                    new SelectionDescription(
                            "0@A.com-selection",
                            "0@A.com",
                            0),
                    new SelectionDescription(
                            "0@B.com-selection",
                            "0@B.com",
                            1)),
            null, null,
            ImmutableList.of(
                    new SelectionDescription(
                            "0@A.com-contest-2-placeholder",
                            "0@A.com-contest-2-candidate",
                            2)
            ));

    assertThat(description.is_valid()).isTrue();
  }

  @Example
  public void test_contest_description_invalid_input_fails() {
    ContestDescriptionWithPlaceholders description = new ContestDescriptionWithPlaceholders(
            "0@A.com-contest",
            "0@A.com-gp-unit",
            1,
            VoteVariationType.n_of_m,
            1,
            1,
            "",
            ImmutableList.of(
                    new SelectionDescription(
                            "0@A.com-selection",
                            "0@A.com",
                            0),
                    // simulate a bad selection description input
                    new SelectionDescription(
                            "0@A.com-selection",
                            "0@A.com",
                            1)),
            null, null,
            ImmutableList.of(
                    new SelectionDescription(
                            "0@A.com-contest-2-placeholder",
                            "0@A.com-contest-2-candidate",
                            2)
            ));

    assertThat(description.is_valid()).isFalse();
  }


}
