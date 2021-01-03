package com.sunya.electionguard.verifier;

import com.sunya.electionguard.publish.Consumer;

import java.io.IOException;

public class VerifyElectionTallyMain {

  public static void main(String[] args) throws IOException {
    String topdir = args[0];

      // set up
    Consumer consumer = new Consumer(topdir);
    ElectionParameters electionParameters = new ElectionParameters(consumer);
    System.out.println("set up finished. ");

    // baseline parameter check
    System.out.println(" ------------ [box 1] baseline parameter check ------------");
    ParameterValidation blv = new ParameterValidation(electionParameters);
    boolean blvOk = blv.verify_all_params();
    System.out.printf(" OK %s%n", blvOk);

    // key generation check
    System.out.println(" ------------ [box 2] key generation parameter check ------------");
    GuardianPublicKeyValidation kgv = new GuardianPublicKeyValidation(electionParameters);
    boolean kgvOk = kgv.verify_all_guardians();
    System.out.printf(" OK %s%n", kgvOk);

    // all ballot check
    System.out.println(" ------------ [box 3, 4, 5] ballot encryption check ------------");
    EncyrptionValidation abv = new EncyrptionValidation(electionParameters, consumer);
    boolean abvOk = abv.verify_all_ballots();
    System.out.printf(" OK %s%n", abvOk);

    // tally and spoiled ballot check
    System.out.println(" ------------ [box 6, 9] cast ballot tally check ------------");
    DecryptionValidation dv = new DecryptionValidation(electionParameters, consumer);
    boolean dvOk = dv.verify_cast_ballot_tallies();
    System.out.printf(" OK %s%n", dvOk);

    System.out.println(" ------------ [box 10] spoiled ballot check ------------");
    dvOk = dv.verify_all_spoiled_ballots();
    System.out.printf(" OK %s%n", dvOk);
  }
}
