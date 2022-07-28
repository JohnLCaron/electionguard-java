package com.sunya.electionguard.input;

import com.google.common.flogger.FluentLogger;
import com.sunya.electionguard.ballot.EncryptedTally;
import com.sunya.electionguard.Group;
import com.sunya.electionguard.Manifest;

import java.util.ArrayList;
import java.util.Formatter;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

/** Validate encrypted tally against the election manifest, give human readable error information. */
public class CiphertextTallyInputValidation {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final Map<String, ElectionContest> contestMap;

  public CiphertextTallyInputValidation(Manifest election) {
    this.contestMap = election.contests().stream().collect(Collectors.toMap(c -> c.contestId(), ElectionContest::new));
  }

  /** Determine if a tally is valid and well-formed for the given election manifest. */
  public boolean validateTally(EncryptedTally tally, Formatter problems) {
    Messenger messes = new Messenger(tally.object_id());

    for (Map.Entry<String, EncryptedTally.Contest> entry : tally.contests.entrySet()) {
      if (!entry.getKey().equals(entry.getValue().object_id())) {
        String msg = String.format("CiphertextTally.B.1 Contest id key '%s' doesnt match value '%s'", entry.getKey(),
                entry.getValue().object_id());
        messes.add(msg);
        logger.atWarning().log(msg);
        break;
      }

      EncryptedTally.Contest tallyContest = entry.getValue();
      ElectionContest manifestContest = contestMap.get(tallyContest.object_id());
      // Referential integrity of tallyContest id and cryptoHash
      if (manifestContest == null) {
        String msg = String.format("CiphertextTally.A.1 Tally Contest '%s' does not exist in manifest", tallyContest.object_id());
        messes.add(msg);
        logger.atWarning().log(msg);
      } else {
        if (!manifestContest.cryptoHash.equals(tallyContest.contestDescriptionHash)) {
          String msg = String.format("CiphertextTally.A.1.1 Tally Contest '%s' crypto_hash does not match manifest contest",
                  tallyContest.object_id());
          messes.add(msg);
          logger.atWarning().log(msg);
        }
        validateContest(tallyContest, manifestContest, messes);
      }
    }
    return messes.makeMesses(problems);
  }

  /** Determine if contest is valid. */
  void validateContest(EncryptedTally.Contest tallyContest, ElectionContest manifestContest, Messenger messes) {
    Messenger contestMesses = messes.nested(tallyContest.object_id());

    for (Map.Entry<String, EncryptedTally.Selection> entry : tallyContest.selections.entrySet()) {
      if (!entry.getKey().equals(entry.getValue().object_id())) {
        String msg = String.format("CiphertextTally.B.2 Selection id key '%s' doesnt match value '%s'", entry.getKey(),
                entry.getValue().object_id());
        messes.add(msg);
        logger.atWarning().log(msg);
        break;
      }
      EncryptedTally.Selection tallySelection = entry.getValue();

      Manifest.SelectionDescription manifestSelection = manifestContest.selectionMap.get(tallySelection.object_id());
      // Referential integrity of tallySelection id
      if (manifestSelection == null) {
        String msg = String.format("CiphertextBallot.A.2 Tally Selection '%s' does not exist in contest '%s'",
                tallySelection.object_id(), tallyContest.object_id());
        contestMesses.add(msg);
        logger.atWarning().log(msg);
        break;
      }

      // Referential integrity of tallySelection cryptoHash
      if (!manifestSelection.cryptoHash().equals(tallySelection.description_hash())) {
        String msg = String.format("CiphertextTally.A.2.1 Tally Selection '%s-%s' crypto_hash does not match manifest selection",
                tallyContest.object_id(), tallySelection.object_id());
        messes.add(msg);
        logger.atWarning().log(msg);
      }
    }
  }

  private static class ElectionContest {
    private final String contestId;
    private final Group.ElementModQ cryptoHash;
    private final int allowed;
    private final Map<String, Manifest.SelectionDescription> selectionMap;

    ElectionContest(Manifest.ContestDescription electionContest) {
      this.contestId = electionContest.contestId();
      this.cryptoHash = electionContest.cryptoHash();
      this.allowed = electionContest.votesAllowed();
      // allow same object id
      this.selectionMap = new HashMap<>();
      for (Manifest.SelectionDescription sel : electionContest.selections()) {
        this.selectionMap.put(sel.selectionId(), sel);
      }
    }
  }

  private static class Messenger {
    private final String id;
    private final ArrayList<String> messages = new ArrayList<>();
    private final ArrayList<Messenger> nested = new ArrayList<>();

    public Messenger(String id) {
      this.id = id;
    }

    void add(String mess) {
      messages.add(mess);
    }

    Messenger nested(String id) {
      Messenger mess = new Messenger(id);
      nested.add(mess);
      return mess;
    }

    boolean makeMesses(Formatter problems) {
      if (hasProblem()) {
        problems.format("CiphertextTally '%s' has problems%n", id);
        for (String mess : messages) {
          problems.format("  %s%n", mess);
        }
        for (Messenger nest : nested) {
          if (nest.hasProblem()) {
            problems.format("  CiphertextTally.Contest '%s' has problems%n", nest.id);
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
      return !messages.isEmpty() || nested.stream().map(Messenger::hasProblem).findAny().orElse(false);
    }
  }
}
