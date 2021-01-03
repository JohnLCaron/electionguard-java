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
    ParameterValidator blv = new ParameterValidator(electionParameters);
    boolean blvOk = blv.verify_all_params();

    // key generation check
    System.out.println(" ------------ [box 2] key generation parameter check ------------");
    GuardianPublicKeyValidator kgv = new GuardianPublicKeyValidator(electionParameters);
    boolean kgvOk = kgv.verify_all_guardians();

    // all ballot check
    System.out.println(" ------------ [box 3, 4, 5] ballot encryption check ------------");
    EncyrptionValidator abv = new EncyrptionValidator(electionParameters, consumer);
    boolean abvOk = abv.verify_all_ballots();

    // tally and spoiled ballot check
    System.out.println(" ------------ [box 6, 9] cast ballot tally check ------------");
    DecryptionValidator dv = new DecryptionValidator(electionParameters, consumer);
    boolean dvOk = dv.verify_cast_ballot_tallies();

    System.out.println(" ------------ [box 10] spoiled ballot check ------------");
    boolean dvsOk = dv.verify_all_spoiled_ballots();

    if (blvOk && kgvOk && abvOk && dvOk && dvsOk) {
      System.out.printf("%n===== ALL OK! ===== %n");
    } else {
      System.out.printf("%n!!!!!! NOT OK !!!!!! %n");
    }
  }
}
