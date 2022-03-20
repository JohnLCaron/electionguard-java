package com.sunya.electionguard;

import com.google.common.collect.ImmutableList;
import net.jqwik.api.Example;

import static com.google.common.truth.Truth.assertThat;
import static com.sunya.electionguard.InternalManifest.*;
import static com.sunya.electionguard.Manifest.SelectionDescription;
import static com.sunya.electionguard.Manifest.VoteVariationType;

public class TestInternalManifest {

  @Example
  public void test_contest_description_valid_input_succeeds() {
    Manifest.ContestDescription contest = new Manifest.ContestDescription(
            "0@A.com-contest",
            1,
            "0@A.com-gp-unit",
            VoteVariationType.n_of_m,
            1,
            1,
            "",
            ImmutableList.of(
                    new SelectionDescription(
                            "0@A.com-selection",
                            0,
                            "0@A.com", null),
                    new SelectionDescription(
                            "0@B.com-selection",
                            1,
                            "0@B.com", null)),
            null, null,
            ImmutableList.of(), null);

    ContestWithPlaceholders contestp = new ContestWithPlaceholders(
            contest,
            ImmutableList.of(
                    new SelectionDescription(
                            "0@A.com-contest-2-placeholder",
                            2,
                            "0@A.com-contest-2-candidate",
                            null)
            ));

    assertThat(contestp.is_valid()).isTrue();
  }

  @Example
  public void test_contest_description_invalid_input_fails() {
    Manifest.ContestDescription contest = new Manifest.ContestDescription(
            "0@A.com-contest",
            1,
            "0@A.com-gp-unit",
            VoteVariationType.n_of_m,
            1,
            1,
            "",
            ImmutableList.of(
                    new SelectionDescription(
                            "0@A.com-selection",
                            0,
                            "0@A.com", null),
                    // simulate a bad selection description input
                    new SelectionDescription(
                            "0@A.com-selection",
                            1,
                            "0@A.com", null)),
            null, null, ImmutableList.of(), null);

    ContestWithPlaceholders contestp = new ContestWithPlaceholders(
            contest,
            ImmutableList.of(
                    new SelectionDescription(
                            "0@A.com-contest-2-placeholder",
                            2,
                            "0@A.com-contest-2-candidate",
                            null)
            ));

    assertThat(contestp.is_valid()).isFalse();
  }


}
