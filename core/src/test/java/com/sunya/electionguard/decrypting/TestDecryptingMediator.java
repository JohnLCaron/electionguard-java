package com.sunya.electionguard.decrypting;

import com.sunya.electionguard.Group;
import com.sunya.electionguard.PlaintextBallot;
import com.sunya.electionguard.PlaintextTally;
import com.sunya.electionguard.SubmittedBallot;
import com.sunya.electionguard.TestProperties;
import com.sunya.electionguard.proto.TrusteeFromProto;
import com.sunya.electionguard.publish.CloseableIterable;
import com.sunya.electionguard.publish.Consumer;
import com.sunya.electionguard.publish.PrivateData;
import com.sunya.electionguard.verifier.ElectionRecord;
import net.jqwik.api.Example;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth8.assertThat;

public class TestDecryptingMediator extends TestProperties {
  public static final String DECRYPTING_DATA_DIR = "src/test/data/workflow/encryptor/";
  public static final String TRUSTEE_DATA_DIR = "src/test/data/workflow/keyCeremony/election_private_data/";
  // public static final String DECRYPTING_DATA_DIR = "/home/snake/tmp/electionguard/remoteWorkflow/encryptor/";
  // public static final String TRUSTEE_DATA_DIR = "/home/snake/tmp/electionguard/remoteWorkflow/keyCeremony/election_private_data/";

  List<DecryptingTrusteeIF> trustees = new ArrayList<>();
  Consumer consumer;
  ElectionRecord electionRecord;
  Map<String, Group.ElementModP> guardianPublicKeys;

  Map<String, Integer> expectedTally;
  CloseableIterable<SubmittedBallot> spoiledBallots;

  public TestDecryptingMediator() throws IOException {
    trustees.add(TrusteeFromProto.readTrustee(TRUSTEE_DATA_DIR + "/remoteTrustee1.protobuf"));
    trustees.add(TrusteeFromProto.readTrustee(TRUSTEE_DATA_DIR + "/remoteTrustee2.protobuf"));
    trustees.add(TrusteeFromProto.readTrustee(TRUSTEE_DATA_DIR + "/remoteTrustee3.protobuf"));

    this.consumer = new Consumer(DECRYPTING_DATA_DIR);
    this.electionRecord = consumer.readElectionRecord();

    // The guardians' election public key is in the electionRecord.guardianCoefficients.
    this.guardianPublicKeys = electionRecord.guardianRecords.stream().collect(
            Collectors.toMap(coeff -> coeff.guardian_id(), coeff -> coeff.election_public_key()));

    // hand tally the results
    expectedTally = new HashMap<>();
    expectedTally.put("referendum-pineapple:referendum-pineapple-affirmative-selection", 0);
    expectedTally.put("referendum-pineapple:referendum-pineapple-negative-selection", 0);
    expectedTally.put("justice-supreme-court:benjamin-franklin-selection", 0);
    expectedTally.put("justice-supreme-court:john-adams-selection", 2);
    expectedTally.put("justice-supreme-court:john-hancock-selection", 1);
    expectedTally.put("justice-supreme-court:write-in-selection", 3);

    this.spoiledBallots =  consumer.submittedSpoiledBallotsProto();
  }

  DecryptingMediator makeDecryptingMediator() {
    return new DecryptingMediator(this.electionRecord.context,
            this.electionRecord.encryptedTally,
            this.spoiledBallots,
            this.guardianPublicKeys);
  }

  @Example
  public void testConstructor() {
    DecryptingMediator subject = makeDecryptingMediator();

    assertThat(subject.getAvailableGuardians()).isNull();
    assertThat(subject.get_plaintext_tally()).isEmpty();
    assertThat(subject.get_plaintext_ballots()).isEmpty();
  }

  @Example
  public void testAnnounce() {
    DecryptingMediator subject = makeDecryptingMediator();
    for (DecryptingTrusteeIF trustee : this.trustees) {
      assertThat(subject.announce(trustee)).isTrue();
    }

    assertThat(subject.get_plaintext_tally()).isPresent();
    assertThat(subject.get_plaintext_ballots()).isPresent();
  }

  @Example
  public void testAnnounceNoQuorum() {
    DecryptingMediator subject = makeDecryptingMediator();
    assertThat(subject.announce(this.trustees.get(0))).isTrue();

    // Can only announce once
    assertThat(subject.announce(this.trustees.get(0))).isFalse();

    // Cannot get plaintext tally without a quorum
    assertThat(subject.get_plaintext_tally()).isEmpty();
    assertThat(subject.get_plaintext_ballots()).isEmpty();
  }

  @Example
  public void testAllGuardiansPresent() throws IOException {
    DecryptingMediator mediator = makeDecryptingMediator();
    for (DecryptingTrusteeIF DecryptingTrusteeIF : this.trustees) {
      assertThat(mediator.announce(DecryptingTrusteeIF)).isTrue();
    }

    Optional<PlaintextTally> decrypted_tallies = mediator.get_plaintext_tally();
    assertThat(decrypted_tallies).isPresent();
    Map<String, Integer> result = this.convertToCounts(decrypted_tallies.get());
    assertThat(result).isNotEmpty();
    assertThat(result).isEqualTo(this.expectedTally);

    // Verify we get the same tally back if we call again
    Optional<PlaintextTally> another_decrypted_tally = mediator.get_plaintext_tally();
    assertThat(another_decrypted_tally).isPresent();
    assertThat(decrypted_tallies.get()).isEqualTo(another_decrypted_tally.get());

    // Verify that the decrypted ballots equal the original ballots
    List<PlaintextTally> spoiledBallots = mediator.decrypt_spoiled_ballots().orElseThrow();
    System.out.printf("spoiledBallots = %d%n", spoiledBallots.size());
    checkDecrypted(spoiledBallots);
  }

  @Example
  public void testCompensateMissingGuardian() throws IOException {
    DecryptingMediator mediator = makeDecryptingMediator();
    assertThat(mediator.announce(this.trustees.get(0))).isTrue();
    assertThat(mediator.announce(this.trustees.get(1))).isTrue();

    Optional<PlaintextTally> decrypted_tallies = mediator.get_plaintext_tally();
    assertThat(decrypted_tallies).isPresent();
    Map<String, Integer> result = this.convertToCounts(decrypted_tallies.get());
    assertThat(result).isNotEmpty();
    assertThat(result).isEqualTo(this.expectedTally);

    // Verify that the decrypted ballots equal the original ballots
    List<PlaintextTally> spoiledBallots = mediator.decrypt_spoiled_ballots().orElseThrow();
    System.out.printf("spoiledBallots = %d%n", spoiledBallots.size());
    checkDecrypted(spoiledBallots);
  }

  private void checkDecrypted(List<PlaintextTally> decrypteds) throws IOException {
    PrivateData pdata = new PrivateData(DECRYPTING_DATA_DIR, false, false);
    List<PlaintextBallot> inputBallots = pdata.inputBallots();
    Map<String, PlaintextBallot> inputBallotsMap = inputBallots.stream().collect(Collectors.toMap(e -> e.object_id(), e -> e));
    for (PlaintextTally decrypted : decrypteds) {
      PlaintextBallot input_ballot = inputBallotsMap.get(decrypted.object_id);
      assertThat(input_ballot).isNotNull();
      checkTallyAgainstBallot(decrypted, input_ballot);
    }
  }

  private void checkTallyAgainstBallot(PlaintextTally decrypted_tally, PlaintextBallot input_ballot) {
    Map<String, Integer> tally_counts = convertToCounts(decrypted_tally);
    Map<String, Integer> ballot_counts = convertToCounts(input_ballot);
    for (Map.Entry<String, Integer> tally_count : tally_counts.entrySet()) {
      Integer ballot_count = ballot_counts.get(tally_count.getKey());
      if (ballot_count == null) {
        ballot_count = 0;
      }
      assertThat(ballot_count).isEqualTo(tally_count.getValue());
    }
  }

  private Map<String, Integer> convertToCounts(PlaintextTally tally) {
    Map<String, Integer> counts = new HashMap<>();
    for (PlaintextTally.Contest contest : tally.contests.values()) {
      for (Map.Entry<String, PlaintextTally.Selection> entry : contest.selections().entrySet()) {
        counts.put(contest.object_id() + ":" + entry.getKey(), entry.getValue().tally());
      }
    }
    return counts;
  }

  private Map<String, Integer> convertToCounts(PlaintextBallot tally) {
    Map<String, Integer> counts = new HashMap<>();
    for (PlaintextBallot.Contest contest : tally.contests) {
      for (PlaintextBallot.Selection selection : contest.ballot_selections) {
        counts.put(contest.contest_id + ":" + selection.selection_id, selection.vote);
      }
    }
    return counts;
  }

}
