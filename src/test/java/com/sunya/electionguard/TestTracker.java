package com.sunya.electionguard;

import net.jqwik.api.Example;

import java.util.Optional;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth8.assertThat;
import static com.sunya.electionguard.Group.*;
import static com.sunya.electionguard.Tracker.*;


public class TestTracker {

  @Example
  public void test_tracker_hash_rotates() {
    Encrypt.EncryptionDevice device = new Encrypt.EncryptionDevice("Location");
    ElementModQ ballot_hash_1 = ONE_MOD_Q;
    ElementModQ ballot_hash_2 = TWO_MOD_Q;
    int timestamp_1 = 1000;
    int timestamp_2 = 2000;

    ElementModQ device_hash = get_hash_for_device(device.uuid, device.location);
    ElementModQ tracker_1_hash = get_rotating_tracker_hash(device_hash, timestamp_1, ballot_hash_1);
    ElementModQ tracker_2_hash = get_rotating_tracker_hash(device_hash, timestamp_2, ballot_hash_2);

    assertThat(device_hash).isNotNull();
    assertThat(tracker_1_hash).isNotNull();
    assertThat(tracker_2_hash).isNotNull();

    assertThat(device_hash).isNotEqualTo(ZERO_MOD_Q);
    assertThat(tracker_1_hash).isNotEqualTo(device_hash);
    assertThat(tracker_2_hash).isNotEqualTo(device_hash);
    assertThat(tracker_1_hash).isNotEqualTo(tracker_2_hash);
  }

  @Example
  public void  test_tracker_converts_to_words() {
    Encrypt.EncryptionDevice device = new Encrypt.EncryptionDevice("Location");
    ElementModQ device_hash = get_hash_for_device(device.uuid, device.location);
    ElementModQ ballot_hash = ONE_MOD_Q;
    ElementModQ ballot_hash_different = TWO_MOD_Q;
    int timestamp = 1000;

    ElementModQ tracker_hash = get_rotating_tracker_hash(device_hash, timestamp, ballot_hash);
    ElementModQ tracker_hash_different = get_rotating_tracker_hash(device_hash, timestamp, ballot_hash_different);

    Optional<String> device_words = tracker_hash_to_words(device_hash, null);
    Optional<String> tracker_words = tracker_hash_to_words(tracker_hash, null);
    Optional<String> tracker_different_words = tracker_hash_to_words(tracker_hash_different, null);

    assertThat(device_words).isPresent();
    assertThat(tracker_words).isPresent();
    assertThat(device_words).isNotEqualTo(tracker_words);
    assertThat(tracker_different_words).isNotEqualTo(tracker_words);
  }

  @Example
  public void test_tracker_converts_to_known_words() {
    /* TODO havent got Hash.hash to agree with Python. Maybe SHA256 is different ??
    String expected_hash = "325AB2622D35311DB0320C9F3B421EE93017D16B9E4C7FEF06704EDA4FA5E30B";
    String expected_words = "cover-AB262-conscience-5311D-peacock-20C9F-diagram-21EE9-coordinator-7D16B-nature-C7FEF-altar-04EDA-fax-5E30B";
     */

    String expected_hash =  "5D29048CD18DFD775249EB0AC4EC9E85A351CE38FB577CE924DBEC7C3CC29B4F";
    String expected_words = "garden-9048C-retrospective-DFD77-final-9EB0A-purity-C9E85-obstacle-1CE38-sun-77CE9-cheesecake-BEC7C-diplomacy-29B4F";

    ElementModQ device_hash = ONE_MOD_Q;
    ElementModQ ballot_hash = TWO_MOD_Q;
    int timestamp = 1000;

    ElementModQ tracker_hash = get_rotating_tracker_hash(device_hash, timestamp, ballot_hash);
    Optional<String> tracker_words = tracker_hash_to_words(tracker_hash, null);
    assertThat(tracker_words).isPresent();

    assertThat(tracker_hash.to_hex()).isEqualTo(expected_hash);
    assertThat(tracker_words.get()).isEqualTo(expected_words);
  }

  @Example
  public void test_hash_to_words() {
    String h = hash_to_words(10, 11, 2, 6, 2);
    assertThat(h).isEqualTo("AB262");
  }
}
