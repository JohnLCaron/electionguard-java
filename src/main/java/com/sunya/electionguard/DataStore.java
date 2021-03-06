package com.sunya.electionguard;

import javax.annotation.Nullable;
import java.util.*;

/** A mutable store for SubmittedBallot. */
class DataStore implements Iterable<SubmittedBallot> {
  private final HashMap<String, SubmittedBallot> map = new HashMap<>();

  /** Does the store contain the given key? */
  boolean containsKey(String key) {
    return map.containsKey(key);
  }

  /**
   * Associates the specified value with the specified key in this map.
   * If the map previously contained a mapping for the key, the old value is replaced.
   *
   * @param key   key with which the specified value is to be associated
   * @param value value to be associated with the specified key
   * @return the previous value associated with {@code key}, or {@code null} if there was no mapping for {@code key}.
   * (A {@code null} return can also indicate that the map previously associated {@code null} with {@code key}.)
   */
  @Nullable
  SubmittedBallot put(String key, @Nullable SubmittedBallot value) {
    return map.put(key, value);
  }

  /** Return the value for the given key, or empty. */
  Optional<SubmittedBallot> get(String key) {
    SubmittedBallot value = map.get(key);
    return Optional.ofNullable(value);
  }

  /** An iterator over the values of the DataStore. */
  @Override
  public Iterator<SubmittedBallot> iterator() {
    return map.values().iterator();
  }

}
