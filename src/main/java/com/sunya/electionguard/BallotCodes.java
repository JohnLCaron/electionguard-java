package com.sunya.electionguard;

/** Static methods for ballot coides, aka tracking codes. */
public class BallotCodes {

  /**
   * Get starting hash for given device
   */
  public static Group.ElementModQ get_hash_for_device(long uuid, String session_id, int launch_code, String location) {
    return Hash.hash_elems(uuid, session_id, launch_code, location);
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

}
