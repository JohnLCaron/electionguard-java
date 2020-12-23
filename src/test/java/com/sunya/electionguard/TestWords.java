package com.sunya.electionguard;

import net.jqwik.api.Example;

import java.util.Optional;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth8.assertThat;
import static com.sunya.electionguard.Words.*;


public class TestWords {

  @Example
  public void test_get_word() {
    int INDEX_MIN = 0;
    int INDEX_RANDOM_1 = 100;
    int INDEX_RANDOM_2 = 1000;
    int INDEX_MAX = 4095;

    String word_min = get_word(INDEX_MIN).get();
    String word_random_1 = get_word(INDEX_RANDOM_1).get();
    String word_random_2 = get_word(INDEX_RANDOM_2).get();
    String word_max = get_word(INDEX_MAX).get();
    int reverse_find_of_index_random_1 = get_index_from_word(word_random_1).get();

    assertThat(word_min).isEqualTo("aardvark");
    assertThat(word_random_1).isEqualTo("alpaca");
    assertThat(word_random_2).isEqualTo("dividend");
    assertThat(word_max).isEqualTo("system");
    assertThat(INDEX_RANDOM_1).isEqualTo(reverse_find_of_index_random_1);
  }

  @Example
  public void test_get_word_when_out_of_range() {
    int INDEX_BELOW_MIN = -1;
    int INDEX_ABOVE_MAX = 4096;

    Optional<String> word_past_min = get_word(INDEX_BELOW_MIN);
    Optional<String> word_past_max = get_word(INDEX_ABOVE_MAX);

    assertThat(word_past_min).isEmpty();
    assertThat(word_past_max).isEmpty();
  }

  @Example
  public void test_get_index_of_word_not_in_list() {
    String FAILING_WORD = "thiswordshouldfail";

    Optional<Integer> failed_index = get_index_from_word(FAILING_WORD);

    assertThat(failed_index).isEmpty();
  }
}
