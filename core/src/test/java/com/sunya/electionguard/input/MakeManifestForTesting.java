package com.sunya.electionguard.input;

import com.sunya.electionguard.ElectionConstants;
import com.sunya.electionguard.Manifest;
import com.sunya.electionguard.publish.Publisher;
import electionguard.ballot.ElectionConfig;

import java.io.IOException;
import java.util.Formatter;

import static com.sunya.electionguard.publish.Publisher.Mode.createIfMissing;

public class MakeManifestForTesting {
  private static final String output = "/home/snake/tmp/electionguard/kickstart/start";
  private static final int ncontests = 25;
  private static final int nselections = 4;

  public static void main(String[] args) throws IOException {
    int selectionId = 0;
    ManifestInputBuilder ebuilder = new ManifestInputBuilder("election_scope_id");
    for (int contestId = 0; contestId < ncontests; contestId++) {
      ManifestInputBuilder.ContestBuilder cbuilder = ebuilder.addContest("contest" + contestId);
      for (int selection = 0; selection < nselections; selection++) {
        cbuilder.addSelection("selection" + selectionId, "candidate" + selectionId++);
      }
      cbuilder.done();
    }
    Manifest manifest = ebuilder.build();

    ManifestInputValidation validator = new ManifestInputValidation(manifest);
    Formatter errors = new Formatter();
    if (!validator.validateElection(errors)) {
      System.out.printf("*** ManifestInputValidation FAILED%n%s", errors);
      System.exit(1);
    }

    Publisher publisher = new Publisher(output, createIfMissing);
    ElectionConfig config = new ElectionConfig(manifest, 3, 3);
    publisher.writeElectionConfig(config);
  }
}
