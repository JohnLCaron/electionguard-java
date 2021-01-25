package com.sunya.electionguard;

import com.google.common.base.Stopwatch;
import com.sunya.electionguard.proto.ElectionRecordFromProto;
import com.sunya.electionguard.verifier.ElectionRecord;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static com.sunya.electionguard.Election.CiphertextElectionContext;
import static com.sunya.electionguard.Election.ElectionConstants;
import static com.sunya.electionguard.Election.ElectionDescription;
import static com.sunya.electionguard.Election.InternalElectionDescription;

public class TimeReadTallyDecrypt {

  public static void main(String[] args) throws IOException {
    TimeReadTallyDecrypt timer = new TimeReadTallyDecrypt();
    timer.run();
  }

  String outputDir;
  int nballots = 10;

  // Step 1 - Read Ballot Chain
  ElectionDescription description;
  InternalElectionDescription election;
  CiphertextElectionContext context;
  ElectionConstants constants;
  List<KeyCeremony.CoefficientValidationSet> guardianCoefficients;
  Encrypt.EncryptionDevice device;
  List<Ballot.CiphertextAcceptedBallot> castBallots;

  // Step 2 - Tally
  Tally.CiphertextTally ciphertext_tally;

  // Step 3 - Decrypt
  Tally.PlaintextTally decryptedTally;
  DecryptionMediator decrypter;

  public TimeReadTallyDecrypt() throws IOException {
    Path tmp = Files.createTempDirectory(null);
    tmp.toFile().deleteOnExit();
    // outputDir = "/home/snake/tmp/testEndToEnd";
    outputDir = tmp.toAbsolutePath().toString();
    System.out.printf("=========== outputDir = %s nballots = %d%n", outputDir, nballots);
  }

  public void run() throws IOException {
    Stopwatch stopwatch = Stopwatch.createStarted();

    this.step_1_read_ballot_chain();
    System.out.printf("*** step_1_read_ballot_chain elapsed = %s%n", stopwatch);
    stopwatch.reset().start();

    this.step_2_tally_ballots();
    float timePerBallot = ((float) stopwatch.elapsed(TimeUnit.MILLISECONDS)) / nballots;
    System.out.printf("*** step_2_tally_ballots elapsed = %s nballots = %d timePerBallot = %f ms%n", stopwatch, nballots, timePerBallot);
    stopwatch.reset().start();

    /* this.step_3_decrypt_tally();
    timePerBallot = ((float) stopwatch.elapsed(TimeUnit.MILLISECONDS)) / nballots;
    System.out.printf("*** step_3_decrypt_tally elapsed = %s nballots = %d timePerBallot = %f ms%n", stopwatch, nballots, timePerBallot);
    stopwatch.reset().start(); */

    Scheduler.shutdown();
  }

  void step_1_read_ballot_chain() throws IOException {
    System.out.printf("%n1. Read ballot chain%n");
    ElectionRecord electionRecord = ElectionRecordFromProto.read("/home/snake/tmp/TimeBallotEncryptWrite/ballotChain.proto");
    this.constants = electionRecord.constants;
    this.context = electionRecord.context;
    this.description = electionRecord.election;
    this.device = electionRecord.devices.get(0);
    this.guardianCoefficients = electionRecord.guardianCoefficients;
    this.castBallots = electionRecord.castBallots;

    this.election = new InternalElectionDescription(this.description);
  }

  void step_2_tally_ballots() {
    System.out.printf("%n2. Homomorphically Accumulate tally%n");
    // Generate a Homomorphically Accumulated Tally of the ballots
    this.ciphertext_tally = Tally.tally_ballots(this.castBallots, this.election, this.context).orElseThrow();
  }

  /* void step_3_decrypt_tally() {
    // Configure the Decryption
    this.decrypter = new DecryptionMediator(this.election, this.context, this.ciphertext_tally);

    // Announce each guardian as present
    for (Guardian guardian : this.guardians) {
      Optional<DecryptionShare.TallyDecryptionShare> decryption_share = this.decrypter.announce(guardian);
      System.out.printf("Guardian Present: %s%n", guardian.object_id);
      assertThat(decryption_share).isPresent();
    }

    // Here's where the ciphertext Tally is decrypted.
    this.decryptedTally = this.decrypter.get_plaintext_tally(false, null).orElseThrow();
    System.out.printf("Tally Decrypted%n");

    // Now, compare the results
    // this.compare_results();
  } */

}
