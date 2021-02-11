package com.sunya.electionguard.workflow;

import com.google.common.collect.ImmutableList;
import com.sunya.electionguard.Election;
import com.sunya.electionguard.Group;
import com.sunya.electionguard.Guardian;
import com.sunya.electionguard.GuardianBuilder;
import com.sunya.electionguard.Hash;
import com.sunya.electionguard.KeyCeremony;
import com.sunya.electionguard.KeyCeremonyMediator;
import com.sunya.electionguard.proto.KeyCeremonyFromProto;
import com.sunya.electionguard.proto.KeyCeremonyProto;
import com.sunya.electionguard.proto.KeyCeremonyToProto;
import com.sunya.electionguard.publish.Consumer;
import com.sunya.electionguard.publish.Publisher;
import net.jqwik.api.Example;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static com.google.common.truth.Truth.assertThat;

/** KeyCeremony to create the Guardians. */
public class TestKeyCeremonySerializing {

  static class RandomCoefficientsProvider implements CoefficientsProvider {
    final int nguardians;
    final int quorum;

    public RandomCoefficientsProvider(int nguardians, int quorum) {
      this.nguardians = nguardians;
      this.quorum = quorum;
    }

    @Override
    public int quorum() {
      return quorum;
    }

    @Override
    public Iterable<KeyCeremony.CoefficientSet> guardianCoefficients() {
      ArrayList<KeyCeremony.CoefficientSet> coeffSets = new ArrayList<>();
      for (int k = 0; k < this.nguardians; k++) {
        int sequence = k + 1;
        ArrayList<Group.ElementModQ> coefficients = new ArrayList<>();
        for (int j = 0; j < this.quorum; j++) {
          coefficients.add(Group.rand_q()); // ramdomly chosen
        }
        coeffSets.add(KeyCeremony.CoefficientSet.create("guardian_" + sequence, sequence, coefficients));
      }
      return coeffSets;
    }
  }

  ///////////////////////////////////////////////////////////////////////////
  final int numberOfGuardians = 4;
  final int quorum = 4;
  final Election.ElectionDescription election;
  final String inputDir = "/home/snake/tmp/electionguard/publishEndToEnd";
  final String outputDir = "/home/snake/tmp/electionguard/publishTest";
  final Publisher publisher;

  Group.ElementModP jointKey;
  Election.CiphertextElectionContext context;
  Group.ElementModQ crypto_base_hash;

  List<GuardianBuilder> guardianBuilders;
  List<KeyCeremony.CoefficientValidationSet> coefficientValidationSets = new ArrayList<>();
  List<Guardian> guardians;

  public TestKeyCeremonySerializing() throws IOException {
    Consumer consumer = new Consumer(inputDir);
    this.election = consumer.election();
    CoefficientsProvider coefficientsProvider = new RandomCoefficientsProvider(numberOfGuardians, quorum);
    System.out.printf("  Create %d Guardians, quorum = %d%n", this.numberOfGuardians, this.quorum);

    this.crypto_base_hash = Election.make_crypto_base_hash(this.numberOfGuardians, this.quorum, election);

    this.guardianBuilders = new ArrayList<>();
    for (KeyCeremony.CoefficientSet coeffSet : coefficientsProvider.guardianCoefficients()) {
      this.guardianBuilders.add(GuardianBuilder.createRandom(coeffSet, numberOfGuardians, quorum));
    }

    System.out.printf("%nKey Ceremony%n");
    if (!keyCeremony()) {
      throw new RuntimeException("*** Key Ceremony failed");
    }
    buildElectionContext(election, this.jointKey);

    this.publisher = new Publisher(outputDir, false, false);
    publish();
  }

  /**
   * Using the numberOfGuardians, generate public-private keypairs and share
   * representations of those keys with quorum of other Guardians.  Then, combine
   * the public election keys to make a joint election key that is used to encrypt ballots
   */
  boolean keyCeremony() {
    // Setup Mediator
    KeyCeremony.CeremonyDetails details =
            KeyCeremony.CeremonyDetails.create(this.numberOfGuardians,  this.quorum);
    KeyCeremonyMediator keyCeremony = new KeyCeremonyMediator(details);

    // Attendance (Public Key Share)
    for (GuardianBuilder guardian : this.guardianBuilders) {
      keyCeremony.announce(guardian);
    }

    System.out.printf(" Confirm all guardians have shared their public keys%n");
    if (!keyCeremony.all_guardians_in_attendance()) {
      System.out.printf(" *** FAILED%n");
      return false;
    }

    // Run the Key Ceremony process, which shares the keys among the guardians
    Optional<List<GuardianBuilder>> orchestrated = keyCeremony.orchestrate(null);
    System.out.printf(" Execute the key exchange between guardians%n");
    if (orchestrated.isEmpty()) {
      System.out.printf(" *** FAILED%n");
      return false;
    }

    System.out.printf(" Confirm all guardians have shared their partial key backups%n");
    if (!keyCeremony.all_election_partial_key_backups_available()) {
      System.out.printf(" *** FAILED%n");
      return false;
    }

    // Verification
    boolean verified = keyCeremony.verify(null);
    System.out.printf(" Confirm all guardians truthfully executed the ceremony%n");
    if (!verified) {
      System.out.printf(" *** FAILED%n");
      return false;
    }

    System.out.printf(" Confirm all guardians have submitted a verification of the backups of all other guardians%n");
    if (!keyCeremony.all_election_partial_key_verifications_received()) {
      System.out.printf(" *** FAILED%n");
      return false;
    }

    System.out.printf(" Confirm all guardians have verified the backups of all other guardians%n");
    if (!keyCeremony.all_election_partial_key_backups_verified()) {
      System.out.printf(" *** FAILED%n");
      return false;
    }

    // Joint Key
    Optional<Group.ElementModP> joint_key = keyCeremony.publish_joint_key();
    System.out.printf(" Create the Joint Election Key%n");
    if (joint_key.isEmpty()) {
      System.out.printf(" *** FAILED%n");
      return false;
    }
    this.jointKey = joint_key.get();

    // Save Validation Keys
    for (GuardianBuilder guardian : this.guardianBuilders) {
      this.coefficientValidationSets.add(guardian.share_coefficient_validation_set());
    }

    return true;
  }

  void buildElectionContext(Election.ElectionDescription description, Group.ElementModP joint_key) {
    this.context = Election.make_ciphertext_election_context(
            this.numberOfGuardians,
            this.quorum,
            joint_key,
            description);
  }

  void publish() throws IOException {
    publisher.writeKeyCeremonyProto(
            this.election,
            this.context,
            new Election.ElectionConstants(),
            this.coefficientValidationSets);

    this.guardians = guardianBuilders.stream().map(GuardianBuilder::build).collect(Collectors.toList());
    KeyCeremonyProto.Guardians guardianProto = KeyCeremonyToProto.convertGuardians(guardians, this.quorum);

    publisher.writeGuardiansProto(guardianProto);
  }

  @Example
  public void checkGuardians() throws IOException {
    ImmutableList<Guardian> roundtrip = KeyCeremonyFromProto.readGuardians(this.publisher.guardiansPath().toString());

    assertThat(roundtrip).hasSize(this.guardians.size());
    for (Guardian guardian : this.guardians) {
      System.out.printf("Test Guardian %s%n", guardian.object_id);
      Guardian rguardian = roundtrip.stream().filter(g -> g.object_id.equals(guardian.object_id)).findFirst().orElseThrow();
      assertThat(rguardian.equals(guardian)).isTrue();
      assertThat(rguardian).isEqualTo(guardian);
    }

  }

}
