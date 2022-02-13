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
          CiphertextElectionContext context) {

    if (!ballot_is_valid_for_style(ballot, manifest)) {
      return false;
    }

    if (!ballot.is_valid_encryption(
            manifest.manifest.crypto_hash(),
            context.elgamal_public_key,
            context.crypto_extended_base_hash)) {
      logger.atInfo().log("ballot_is_valid_for_election: mismatching ballot encryption %s", ballot.object_id());
      return false;
    }
    return true;
  }

  /** Determine if selection is valid for ballot style. */
  static boolean selection_is_valid_for_style(
          CiphertextBallot.Selection selection, Manifest.SelectionDescription description) {

    if (!selection.description_hash().equals(description.crypto_hash())) {
      logger.atInfo().log("ballot is not valid for style: mismatched selection description hash %s for selection %s hash %s",
              selection.description_hash(), description.object_id(), description.crypto_hash());
      return false;
    }

    return true;
  }

  /** Determine if contest is valid for ballot style. */
  static boolean contest_is_valid_for_style(CiphertextBallot.Contest contest, ContestWithPlaceholders contestp) {
    Manifest.ContestDescription contestm = contestp.contest;
    // verify the hash matches
    if (!contest.contest_hash.equals(contestm.crypto_hash())) {
      logger.atInfo().log("ballot is not valid for style: mismatched description hash %s for contest %s hash %s",
              contest.contest_hash, contestm.object_id(), contestm.crypto_hash());
      return false;
    }

    //  verify the placeholder count
    if (contest.ballot_selections.size() !=
            (contestm.ballot_selections().size()) + contestp.placeholder_selections.size()) {
      logger.atInfo().log("ballot is not valid for style: mismatched selection count for contest %s",
              contestm.object_id());
      return false;
    }
    return true;
  }

  /** Determine if ballot is valid for ballot style. */
  static boolean ballot_is_valid_for_style(CiphertextBallot ballot, InternalManifest manifest) {
    Map<String, CiphertextBallot.Contest> contestMap = ballot.contests.stream().collect(Collectors.toMap(c -> c.object_id, c -> c));

    List<ContestWithPlaceholders> contests = manifest.get_contests_for_style(ballot.style_id);
    for (ContestWithPlaceholders contestp : contests) {
      Manifest.ContestDescription contestm = contestp.contest;
      CiphertextBallot.Contest use_contest = contestMap.get(contestm.object_id());

      // verify the contest exists on the ballot LOOK we reject ballots that dont have all contests on them.
      if (use_contest == null) {
        logger.atInfo().log("ballot is not valid for style: mismatched contest %s", contestm.object_id());
        return false;
      }

      if (!contest_is_valid_for_style(use_contest, contestp)) {
        return false;
      }

      // verify the selection metadata
      for (Manifest.SelectionDescription selection_description : contestm.ballot_selections()) {
        CiphertextBallot.Selection use_selection = null;
        for (CiphertextBallot.Selection selection : use_contest.ballot_selections) {
          if (selection_description.object_id().equals(selection.object_id())) {
            use_selection = selection;
            break;
          }
        }

        if (use_selection == null) {
          logger.atInfo().log("ballot is not valid for style: missing selection %s", selection_description.object_id());
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
