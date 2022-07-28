package com.sunya.electionguard;

import com.sunya.electionguard.ballot.EncryptedBallot;
import com.sunya.electionguard.ballot.EncryptedTally;
import electionguard.ballot.Guardian;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import static com.google.common.truth.Truth.assertThat;

public class CompareHelper {

  public static void compareCiphertextTally(EncryptedTally actual, EncryptedTally expected) {
    for (Map.Entry<String, EncryptedTally.Contest> entry : expected.contests.entrySet()) {
      EncryptedTally.Contest expectedContest = entry.getValue();
      EncryptedTally.Contest contest = actual.contests.get(entry.getKey());
      assertThat(contest).isNotNull();
      for (Map.Entry<String, EncryptedTally.Selection> entry2 : expectedContest.selections.entrySet()) {
        EncryptedTally.Selection expectedSelection = entry2.getValue();
        EncryptedTally.Selection selection = contest.selections.get(entry2.getKey());
        compareCiphertextTallySelection(selection, expectedSelection);
        assertThat(selection).isEqualTo(expectedSelection);
      }
    }
  }

  //   public final String selectionId;
  //  public final int sequenceOrder;
  //  public final Group.ElementModQ selectionHash;
  //  private final ElGamal.Ciphertext ciphertext; // only accessed through ciphertext(), so subclass can override
  //  public final boolean isPlaceholderSelection;
  public static void compareCiphertextTallySelection(EncryptedTally.Selection actual, EncryptedTally.Selection expected) {
    assertThat(actual.selectionId).isEqualTo(expected.selectionId);
    assertThat(actual.sequenceOrder).isEqualTo(expected.sequenceOrder);
    assertThat(actual.selectionHash).isEqualTo(expected.selectionHash);
    compareElgamalCiphertext(actual.ciphertext(), expected.ciphertext());
    assertThat(actual.isPlaceholderSelection).isEqualTo(expected.isPlaceholderSelection);
  }

  public static void comparePlaintextTally(PlaintextTally actual, PlaintextTally expected) {
    for (Map.Entry<String, PlaintextTally.Contest> entry : expected.contests.entrySet()) {
      PlaintextTally.Contest expectedContest = entry.getValue();
      PlaintextTally.Contest contest = actual.contests.get(entry.getKey());
      assertThat(contest).isNotNull();
      for (Map.Entry<String, PlaintextTally.Selection> entry2 : expectedContest.selections().entrySet()) {
        PlaintextTally.Selection expectedSelection = entry2.getValue();
        PlaintextTally.Selection selection = contest.selections().get(entry2.getKey());
        comparePlaintextTallySelection(selection, expectedSelection);
        assertThat(selection).isEqualTo(expectedSelection);
      }
    }
  }

  // String selectionId,
  //          Integer tally,
  //          ElementModP value,
  //          ElGamal.Ciphertext message,
  //          List<DecryptionShare.CiphertextDecryptionSelection> shares
  public static void comparePlaintextTallySelection(PlaintextTally.Selection actual, PlaintextTally.Selection expected) {
    assertThat(actual.selectionId()).isEqualTo(expected.selectionId());
    assertThat(actual.tally()).isEqualTo(expected.tally());
    assertThat(actual.message()).isEqualTo(expected.message());
    for (DecryptionShare.CiphertextDecryptionSelection share : actual.shares()) {
      // String selectionId,
      //          String guardianId,
      //          ElementModP share,
      //          Optional<ChaumPedersen.ChaumPedersenProof> proof,
      //          Optional<Map<String, CiphertextCompensatedDecryptionSelection>> recoveredParts
      DecryptionShare.CiphertextDecryptionSelection eshare = expected.shares().stream()
              .filter(s -> s.guardianId().equals(share.guardianId())).findFirst().orElseThrow();
      // this will all be identical - so unneeded
      assertThat(share.selectionId()).isEqualTo(eshare.selectionId());
      if (!share.share().equals(eshare.share())) {
        System.out.printf("HEY");
      }
      assertThat(share.share()).isEqualTo(eshare.share());
      assertThat(share.proof().isPresent()).isEqualTo(eshare.proof().isPresent());
      share.proof().ifPresent(proof -> {
        compareChaumPedersenProof(proof, eshare.proof().get());
      });
      assertThat(share.recoveredParts().isPresent()).isEqualTo(eshare.recoveredParts().isPresent());
      share.recoveredParts().ifPresent(parts -> {
        Map<String, DecryptionShare.CiphertextCompensatedDecryptionSelection> eparts = eshare.recoveredParts().get();
        for (Map.Entry<String, DecryptionShare.CiphertextCompensatedDecryptionSelection> entry : parts.entrySet()) {
          DecryptionShare.CiphertextCompensatedDecryptionSelection epart = eparts.get(entry.getKey());
          assertThat(epart).isNotNull();
          compareCompensatedDecryptionSelection(entry.getValue(), epart);
          if (!epart.equals(entry.getValue())) {
            System.out.printf("HEY");
          }
          assertThat(epart).isEqualTo(entry.getValue());
        }
      });
    }
  }

  //           String selectionId,
  //          String guardianId,
  //          String missing_guardian_id,
  //          ElementModP share,
  //          ElementModP recoveryKey,
  //          ChaumPedersen.ChaumPedersenProof proof
  public static void compareCompensatedDecryptionSelection(DecryptionShare.CiphertextCompensatedDecryptionSelection actual, DecryptionShare.CiphertextCompensatedDecryptionSelection expected) {
    assertThat(actual.guardianId()).isEqualTo(expected.guardianId());
    assertThat(actual.selectionId()).isEqualTo(expected.selectionId());
    assertThat(actual.missing_guardian_id()).isEqualTo(expected.missing_guardian_id());
    compareElementModP(actual.share(), expected.share());
    assertThat(actual.share()).isEqualTo(expected.share());
    assertThat(actual.recoveryKey()).isEqualTo(expected.recoveryKey());
    compareChaumPedersenProof(actual.proof(), expected.proof());
  }

  public static void compareGuardian(Guardian guardian, List<GuardianRecord> records) {
    GuardianRecord expected = records.stream()
            .filter(g -> g.guardianId().equals(guardian.getGuardianId()))
            .findFirst().orElseThrow();

    assertThat(guardian.getXCoordinate()).isEqualTo(expected.xCoordinate());
    compareCoefficientCommitments(guardian.getCoefficientCommitments(), expected.coefficientCommitments());
    assertThat(guardian.getCoefficientProofs()).isEqualTo(expected.coefficientProofs());
  }

  public static void compareGuardianRecord(GuardianRecord actual, GuardianRecord expected) {
    assertThat(actual.guardianId()).isEqualTo(expected.guardianId());
    assertThat(actual.xCoordinate()).isEqualTo(expected.xCoordinate());
    assertThat(actual.guardianPublicKey()).isEqualTo(expected.guardianPublicKey());
    compareCoefficientCommitments(actual.coefficientCommitments(), expected.coefficientCommitments());
    assertThat(actual.coefficientProofs()).isEqualTo(expected.coefficientProofs());
  }

  public static void compareGuardian(electionguard.ballot.Guardian actual, electionguard.ballot.Guardian expected) {
    assertThat(actual.getGuardianId()).isEqualTo(expected.getGuardianId());
    assertThat(actual.getXCoordinate()).isEqualTo(expected.getXCoordinate());
    assertThat(actual.getCoefficientProofs()).isEqualTo(expected.getCoefficientProofs());
    compareCoefficientCommitments(actual.getCoefficientCommitments(), expected.getCoefficientCommitments());
  }

  public static void compareCoefficientCommitments(List<Group.ElementModP> actuals, List<Group.ElementModP> expecteds) {
    assertThat(actuals.size()).isEqualTo(expecteds.size());
    for (int i = 0; i < actuals.size(); i++) {
      assertThat(actuals.get(i)).isEqualTo(expecteds.get(i));
    }
  }

  public static void compareCiphertextBallot(EncryptedBallot actual, EncryptedBallot expected) throws IOException {
    int contestIdx = 0;
    for (CiphertextBallot.Contest contest : actual.contests) {
      CiphertextBallot.Contest econtest = expected.contests.get(contestIdx);
      int selectionIdx = 0;
      for (CiphertextBallot.Selection selection : contest.selections) {
        CiphertextBallot.Selection eselection = econtest.selections.get(selectionIdx);
        compareCiphertextBallotSelection(selection, eselection);
        assertThat(selection).isEqualTo(eselection);
        selectionIdx++;
      }
      contestIdx++;
    }
    assertThat(actual.contests).isEqualTo(expected.contests);
    assertThat(actual).isEqualTo(expected);
  }

  public static void compareCiphertextBallotSelection(CiphertextBallot.Selection actual, CiphertextBallot.Selection expected) {
    assertThat(actual.selectionId).isEqualTo(expected.selectionId);
    assertThat(actual.sequenceOrder).isEqualTo(expected.sequenceOrder);
    assertThat(actual.selectionHash).isEqualTo(expected.selectionHash);
    compareElgamalCiphertext(actual.ciphertext(), expected.ciphertext());
    assertThat(actual.isPlaceholderSelection).isEqualTo(expected.isPlaceholderSelection);
    assertThat(actual.crypto_hash).isEqualTo(expected.crypto_hash);
    assertThat(actual.proof.isPresent()).isEqualTo(expected.proof.isPresent());
    actual.proof.ifPresent(proof -> {
      compareDisjunctiveChaumPedersenProof(proof, expected.proof.get());
    });
  }

  public static void compareElgamalCiphertext(ElGamal.Ciphertext actual, ElGamal.Ciphertext expected) {
    compareElementModP(actual.data(), expected.data());
    assertThat(actual.data()).isEqualTo(expected.data());
    compareElementModP(actual.pad(), expected.pad());
    assertThat(actual.pad()).isEqualTo(expected.pad());
  }

  public static void compareChaumPedersenProof(ChaumPedersen.ChaumPedersenProof actual, ChaumPedersen.ChaumPedersenProof expected) {
    compareElementModP(actual.pad, expected.pad);
    assertThat(actual.pad).isEqualTo(expected.pad);
    compareElementModP(actual.data, expected.data);
    assertThat(actual.data).isEqualTo(expected.data);
    compareElementModQ(actual.challenge, expected.challenge);
    assertThat(actual.challenge).isEqualTo(expected.challenge);
    compareElementModQ(actual.response, expected.response);
    assertThat(actual.response).isEqualTo(expected.response);
  }

  public static void compareDisjunctiveChaumPedersenProof(ChaumPedersen.DisjunctiveChaumPedersenProof actual, ChaumPedersen.DisjunctiveChaumPedersenProof expected) {
    compareChaumPedersenProof(actual.proof0, expected.proof0);
    compareChaumPedersenProof(actual.proof1, expected.proof1);
    assertThat(actual.challenge).isEqualTo(expected.challenge);
  }

  public static void compareElementModP(Group.ElementModP actual, Group.ElementModP expected) {
    if (!actual.equals(expected)) {
      System.out.printf("HEY %s != %s%n", actual.base16(), expected.base16());
    }
    assertThat(actual).isEqualTo(expected);
  }

  public static void compareElementModQ(Group.ElementModQ actual, Group.ElementModQ expected) {
    if (!actual.equals(expected)) {
      System.out.printf("HEY %s != %s%n", actual.base16(), expected.base16());
    }
    assertThat(actual).isEqualTo(expected);
  }


}
