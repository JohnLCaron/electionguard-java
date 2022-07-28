package com.sunya.electionguard.decrypting;

import com.google.common.base.Stopwatch;
import com.sunya.electionguard.Group;
import com.sunya.electionguard.PlaintextBallot;
import com.sunya.electionguard.PlaintextTally;
import com.sunya.electionguard.ballot.EncryptedBallot;
import com.sunya.electionguard.TestProperties;
import com.sunya.electionguard.protoconvert.TrusteeFromProto;
import com.sunya.electionguard.publish.CloseableIterable;
import com.sunya.electionguard.publish.Consumer;
import com.sunya.electionguard.publish.ElectionRecordPath;
import com.sunya.electionguard.publish.PrivateData;
import com.sunya.electionguard.publish.ElectionRecord;
import net.jqwik.api.Example;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.stream.Collectors;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth8.assertThat;

public class TestDecryptingMediator extends TestProperties {
  public static final String DECRYPTING_DATA_DIR = "src/test/data/workflow/encryptor/";
  public static final String TRUSTEE_DATA_DIR =    "src/test/data/workflow/keyCeremony/election_private_data/";
  //public static final String DECRYPTING_DATA_DIR = "/home/snake/tmp/electionguard/kickstart/encryptor/";
  //public static final String TRUSTEE_DATA_DIR = "/home/snake/tmp/electionguard/kickstart/keyCeremony/election_private_data/";
  private static final ElectionRecordPath path = new ElectionRecordPath(DECRYPTING_DATA_DIR);

  List<DecryptingTrusteeIF> trustees = new ArrayList<>();
  Consumer consumer;
  ElectionRecord electionRecord;
  Map<String, Group.ElementModP> guardianPublicKeys;

  Map<String, Integer> expectedTally;
  CloseableIterable<EncryptedBallot> spoiledBallots;

  public TestDecryptingMediator() throws IOException {
    trustees.add(TrusteeFromProto.readTrustee(path.decryptingTrusteePath(TRUSTEE_DATA_DIR, "remoteTrustee1")));
    trustees.add(TrusteeFromProto.readTrustee(path.decryptingTrusteePath(TRUSTEE_DATA_DIR, "remoteTrustee2")));
    trustees.add(TrusteeFromProto.readTrustee(path.decryptingTrusteePath(TRUSTEE_DATA_DIR, "remoteTrustee3")));
    trustees.add(TrusteeFromProto.readTrustee(path.decryptingTrusteePath(TRUSTEE_DATA_DIR, "remoteTrustee4")));

    this.consumer = new Consumer(DECRYPTING_DATA_DIR);
    this.electionRecord = consumer.readElectionRecord();

    // The guardians' election public key is in the electionRecord.guardianCoefficients.
    this.guardianPublicKeys = electionRecord.guardians().stream().collect(
            Collectors.toMap(guardian -> guardian.getGuardianId(), guardian -> guardian.publicKey()));

    // hand tally the results
    this.expectedTally = getExpectedTally();
    this.spoiledBallots =  consumer.iterateSpoiledBallots();
  }

  DecryptingMediator makeDecryptingMediator() {
    return new DecryptingMediator(this.electionRecord,
            this.electionRecord.ciphertextTally(),
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

    Stopwatch stopwatch = Stopwatch.createStarted();
    assertThat(subject.get_plaintext_tally()).isPresent();
    System.out.printf("That took %s%n", stopwatch);
    // assertThat(subject.get_plaintext_ballots()).isPresent();
  }

  @Example
  public void testAnnounceNoQuorum() {
    DecryptingMediator subject = makeDecryptingMediator();
    assertThat(subject.announce(this.trustees.get(0))).isTrue();

    // Can only announce once
    assertThat(subject.announce(this.trustees.get(0))).isFalse();

    // Cannot get plaintext tally without a quorum
    assertThat(subject.get_plaintext_tally()).isEmpty();
    // assertThat(subject.get_plaintext_ballots()).isEmpty();
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
    assertThat(mediator.announce(this.trustees.get(3))).isTrue();

    Stopwatch stopwatch = Stopwatch.createStarted();
    Optional<PlaintextTally> decrypted_tallies = mediator.get_plaintext_tally();
    System.out.printf("TestCompensateMissingGuardian: get_plaintext_tally took %s%n", stopwatch);
    assertThat(decrypted_tallies).isPresent();
    Map<String, Integer> result = this.convertToCounts(decrypted_tallies.get());
    assertThat(result).isNotEmpty();
    assertThat(result).isEqualTo(this.expectedTally);

    // Verify that the decrypted ballots equal the original ballots
    stopwatch.reset();
    List<PlaintextTally> spoiledBallots = mediator.decrypt_spoiled_ballots().orElseThrow();
    checkDecrypted(spoiledBallots);
    System.out.printf("  decrypt_spoiled_ballots took %s for %d spoiled ballots%n", stopwatch, spoiledBallots.size());
  }

  private void checkDecrypted(List<PlaintextTally> decrypteds) throws IOException {
    PrivateData pdata = new PrivateData(DECRYPTING_DATA_DIR, false, true);
    List<PlaintextBallot> inputBallots = pdata.readInputBallots();
    Map<String, PlaintextBallot> inputBallotsMap = inputBallots.stream().collect(Collectors.toMap(e -> e.object_id(), e -> e));
    for (PlaintextTally decrypted : decrypteds) {
      PlaintextBallot input_ballot = inputBallotsMap.get(decrypted.tallyId);
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
    Map<String, Integer> counts = new TreeMap<>();
    for (PlaintextTally.Contest contest : tally.contests.values()) {
      for (Map.Entry<String, PlaintextTally.Selection> entry : contest.selections().entrySet()) {
        counts.put(contest.contestId() + ":" + entry.getKey(), entry.getValue().tally());
      }
    }
    // counts.entrySet().forEach(entry -> System.out.printf("    expectedTally.put(\"%s\",%d);%n", entry.getKey(), entry.getValue()));
    return counts;
  }

  private Map<String, Integer> convertToCounts(PlaintextBallot tally) {
    Map<String, Integer> counts = new HashMap<>();
    for (PlaintextBallot.Contest contest : tally.contests) {
      for (PlaintextBallot.Selection selection : contest.selections) {
        counts.put(contest.contestId + ":" + selection.selectionId, selection.vote);
      }
    }
    return counts;
  }

  private Map<String, Integer> getExpectedTally() {
    Map<String, Integer> expectedTally = new HashMap<>();
    expectedTally.put("contest0:selection0",2);
    expectedTally.put("contest0:selection1",0);
    expectedTally.put("contest0:selection2",0);
    expectedTally.put("contest0:selection3",0);
    expectedTally.put("contest10:selection40",1);
    expectedTally.put("contest10:selection41",0);
    expectedTally.put("contest10:selection42",1);
    expectedTally.put("contest10:selection43",0);
    expectedTally.put("contest11:selection44",1);
    expectedTally.put("contest11:selection45",1);
    expectedTally.put("contest11:selection46",0);
    expectedTally.put("contest11:selection47",0);
    expectedTally.put("contest12:selection48",2);
    expectedTally.put("contest12:selection49",0);
    expectedTally.put("contest12:selection50",0);
    expectedTally.put("contest12:selection51",0);
    expectedTally.put("contest13:selection52",0);
    expectedTally.put("contest13:selection53",1);
    expectedTally.put("contest13:selection54",1);
    expectedTally.put("contest13:selection55",0);
    expectedTally.put("contest14:selection56",1);
    expectedTally.put("contest14:selection57",1);
    expectedTally.put("contest14:selection58",0);
    expectedTally.put("contest14:selection59",0);
    expectedTally.put("contest15:selection60",2);
    expectedTally.put("contest15:selection61",0);
    expectedTally.put("contest15:selection62",0);
    expectedTally.put("contest15:selection63",0);
    expectedTally.put("contest16:selection64",2);
    expectedTally.put("contest16:selection65",0);
    expectedTally.put("contest16:selection66",0);
    expectedTally.put("contest16:selection67",0);
    expectedTally.put("contest17:selection68",1);
    expectedTally.put("contest17:selection69",0);
    expectedTally.put("contest17:selection70",0);
    expectedTally.put("contest17:selection71",0);
    expectedTally.put("contest18:selection72",1);
    expectedTally.put("contest18:selection73",1);
    expectedTally.put("contest18:selection74",0);
    expectedTally.put("contest18:selection75",0);
    expectedTally.put("contest19:selection76",1);
    expectedTally.put("contest19:selection77",0);
    expectedTally.put("contest19:selection78",0);
    expectedTally.put("contest19:selection79",1);
    expectedTally.put("contest1:selection4",1);
    expectedTally.put("contest1:selection5",0);
    expectedTally.put("contest1:selection6",0);
    expectedTally.put("contest1:selection7",0);
    expectedTally.put("contest20:selection80",0);
    expectedTally.put("contest20:selection81",1);
    expectedTally.put("contest20:selection82",0);
    expectedTally.put("contest20:selection83",1);
    expectedTally.put("contest21:selection84",2);
    expectedTally.put("contest21:selection85",0);
    expectedTally.put("contest21:selection86",0);
    expectedTally.put("contest21:selection87",0);
    expectedTally.put("contest22:selection88",1);
    expectedTally.put("contest22:selection89",0);
    expectedTally.put("contest22:selection90",1);
    expectedTally.put("contest22:selection91",0);
    expectedTally.put("contest23:selection92",2);
    expectedTally.put("contest23:selection93",0);
    expectedTally.put("contest23:selection94",0);
    expectedTally.put("contest23:selection95",0);
    expectedTally.put("contest24:selection96",1);
    expectedTally.put("contest24:selection97",1);
    expectedTally.put("contest24:selection98",0);
    expectedTally.put("contest24:selection99",0);
    expectedTally.put("contest2:selection10",0);
    expectedTally.put("contest2:selection11",0);
    expectedTally.put("contest2:selection8",1);
    expectedTally.put("contest2:selection9",1);
    expectedTally.put("contest3:selection12",0);
    expectedTally.put("contest3:selection13",2);
    expectedTally.put("contest3:selection14",0);
    expectedTally.put("contest3:selection15",0);
    expectedTally.put("contest4:selection16",2);
    expectedTally.put("contest4:selection17",0);
    expectedTally.put("contest4:selection18",0);
    expectedTally.put("contest4:selection19",0);
    expectedTally.put("contest5:selection20",1);
    expectedTally.put("contest5:selection21",0);
    expectedTally.put("contest5:selection22",1);
    expectedTally.put("contest5:selection23",0);
    expectedTally.put("contest6:selection24",1);
    expectedTally.put("contest6:selection25",0);
    expectedTally.put("contest6:selection26",1);
    expectedTally.put("contest6:selection27",0);
    expectedTally.put("contest7:selection28",1);
    expectedTally.put("contest7:selection29",0);
    expectedTally.put("contest7:selection30",0);
    expectedTally.put("contest7:selection31",0);
    expectedTally.put("contest8:selection32",1);
    expectedTally.put("contest8:selection33",0);
    expectedTally.put("contest8:selection34",0);
    expectedTally.put("contest8:selection35",1);
    expectedTally.put("contest9:selection36",1);
    expectedTally.put("contest9:selection37",0);
    expectedTally.put("contest9:selection38",0);
    expectedTally.put("contest9:selection39",1);
    return expectedTally;
  }

}
