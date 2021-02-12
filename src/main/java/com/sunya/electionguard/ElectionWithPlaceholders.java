package com.sunya.electionguard;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.flogger.FluentLogger;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

/** An election with contests that have been filled out with selection placeholders. */
public class ElectionWithPlaceholders {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  public final Election.ElectionDescription election;
  public ImmutableList<ContestWithPlaceholders> contests;

  public ElectionWithPlaceholders(Election.ElectionDescription election) {
    this.election = Preconditions.checkNotNull(election);

    // For each contest, append the `number_elected` number of placeholder selections to the end of the contest collection.
    ImmutableList.Builder<ContestWithPlaceholders> builder = ImmutableList.builder();
    for (Election.ContestDescription contest : election.contests) {
      List<Election.SelectionDescription> placeholders = generate_placeholder_selections_from(contest, contest.number_elected);
      builder.add(contest_description_with_placeholders_from(contest, placeholders));
    }
    this.contests = builder.build();
  }

  static ContestWithPlaceholders contest_description_with_placeholders_from(
          Election.ContestDescription contest, List<Election.SelectionDescription> placeholders) {

    return new ContestWithPlaceholders(
            contest.object_id,
            contest.electoral_district_id,
            contest.sequence_order,
            contest.vote_variation,
            contest.number_elected,
            contest.votes_allowed.orElse(0), // LOOK
            contest.name,
            contest.ballot_selections,
            contest.ballot_title.orElse(null),
            contest.ballot_subtitle.orElse(null),
            placeholders);
  }

  /**
   * Generates the specified number of placeholder selections in ascending sequence order from the max selection sequence order
   * <p>
   * @param contest: ContestDescription for input
   * @param count: optionally specify a number of placeholders to generate
   * @return a collection of `SelectionDescription` objects, which may be empty
   */
  static List<Election.SelectionDescription> generate_placeholder_selections_from(Election.ContestDescription contest, int count) {
    //  max_sequence_order = max([selection.sequence_order for selection in contest.ballot_selections]);
    int max_sequence_order = contest.ballot_selections.stream().map(s -> s.sequence_order).max(Integer::compare).orElse(0);
    List<Election.SelectionDescription> selections = new ArrayList<>();
    for (int i = 0; i < count; i++) {
      int sequence_order = max_sequence_order + 1 + i;
      Optional<Election.SelectionDescription> sd = generate_placeholder_selection_from(contest, Optional.of(sequence_order));
      selections.add(sd.orElseThrow(IllegalStateException::new));
    }
    return selections;
  }

  /**
   * Generates a placeholder selection description that is unique so it can be hashed.
   *
   * @param use_sequence_idO: an optional integer unique to the contest identifying this selection's place in the contest
   * @return a SelectionDescription or None
   */
  static Optional<Election.SelectionDescription> generate_placeholder_selection_from(
          Election.ContestDescription contest, Optional<Integer> use_sequence_idO) {

    // sequence_ids = [selection.sequence_order for selection in contest.ballot_selections]
    List<Integer> sequence_ids = contest.ballot_selections.stream().map(s -> s.sequence_order).collect(Collectors.toList());

    int use_sequence_id;
    if (use_sequence_idO.isEmpty()) {
      // if no sequence order is specified, take the max
      use_sequence_id = sequence_ids.stream().max(Integer::compare).orElse(0) + 1;
    } else {
      use_sequence_id = use_sequence_idO.get();
      if (sequence_ids.contains(use_sequence_id)) {
        logger.atWarning().log("mismatched placeholder selection %s already exists", use_sequence_id);
        return Optional.empty();
      }
    }

    String placeholder_object_id = String.format("%s-%s", contest.object_id, use_sequence_id);
    return Optional.of(new Election.SelectionDescription(
            String.format("%s-placeholder", placeholder_object_id),
            String.format("%s-candidate", placeholder_object_id),
            use_sequence_id));
  }

  Optional<ContestWithPlaceholders> contest_for(String contest_id) {
    return contests.stream().filter(c -> c.object_id.equals(contest_id)).findFirst();
  }


  /** Find the ballot style for a specified ballot_style_id */
  public Optional<Election.BallotStyle> get_ballot_style(String ballot_style_id) {
    return election.ballot_styles.stream().filter(bs -> bs.object_id.equals(ballot_style_id)).findFirst();
  }

  /** Get contests that have the given ballot style. */
  public List<ContestWithPlaceholders> get_contests_for(String ballot_style_id) {
    Optional<Election.BallotStyle> style = this.get_ballot_style(ballot_style_id);
    if (style.isEmpty() || style.get().geopolitical_unit_ids.isEmpty()) {
      return new ArrayList<>();
    }
    List<String> gp_unit_ids = new ArrayList<>(style.get().geopolitical_unit_ids);
    return this.contests.stream().filter(c -> gp_unit_ids.contains(c.electoral_district_id)).collect(Collectors.toList());
  }

  /**
   * A contest thats been filled with placeholder_selections.
   * The ElectionGuard spec requires that n-of-m elections have *exactly* n counters that are one,
   * with the rest zero, so if a voter deliberately undervotes, one or more of the placeholder counters will
   * become one. This allows the `ConstantChaumPedersenProof` to verify correctly for undervoted contests.
   */
  @Immutable
  public static class ContestWithPlaceholders extends Election.ContestDescription {
    final ImmutableList<Election.SelectionDescription> placeholder_selections;

    public ContestWithPlaceholders(String object_id,
                                   String electoral_district_id,
                                   int sequence_order,
                                   Election.VoteVariationType vote_variation,
                                   int number_elected,
                                   int votes_allowed,
                                   String name,
                                   List<Election.SelectionDescription> ballot_selections,
                                   @Nullable Election.InternationalizedText ballot_title,
                                   @Nullable Election.InternationalizedText ballot_subtitle,
                                   List<Election.SelectionDescription> placeholder_selections) {

      super(object_id, electoral_district_id, sequence_order, vote_variation, number_elected, votes_allowed, name,
              ballot_selections, ballot_title, ballot_subtitle);
      this.placeholder_selections = toImmutableListEmpty(placeholder_selections);
    }

    boolean is_valid() {
      boolean contest_description_validates = super.is_valid();
      return contest_description_validates && this.placeholder_selections.size() == this.number_elected;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      if (!super.equals(o)) return false;
      ContestWithPlaceholders that = (ContestWithPlaceholders) o;
      return placeholder_selections.equals(that.placeholder_selections);
    }

    @Override
    public int hashCode() {
      return Objects.hash(super.hashCode(), placeholder_selections);
    }

    /**
     * Gets the description for a particular id
     * @param selection_id: Id of Selection
     * @return description
     */
    Optional<Election.SelectionDescription> selection_for(String selection_id) {

      Optional<Election.SelectionDescription> first_match = this.ballot_selections.stream()
              .filter(s -> s.object_id.equals(selection_id)).findFirst();
      if (first_match.isPresent()) {
        return first_match;
      }

      return this.placeholder_selections.stream()
              .filter(s -> s.object_id.equals(selection_id)).findFirst();
    }
  }

  private static <T> ImmutableList<T> toImmutableListEmpty(List<T> from) {
    if (from == null || from.isEmpty()) {
      return ImmutableList.of();
    }
    return ImmutableList.copyOf(from);
  }
}
