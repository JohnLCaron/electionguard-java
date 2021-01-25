package com.sunya.electionguard.verifier;

import com.sunya.electionguard.proto.ElectionRecordFromProto;
import com.sunya.electionguard.publish.Consumer;

import java.io.IOException;

/** Verify all sections of the spec, on a completed election record. */
public class VerifyElectionRecordMain {

  public static void main(String[] args) throws IOException {
    String topdir = args[0];
    boolean isProtoElectionRecord = args.length > 1 && args[1].contains("proto");

    ElectionRecord electionRecord;
    if (isProtoElectionRecord) {
      electionRecord = ElectionRecordFromProto.read(topdir);
    } else {
      Consumer consumer = new Consumer(topdir);
      electionRecord = consumer.getElectionRecord();
    }

  verifyElectionRecord(electionRecord);
  }

  static void verifyElectionRecord(ElectionRecord electionRecord) throws IOException {
    System.out.println("============ Ballot Verification =========================");
    System.out.println("------------ [box 1] Parameter Validation ------------");
    ParameterVerifier blv = new ParameterVerifier(electionRecord);
    boolean blvOk = blv.verify_all_params();

    System.out.println("------------ [box 2] Guardian Public-Key Validation ------------");
    GuardianPublicKeyVerifier gpkv = new GuardianPublicKeyVerifier(electionRecord);
    boolean gpkvOk = gpkv.verify_all_guardians();

    System.out.println("------------ [box 3] Election Public-Key Validation ------------");
    ElectionPublicKeyVerifier epkv = new ElectionPublicKeyVerifier(electionRecord);
    boolean epkvOk = epkv.verify_public_keys();

    System.out.println("------------ [box 4] Selection Encryption Validation ------------");
    SelectionEncyrptionVerifier sev = new SelectionEncyrptionVerifier(electionRecord);
    boolean sevOk = sev.verify_all_selections();

    System.out.println("------------ [box 5] Contest Vote Limits Validation ------------");
    ContestVoteLimitsVerifier cvlv = new ContestVoteLimitsVerifier(electionRecord);
    boolean cvlvOk = cvlv.verify_all_contests();

    System.out.println("------------ [box 6] Ballot Chaining Validation ------------");
    BallotChainingVerifier bcv = new BallotChainingVerifier(electionRecord);
    boolean bcvOk = bcv.verify_all_ballots();

    System.out.println("============ Decryption Verification =========================");
    System.out.println("------------ [box 7] Ballot Aggregation Validation ------------");
    BallotAggregationVerifier bav = new BallotAggregationVerifier(electionRecord);
    boolean bavOk = bav.verify_ballot_aggregation();

    System.out.println("------------ [box 8, 9] Correctness of Decryptions ------------");
    DecryptionVerifier dv = new DecryptionVerifier(electionRecord);
    boolean dvOk = dv.verify_cast_ballot_tallies();

    System.out.println("------------ [box 11] Validation of Correct Decryption of Tallies ------------");
    boolean bavt = bav.verify_tally_decryption();

    System.out.println("------------ [box 12] Correct Decryption of Spoiled Ballots ------------");
    boolean dvsOk = dv.verify_spoiled_ballots();

    if (blvOk && gpkvOk && epkvOk && sevOk && cvlvOk && bcvOk && bavOk && dvOk && bavt && dvsOk) {
      System.out.printf("%n===== ALL OK! ===== %n");
    } else {
      System.out.printf("%n!!!!!! NOT OK !!!!!! %n");
    }
  }
}
