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
    ContestWithPlaceholders description = new ContestWithPlaceholders(
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
    ContestWithPlaceholders description = new ContestWithPlaceholders(
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
