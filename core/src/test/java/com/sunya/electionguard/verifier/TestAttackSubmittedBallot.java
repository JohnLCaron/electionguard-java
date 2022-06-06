package com.sunya.electionguard.verifier;

import com.sunya.electionguard.CiphertextBallot;
import com.sunya.electionguard.Group;
import com.sunya.electionguard.Hash;
import com.sunya.electionguard.SubmittedBallot;
import com.sunya.electionguard.publish.Consumer;
import com.sunya.electionguard.publish.ElectionRecord;
import com.sunya.electionguard.json.PublisherOld;
import net.jqwik.api.Example;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static com.google.common.truth.Truth.assertThat;
import static com.sunya.electionguard.json.PublisherOld.Mode.createIfMissing;

// try to modify a SubmittedBallot and see if it still validates
public class TestAttackSubmittedBallot {
  private static final boolean write = true;
  private static final String output = "/home/snake/tmp/electionguard/attack/encrypted";
  private static final String ballotId = "ballot-id--790149674";

  public TestAttackSubmittedBallot() throws IOException {
    String topdir = TestParameterVerifier.topdirProto;

    Consumer consumer = new Consumer(topdir);
    ElectionRecord electionRecord = consumer.readElectionRecord();
    SelectionEncryptionVerifier sev = new SelectionEncryptionVerifier(electionRecord);

    boolean sevOk = sev.verify_all_selections();
    assertThat(sevOk).isTrue();

    List<SubmittedBallot> ballots = new ArrayList<>();
    TestSelectionEncryptionVerifier.Tester tester = new TestSelectionEncryptionVerifier.Tester(electionRecord);
    for (SubmittedBallot ballot : electionRecord.submittedBallots()) {
      if (ballot.ballotId.equals(ballotId)) { // random ballot to change
        CiphertextBallot.Contest contestChanged = null;
        int idx = 0;
        int wantIdx = -1;
        for (CiphertextBallot.Contest contest : ballot.contests) {
          if (contest.contestId.equals("justice-supreme-court")) {
            contestChanged = switchVotes(tester, contest);
            tester.verify_a_contest(contestChanged);
            wantIdx = idx;
          }
          idx++;
        }

        List<CiphertextBallot.Contest> contests = new ArrayList<>(ballot.contests);
        contests.set(wantIdx, contestChanged);

        List<Group.ElementModQ> contest_hashes = contests.stream().map(s -> s.crypto_hash).toList();
        Group.ElementModQ change_crypto = Hash.hash_elems(ballot.ballotId, ballot.manifestHash, contest_hashes);

        ballots.add(new SubmittedBallot(
                ballot.ballotId,
                ballot.ballotStyleId,
                ballot.manifestHash,
                ballot.code_seed,
                contests,
                ballot.code,
                ballot.timestamp,
                change_crypto,
                ballot.state
        ));
        System.out.printf("changed ballot %s %s.%n", ballot.ballotId, ballot.state);

      } else {
        ballots.add(ballot);
      }

      if (write) {
        PublisherOld publisher = new PublisherOld(output, createIfMissing);
        publisher.writeEncryptionResultsProto(electionRecord, ballots);
      }
    }
  }

  private CiphertextBallot.Contest switchVotes(TestSelectionEncryptionVerifier.Tester tester, CiphertextBallot.Contest contest) {
    int benIdx = -1;
    int johnIdx = -1;

    for (int idx = 0; idx < contest.selections.size(); idx++) {
      if (contest.selections.get(idx).selectionId.equals("benjamin-franklin-selection")) {
        benIdx = idx;
      }
      if (contest.selections.get(idx).selectionId.equals("john-adams-selection")) {
        johnIdx = idx;
      }
    }
    CiphertextBallot.Selection bens = contest.selections.get(benIdx);
    CiphertextBallot.Selection johns = contest.selections.get(johnIdx);

    CiphertextBallot.Selection bens2 = switchVote(bens, johns);
    tester.verify_selection_validity(bens2);
    CiphertextBallot.Selection johns2 = switchVote(johns, bens);
    tester.verify_selection_validity(johns2);

    List<CiphertextBallot.Selection> selections2 = new ArrayList<>(contest.selections);
    selections2.set(benIdx, bens2);
    selections2.set(johnIdx, johns2);

    Group.ElementModQ change_crypto = CiphertextBallot.ciphertext_ballot_context_crypto_hash(
            contest.contestId,
            selections2,
            contest.contestHash
    );

    return new CiphertextBallot.Contest(
            contest.contestId,
            contest.sequenceOrder,
            contest.contestHash,
            selections2,
            change_crypto,
            contest.nonce,
            contest.proof
    );
  }

  // this fails in CiphertextBallot.Selection.is_valid_encryption() because the crypto_hash includes the
  // selection_id and the ciphertext.
  private CiphertextBallot.Selection switchVote(CiphertextBallot.Selection s1, CiphertextBallot.Selection s2) {
    Group.ElementModQ change_hash = Hash.hash_elems(s1.selectionId, s1.selectionHash, s2.ciphertext().crypto_hash());
    return new CiphertextBallot.Selection(
            s1.selectionId, s1.sequenceOrder, s1.selectionHash,
            s2.ciphertext(),
            change_hash, s2.is_placeholder_selection, s2.nonce, s2.proof, s2.extended_data
    );
  }

  @Example
  public void testSelectionEncryptionVerifier() throws IOException {
    Consumer consumer = new Consumer(output);
    TestSelectionEncryptionVerifier.testSelectionEncryptionValidation(consumer.readElectionRecord());
  }

}
