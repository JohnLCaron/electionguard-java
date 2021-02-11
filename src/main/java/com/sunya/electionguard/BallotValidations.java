package com.sunya.electionguard;

import com.google.common.flogger.FluentLogger;

import java.util.List;

import static com.sunya.electionguard.Ballot.*;
import static com.sunya.electionguard.Election.*;
import static com.sunya.electionguard.ElectionWithPlaceholders.ContestWithPlaceholders;

/** Static helper methods for ballot validation. */
public class BallotValidations {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  /** Determine if a ballot is valid for a given election . */
  // LOOK why ElectionDescriptionWithPlaceholders?
  static boolean ballot_is_valid_for_election(
          CiphertextBallot ballot,
          ElectionWithPlaceholders metadata,
          CiphertextElectionContext context) {

    if (!ballot_is_valid_for_style(ballot, metadata)) {
      return false;
    }

    if (!ballot.is_valid_encryption(
            metadata.election.crypto_hash,
            context.elgamal_public_key,
            context.crypto_extended_base_hash)) {
      logger.atInfo().log("ballot_is_valid_for_election: mismatching ballot encryption %s", ballot.object_id);
      return false;
    }
    return true;
  }

  /** Determine if selection is valid for ballot style. */
  static boolean selection_is_valid_for_style(
          CiphertextBallotSelection selection, SelectionDescription description) {

    if (!selection.description_hash.equals(description.crypto_hash())) {
      logger.atInfo().log("ballot is not valid for style: mismatched selection description hash %s for selection %s hash %s",
              selection.description_hash, description.object_id, description.crypto_hash());
      return false;
    }

    return true;
  }

  /** Determine if contest is valid for ballot style. */
  static boolean contest_is_valid_for_style(
          CiphertextBallotContest contest, ContestWithPlaceholders description) {

    // verify the hash matches
    if (!contest.description_hash.equals(description.crypto_hash())) {
      logger.atInfo().log("ballot is not valid for style: mismatched description hash %s for contest %s hash %s",
              contest.description_hash, description.object_id, description.crypto_hash());
      return false;
    }

    //  verify the placeholder count
    if (contest.ballot_selections.size() !=
            (description.ballot_selections.size()) + description.placeholder_selections.size()) {
      logger.atInfo().log("ballot is not valid for style: mismatched selection count for contest %s",
              description.object_id);
      return false;
    }
    return true;
  }

  /** Determine if ballot is valid for ballot style. */
  static boolean ballot_is_valid_for_style(CiphertextBallot ballot, ElectionWithPlaceholders metadata) {
    List<ContestWithPlaceholders> descriptions = metadata.get_contests_for(ballot.ballot_style);

    // LOOK WTF?
    for (ContestWithPlaceholders description : descriptions) {
      CiphertextBallotContest use_contest = null;
      for (CiphertextBallotContest contest : ballot.contests) {
        if (description.object_id.equals(contest.object_id)) {
          use_contest = contest;
          break;
        }
      }

      // verify the contest exists on the ballot
      if (use_contest == null) {
        logger.atInfo().log("ballot is not valid for style: mismatched contest %s", description.object_id);
        return false;
      }

      if (!contest_is_valid_for_style(use_contest, description)) {
        return false;
      }

      // verify the selection metadata
      for (SelectionDescription selection_description : description.ballot_selections) {
        CiphertextBallotSelection use_selection = null;
        for (CiphertextBallotSelection selection : use_contest.ballot_selections) {
          if (selection_description.object_id.equals(selection.object_id)) {
            use_selection = selection;
            break;
          }
        }

        if (use_selection == null) {
          logger.atInfo().log("ballot is not valid for style: missing selection %s", selection_description.object_id);
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
