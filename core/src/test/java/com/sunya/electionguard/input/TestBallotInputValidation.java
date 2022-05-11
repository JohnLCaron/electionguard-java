package com.sunya.electionguard.input;

import com.sunya.electionguard.CiphertextBallot;
import com.sunya.electionguard.ElectionCryptoContext;
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
import static org.junit.Assert.fail;

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
    ManifestInputValidation validator = new ManifestInputValidation(election);
    Formatter problems = new Formatter();
    boolean isValid = validator.validateElection(problems);
    if (!isValid) {
      System.out.printf("Manifest Problems=%n%s", problems);
    } else if (!election.is_valid()) {
      System.out.printf("Manifest is_valid() FAIL%n");
    }
    return isValid;
  }

  @Example
  public void testFakeInputOk() {
    Manifest fakeElection = ElectionFactory.get_fake_manifest();
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
    ManifestInputBuilder ebuilder = new ManifestInputBuilder("ballot_id");
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
    ManifestInputBuilder ebuilder = new ManifestInputBuilder("ballot_id")
            .setDefaultStyle("badHairDay");
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
    testValidate(testStylingNotExist(), "Ballot.A.1");
  }

  @Example
  public void testStylingNotExistEncrypt() {
    testEncrypt(testStylingNotExist(), false);
  }

  ElectionAndBallot testGpunitListed(boolean listed) {
    ManifestInputBuilder ebuilder = new ManifestInputBuilder("ballot_id")
            .addStyle("styling", "orphan", "annie");
    Manifest election = ebuilder.addContest("contest_id")
            .addSelection("selection_id", "candidate_1")
            .addSelection("selection_id2", "candidate_2")
            .setGpunit(listed ? "orphan" : "parented")
            .done()
            .build();
    assertThat(validate(election)).isEqualTo(listed);

    BallotInputBuilder builder = new BallotInputBuilder("ballot_id");
    PlaintextBallot ballot = builder.addContest("contest_id")
            .addSelection("selection_id", 1)
            .done()
            .build();

    return new ElectionAndBallot(election, ballot);
  }

  @Example
  public void testGpunitListedValidate() {
    testValidate(testGpunitListed(true), null);
  }

  @Example
  public void testGpunitNotListedValidate() {
    testValidate(testGpunitListed(false), "Ballot.A.3");
  }

  ElectionAndBallot testInvalidContest() {
    ManifestInputBuilder ebuilder = new ManifestInputBuilder("ballot_id");
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
    testValidate(testInvalidContest(), "Ballot.A.2");
  }

  @Example
  public void testInvalidContestEncrypt() {
    testEncrypt(testInvalidContest(), false);
  }

  ElectionAndBallot testInvalidSelection() {
    ManifestInputBuilder ebuilder = new ManifestInputBuilder("ballot_id");
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
    testValidate(testInvalidSelection(), "Ballot.B.2.1");
  }

  @Example
  public void testInvalidSelectionEncrypt() {
    testEncrypt(testInvalidSelection(), false);
  }

  ElectionAndBallot testZeroOrOne() {
    ManifestInputBuilder ebuilder = new ManifestInputBuilder("ballot_id");
    Manifest election = ebuilder.addContest("contest_id")
            .setVoteVariationType(Manifest.VoteVariationType.n_of_m, 2)
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
    testValidate(testZeroOrOne(), "Ballot.C.1");
  }

  @Example
  public void testZeroOrOneEncrypt() {
    testEncrypt(testZeroOrOne(), false);
  }

  ElectionAndBallot testOvervote() {
    ManifestInputBuilder ebuilder = new ManifestInputBuilder("ballot_id");
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
    testValidate(testOvervote(), "Ballot.C.2");
  }

  @Example
  public void testOvervoteEncrypt() {
    testEncrypt(testOvervote(), false);
  }

  private ElectionAndBallot testContestDeclaredTwice() {
    ManifestInputBuilder ebuilder = new ManifestInputBuilder("ballot_id");
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
    testValidate(testContestDeclaredTwice(), "Ballot.B.1");
  }

  @Example
  public void testContestDeclaredTwiceEncrypt() {
    testEncrypt(testContestDeclaredTwice(), false);
  }

  private ElectionAndBallot testSelectionDeclaredTwice() {
    ManifestInputBuilder ebuilder = new ManifestInputBuilder("ballot_id");
    Manifest election = ebuilder.addContest("contest_id")
            .setVoteVariationType(Manifest.VoteVariationType.n_of_m, 2)
            .addSelection("selection_id", "candidate_1")
            .addSelection("selection_id2", "candidate_2")
            .done()
            .build();
    assertThat(validate(election)).isTrue();

    BallotInputBuilder builder = new BallotInputBuilder("ballot_id");
    PlaintextBallot ballot = builder.addContest("contest_id")
            .addSelection("selection_id", 1)
            .addSelection("selection_id", 1) // voting for same candidate twice
            .done()
            .build();

    return new ElectionAndBallot(election, ballot);
  }

  @Example
  public void testSelectionDeclaredTwiceValidate() {
    testValidate(testSelectionDeclaredTwice(), "Ballot.B.2");
  }

  @Example
  public void testSelectionDeclaredTwiceEncrypt() {
    testEncrypt(testSelectionDeclaredTwice(), false);
  }

  private ElectionAndBallot testSelectionNoMatch() {
    ManifestInputBuilder ebuilder = new ManifestInputBuilder("ballot_id");
    Manifest election = ebuilder.addContest("contest_id")
            .setVoteVariationType(Manifest.VoteVariationType.n_of_m, 2)
            .addSelection("selection_id", "candidate_1")
            .addSelection("selection_id2", "candidate_2")
            .done()
            .build();
    assertThat(validate(election)).isTrue();

    BallotInputBuilder builder = new BallotInputBuilder("ballot_id");
    PlaintextBallot ballot = builder.addContest("contest_id")
            .addSelection("selection_id", 1)
            .addSelection("selection_id3", 0) // voting for same candidate twice
            .done()
            .build();

    return new ElectionAndBallot(election, ballot);
  }

  @Example
  public void testSelectionNoMatchValidate() {
    testValidate(testSelectionNoMatch(), "Ballot.B.2.1");
  }

  @Example
  public void testSelectionNoMatchEncrypt() {
    testEncrypt(testSelectionNoMatch(), false);
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

  // check that the ballot can be encrypted and is valid
  void testEncrypt(ElectionAndBallot eandb, boolean expectValid) {
    ElGamal.KeyPair keypair = elgamal_keypair_from_secret(int_to_q_unchecked(BigInteger.TWO)).orElseThrow();
    ElectionBuilder.DescriptionAndContext tuple = ElectionFactory.get_fake_ciphertext_election(eandb.election, keypair.public_key()).orElseThrow();
    ElectionCryptoContext context = tuple.context;

    InternalManifest metadata = new InternalManifest(eandb.election);
    Encrypt.EncryptionDevice device = Encrypt.createDeviceForTest("device");

    Encrypt.EncryptionMediator encryptor = new Encrypt.EncryptionMediator(metadata, context, device);
    try {
      Optional<CiphertextBallot> encrypted_ballot = encryptor.encrypt(eandb.ballot);
      assertThat(encrypted_ballot).isPresent();
      if (!expectValid) {
        fail();
      }
    } catch (Throwable t) {
      if (expectValid) {
        fail();
      }
    }
  }
}
