package com.sunya.electionguard.input;

import com.google.common.flogger.FluentLogger;
import com.sunya.electionguard.Manifest;

import java.util.ArrayList;
import java.util.Formatter;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

/** Validate an election manifest, give human readable error information. */
public class ManifestInputValidation {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final Manifest election;
  private final Set<String> gpUnits;
  private final Set<String> candidates;
  private final Set<String> parties;

  public ManifestInputValidation(Manifest election) {
    this.election = election;
    this.gpUnits = election.geopolitical_units().stream().map(gp -> gp.object_id()).collect(Collectors.toSet());
    this.candidates = election.candidates().stream().map(c -> c.object_id()).collect(Collectors.toSet());
    this.parties = election.parties().stream().map(p -> p.object_id()).collect(Collectors.toSet());
  }

  /** Determine if a ballot is valid and well-formed for the given election. */
  public boolean validateElection(Formatter problems) {
    Messes ballotMesses = new Messes(election.election_scope_id());

    // Referential integrity of BallotStyle geopolitical_unit_ids
    for (Manifest.BallotStyle ballotStyle : election.ballot_styles()) {
      for (String gpunit : ballotStyle.geopolitical_unit_ids()) {
        if (!gpUnits.contains(gpunit)) {
          String msg = String.format("Manifest.A.1 BallotStyle '%s' has geopolitical_unit_id '%s' that does not exist in election's geopolitical_units",
                  ballotStyle.object_id(), gpunit);
          ballotMesses.add(msg);
          logger.atWarning().log(msg);
        }
      }
    }

    // Referential integrity of Candidate party_id
    for (Manifest.Candidate candidate : election.candidates()) {
      if (candidate.party_id() != null) {
        if (!parties.contains(candidate.party_id())) {
          String msg = String.format("Manifest.A.2 Candidate '%s' party_id '%s' does not exist in election's Parties",
                  candidate.object_id(), candidate.party_id());
          ballotMesses.add(msg);
          logger.atWarning().log(msg);
        }
      }
    }

    Set<String> contestIds = new HashSet<>();
    Set<Integer> contestSeqs = new HashSet<>();
    Set<String> selectionIds = new HashSet<>();
    for (Manifest.ContestDescription electionContest : election.contests()) {
      // No duplicate contest object_id
      if (contestIds.contains(electionContest.object_id())) {
        String msg = String.format("Manifest.B.1 Multiple Contests have same id '%s'", electionContest.object_id());
        ballotMesses.add(msg);
        logger.atWarning().log(msg);
      } else {
        contestIds.add(electionContest.object_id());
      }

      // No duplicate contest sequence
      if (contestSeqs.contains(electionContest.sequence_order())) {
        String msg = String.format("Manifest.B.2 Multiple Contests have same sequence order %d", electionContest.sequence_order());
        ballotMesses.add(msg);
        logger.atWarning().log(msg);
      } else {
        contestSeqs.add(electionContest.sequence_order());
      }

      // No duplicate sequenceIds across contests
      for (Manifest.SelectionDescription electionSelection : electionContest.ballot_selections()) {
        if (selectionIds.contains(electionSelection.object_id())) {
          String msg = String.format("Manifest.B.6 All SelectionDescription have a unique object_id within the election %s", electionContest.object_id());
          ballotMesses.add(msg);
          logger.atWarning().log(msg);
        }
        selectionIds.add(electionSelection.object_id());
      }

      validateContest(electionContest, ballotMesses);
    }

    return ballotMesses.makeMesses(problems);
  }

  /** Determine if contest is valid for ballot style. */
  void validateContest(Manifest.ContestDescription contest, Messes ballotMesses) {
    Messes contestMesses = ballotMesses.nested(contest.object_id());

    // Referential integrity of Contest electoral_district_id
    if (!gpUnits.contains(contest.electoral_district_id())) {
      String msg = String.format("Manifest.A.3 Contest's electoral_district_id '%s' does not exist in election's geopolitical_units",
              contest.electoral_district_id());
      contestMesses.add(msg);
      logger.atWarning().log(msg);
    }

    Set<String> selectionIds = new HashSet<>();
    Set<Integer> selectionSeqs = new HashSet<>();
    Set<String> candidateIds = new HashSet<>();
    for (Manifest.SelectionDescription electionSelection : contest.ballot_selections()) {
      // No duplicate selection ids
      if (selectionIds.contains(electionSelection.object_id())) {
        String msg = String.format("Manifest.B.3 Multiple Selections have same id '%s'", electionSelection.object_id());
        contestMesses.add(msg);
        logger.atWarning().log(msg);
      } else {
        selectionIds.add(electionSelection.object_id());
      }

      // No duplicate selection sequence_order
      if (selectionSeqs.contains(electionSelection.sequence_order())) {
        String msg = String.format("Manifest.B.4 Multiple Selections have same sequence %d", electionSelection.sequence_order());
        contestMesses.add(msg);
        logger.atWarning().log(msg);
      } else {
        selectionSeqs.add(electionSelection.sequence_order());
      }

      // No duplicate selection candidates
      if (candidateIds.contains(electionSelection.candidate_id())) {
        String msg = String.format("Manifest.B.5 Multiple Selections have same candidate id '%s'", electionSelection.candidate_id());
        contestMesses.add(msg);
        logger.atWarning().log(msg);
      } else {
        candidateIds.add(electionSelection.candidate_id());
      }

      // Referential integrity of Selection candidate ids
      if (!candidates.contains(electionSelection.candidate_id())) {
        String msg = String.format("Manifest.A.4 Ballot Selection '%s' candidate_id '%s' does not exist in election's Candidates",
                electionSelection.object_id(), electionSelection.candidate_id());
        contestMesses.add(msg);
        logger.atWarning().log(msg);
      }
    }
  }

  private static class Messes {
    private final String id;
    private final ArrayList<String> messages = new ArrayList<>();
    private final ArrayList<Messes> nested = new ArrayList<>();

    public Messes(String id) {
      this.id = id;
    }

    void add(String mess) {
      messages.add(mess);
    }

    Messes nested(String id) {
      Messes mess = new Messes(id);
      nested.add(mess);
      return mess;
    }

    boolean makeMesses(Formatter problems) {
      if (hasProblem()) {
        problems.format("Manifest '%s' has problems%n", id);
        for (String mess : messages) {
          problems.format("  %s%n", mess);
        }
        for (Messes nest : nested) {
          if (nest.hasProblem()) {
            problems.format("  Contest '%s' has problems%n", nest.id);
            for (String mess : nest.messages) {
              problems.format("    %s%n", mess);
            }
          }
        }
        problems.format("==============================%n");
        return false;
      }
      return true;
    }

    boolean hasProblem() {
      return !messages.isEmpty() || nested.stream().map(Messes::hasProblem).findAny().orElse(false);
    }
  }

}
