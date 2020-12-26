package com.sunya.electionguard.publish;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.sunya.electionguard.Ballot;
import com.sunya.electionguard.Guardian;
import com.sunya.electionguard.KeyCeremony;
import com.sunya.electionguard.Tally;
import net.jqwik.api.Example;
import net.jqwik.api.lifecycle.BeforeProperty;
import org.junit.Rule;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.math.BigInteger;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static com.google.common.truth.Truth.assertThat;
import static com.sunya.electionguard.Election.*;
import static com.sunya.electionguard.Group.*;


public class TestPublish {

  @Rule
  public static TemporaryFolder tempFolder = new TemporaryFolder();
  String outputDir;

  @BeforeProperty
  public void setUp() throws IOException {
    outputDir = "/home/snake/tmp/testPublish"; // tempFolder.newFile().getAbsolutePath();
  }

  @Example
  public void test_publish() throws IOException {
    LocalDate now = LocalDate.now();
    ElectionDescription description = new ElectionDescription(
            "", ElectionType.unknown, now, now, ImmutableList.of(), ImmutableList.of(),
            ImmutableList.of(), ImmutableList.of(), ImmutableList.of(), null, null);
    InternalElectionDescription metadata = new InternalElectionDescription(description);

    CiphertextElectionContext context = make_ciphertext_election_context(1, 1, ONE_MOD_P, ONE_MOD_Q);
    List<KeyCeremony.CoefficientValidationSet> coefficients = ImmutableList.of(
            KeyCeremony.CoefficientValidationSet.create("", ImmutableList.of(), ImmutableList.of()));
    Tally.PlaintextTally plaintext_tally = new Tally.PlaintextTally("", ImmutableMap.of(), ImmutableMap.of());

    Tally.PublishedCiphertextTally ciphertext_tally = Tally.publish_ciphertext_tally(
            new Tally.CiphertextTally("", metadata, context));

    Publish.publish(
            description,
            context,
            new ElectionConstants(),
            ImmutableList.of(),
            ImmutableList.of(),
            ImmutableList.of(),
            ciphertext_tally,
            plaintext_tally,
            coefficients,
            outputDir);

    File testFile = new File(outputDir, Publish.DEVICES_DIR);
    assertThat(testFile.exists()).isTrue();
  }

  @Example
  public void test_publish_private_data() throws IOException {
    List<Ballot.PlaintextBallot> plaintext_ballots = ImmutableList.of(new Ballot.PlaintextBallot("", "",ImmutableList.of()));
    List<Ballot.CiphertextBallot> encrypted_ballots = ImmutableList.of(
            Ballot.make_ciphertext_ballot("", "", int_to_q_unchecked(BigInteger.ZERO),
                    Optional.of(int_to_q_unchecked(BigInteger.ZERO)), ImmutableList.of(), Optional.empty(),Optional.empty(),Optional.empty()));

    List<Guardian> guardians = ImmutableList.of( new Guardian("", 1, 1, 1, null));

    Publish.publish_private_data(
            plaintext_ballots,
            encrypted_ballots,
            guardians, outputDir);

    File testFile = new File(outputDir, Publish.PRIVATE_DIR);
    assertThat(testFile.exists()).isTrue();
  }

}
