package com.sunya.electionguard.proto;

import com.google.common.collect.ImmutableList;
import com.sunya.electionguard.Group;
import com.sunya.electionguard.Guardian;
import com.sunya.electionguard.GuardianBuilder;
import com.sunya.electionguard.KeyCeremony;
import com.sunya.electionguard.KeyCeremonyMediator;
import com.sunya.electionguard.publish.Publisher;
import com.sunya.electionguard.workflow.CoefficientsProvider;
import net.jqwik.api.Example;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static com.google.common.truth.Truth.assertThat;

public class TestKeyCeremonyProtoRoundtrip {
  private static final String DIR = "/home/snake/tmp/electionguard/publishTest/";
  private static final String PB = "/home/snake/tmp/electionguard/publishTest/private/guardians.protobuf";
  private static final int NGUARDIANS = 6;
  private static final int QUORUM = 5;

  @Example
  public void testGuardiansRoundtrip() throws IOException {
    // create guardians
    CoefficientsProvider coefficientsProvider = new RandomCoefficientsProvider(NGUARDIANS, QUORUM);
    ArrayList<GuardianBuilder> guardianBuilders = new ArrayList<>();
    for (KeyCeremony.CoefficientSet coeffSet : coefficientsProvider.guardianCoefficients()) {
      guardianBuilders.add(GuardianBuilder.createRandom(coeffSet, NGUARDIANS, QUORUM));
    }
    assertThat(keyCeremony(guardianBuilders)).isTrue();

    // write
    List<Guardian> guardians = guardianBuilders.stream().map(gb -> gb.build()).collect(Collectors.toList());
    KeyCeremonyProto.Guardians guardianProto = KeyCeremonyToProto.convertGuardians(guardians, QUORUM);
    Publisher publisher = new Publisher(DIR, false, false);
    publisher.writeGuardiansProto(guardianProto);

    // read back and compare
    ImmutableList<Guardian> roundtrips = KeyCeremonyFromProto.readGuardians(PB);
    assertThat(roundtrips.size()).isEqualTo(guardians.size());
    for (Guardian roundtrip : roundtrips) {
      Guardian org = guardians.stream().filter(g -> g.object_id.equals(roundtrip.object_id)).findFirst().orElseThrow();
      assertThat(roundtrip).isEqualTo(org);
    }
  }

  boolean keyCeremony(ArrayList<GuardianBuilder> guardianBuilders) {
    // Setup Mediator
    com.sunya.electionguard.KeyCeremony.CeremonyDetails details =
            com.sunya.electionguard.KeyCeremony.CeremonyDetails.create(NGUARDIANS, QUORUM);
    KeyCeremonyMediator keyCeremony = new KeyCeremonyMediator(details);

    // Attendance (Public Key Share)
    for (GuardianBuilder guardian : guardianBuilders) {
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
    System.out.printf(" Create the Joint Manifest Key%n");
    if (joint_key.isEmpty()) {
      System.out.printf(" *** FAILED%n");
      return false;
    }
    return true;
  }

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
    public Iterable<com.sunya.electionguard.KeyCeremony.CoefficientSet> guardianCoefficients() {
      ArrayList<com.sunya.electionguard.KeyCeremony.CoefficientSet> coeffSets = new ArrayList<>();
      for (int k = 0; k < this.nguardians; k++) {
        int sequence = k + 1;
        ArrayList<Group.ElementModQ> coefficients = new ArrayList<>();
        for (int j = 0; j < this.quorum; j++) {
          coefficients.add(Group.rand_q()); // ramdomly chosen
        }
        coeffSets.add(com.sunya.electionguard.KeyCeremony.CoefficientSet.create("guardian_" + sequence, sequence, coefficients));
      }
      return coeffSets;
    }
  }
}
