package com.sunya.integration;

import com.sunya.electionguard.Election;
import com.sunya.electionguard.ElectionFactory;

import java.io.IOException;

public class TestHamiltonCountyElection {

  public static void main(String[] args) throws IOException {
    ElectionFactory election_factory = new ElectionFactory();
    Election.ElectionDescription description = election_factory.get_hamilton_election_from_file();
  }
}
