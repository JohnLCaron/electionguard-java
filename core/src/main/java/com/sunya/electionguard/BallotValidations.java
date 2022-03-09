package com.sunya.electionguard;

import com.google.common.flogger.FluentLogger;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.sunya.electionguard.InternalManifest.ContestWithPlaceholders;

/** Static helper methods for ballot validation. */
class BallotValidations {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  /** Determine if a ballot is valid for this election . */
  // LOOK why ElectionDescriptionWithPlaceholders?
  static boolean ballot_is_valid_for_election(
          CiphertextBallot ballot,
          InternalManifest manifest,
          ElectionContext context) {

    if (!ballot_is_valid_for_style(ballot, manifest)) {
      return false;
    }

    if (!ballot.is_valid_encryption(
            manifest.manifest.cryptoHash(),
            context.jointPublicKey,
            context.cryptoExtendedBaseHash)) {
      logger.atInfo().log("ballot_is_valid_for_election: mismatching ballot encryption %s", ballot.object_id());
      return false;
    }
    return true;
  }

  /** Determine if selection is valid for ballot style. */
  static boolean selection_is_valid_for_style(
          CiphertextBallot.Selection selection, Manifest.SelectionDescription description) {

    if (!selection.description_hash().equals(description.cryptoHash())) {
      logger.atInfo().log("ballot is not valid for style: mismatched selection description hash %s for selection %s hash %s",
              selection.description_hash(), description.selectionId(), description.cryptoHash());
      return false;
    }

    return true;
  }

  /** Determine if contest is valid for ballot style. */
  static boolean contest_is_valid_for_style(CiphertextBallot.Contest contest, ContestWithPlaceholders contestp) {
    Manifest.ContestDescription contestm = contestp.contest;
    // verify the hash matches
    if (!contest.contestHash.equals(contestm.cryptoHash())) {
      logger.atInfo().log("ballot is not valid for style: mismatched description hash %s for contest %s hash %s",
              contest.contestHash, contestm.contestId(), contestm.cryptoHash());
      return false;
    }

    //  verify the placeholder count
    if (contest.selections.size() !=
            (contestm.selections().size()) + contestp.placeholder_selections.size()) {
      logger.atInfo().log("ballot is not valid for style: mismatched selection count for contest %s",
              contestm.contestId());
      return false;
    }
    return true;
  }

  /** Determine if ballot is valid for ballot style. */
  static boolean ballot_is_valid_for_style(CiphertextBallot ballot, InternalManifest manifest) {
    Map<String, CiphertextBallot.Contest> contestMap = ballot.contests.stream().collect(Collectors.toMap(c -> c.contestId, c -> c));

    List<ContestWithPlaceholders> contests = manifest.get_contests_for_style(ballot.ballotStyleId);
    for (ContestWithPlaceholders contestp : contests) {
      Manifest.ContestDescription contestm = contestp.contest;
      CiphertextBallot.Contest use_contest = contestMap.get(contestm.contestId());

      // verify the contest exists on the ballot LOOK we reject ballots that dont have all contests on them.
      if (use_contest == null) {
        logger.atInfo().log("ballot is not valid for style: mismatched contest %s", contestm.contestId());
        return false;
      }

      if (!contest_is_valid_for_style(use_contest, contestp)) {
        return false;
      }

      // verify the selection metadata
      for (Manifest.SelectionDescription selection_description : contestm.selections()) {
        CiphertextBallot.Selection use_selection = null;
        for (CiphertextBallot.Selection selection : use_contest.selections) {
          if (selection_description.selectionId().equals(selection.object_id())) {
            use_selection = selection;
            break;
          }
        }

        if (use_selection == null) {
          logger.atInfo().log("ballot is not valid for style: missing selection %s", selection_description.selectionId());
          return false;
        }

        if (!selection_is_valid_for_style(use_selection, selection_description)) {
          return false;
        }
      }
    }
    return true;
  }
}
