package com.sunya.electionguard.verifier;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.beust.jcommander.ParameterException;
import com.sunya.electionguard.publish.Consumer;
import com.sunya.electionguard.workflow.EncryptBallots;

import java.io.IOException;

/**
 * A command line program to verify a complete election record.
 * <p>
 * For command line help:
 * <strong>
 * <pre>
 *  java -classpath electionguard-java-all.jar com.sunya.electionguard.verifier.VerifyElectionRecord --help
 * </pre>
 * or
 * <pre>
 *  java -jar electionguard-java-all.jar --help
 * </pre>
 * </strong>
 *
 * @see <a href="https://www.electionguard.vote/spec/0.95.0/9_Verifier_construction/">Election Verifier</a>
 */
public class VerifyElectionRecord {

  private static class CommandLine {
    @Parameter(names = {"-in"}, order = 0,
            description = "Directory containing input election record", required = true)
    String inputDir;

    @Parameter(names = {"--proto"}, order = 1, description = "Input election record is in protobuf format")
    boolean isProto = false;

    @Parameter(names = {"-h", "--help"}, order = 2, description = "Display this help and exit", help = true)
    boolean help = false;

    private final JCommander jc;

    public CommandLine(String[] args) throws ParameterException {
      this.jc = new JCommander(this);
      this.jc.parse(args);
      jc.setProgramName("java -jar electionguard-java-all.jar"); // Displayed in the usage information.
    }

    public void printUsage() {
      jc.usage();
    }
  }

  public static void main(String[] args) throws IOException {
    CommandLine cmdLine = null;

    try {
      cmdLine = new CommandLine(args);
      if (cmdLine.help) {
        cmdLine.printUsage();
        return;
      }
    } catch (ParameterException e) {
      System.err.println(e.getMessage());
      System.err.printf("Try --help for more information.%n");
      System.exit(1);
    }

    ElectionRecord electionRecord;
    Consumer consumer = new Consumer(cmdLine.inputDir);
    if (cmdLine.isProto) {
      electionRecord = consumer.readElectionRecordProto();
    } else {
      electionRecord = consumer.readElectionRecordJson();
    }

    System.out.printf(" VerifyElectionRecord read from %s isProto = %s%n", cmdLine.inputDir, cmdLine.isProto);
    boolean ok = verifyElectionRecord(electionRecord);
    System.exit(ok ? 0 : 1);
  }

  static boolean verifyElectionRecord(ElectionRecord electionRecord) throws IOException {
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

    System.out.println("\n============ Decryption Verification =========================");
    System.out.println("------------ [box 7] Ballot Aggregation Validation ------------");
    BallotAggregationVerifier bav = new BallotAggregationVerifier(electionRecord);
    boolean bavOk = bav.verify_ballot_aggregation();

    System.out.println("------------ [box 8, 9] Correctness of Decryptions ------------");
    DecryptionVerifier dv = new DecryptionVerifier(electionRecord);
    boolean dvOk = dv.verify_election_tally();

    System.out.println("------------ [box 10] Correctness of Replacement Partial Decryptions ------------");
    PartialDecryptionVerifier pdv = new PartialDecryptionVerifier(electionRecord);
    boolean pdvOk = pdv.verify_replacement_partial_decryptions();

    System.out.println("------------ [box 11] Correctness of Decryption of Tallies ------------");
    boolean bavt = bav.verify_tally_decryption();

    System.out.println("------------ [box 12] Correct Decryption of Spoiled Ballots ------------");
    boolean dvsOk = dv.verify_spoiled_tallies(electionRecord.spoiledTallies);

    PlaintextBallotVerifier pbv = new PlaintextBallotVerifier(electionRecord);
    boolean pbvOk = pbv.verify_plaintext_ballot();

    boolean allOk = (blvOk && gpkvOk && epkvOk && sevOk && cvlvOk && bcvOk && bavOk && dvOk && pdvOk && bavt && dvsOk && pbvOk);
    if (allOk) {
      System.out.printf("%n===== ALL OK! ===== %n");
    } else {
      System.out.printf("%n!!!!!! NOT OK !!!!!! %n");
    }

    return allOk;
  }
}
