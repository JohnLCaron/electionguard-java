package com.sunya.electionguard.input;

import com.sunya.electionguard.CiphertextBallot;
import com.sunya.electionguard.CiphertextElectionContext;
import com.sunya.electionguard.ElGamal;
import com.sunya.electionguard.Manifest;
import com.sunya.electionguard.ElectionBuilder;
import com.sunya.electionguard.ElectionFactory;
import com.sunya.electionguard.InternalManifest;
import com.sunya.electionguard.Encrypt;
import com.sunya.electionguard.PlaintextBallot;
import net.jqwik.api.Example;

import java.math.BigInteger;
import java.util.Formatter;
import java.util.Optional;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth8.assertThat;
import static com.sunya.electionguard.ElGamal.elgamal_keypair_from_secret;
import static com.sunya.electionguard.Group.int_to_q_unchecked;

/** Tester for {@link BallotInputValidation */
public class TestBallotInputValidation {
  private static final int NFAKE_BALLOTS = 11;

  static class ElectionAndBallot {
    final Manifest election;
    final PlaintextBallot ballot;

    public ElectionAndBallot(Manifest election, PlaintextBallot ballot) {
      this.election = election;
      this.ballot = ballot;
    }
  }

  private boolean validate(Manifest election) {
    ElectionInputValidation validator = new ElectionInputValidation(election);
    Formatter problems = new Formatter();
    boolean isValid = validator.validateElection(problems);
    if (!isValid) {
      System.out.printf("Manifest Problems=%n%s", problems);
    }
    return isValid;
  }

  @Example
  public void testFakeInputOk() {
    Manifest fakeElection = ElectionFactory.get_fake_election();
    assertThat(validate(fakeElection)).isTrue();

    for (int i=0; i<NFAKE_BALLOTS; i++) {
      PlaintextBallot fakeBallot = ElectionFactory.get_fake_ballot(fakeElection, "ballot_" + i);
      ElectionAndBallot eandb = new ElectionAndBallot(fakeElection, fakeBallot);
      testValidate(eandb, null);
      // test_encrypt_ballot_simple_succeeds(eandb, true);
      testEncrypt(eandb, true);
    }
  }

  ElectionAndBallot testDefaultOk() {
    ElectionInputBuilder ebuilder = new ElectionInputBuilder("ballot_id");
    Manifest election = ebuilder.addContest("contest_id")
            .addSelection("selection_id", "candidate_1")
            .addSelection("selection_id2", "candidate_2")
            .done()
            .build();
    assertThat(validate(election)).isTrue();

    BallotInputBuilder builder = new BallotInputBuilder("ballot_id");
    PlaintextBallot ballot = builder.addContest("contest_id")
            .addSelection("selection_id", 1)
            .done()
            .build();

    return new ElectionAndBallot(election, ballot);
  }

  @Example
  public void testDefaultOkValidate() {
    testValidate(testDefaultOk(), null);
  }

  @Example
  public void testDefaultOkEncrypt() {
    testEncrypt(testDefaultOk(), true);
  }

  ElectionAndBallot testStylingNotExist() {
    ElectionInputBuilder ebuilder = new ElectionInputBuilder("ballot_id")
            .setStyle("badHairDay");
    Manifest election = ebuilder.addContest("contest_id")
            .addSelection("selection_id", "candidate_1")
            .addSelection("selection_id2", "candidate_2")
            .done()
            .build();
    assertThat(validate(election)).isTrue();

    BallotInputBuilder builder = new BallotInputBuilder("ballot_id");
    PlaintextBallot ballot = builder.addContest("contest_id")
            .addSelection("selection_id", 1)
            .done()
            .build();

    return new ElectionAndBallot(election, ballot);
  }

  @Example
  public void testStylingNotExistValidate() {
    testValidate(testStylingNotExist(), "Ballot Style 'styling' does not exist in election");
  }

  @Example
  public void testStylingNotExistEncrypt() {
    testEncrypt(testStylingNotExist(), false);
  }

  ElectionAndBallot testInvalidContest() {
    ElectionInputBuilder ebuilder = new ElectionInputBuilder("ballot_id");
    Manifest election = ebuilder.addContest("contest_id")
            .addSelection("selection_id", "candidate_1")
            .addSelection("selection_id2", "candidate_2")
            .done()
            .build();
    assertThat(validate(election)).isTrue();

    BallotInputBuilder builder = new BallotInputBuilder("ballot_id");
    PlaintextBallot ballot = builder.addContest("contest_bad") // invalid contest id
            .addSelection("selection_id", 1)
            .addSelection("selection_id2", 0)
            .done()
            .build();

    return new ElectionAndBallot(election, ballot);
  }

  @Example
  public void testInvalidContestValidate() {
    testValidate(testInvalidContest(), "Ballot Contest 'contest_bad' does not exist in election");
  }

  @Example
  public void testInvalidContestEncrypt() {
    testEncrypt(testInvalidContest(), false);
  }

  ElectionAndBallot testInvalidSelection() {
    ElectionInputBuilder ebuilder = new ElectionInputBuilder("ballot_id");
    Manifest election = ebuilder.addContest("contest_id")
            .addSelection("selection_id", "candidate_1")
            .addSelection("selection_id2", "candidate_2")
            .done()
            .build();
    assertThat(validate(election)).isTrue();

    BallotInputBuilder builder = new BallotInputBuilder("ballot_id");
    PlaintextBallot ballot = builder.addContest("contest_id")
            .addSelection("selection_id", 0)
            .addSelection("selection_bad", 1) // invalid selection id
            .done()
            .build();

    return new ElectionAndBallot(election, ballot);
  }

  @Example
  public void testInvalidSelectionValidate() {
    testValidate(testInvalidSelection(), "Ballot Selection 'selection_bad' does not exist in contest");
  }

  @Example
  public void testInvalidSelectionEncrypt() {
    testEncrypt(testInvalidSelection(), false);
  }

  ElectionAndBallot testZeroOrOne() {
    ElectionInputBuilder ebuilder = new ElectionInputBuilder("ballot_id");
    Manifest election = ebuilder.addContest("contest_id")
            .setAllowedVotes(2)
            .addSelection("selection_id", "candidate_1")
            .addSelection("selection_id2", "candidate_2")
            .done()
            .build();
    assertThat(validate(election)).isTrue();

    BallotInputBuilder builder = new BallotInputBuilder("ballot_id");
    PlaintextBallot ballot = builder.addContest("contest_id")
            .addSelection("selection_id", 0)
            .addSelection("selection_id2", 2)
            .done()
            .build();

      return new ElectionAndBallot(election, ballot);
  }

  @Example
  public void testZeroOrOneValidate() {
    testValidate(testZeroOrOne(), "Ballot Selection 'selection_id2' vote (2) must be 0 or 1");
  }

  @Example
  public void testZeroOrOneEncrypt() {
    testEncrypt(testZeroOrOne(), false);
  }

  ElectionAndBallot testOvervote() {
    ElectionInputBuilder ebuilder = new ElectionInputBuilder("ballot_id");
    Manifest election = ebuilder.addContest("contest_id")
            .addSelection("selection_id", "candidate_1")
            .addSelection("selection_id2", "candidate_2")
            .done()
            .build();
    assertThat(validate(election)).isTrue();

    BallotInputBuilder builder = new BallotInputBuilder("ballot_id");
    PlaintextBallot ballot = builder.addContest("contest_id")
            .addSelection("selection_id", 1)
            .addSelection("selection_id2", 1)
            .done()
            .build();

    return new ElectionAndBallot(election, ballot);
  }

  @Example
  public void testOvervoteValidate() {
    testValidate(testOvervote(), "OVERVOTE: Ballot Selection votes (2) exceeds limit (1)");
  }

  @Example
  public void testOvervoteEncrypt() {
    testEncrypt(testOvervote(), false);
  }

  private ElectionAndBallot testContestDeclaredTwice() {
    ElectionInputBuilder ebuilder = new ElectionInputBuilder("ballot_id");
    Manifest election = ebuilder.addContest("contest_id")
            .addSelection("selection_id", "candidate_1")
            .addSelection("selection_id2", "candidate_2")
            .done()
            .build();
    assertThat(validate(election)).isTrue();

    BallotInputBuilder builder = new BallotInputBuilder("ballot_id");
    PlaintextBallot ballot = builder.addContest("contest_id")
            .addSelection("selection_id", 1)
            .done()
        .addContest("contest_id")
            .addSelection("selection_id", 1)
            .done()
            .build();

    return new ElectionAndBallot(election, ballot);
  }

  @Example
  public void testContestDeclaredTwiceValidate() {
    testValidate(testContestDeclaredTwice(), "Multiple Ballot contests have same id 'contest_id'");
  }

  @Example
  public void testContestDeclaredTwiceEncrypt() {
    testEncrypt(testContestDeclaredTwice(), false);
  }

  private ElectionAndBallot testSelectionDeclaredTwice() {
    ElectionInputBuilder ebuilder = new ElectionInputBuilder("ballot_id");
    Manifest election = ebuilder.addContest("contest_id").setAllowedVotes(2)
            .addSelection("selection_id", "candidate_1")
            .addSelection("selection_id2", "candidate_2")
            .done()
            .build();
    assertThat(validate(election)).isTrue();

    BallotInputBuilder builder = new BallotInputBuilder("ballot_id");
    PlaintextBallot ballot = builder.addContest("contest_id")
            .addSelection("selection_id", 1)
            .addSelection("selection_id", 1)
            .done()
            .build();

    return new ElectionAndBallot(election, ballot);
  }

  @Example
  public void testSelectionDeclaredTwiceValidate() {
    testValidate(testSelectionDeclaredTwice(), "Multiple Ballot selections have same id 'selection_id'");
  }

  @Example
  public void testSelectionDeclaredTwiceEncrypt() {
    testEncrypt(testSelectionDeclaredTwice(), false);
  }

  void testValidate(ElectionAndBallot eanb, String expectMessage) {
    BallotInputValidation validator = new BallotInputValidation(eanb.election);
    Formatter problems = new Formatter();
    boolean isValid = validator.validateBallot(eanb.ballot, problems);
    if (!isValid) {
      System.out.printf("Problems=%n%s", problems);
    }
    if (expectMessage != null) {
      assertThat(isValid).isFalse();
      assertThat(problems.toString()).contains(expectMessage);
    } else {
      assertThat(isValid).isTrue();
    }
  }

  void testEncrypt(ElectionAndBallot eandb, boolean expectValid) {
    ElGamal.KeyPair keypair = elgamal_keypair_from_secret(int_to_q_unchecked(BigInteger.TWO)).orElseThrow();
    ElectionBuilder.DescriptionAndContext tuple = ElectionFactory.get_fake_ciphertext_election(eandb.election, keypair.public_key).orElseThrow();
    CiphertextElectionContext context = tuple.context;

    InternalManifest metadata = new InternalManifest(eandb.election);
    Encrypt.EncryptionDevice device = new Encrypt.EncryptionDevice("device");

    Encrypt.EncryptionMediator encryptor = new Encrypt.EncryptionMediator(metadata, context, device);
    Optional<CiphertextBallot> encrypted_ballot = encryptor.encrypt(eandb.ballot);
    if (expectValid) {
      assertThat(encrypted_ballot).isPresent();
    } else {
      assertThat(encrypted_ballot).isEmpty();
    }
  }
}
