package com.sunya.electionguard;

import net.jqwik.api.Example;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.ShrinkingMode;
import org.junit.Test;

import java.util.List;
import java.util.Optional;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth8.assertThat;
import static com.sunya.electionguard.Ballot.*;

public class TestBallotProperties extends TestProperties {
  static BallotFactory factory = new BallotFactory();

  @Example
  public void test_ballot_is_valid() {
    Ballot.PlaintextBallot subject = factory.get_simple_ballot_from_file();

    assertThat(subject.object_id).isNotNull();
    assertThat(subject.object_id).isEqualTo("some-external-id-string-123");
    assertThat(subject.is_valid("jefferson-county-ballot-style")).isTrue();

    PlaintextBallotContest first_contest = subject.contests.get(0);
    assertThat(first_contest).isNotNull();
    assertThat(first_contest.is_valid("justice-supreme-court", 2, 2, Optional.empty())).isTrue();
  }

  @Example
  public void test_ballots_are_valid() {
    List<PlaintextBallot> ballots = factory.get_simple_ballots_from_file();
    for (PlaintextBallot subject : ballots) {
      assertThat(subject.object_id).isNotNull();
      assertThat(subject.ballot_style).isAnyOf("jefferson-county-ballot-style", "harrison-township-ballot-style");

      for (PlaintextBallotContest contest : subject.contests) {
        assertThat(contest.object_id).isAnyOf("justice-supreme-court", "referendum-pineapple");
        for (PlaintextBallotSelection selection : contest.ballot_selections) {
          if (selection.object_id.equals("write-in-selection")) {
            assertThat(selection.extended_data).isPresent();
            assertThat(selection.extended_data.get().value).isEqualTo("Susan B. Anthony");
          } else {
            assertThat(selection.object_id).isAnyOf("benjamin-franklin-selection", "john-hancock-selection",
                    "john-adams-selection", "john-adams-selection", "referendum-pineapple-affirmative-selection",
                    "referendum-pineapple-negative-selection");
          }
          assertThat(selection.vote).isEqualTo("True");
        }
      }
    }
  }

  @Property(tries = 10, shrinking = ShrinkingMode.OFF)
  public void test_plaintext_ballot_selection_is_valid(
          @ForAll("get_selection_well_formed") PlaintextBallotSelection selection) {
    assertThat(selection.is_valid(selection.object_id)).isTrue();

    int as_int = selection.to_int();
    assertThat(as_int >= 0 && as_int <= 1).isTrue();
  }

  @Property(tries = 10, shrinking = ShrinkingMode.OFF)
  public void test_plaintext_ballot_selection_is_invalid (
    @ForAll("get_selection_poorly_formed") PlaintextBallotSelection selection) {
    assertThat(selection.is_valid(selection.object_id)).isFalse();

    int as_int = selection.to_int();
    assertThat(as_int >= 0 && as_int <= 1).isFalse();
  }

}
