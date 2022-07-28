package com.sunya.electionguard.keyceremony;

import com.google.common.collect.ImmutableList;
import com.sunya.electionguard.Group;
import com.sunya.electionguard.Manifest;
import com.sunya.electionguard.input.ManifestInputBuilder;
import com.sunya.electionguard.simulate.KeyCeremonyTrusteeSimulator;
import net.jqwik.api.Example;

import java.util.ArrayList;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth8.assertThat;
import static org.junit.Assert.fail;

public class TestKeyCeremonyRemoteMediator {

  private static final String GUARDIAN1_ID = "Guardian 1";
  private static final int GUARDIAN1_X_COORDINATE = 11;
  private static final int QUORUM = 3;
  private static final String GUARDIAN2_ID = "Guardian 2";
  private static final int GUARDIAN2_X_COORDINATE = 99;
  private static final String GUARDIAN3_ID = "Guardian 3";
  private static final int GUARDIAN3_X_COORDINATE = 1;

  Manifest manifest;

  public TestKeyCeremonyRemoteMediator() {
    ManifestInputBuilder ebuilder = new ManifestInputBuilder("ballot_id");
    this.manifest = ebuilder.addContest("contest_id")
            .addSelection("selection_id", "candidate_1")
            .addSelection("selection_id2", "candidate_2")
            .done()
            .build();
  }

  private KeyCeremonyRemoteMediator makeMediator() {
    KeyCeremonyTrustee trustee1 = new KeyCeremonyTrustee(GUARDIAN1_ID, GUARDIAN1_X_COORDINATE, QUORUM, null);
    KeyCeremonyTrustee trustee2 = new KeyCeremonyTrustee(GUARDIAN2_ID, GUARDIAN2_X_COORDINATE, QUORUM, null);
    KeyCeremonyTrustee trustee3 = new KeyCeremonyTrustee(GUARDIAN3_ID, GUARDIAN3_X_COORDINATE, QUORUM, null);

    return new KeyCeremonyRemoteMediator(this.manifest, QUORUM,
            ImmutableList.of(new KeyCeremonyTrusteeSimulator(trustee1),
                    new KeyCeremonyTrusteeSimulator(trustee2),
                    new KeyCeremonyTrusteeSimulator((trustee3))));
  }

  @Example
  public void testConstructor() {
    KeyCeremonyRemoteMediator mediator = makeMediator();

    assertThat(mediator.election).isEqualTo(this.manifest);
    assertThat(mediator.quorum).isEqualTo(QUORUM);
    assertThat(mediator.trusteeProxies).hasSize(QUORUM);
    assertThat(mediator.findTrusteeById(GUARDIAN1_ID).get().id()).isEqualTo(GUARDIAN1_ID);
    assertThat(mediator.findTrusteeById(GUARDIAN2_ID).get().id()).isEqualTo(GUARDIAN2_ID);
    assertThat(mediator.findTrusteeById(GUARDIAN3_ID).get().id()).isEqualTo(GUARDIAN3_ID);
    assertThat(mediator.findTrusteeById("Unknown")).isEmpty();

    assertThat(mediator.publicKeysMap).isEmpty();
    assertThat(mediator.guardianRecords).isEmpty();
    assertThat(mediator.commitmentsHash).isNull();
    assertThat(mediator.jointKey).isNull();
    assertThat(mediator.context).isNull();
  }

  @Example
  public void testDuplicateTrusteeId() {
    KeyCeremonyTrustee trustee1 = new KeyCeremonyTrustee(GUARDIAN1_ID, GUARDIAN1_X_COORDINATE, QUORUM, null);
    KeyCeremonyTrustee trustee2 = new KeyCeremonyTrustee(GUARDIAN2_ID, GUARDIAN2_X_COORDINATE, QUORUM, null);
    KeyCeremonyTrustee trustee3 = new KeyCeremonyTrustee(GUARDIAN2_ID, GUARDIAN3_X_COORDINATE, QUORUM, null);

    try {
      new KeyCeremonyRemoteMediator(this.manifest, QUORUM,
              ImmutableList.of(new KeyCeremonyTrusteeSimulator(trustee1),
                      new KeyCeremonyTrusteeSimulator(trustee2),
                      new KeyCeremonyTrusteeSimulator((trustee3))));
      fail();
    } catch (RuntimeException e) {
      assertThat(e.getMessage()).contains("Duplicate trustee id = ");
    }
  }

  @Example
  public void testDuplicateTrusteeCoord() {
    KeyCeremonyTrustee trustee1 = new KeyCeremonyTrustee(GUARDIAN1_ID, GUARDIAN1_X_COORDINATE, QUORUM, null);
    KeyCeremonyTrustee trustee2 = new KeyCeremonyTrustee(GUARDIAN2_ID, GUARDIAN2_X_COORDINATE, QUORUM, null);
    KeyCeremonyTrustee trustee3 = new KeyCeremonyTrustee(GUARDIAN3_ID, GUARDIAN2_X_COORDINATE, QUORUM, null);

    try {
      new KeyCeremonyRemoteMediator(this.manifest, QUORUM,
              ImmutableList.of(new KeyCeremonyTrusteeSimulator(trustee1),
                      new KeyCeremonyTrusteeSimulator(trustee2),
                      new KeyCeremonyTrusteeSimulator((trustee3))));
      fail();
    } catch (RuntimeException e) {
      assertThat(e.getMessage()).contains("Duplicate trustee xCoordinate = ");
    }
  }

  @Example
  public void testRound1() {
    KeyCeremonyRemoteMediator mediator = makeMediator();
    assertThat(mediator.trusteeProxies).hasSize(QUORUM);

    assertThat(mediator.round1()).isTrue();
    assertThat(mediator.guardianRecords).isEmpty();
    assertThat(mediator.commitmentsHash).isNull();
    assertThat(mediator.jointKey).isNull();
    assertThat(mediator.context).isNull();

    assertThat(mediator.publicKeysMap).hasSize(QUORUM);
    for (KeyCeremonyTrusteeIF trustee : mediator.trusteeProxies) {
      assertThat(mediator.publicKeysMap.get(trustee.id())).isNotNull();
    }
  }

  @Example
  public void testRound2() {
    KeyCeremonyRemoteMediator mediator = makeMediator();

    assertThat(mediator.round1()).isTrue();
    ArrayList<KeyCeremony2.PartialKeyVerification> failures = new ArrayList<>();
    assertThat(mediator.round2(failures)).isTrue();
    assertThat(failures).isEmpty();

    assertThat(mediator.guardianRecords).isEmpty();
    assertThat(mediator.commitmentsHash).isNull();
    assertThat(mediator.jointKey).isNull();
    assertThat(mediator.context).isNull();
  }

  @Example
  public void testRound3() {
    KeyCeremonyRemoteMediator mediator = makeMediator();

    assertThat(mediator.round1()).isTrue();
    assertThat(mediator.round2(new ArrayList<>())).isTrue();

    // suppose trustee1 challenges everyone else
    ArrayList<KeyCeremony2.PartialKeyVerification> failures = new ArrayList<>();
    failures.add(new KeyCeremony2.PartialKeyVerification(GUARDIAN1_ID, GUARDIAN2_ID, null));
    failures.add(new KeyCeremony2.PartialKeyVerification(GUARDIAN1_ID, GUARDIAN3_ID, null));

    assertThat(mediator.round3(failures)).isTrue();

    assertThat(mediator.guardianRecords).isEmpty();
    assertThat(mediator.commitmentsHash).isNull();
    assertThat(mediator.jointKey).isNull();
    assertThat(mediator.context).isNull();
  }

  @Example
  public void testRunKeyCeremony() {
    KeyCeremonyRemoteMediator mediator = makeMediator();
    mediator.runKeyCeremony();

    assertThat(mediator.guardianRecords).isNotEmpty();
    assertThat(mediator.commitmentsHash).isNotNull();
    assertThat(mediator.jointKey).isNotNull();
    assertThat(mediator.context).isNotNull();

    Group.ElementModP K = null;
    for (KeyCeremonyTrusteeIF trustee : mediator.trusteeProxies) {
      assertThat(trustee.sendJointPublicKey()).isPresent();
      Group.ElementModP trusteeK = trustee.sendJointPublicKey().get();
      if (K == null) {
        K = trusteeK;
      } else {
        assertThat(K).isEqualTo(trusteeK);
      }
    }

    for (KeyCeremonyTrusteeIF trustee : mediator.trusteeProxies) {
      KeyCeremonyTrustee trusteeImpl = ((KeyCeremonyTrusteeSimulator) trustee).delegate;
      assertThat(trusteeImpl.guardianPublicKeys).hasSize(3);
      assertThat(trusteeImpl.mySecretKeyShares).hasSize(2);
      assertThat(trusteeImpl.guardianSecretKeyShares).hasSize(2);
    }
  }
}
