package com.sunya.electionguard.standard;

import com.sunya.electionguard.ElectionCryptoContext;
import com.sunya.electionguard.ElectionFactory;
import com.sunya.electionguard.Group;
import com.sunya.electionguard.GuardianRecord;
import com.sunya.electionguard.Hash;
import com.sunya.electionguard.Manifest;
import com.sunya.electionguard.json.JsonConsumer;
import com.sunya.electionguard.json.JsonPrivateData;
import com.sunya.electionguard.json.JsonPublisher;
import com.sunya.electionguard.publish.PublisherOld;
import net.jqwik.api.Example;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static com.google.common.truth.Truth.assertThat;
import static com.sunya.electionguard.CompareHelper.compareGuardianRecord;

/** KeyCeremony to create the Guardians. */
public class TestGuardianSerializing {

  static class RandomCoefficientsProvider {
    final int nguardians;
    final int quorum;

    public RandomCoefficientsProvider(int nguardians, int quorum) {
      this.nguardians = nguardians;
      this.quorum = quorum;
    }

    public int quorum() {
      return quorum;
    }

    public Iterable<KeyCeremony.CoefficientSet> guardianCoefficients() {
      ArrayList<KeyCeremony.CoefficientSet> coeffSets = new ArrayList<>();
      for (int k = 0; k < this.nguardians; k++) {
        int sequence = k + 1;
        ArrayList<Group.ElementModQ> coefficients = new ArrayList<>();
        for (int j = 0; j < this.quorum; j++) {
          coefficients.add(Group.rand_q()); // ramdomly chosen
        }
        coeffSets.add(new KeyCeremony.CoefficientSet("guardian_" + sequence, sequence, coefficients));
      }
      return coeffSets;
    }
  }

  ///////////////////////////////////////////////////////////////////////////
  final int numberOfGuardians = 4;
  final int quorum = 4;
  final Manifest election;
  final String outputDir;
  final JsonPublisher publisher;
  final JsonPrivateData pdata;

  KeyCeremony.ElectionJointKey jointKey;
  List<Guardian> guardians;
  List<GuardianRecord> guardianRecords;

  public TestGuardianSerializing() throws IOException {
    this.election = ElectionFactory.get_hamilton_election_from_file();
    RandomCoefficientsProvider coefficientsProvider = new RandomCoefficientsProvider(numberOfGuardians, quorum);
    System.out.printf("  Create %d Guardians, quorum = %d%n", this.numberOfGuardians, this.quorum);

    Path tmp = Files.createTempDirectory(null);
    tmp.toFile().deleteOnExit();
    this.outputDir = "/home/snake/tmp/publishTmp"; // tmp.toAbsolutePath().toString();
    this.publisher = new JsonPublisher(outputDir, PublisherOld.Mode.createNew);
    this.pdata = new JsonPrivateData(outputDir, true, true);

    this.guardians = new ArrayList<>();
    this.guardianRecords = new ArrayList<>();
    List<Group.ElementModP> commitments = new ArrayList<>();
    for (KeyCeremony.CoefficientSet coeffSet : coefficientsProvider.guardianCoefficients()) {
      Guardian guardian = Guardian.createRandom(coeffSet, numberOfGuardians, quorum);
      this.guardians.add(guardian);
      this.guardianRecords.add(guardian.publish());
      GuardianRecord coeffValidSet = guardian.publish();
      commitments.addAll(coeffValidSet.coefficientCommitments());
    }
    Group.ElementModQ commitmentsHash = Hash.hash_elems(commitments);

    System.out.printf("%nKey Ceremony%n");
    this.jointKey = keyCeremony(guardians);

    ElectionCryptoContext context = ElectionCryptoContext.create(
            this.numberOfGuardians,
            this.quorum,
            this.jointKey.joint_public_key(),
            this.election,
            commitmentsHash,
            null);

    publish(context);
  }

  KeyCeremony.ElectionJointKey keyCeremony(List<Guardian> guardians) {
    // Setup Mediator
    KeyCeremony.CeremonyDetails details =
            new KeyCeremony.CeremonyDetails(numberOfGuardians, quorum);
    KeyCeremonyMediator keyCeremony = new KeyCeremonyMediator("TestKeyCeremonyProtoRoundtrip", details);

    KeyCeremonyHelper.perform_full_ceremony(guardians, keyCeremony);
    return keyCeremony.publish_joint_key().orElseThrow();
  }

  void publish(ElectionCryptoContext context) throws IOException {
    publisher.writeKeyCeremonyJson(
            this.election,
            context,
            Group.getPrimes(),
            this.guardianRecords);

    List<GuardianPrivateRecord> gprivate = this.guardians.stream().map(g -> g.export_private_data()).toList();
    pdata.publish_private_data(null, gprivate);
  }

  @Example
  public void checkGuardianRecord() throws IOException {
    JsonConsumer consumer = new JsonConsumer(this.publisher);
    List<GuardianRecord> guardianRecords = consumer.guardianRecords();
    assertThat(guardianRecords).hasSize(this.guardians.size());

    for (Guardian guardian : this.guardians) {
      System.out.printf("Test Guardian %s%n", guardian.object_id);
      GuardianRecord guardianRecord = guardian.publish();
      GuardianRecord guardianPrivateRoundtrip = guardianRecords.stream().filter(g -> g.guardianId().equals(guardian.object_id)).findFirst().orElseThrow();
      compareGuardianRecord(guardianPrivateRoundtrip, guardianRecord);
      assertThat(guardianPrivateRoundtrip).isEqualTo(guardianRecord);
      assertThat(guardianPrivateRoundtrip.equals(guardianRecord)).isTrue();
    }
  }

  @Example
  public void checkGuardianPrivate() throws IOException {
    List<GuardianPrivateRecord> roundtrip = pdata.readGuardianPrivateJson();
    assertThat(roundtrip).hasSize(this.guardians.size());

    for (Guardian guardian : this.guardians) {
      System.out.printf("Test Guardian %s%n", guardian.object_id);
      GuardianPrivateRecord guardianPrivate = guardian.export_private_data();
      GuardianPrivateRecord guardianPrivateRoundtrip = roundtrip.stream().filter(g -> g.guardian_id().equals(guardian.object_id)).findFirst().orElseThrow();
      //assertThat(guardianPrivateRoundtrip.equals(guardianPrivate)).isTrue();
      assertThat(guardianPrivateRoundtrip).isEqualTo(guardianPrivate);
    }
  }

}
