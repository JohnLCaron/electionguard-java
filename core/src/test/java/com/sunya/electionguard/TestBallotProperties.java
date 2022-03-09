package com.sunya.electionguard;

import net.jqwik.api.Example;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.ShrinkingMode;

import java.io.IOException;
import java.util.List;

import static com.google.common.truth.Truth.assertThat;

public class TestBallotProperties extends TestProperties {
  static BallotFactory factory = new BallotFactory();

  @Example
  public void test_ballots_is_valid() throws IOException {
    List<PlaintextBallot> originalPlaintextBallots = new BallotFactory().get_simple_ballots_from_file();
    assertThat(originalPlaintextBallots).isNotNull();
    assertThat(originalPlaintextBallots).isNotEmpty();
    assertThat(originalPlaintextBallots).hasSize(6);
  }

  @Example
  public void test_ballot_is_valid() throws IOException {
    PlaintextBallot subject = factory.get_simple_ballot_from_file();

    assertThat(subject.object_id()).isNotNull();
    assertThat(subject.object_id()).isEqualTo("some-external-id-string-123");
    assertThat(subject.is_valid("jefferson-county-ballot-style")).isTrue();

    PlaintextBallot.Contest first_contest = subject.contests.get(0);
    assertThat(first_contest).isNotNull();
    assertThat(first_contest.is_valid("justice-supreme-court", 2, 2, 2)).isTrue();
  }

  @Example
  public void test_ballots_are_valid() throws IOException {
    List<PlaintextBallot> ballots = factory.get_simple_ballots_from_file();
    for (PlaintextBallot subject : ballots) {
      assertThat(subject.object_id()).isNotNull();
      assertThat(subject.ballotStyleId).isAnyOf("jefferson-county-ballot-style", "harrison-township-ballot-style");

      for (PlaintextBallot.Contest contest : subject.contests) {
        assertThat(contest.contestId).isAnyOf("justice-supreme-court", "referendum-pineapple");
        for (PlaintextBallot.Selection selection : contest.selections) {
          // LOOK this was disabled because of None
          /* if (selection.selection_id.equals("write-in-selection")) {
            assertThat(selection.extended_data).isPresent();
            assertThat(selection.extended_data.get().value).isEqualTo("Susan B. Anthony");
          } else { */
            assertThat(selection.selectionId).isAnyOf("benjamin-franklin-selection", "john-hancock-selection",
                    "john-adams-selection", "john-adams-selection", "referendum-pineapple-affirmative-selection",
                    "referendum-pineapple-negative-selection", "write-in-selection");
          // }
          assertThat(selection.vote).isEqualTo(1);
        }
      }
    }
  }

  @Property(tries = 10, shrinking = ShrinkingMode.OFF)
  public void test_plaintext_ballot_selection_is_valid(
          @ForAll("get_selection_well_formed") PlaintextBallot.Selection selection) {
    assertThat(selection.is_valid(selection.selectionId)).isTrue();

    int as_int = selection.vote;
    assertThat(as_int >= 0 && as_int <= 1).isTrue();
  }

  @Property(tries = 10, shrinking = ShrinkingMode.OFF)
  public void test_plaintext_ballot_selection_is_invalid (
    @ForAll("get_selection_poorly_formed") PlaintextBallot.Selection selection) {
    assertThat(selection.is_valid(selection.selectionId)).isFalse();

    int as_int = selection.vote;
    assertThat(as_int >= 0 && as_int <= 1).isFalse();
  }

}
