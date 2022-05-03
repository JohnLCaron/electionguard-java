package com.sunya.electionguard;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

import javax.annotation.concurrent.Immutable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** A compact plaintext representation of a ballot minimized for data size */
@Immutable
public class CompactPlaintextBallot {
  private static final int YES_VOTE = 1;
  private static final int NO_VOTE = 0;

  final String object_id;
  final String style_id;
  final ImmutableList<Boolean> selections;
  final ImmutableMap<Integer, String> extended_data;

  public CompactPlaintextBallot(String object_id, String style_id, List<Boolean> selections,
                                Map<Integer, String> extended_data) {
    this.object_id = Preconditions.checkNotNull(object_id);
    this.style_id = Preconditions.checkNotNull(style_id);
    this.selections = ImmutableList.copyOf(selections);
    this.extended_data = ImmutableMap.copyOf(extended_data);
  }

  /** Compress a plaintext ballot into a compact plaintext ballot. */
  static CompactPlaintextBallot compress_plaintext_ballot(PlaintextBallot ballot) {
    List<Boolean> selections = get_compact_selections(ballot);
    Map<Integer, String> extended_data = get_compact_extended_data(ballot);
    return new CompactPlaintextBallot(ballot.object_id(), ballot.ballotStyleId, selections, extended_data);
  }

  private static List<Boolean> get_compact_selections(PlaintextBallot ballot) {
    ArrayList<Boolean> selections = new ArrayList<>();
    for (PlaintextBallot.Contest contest : ballot.contests) { // LOOK How do we guarantee order?
      for (PlaintextBallot.Selection selection: contest.selections) {
        selections.add(selection.vote == YES_VOTE);
      }
    }
    return selections;
  }

  private static Map<Integer, String> get_compact_extended_data(PlaintextBallot ballot) {
    Map<Integer, String> extended_data = new HashMap<>(); // why a map?

    int index = 0;
    for (PlaintextBallot.Contest contest : ballot.contests) { // LOOK How do we guarantee order?
      for (PlaintextBallot.Selection selection : contest.selections) {
        index += 1; // starts at 1 ?
          extended_data.put(index, selection.extendedData);
      }
    }
    return extended_data;
  }

  /** Expand a compact plaintext ballot into the original plaintext ballot. */
  static PlaintextBallot expand_compact_plaintext_ballot(
          CompactPlaintextBallot compact_ballot,
          InternalManifest internal_manifest) {
    return new PlaintextBallot(
            compact_ballot.object_id,
            compact_ballot.style_id,
            get_plaintext_contests(compact_ballot, internal_manifest),
            null);
  }

  /** Get ballot contests from compact plaintext ballot. */
  private static List<PlaintextBallot.Contest> get_plaintext_contests(
          CompactPlaintextBallot compact_ballot, InternalManifest internal_manifest) {

    Map<String, InternalManifest.ContestWithPlaceholders> ballot_style_contests = get_ballot_style_contests(
            compact_ballot.style_id, internal_manifest);

    List<InternalManifest.ContestWithPlaceholders> sortedContests = internal_manifest.contests.values().stream()
            .sorted(Comparator.comparing(o -> o.contest.sequenceOrder())).toList();

    int index = 0;
    List<PlaintextBallot.Contest> contests = new ArrayList<>();
    for (InternalManifest.ContestWithPlaceholders manifest_contest : sortedContests) {
      boolean placeholder = ballot_style_contests.get(manifest_contest.contest.contestId()) == null;

      List<Manifest.SelectionDescription> sortedSelections = manifest_contest.contest.selections().stream()
              .sorted(Comparator.comparing(o -> o.sequenceOrder())).toList();

      // Iterate through selections. If contest not in style, mark as placeholder
      List<PlaintextBallot.Selection> selections = new ArrayList<>();
      for (Manifest.SelectionDescription selection: sortedSelections) {
        selections.add(
                new PlaintextBallot.Selection(
                        selection.selectionId(),
                        selection.sequenceOrder(),
                        compact_ballot.selections.get(index) ? YES_VOTE : NO_VOTE,
                        compact_ballot.extended_data.get(index)));
        index += 1;
      }
      contests.add(new PlaintextBallot.Contest(
              manifest_contest.contest.contestId(),
              manifest_contest.contest.sequenceOrder(),
              selections));
    }
    return contests;
  }

  private static Map<String, InternalManifest.ContestWithPlaceholders> get_ballot_style_contests(
          String ballot_style_id, InternalManifest internal_manifest) {
    List<InternalManifest.ContestWithPlaceholders> ballot_style_contests =
            internal_manifest.get_contests_for_style(ballot_style_id);
    return ballot_style_contests.stream().collect(Collectors.toMap(c -> c.contest.contestId(), c -> c));
  }

}
