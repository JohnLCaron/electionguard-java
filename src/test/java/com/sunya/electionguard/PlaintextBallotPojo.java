package com.sunya.electionguard;

import java.util.List;

public class PlaintextBallotPojo {
    String object_id;
    String ballot_style;
    List<PlaintextBallotContest> contests;

  public static class PlaintextBallotContest {
    String object_id;
    List<PlaintextBallotSelection> ballot_selections;
  }

  public static class PlaintextBallotSelection {
    String object_id;
    String vote;
    String extra_data;
  }
}
