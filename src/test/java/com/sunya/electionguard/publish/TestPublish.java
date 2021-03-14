package com.sunya.electionguard.publish;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.sunya.electionguard.CiphertextElectionContext;
import com.sunya.electionguard.CiphertextTallyBuilder;
import com.sunya.electionguard.ElectionConstants;
import com.sunya.electionguard.Manifest;
import com.sunya.electionguard.InternalManifest;
import com.sunya.electionguard.Guardian;
import com.sunya.electionguard.GuardianBuilder;
import com.sunya.electionguard.KeyCeremony;
import com.sunya.electionguard.CiphertextTally;
import com.sunya.electionguard.PlaintextBallot;
import com.sunya.electionguard.PlaintextTally;
import net.jqwik.api.Example;
import net.jqwik.api.lifecycle.BeforeProperty;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.List;

import static com.google.common.truth.Truth.assertThat;
import static com.sunya.electionguard.Group.*;

public class TestPublish {
  String outputDir;

  @BeforeProperty
  public void setUp() throws IOException {
    Path tempPath = Files.createTempDirectory("electionguardPublish.");
    File tempDir = tempPath.toFile();
    tempDir.deleteOnExit();
    outputDir = tempDir.getAbsolutePath();
    System.out.printf("outputDir %s%n", outputDir);
  }

  @Example
  public void test_publish() throws IOException {
    OffsetDateTime now = OffsetDateTime.now();
    Manifest election = new Manifest(
            "scope", Manifest.ElectionType.unknown, now, now, ImmutableList.of(), ImmutableList.of(),
            ImmutableList.of(), ImmutableList.of(), ImmutableList.of(), null, null);
    InternalManifest metadata = new InternalManifest(election);

    CiphertextElectionContext context = CiphertextElectionContext.create(1, 1, ONE_MOD_P, election, rand_q());
    List<KeyCeremony.CoefficientValidationSet> coefficients = ImmutableList.of(
            KeyCeremony.CoefficientValidationSet.create("hiD", ImmutableList.of(), ImmutableList.of()));
    PlaintextTally plaintext_tally = new PlaintextTally("PlaintextTallyId", ImmutableMap.of());

    CiphertextTally ciphertext_tally =
            new CiphertextTallyBuilder("CiphertextTallyId", metadata, context).build();

    Publisher publisher = new Publisher(outputDir, false, true);
    publisher.writeElectionRecordJson(
            election,
            context,
            new ElectionConstants(),
            ImmutableList.of(),
            ImmutableList.of(),
            ciphertext_tally,
            plaintext_tally,
            coefficients,
            null, null);

    File testFile = new File(outputDir, Publisher.DEVICES_DIR);
    assertThat(testFile.exists()).isTrue();
  }

  @Example
  public void test_publish_private_data() throws IOException {
    List<PlaintextBallot> plaintext_ballots = ImmutableList.of(
            new PlaintextBallot("PlaintextBallotId", "ballot_style", ImmutableList.of()));

    List<Guardian> guardians = ImmutableList.of(
            GuardianBuilder.createForTesting("GuardianId", 1, 1, 1, null).build());

    Publisher publisher = new Publisher(outputDir, false, false);
    publisher.publish_private_data(
            plaintext_ballots,
            guardians);

    File testFile = new File(outputDir, Publisher.PRIVATE_DIR);
    assertThat(testFile.exists()).isTrue();
  }

}
