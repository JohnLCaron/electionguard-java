package com.sunya.electionguard.publish;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.sunya.electionguard.Ballot;
import com.sunya.electionguard.Guardian;
import com.sunya.electionguard.KeyCeremony;
import com.sunya.electionguard.Tally;
import net.jqwik.api.Example;
import net.jqwik.api.lifecycle.BeforeProperty;

import java.io.File;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static com.google.common.truth.Truth.assertThat;
import static com.sunya.electionguard.Election.*;
import static com.sunya.electionguard.Group.*;

public class TestPublish {
  String outputDir;

  @BeforeProperty
  public void setUp() throws IOException {
    Path tempPath = Files.createTempDirectory("electionguardPublish.");
    File tempDir = tempPath.toFile();
    tempDir.deleteOnExit();
    outputDir = tempDir.getAbsolutePath(); // "/home/snake/tmp/testPublish";
    System.out.printf("outputDir %s%n", outputDir);
  }

  @Example
  public void test_publish() throws IOException {
    OffsetDateTime now = OffsetDateTime.now();
    ElectionDescription description = new ElectionDescription(
            "scope", ElectionType.unknown, now, now, ImmutableList.of(), ImmutableList.of(),
            ImmutableList.of(), ImmutableList.of(), ImmutableList.of(), null, null);
    InternalElectionDescription metadata = new InternalElectionDescription(description);

    CiphertextElectionContext context = make_ciphertext_election_context(1, 1, ONE_MOD_P, description);
    List<KeyCeremony.CoefficientValidationSet> coefficients = ImmutableList.of(
            KeyCeremony.CoefficientValidationSet.create("hiD", ImmutableList.of(), ImmutableList.of()));
    Tally.PlaintextTally plaintext_tally = Tally.PlaintextTally.create("PlaintextTallyId", ImmutableMap.of(), ImmutableMap.of());

    Tally.PublishedCiphertextTally ciphertext_tally =
            new Tally.CiphertextTally("CiphertextTallyId", metadata, context).publish_ciphertext_tally();

    Publisher publisher = new Publisher(outputDir, false);
    publisher.writeElectionRecordJson(
            description,
            context,
            new ElectionConstants(),
            ImmutableList.of(),
            ImmutableList.of(),
            ImmutableList.of(),
            ciphertext_tally,
            plaintext_tally,
            coefficients);

    File testFile = new File(outputDir, Publisher.DEVICES_DIR);
    assertThat(testFile.exists()).isTrue();
  }

  @Example
  public void test_publish_private_data() throws IOException {
    List<Ballot.PlaintextBallot> plaintext_ballots = ImmutableList.of(
            new Ballot.PlaintextBallot("PlaintextBallotId", "ballot_style", ImmutableList.of()));
    List<Ballot.CiphertextBallot> encrypted_ballots = ImmutableList.of(
            Ballot.make_ciphertext_ballot("CipherTextBallotId", "ballot_style", int_to_q_unchecked(BigInteger.ZERO),
                    Optional.of(int_to_q_unchecked(BigInteger.ZERO)), ImmutableList.of(), Optional.empty(),Optional.empty(), Optional.empty()));

    List<Guardian> guardians = ImmutableList.of(Guardian.createForTesting("GuardianId", 1, 1, 1, null));

    Publisher publisher = new Publisher(outputDir, false);
    publisher.publish_private_data(
            plaintext_ballots,
            encrypted_ballots,
            guardians);

    File testFile = new File(outputDir, Publisher.PRIVATE_DIR);
    assertThat(testFile.exists()).isTrue();
  }

}
