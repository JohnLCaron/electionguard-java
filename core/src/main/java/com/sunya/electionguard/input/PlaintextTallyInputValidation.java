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
    this.contestMap = election.contests().stream().collect(Collectors.toMap(c -> c.contestId(), ElectionContest::new));
    this.ctally = ctally;
    this.nguardians = nguardians;
    this.navailable = navailable;
  }

  /** Determine if a tally is valid and well-formed for the given election manifest. */
  public boolean validateTally(PlaintextTally tally, Formatter problems) {
    ValidationMessenger messes = new ValidationMessenger("PlaintextTally", tally.tallyId);

    for (Map.Entry<String, PlaintextTally.Contest> entry : tally.contests.entrySet()) {
      if (!entry.getKey().equals(entry.getValue().contestId())) {
        String msg = String.format("PlaintextTally.C.1 Contest id key '%s' doesnt match value '%s'",
                entry.getKey(), entry.getValue().contestId());
        messes.add(msg);
        logger.atWarning().log(msg);
        continue;
      }

      PlaintextTally.Contest tallyContest = entry.getValue();
      ElectionContest manifestContest = contestMap.get(tallyContest.contestId());
      // Referential integrity of tallyContest id and cryptoHash
      if (manifestContest == null) {
        String msg = String.format("PlaintextTally.A.1 Tally Contest '%s' does not exist in manifest", tallyContest.contestId());
        messes.add(msg);
        logger.atWarning().log(msg);
      } else {
        validateManifestContest(tallyContest, manifestContest, messes);
      }

      CiphertextTally.Contest ciphertextContest = ctally.contests.get(tallyContest.contestId());
      // Referential integrity of tallyContest id and cryptoHash
      if (ciphertextContest == null) {
        String msg = String.format("PlaintextTally.B.1 PlaintextTally Contest '%s' does not exist in CiphertextTally.Contest",
                tallyContest.contestId());
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
    ValidationMessenger contestMesses = messes.nested("CiphertextContest", tallyContest.contestId());

    for (Map.Entry<String, PlaintextTally.Selection> entry : tallyContest.selections().entrySet()) {
      PlaintextTally.Selection tallySelection = entry.getValue();

      CiphertextTally.Selection ciphertextSelection = ciphertextContest.selections.get(tallySelection.selectionId());
      // Referential integrity of tallySelection id
      if (ciphertextSelection == null) {
        String msg = String.format("PlaintextTally.B.2 PlaintextTally Selection '%s' does not exist in CiphertextTally contest",
                tallySelection.selectionId());
        contestMesses.add(msg);
        logger.atWarning().log(msg);
        continue;
      }

      if (!tallySelection.message().equals(ciphertextSelection.ciphertext())) {
        String msg = String.format("PlaintextTally.B.2.1 PlaintextTally Selection '%s' message does not match CiphertextTally",
                tallySelection.selectionId());
        messes.add(msg);
        logger.atWarning().log(msg);
      }
    }
  }

  /** Determine if contest is valid. */
  void validateManifestContest(PlaintextTally.Contest tallyContest, ElectionContest manifestContest, ValidationMessenger messes) {
    ValidationMessenger contestMesses = messes.nested("ManifestContest", tallyContest.contestId());

    for (Map.Entry<String, PlaintextTally.Selection> entry : tallyContest.selections().entrySet()) {
      if (!entry.getKey().equals(entry.getValue().selectionId())) {
        String msg = String.format("PlaintextTally.C.2 Selection id key '%s' doesnt match value '%s'",
                entry.getKey(), entry.getValue().selectionId());
        messes.add(msg);
        logger.atWarning().log(msg);
        continue;
      }
      PlaintextTally.Selection tallySelection = entry.getValue();

      Manifest.SelectionDescription manifestSelection = manifestContest.selectionMap.get(tallySelection.selectionId());
      // Referential integrity of tallySelection id
      if (manifestSelection == null) {
        String msg = String.format("PlaintextTally.A.2 Tally Selection '%s' does not exist in contest '%s'",
                tallySelection.selectionId(), tallyContest.contestId());
        contestMesses.add(msg);
        logger.atWarning().log(msg);
      }

      if (tallySelection.shares().size() != nguardians) {
        String msg = String.format("PlaintextTally.D.3 tallySelection '%s' number of shares = %d should be %d",
                tallySelection.selectionId(), tallySelection.shares().size(), nguardians);
        messes.add(msg);
        logger.atWarning().log(msg);
      }

      validateShares(tallySelection, contestMesses);
    }
  }

  /** Determine if share is valid. */
  void validateShares(PlaintextTally.Selection tallySelection, ValidationMessenger messes) {
    ValidationMessenger selectionMesses = messes.nested("Shares", tallySelection.selectionId());

    Set<String> guardianIds = new HashSet<>();
    for (DecryptionShare.CiphertextDecryptionSelection share : tallySelection.shares()) {
      if (!share.selectionId().equals(tallySelection.selectionId())) {
        String msg = String.format("PlaintextTally.D.1 Share id '%s' doesnt match selection '%s'",
                share.selectionId(), tallySelection.selectionId());
        selectionMesses.add(msg);
        logger.atWarning().log(msg);
        continue;
      }

      if (guardianIds.contains(share.guardianId())) {
        String msg = String.format("PlaintextTally.D.2 Multiple shares have same guardian_id '%s'", share.guardianId());
        selectionMesses.add(msg);
        logger.atWarning().log(msg);
      } else {
        guardianIds.add(share.guardianId());
      }

      if (share.recoveredParts().isPresent()) {
        validateRecoveredParts(share, selectionMesses);
      }
    }
  }

  void validateRecoveredParts(DecryptionShare.CiphertextDecryptionSelection share, ValidationMessenger messes) {
    ValidationMessenger partsMesses = messes.nested("RecoveryParts", share.selectionId());

    Map<String, DecryptionShare.CiphertextCompensatedDecryptionSelection> parts = share.recoveredParts().get();
    if (parts.size() != navailable) {
      String msg = String.format("PlaintextTally.E.3 share '%s' number of parts = %d should be navailable = %d",
              share.selectionId(), parts.size(), navailable);
      messes.add(msg);
      logger.atWarning().log(msg);
    }

    for (Map.Entry<String, DecryptionShare.CiphertextCompensatedDecryptionSelection> entry : parts.entrySet()) {
      if (!entry.getKey().equals(entry.getValue().guardianId())) {
        String msg = String.format("PlaintextTally.E.2 recovered_parts key '%s' doesnt match value.guardian_id '%s'",
                entry.getKey(), entry.getValue().guardianId());
        partsMesses.add(msg);
        logger.atWarning().log(msg);
        continue;
      }
      DecryptionShare.CiphertextCompensatedDecryptionSelection part = entry.getValue();

      if (!share.selectionId().equals(part.selectionId())) {
        String msg = String.format("PlaintextTally.E.1 recovered_parts object_id '%s' doesnt match share object_id '%s'",
                part.selectionId(), share.selectionId());
        partsMesses.add(msg);
        logger.atWarning().log(msg);
        continue;
      }

      if (!share.guardianId().equals(part.missing_guardian_id())) {
        String msg = String.format("PlaintextTally.E.4 missing_guardian_id '%s' doesnt match share guardian_id '%s'",
                part.missing_guardian_id(), share.guardianId());
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
      for (Manifest.SelectionDescription sel : electionContest.selections()) {
        this.selectionMap.put(sel.selectionId(), sel);
      }
    }
  }

}
