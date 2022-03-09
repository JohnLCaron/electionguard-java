package com.sunya.electionguard;

import net.jqwik.api.Example;

import static com.google.common.truth.Truth.assertThat;
import static com.sunya.electionguard.Group.*;
import static com.sunya.electionguard.BallotCodes.*;


public class TestBallotCodes {

  @Example
  public void test_rotate_ballot_code() {
    Encrypt.EncryptionDevice device = Encrypt.createDeviceForTest("Location");
    ElementModQ ballot_hash_1 = ONE_MOD_Q;
    ElementModQ ballot_hash_2 = TWO_MOD_Q;
    int timestamp_1 = 1000;
    int timestamp_2 = 2000;

    ElementModQ device_hash = get_hash_for_device(device.deviceId(), device.sessionId(), device.launchCode(), device.location());
    ElementModQ tracker_1_hash = get_rotating_ballot_code(device_hash, timestamp_1, ballot_hash_1);
    ElementModQ tracker_2_hash = get_rotating_ballot_code(device_hash, timestamp_2, ballot_hash_2);

    assertThat(device_hash).isNotNull();
    assertThat(tracker_1_hash).isNotNull();
    assertThat(tracker_2_hash).isNotNull();

    assertThat(device_hash).isNotEqualTo(ZERO_MOD_Q);
    assertThat(tracker_1_hash).isNotEqualTo(device_hash);
    assertThat(tracker_2_hash).isNotEqualTo(device_hash);
    assertThat(tracker_1_hash).isNotEqualTo(tracker_2_hash);
  }
}
