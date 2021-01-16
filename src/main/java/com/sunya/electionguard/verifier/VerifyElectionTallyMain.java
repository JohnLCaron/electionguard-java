package com.sunya.electionguard.verifier;

import com.sunya.electionguard.publish.Consumer;

import java.io.IOException;

public class VerifyElectionTallyMain {

  public static void main(String[] args) throws IOException {
    String topdir = args[0];

    // set up
    Consumer consumer = new Consumer(topdir);
    ElectionParameters electionParameters = new ElectionParameters(consumer);

    System.out.println(" ------------ [box 1] Parameter Validation ------------");
    ParameterVerifier blv = new ParameterVerifier(electionParameters);
    boolean blvOk = blv.verify_all_params();

    System.out.println(" ------------ [box 2] Guardian Public-Key Validation ------------");
    GuardianPublicKeyVerifier gpkv = new GuardianPublicKeyVerifier(electionParameters);
    boolean gpkvOk = gpkv.verify_all_guardians();

    System.out.println(" ------------ [box 3] Election Public-Key Validation ------------");
    ElectionPublicKeyVerifier epkv = new ElectionPublicKeyVerifier(electionParameters);
    boolean epkvOk = epkv.verify_public_keys();

    System.out.println(" ------------ [box 4] Selection Encryption Validation ------------");
    SelectionEncyrptionVerifier sev = new SelectionEncyrptionVerifier(electionParameters, consumer);
    boolean sevOk = sev.verify_all_selections();

    System.out.println(" ------------ [box 5] Contest Vote Limits Validation ------------");
    ContestVoteLimitsVerifier cvlv = new ContestVoteLimitsVerifier(electionParameters, consumer);
    boolean cvlvOk = cvlv.verify_all_contests();

    System.out.println(" ------------ [box 6] Ballot Chaining Validation ------------");
    BallotChainingVerifier bcv = new BallotChainingVerifier(electionParameters, consumer);
    boolean bcvOk = bcv.verify_all_ballots();

    // tally and spoiled ballot check. LOOK is this a different workflow? after decryption ?

    System.out.println(" ------------ [box 7] Ballot Aggregation Validation ------------");
    BallotAggregationVerifier bav = new BallotAggregationVerifier(electionParameters, consumer);
    boolean bavOk = bav.match_total_across_ballots();

    System.out.println(" ------------ [box 8] Correctness of Partial Decryptions ------------");
    PartialDecryptionsVerifier dv = new PartialDecryptionsVerifier(electionParameters, consumer);
    boolean dvOk = dv.verify_cast_ballot_tallies();

    // System.out.println(" ------------ [box 11] Validation of Correct Decryption of Tallies ------------");
    // boolean dvsOk = dv.verify_all_spoiled_ballots();

    System.out.println(" ------------ [box 12] Correct Decryption of Spoiled Ballots ------------");
    boolean dvsOk = dv.verify_all_spoiled_ballots();

    if (blvOk && gpkvOk && epkvOk && sevOk && cvlvOk && bcvOk && bavOk && dvOk && dvsOk) {
      System.out.printf("%n===== ALL OK! ===== %n");
    } else {
      System.out.printf("%n!!!!!! NOT OK !!!!!! %n");
    }
  }
}
