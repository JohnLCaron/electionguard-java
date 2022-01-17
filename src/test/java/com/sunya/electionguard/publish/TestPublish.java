package com.sunya.electionguard.publish;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.sunya.electionguard.CiphertextElectionContext;
import com.sunya.electionguard.CiphertextTallyBuilder;
import com.sunya.electionguard.ElectionFactory;
import com.sunya.electionguard.ElectionTestHelper;
import com.sunya.electionguard.Group;
import com.sunya.electionguard.GuardianRecord;
import com.sunya.electionguard.GuardianRecordPrivate;
import com.sunya.electionguard.Manifest;
import com.sunya.electionguard.InternalManifest;
import com.sunya.electionguard.Guardian;
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
import java.util.Optional;
import java.util.Random;

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

    CiphertextElectionContext context = CiphertextElectionContext.create(1, 1, ONE_MOD_P, election, rand_q(), null);
    Guardian guardian = Guardian.createForTesting("GuardianId", 1, 1, 1, null);
    List<GuardianRecord> coefficients = ImmutableList.of(guardian.publish());
    PlaintextTally plaintext_tally = new PlaintextTally("PlaintextTallyId", ImmutableMap.of());

    CiphertextTally ciphertext_tally =
            new CiphertextTallyBuilder("CiphertextTallyId", metadata, context).build();

    Publisher publisher = new Publisher(outputDir, false, true);
    publisher.writeElectionRecordJson(
            election,
            context,
            Group.getPrimes(),
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
    ElectionTestHelper helper = new ElectionTestHelper(new Random());
    Manifest manifest = ElectionFactory.get_simple_election_from_file();
    List<PlaintextBallot> original_ballots = helper.plaintext_voted_ballots(new InternalManifest(manifest), 1);

    Guardian g = Guardian.createForTesting("GuardianId", 1, 1, 1, null);
    List<GuardianRecordPrivate> guardians = ImmutableList.of(g.export_private_data());
    Publisher publisher = new Publisher(outputDir, false, false);
    publisher.publish_private_data(
            original_ballots,
            guardians);

    Consumer consumer = new Consumer(publisher);
    List<PlaintextBallot> inputs = consumer.inputBallots(publisher.privateBallotsPath().toString());
    assertThat(inputs).hasSize(1);
    assertThat(inputs.get(0)).isEqualTo(original_ballots.get(0));

    List<GuardianRecordPrivate> results = consumer.readGuardianPrivateJson();
    assertThat(results).hasSize(1);
    assertThat(results.get(0)).isEqualTo(guardians.get(0));
  }

}
