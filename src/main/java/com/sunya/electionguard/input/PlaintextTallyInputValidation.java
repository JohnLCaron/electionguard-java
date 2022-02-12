package com.sunya.electionguard.input;

import com.google.common.flogger.FluentLogger;
import com.sunya.electionguard.CiphertextTally;
import com.sunya.electionguard.DecryptionShare;
import com.sunya.electionguard.Manifest;
import com.sunya.electionguard.PlaintextTally;

import java.util.Formatter;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** Validate decrypted tally against the election manifest, give human readable error information. */
public class PlaintextTallyInputValidation {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final Map<String, ElectionContest> contestMap;
  private final CiphertextTally ctally;
  private final int nguardians;
  private final int navailable;

  public PlaintextTallyInputValidation(Manifest election, CiphertextTally ctally, int nguardians, int navailable) {
    this.contestMap = election.contests.stream().collect(Collectors.toMap(c -> c.object_id, ElectionContest::new));
    this.ctally = ctally;
    this.nguardians = nguardians;
    this.navailable = navailable;
  }

  /** Determine if a tally is valid and well-formed for the given election manifest. */
  public boolean validateTally(PlaintextTally tally, Formatter problems) {
    ValidationMessenger messes = new ValidationMessenger("PlaintextTally", tally.object_id);

    for (Map.Entry<String, PlaintextTally.Contest> entry : tally.contests.entrySet()) {
      if (!entry.getKey().equals(entry.getValue().object_id())) {
        String msg = String.format("PlaintextTally.C.1 Contest id key '%s' doesnt match value '%s'",
                entry.getKey(), entry.getValue().object_id());
        messes.add(msg);
        logger.atWarning().log(msg);
        continue;
      }

      PlaintextTally.Contest tallyContest = entry.getValue();
      ElectionContest manifestContest = contestMap.get(tallyContest.object_id());
      // Referential integrity of tallyContest id and cryptoHash
      if (manifestContest == null) {
        String msg = String.format("PlaintextTally.A.1 Tally Contest '%s' does not exist in manifest", tallyContest.object_id());
        messes.add(msg);
        logger.atWarning().log(msg);
      } else {
        validateManifestContest(tallyContest, manifestContest, messes);
      }

      CiphertextTally.Contest ciphertextContest = ctally.contests.get(tallyContest.object_id());
      // Referential integrity of tallyContest id and cryptoHash
      if (ciphertextContest == null) {
        String msg = String.format("PlaintextTally.B.1 PlaintextTally Contest '%s' does not exist in CiphertextTally.Contest",
                tallyContest.object_id());
        messes.add(msg);
        logger.atWarning().log(msg);
      } else {
        validateCiphertextContest(tallyContest, ciphertextContest, messes);
      }
    }
    return messes.makeMesses(problems);
  }

  /** Determine if contest is valid. */
  void validateCiphertextContest(PlaintextTally.Contest tallyContest, CiphertextTally.Contest ciphertextContest, ValidationMessenger messes) {
    ValidationMessenger contestMesses = messes.nested("CiphertextContest", tallyContest.object_id());

    for (Map.Entry<String, PlaintextTally.Selection> entry : tallyContest.selections().entrySet()) {
      PlaintextTally.Selection tallySelection = entry.getValue();

      CiphertextTally.Selection ciphertextSelection = ciphertextContest.selections.get(tallySelection.object_id());
      // Referential integrity of tallySelection id
      if (ciphertextSelection == null) {
        String msg = String.format("PlaintextTally.B.2 PlaintextTally Selection '%s' does not exist in CiphertextTally contest",
                tallySelection.object_id());
        contestMesses.add(msg);
        logger.atWarning().log(msg);
        continue;
      }

      if (!tallySelection.message().equals(ciphertextSelection.ciphertext())) {
        String msg = String.format("PlaintextTally.B.2.1 PlaintextTally Selection '%s' message does not match CiphertextTally",
                tallySelection.object_id());
        messes.add(msg);
        logger.atWarning().log(msg);
      }
    }
  }

  /** Determine if contest is valid. */
  void validateManifestContest(PlaintextTally.Contest tallyContest, ElectionContest manifestContest, ValidationMessenger messes) {
    ValidationMessenger contestMesses = messes.nested("ManifestContest", tallyContest.object_id());

    for (Map.Entry<String, PlaintextTally.Selection> entry : tallyContest.selections().entrySet()) {
      if (!entry.getKey().equals(entry.getValue().object_id())) {
        String msg = String.format("PlaintextTally.C.2 Selection id key '%s' doesnt match value '%s'",
                entry.getKey(), entry.getValue().object_id());
        messes.add(msg);
        logger.atWarning().log(msg);
        continue;
      }
      PlaintextTally.Selection tallySelection = entry.getValue();

      Manifest.SelectionDescription manifestSelection = manifestContest.selectionMap.get(tallySelection.object_id());
      // Referential integrity of tallySelection id
      if (manifestSelection == null) {
        String msg = String.format("PlaintextTally.A.2 Tally Selection '%s' does not exist in contest '%s'",
                tallySelection.object_id(), tallyContest.object_id());
        contestMesses.add(msg);
        logger.atWarning().log(msg);
      }

      if (tallySelection.shares().size() != nguardians) {
        String msg = String.format("PlaintextTally.D.3 tallySelection '%s' number of shares = %d should be %d",
                tallySelection.object_id(), tallySelection.shares().size(), nguardians);
        messes.add(msg);
        logger.atWarning().log(msg);
      }

      validateShares(tallySelection, contestMesses);
    }
  }

  /** Determine if share is valid. */
  void validateShares(PlaintextTally.Selection tallySelection, ValidationMessenger messes) {
    ValidationMessenger selectionMesses = messes.nested("Shares", tallySelection.object_id());

    Set<String> guardianIds = new HashSet<>();
    for (DecryptionShare.CiphertextDecryptionSelection share : tallySelection.shares()) {
      if (!share.object_id().equals(tallySelection.object_id())) {
        String msg = String.format("PlaintextTally.D.1 Share id '%s' doesnt match selection '%s'",
                share.object_id(), tallySelection.object_id());
        selectionMesses.add(msg);
        logger.atWarning().log(msg);
        continue;
      }

      if (guardianIds.contains(share.guardian_id())) {
        String msg = String.format("PlaintextTally.D.2 Multiple shares have same guardian_id '%s'", share.guardian_id());
        selectionMesses.add(msg);
        logger.atWarning().log(msg);
      } else {
        guardianIds.add(share.guardian_id());
      }

      if (share.recovered_parts().isPresent()) {
        validateRecoveredParts(share, selectionMesses);
      }
    }
  }

  void validateRecoveredParts(DecryptionShare.CiphertextDecryptionSelection share, ValidationMessenger messes) {
    ValidationMessenger partsMesses = messes.nested("RecoveryParts", share.object_id());

    Map<String, DecryptionShare.CiphertextCompensatedDecryptionSelection> parts =  share.recovered_parts().get();
    if (parts.size() != navailable) {
      String msg = String.format("PlaintextTally.E.3 share '%s' number of parts = %d should be navailable = %d",
              share.object_id(), parts.size(), navailable);
      messes.add(msg);
      logger.atWarning().log(msg);
    }

    for (Map.Entry<String, DecryptionShare.CiphertextCompensatedDecryptionSelection> entry : parts.entrySet()) {
      if (!entry.getKey().equals(entry.getValue().guardian_id())) {
        String msg = String.format("PlaintextTally.E.2 recovered_parts key '%s' doesnt match value.guardian_id '%s'",
                entry.getKey(), entry.getValue().guardian_id());
        partsMesses.add(msg);
        logger.atWarning().log(msg);
        continue;
      }
      DecryptionShare.CiphertextCompensatedDecryptionSelection part = entry.getValue();

      if (!share.object_id().equals(part.object_id())) {
        String msg = String.format("PlaintextTally.E.1 recovered_parts object_id '%s' doesnt match share object_id '%s'",
                part.object_id(), share.object_id());
        partsMesses.add(msg);
        logger.atWarning().log(msg);
        continue;
      }

      if (!share.guardian_id().equals(part.missing_guardian_id())) {
        String msg = String.format("PlaintextTally.E.4 missing_guardian_id '%s' doesnt match share guardian_id '%s'",
                part.missing_guardian_id(), share.guardian_id());
        partsMesses.add(msg);
        logger.atWarning().log(msg);
        continue;
      }
    }
  }

  private static class ElectionContest {
    private final Map<String, Manifest.SelectionDescription> selectionMap;

    ElectionContest(Manifest.ContestDescription electionContest) {
      this.selectionMap = new HashMap<>();
      for (Manifest.SelectionDescription sel : electionContest.ballot_selections) {
        this.selectionMap.put(sel.object_id, sel);
      }
    }
  }

}
