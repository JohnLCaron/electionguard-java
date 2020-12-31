package com.sunya.electionguard.publish;

import java.util.List;

/** Conversion of PlaintextBallot to Json. */
public class PlaintextBallotPojo {
  public String object_id;
  public String ballot_style;
  public List<PlaintextBallotContest> contests;

  public static class PlaintextBallotContest {
    public String object_id;
    public List<PlaintextBallotSelection> ballot_selections;
  }

  public static class PlaintextBallotSelection {
    public String object_id;
    public String vote;
    public String extra_data;
  }
}
