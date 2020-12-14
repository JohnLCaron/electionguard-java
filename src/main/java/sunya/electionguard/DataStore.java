package sunya.electionguard;

import java.util.*;

public class DataStore {
  HashMap<String, Ballot.CiphertextAcceptedBallot> map = new HashMap<>();

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


}
