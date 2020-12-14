package sunya.electionguard;

import static sunya.electionguard.Group.*;

public class Tracker {

  /**
   *     Get starting hash for given device
   *     :param uuid: Unique identifier of device
   *     :param location: Location of device
   *     :return: Starting hash of device
   */
  static ElementModQ get_hash_for_device(long uuid, String location) {
    return Hash.hash_elems(uuid, location);
  }

  /**
   *     Get the rotated tracker hash for a particular ballot.
   *     :param prev_hash: Previous hash or starting hash from device
   *     :param timestamp: Timestamp in ticks
   *     :param ballot_hash: Hash of ballot to track
   *     :return: Tracker hash
   */
  static ElementModQ get_rotating_tracker_hash(ElementModQ prev_hash, long timestamp, ElementModQ ballot_hash) {
    return Hash.hash_elems(prev_hash, timestamp, ballot_hash);
  }
}
