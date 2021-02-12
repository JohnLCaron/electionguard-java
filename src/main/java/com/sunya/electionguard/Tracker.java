package com.sunya.electionguard;

import com.google.common.base.Preconditions;
import com.google.common.primitives.UnsignedBytes;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static com.sunya.electionguard.Group.*;

class Tracker {
  private static final String DEFAULT_SEPARATOR = "-";

  /**
   * Get starting hash for given device.
   * @param uuid:     Unique identifier of device
   * @param location: Location of device
   * @return Starting hash of device
   */
  static ElementModQ get_hash_for_device(String uuid, String location) {
    return Hash.hash_elems(uuid, location);
  }

  /**
   * Get the rotated tracker hash for a particular ballot.
   * @param prev_hash:   Previous hash or starting hash from device
   * @param timestamp:   Timestamp in ticks
   * @param ballot_hash: Hash of ballot to track
   * @return Tracker hash
   */
  static ElementModQ get_rotating_tracker_hash(ElementModQ prev_hash, long timestamp, ElementModQ ballot_hash) {
    return Hash.hash_elems(prev_hash, timestamp, ballot_hash);
  }

  /**
   * Convert tracker hash to human readable / friendly words.
   * @param tracker_hash: Tracker hash
   * @return Human readable tracker string or None
   */
  static Optional<String> tracker_hash_to_words(ElementModQ tracker_hash, @Nullable String separator) {
    if (separator == null) {
      separator = DEFAULT_SEPARATOR;
    }

    byte[] segments = tracker_hash.to_bytes();
    List<String> words = new ArrayList<>();
    for (int i = 0; i < segments.length; i += 4) {
      // Select 4 bytes for the segment
      int first = UnsignedBytes.toInt(segments[i]);
      int second = UnsignedBytes.toInt(segments[i + 1]);
      int third = UnsignedBytes.toInt(segments[i + 2]);
      int fourth = UnsignedBytes.toInt(segments[i + 3]);

      //word is byte(1) + 1 / 2 of byte(2)
      Optional<String> word_part = Words.get_word((first << 4) + (second >> 4));
      if (word_part.isEmpty()) {
        return Optional.empty();
      }
      words.add(word_part.get());

      //hex is other 1 / 2 of byte(2) + byte(3) + byte(4)
      String hex_string = hash_to_words(
              (second & 0x0F),
              (third & 0xF0) >> 4,
              (third & 0x0F),
              (fourth & 0xF0) >> 4,
              (fourth & 0x0F));
      words.add(hex_string);
    }

    // FIXME ISSUE //82 Minimize length of tracker

    return Optional.of(String.join(separator, words));
  }

  private static final String hexchars = "0123456789ABCDEF";
  static String hash_to_words(int... ubs) {
    char[] result = new char[ubs.length];
    int count = 0;
    for (int ub : ubs) {
      Preconditions.checkArgument(ub >= 0 && ub < 16);
      result[count++] = hexchars.charAt(ub);
    }
    return new String(result);
  }
}
