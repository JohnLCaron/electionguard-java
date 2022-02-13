package com.sunya.electionguard;

import java.util.List;

/** Static methods for ballot codes, aka tracking codes. */
public class BallotCodes {

  /**
   * Get starting hash for given device
   *
   * @param device_id   Unique identifier of device
   * @param session_id  Unique identifier for the session
   * @param launch_code A unique launch code for the election
   * @param location    Location of device
   */
  public static Group.ElementModQ get_hash_for_device(long device_id, long session_id, long launch_code, String location) {
    return Hash.hash_elems(device_id, session_id, launch_code, location);
  }

  /**
   * Get the rotated code for a particular ballot.
   *
   * @param prev_code:   Previous code or starting hash from device
   * @param timestamp:   Timestamp in ticks
   * @param ballot_hash: Hash of ballot
   */
  public static Group.ElementModQ get_rotating_ballot_code(Group.ElementModQ prev_code, long timestamp, Group.ElementModQ ballot_hash) {
    return Hash.hash_elems(prev_code, timestamp, ballot_hash);
  }

  List<Integer> lengthsOfStrings(List<String> input) {
    return input.stream().map(String::length).toList();
  }

}
