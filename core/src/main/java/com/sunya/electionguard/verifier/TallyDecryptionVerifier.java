package com.sunya.electionguard.verifier;

import com.google.common.base.Preconditions;
import com.google.common.flogger.FluentLogger;
import com.sunya.electionguard.InternalManifest;
import com.sunya.electionguard.Manifest;
import com.sunya.electionguard.Group;
import com.sunya.electionguard.PlaintextTally;

import java.math.BigInteger;
import java.util.List;

import static com.sunya.electionguard.Group.ElementModP;

/**
 * This verifies specification section "11 Correct Decryption of Tallies".
 * @see <a href="https://www.electionguard.vote/spec/0.95.0/9_Verifier_construction/#validation-of-correct-decryption-of-tallies">Correct Decryption of Tallies</a>
 */
public class TallyDecryptionVerifier {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  final InternalManifest manifest;
  final PlaintextTally decryptedTally;

  TallyDecryptionVerifier(Manifest election, PlaintextTally decryptedTally) {
    this.manifest = new InternalManifest(election);
    this.decryptedTally = decryptedTally;
  }

  /**
   * 11. An election verifier should confirm the following equations for each (non-placeholder) option in
   * each contest in the ballot coding file.
   * <pre>
   * (A) B = (M ⋅ (∏ Mi )) mod p.
   * (B) M = g^t mod p.
   *
   * (C) An election verifier should also confirm that the text labels listed in the election record match
   * the corresponding text labels in the ballot coding file.
   * </pre>
   */
  public boolean verify_tally_decryption() {
    boolean error = false;
    Preconditions.checkNotNull(decryptedTally);

    for (PlaintextTally.Contest contest : decryptedTally.contests.values()) {
      InternalManifest.ContestWithPlaceholders manifestContest = manifest.getContestById(contest.contestId()).orElse(null);
      if (manifestContest == null) {
        System.out.printf(" 11.C Tally Decryption contains contest (%s) not in manifest%n", contest.contestId());
        error = true;
      }
      for (PlaintextTally.Selection selection : contest.selections().values()) {
        if (manifestContest != null && manifestContest.getSelectionById(selection.selectionId()).isEmpty()) {
          System.out.printf(" 11.C Tally Decryption contest (%s) contains selection (%s) not in manifest%n",
                  contest.contestId(), selection.selectionId());
          error = true;
        }
        String key = contest.contestId() + "." + selection.selectionId();
        List<ElementModP> partialDecryptions = selection.shares().stream()
                .map(s -> s.share())
                .toList();
        ElementModP productMi = Group.mult_p(partialDecryptions);
        ElementModP M = selection.value();
        ElementModP B = selection.message().data();
        if (!B.equals(Group.mult_p(M, productMi))) {
          System.out.printf(" 11.A Tally Decryption failed for %s.%n", key);
          error = true;
        }

        ElementModP t = Group.int_to_p_unchecked(BigInteger.valueOf(selection.tally()));
        if (!M.equals(Group.g_pow_p(t))) {
          System.out.printf(" 11.B Tally Decryption failed for %s.%n", key);
          error = true;
        }
      }
    }

    if (error) {
      System.out.printf(" ***Tally Decryption Validation failed.%n");
    } else {
      System.out.printf(" Tally Decryption Validation success.%n");
    }
    return !error;
  }
}
