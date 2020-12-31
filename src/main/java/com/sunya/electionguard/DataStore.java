package com.sunya.electionguard;

import java.util.*;

/**
 * A lightweight convenience wrapper around a dictionary for data storage.
 * This implementation defines the common interface used to access stored
 * state elements.
 */
public class DataStore implements Iterable<Ballot.CiphertextAcceptedBallot> {
  final HashMap<String, Ballot.CiphertextAcceptedBallot> map = new HashMap<>();

  boolean containsKey(String key) {
    return map.containsKey(key);
  }

  Ballot.CiphertextAcceptedBallot put(String key, Ballot.CiphertextAcceptedBallot value) {
    return map.put(key, value);
  }

  Optional<Ballot.CiphertextAcceptedBallot> get(String key) {
    Ballot.CiphertextAcceptedBallot value = map.get(key);
    return (value == null) ? Optional.empty() : Optional.of(value);
  }

  @Override
  public Iterator<Ballot.CiphertextAcceptedBallot> iterator() {
    return map.values().iterator();
  }

}
