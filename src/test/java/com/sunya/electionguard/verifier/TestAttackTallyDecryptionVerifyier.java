package com.sunya.electionguard.verifier;

import com.google.common.collect.ImmutableMap;
import com.sunya.electionguard.DecryptionShare;
import com.sunya.electionguard.Manifest;
import com.sunya.electionguard.ElectionFactory;
import com.sunya.electionguard.Group;
import com.sunya.electionguard.PlaintextTally;
import com.sunya.electionguard.publish.Consumer;
import com.sunya.electionguard.publish.Publisher;
import net.jqwik.api.Example;

import java.io.IOException;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.function.Function;

import static com.google.common.truth.Truth.assertThat;

public class TestAttackTallyDecryptionVerifyier {
  private static final Random random = new Random(System.currentTimeMillis());

  @Example
  public void attackTallyDecryptionWrongManifest() throws IOException {
    String topdir = TestParameterVerifier.topdirProto;
    Consumer consumer = new Consumer(topdir);
    ElectionRecord electionRecord = consumer.readElectionRecordProto();
    Manifest fakeElection = ElectionFactory.get_fake_election();

    TallyDecryptionVerifier tdv = new TallyDecryptionVerifier(fakeElection, electionRecord.decryptedTally);
    boolean tdvOk = tdv.verify_tally_decryption();
    assertThat(tdvOk).isFalse();
  }

  // Just change the tally
  @Example
  public void attackTallyDecryptionWrongTally() throws IOException {
    String topdir = TestParameterVerifier.topdirProto;
    Consumer consumer = new Consumer(topdir);
    ElectionRecord electionRecord = consumer.readElectionRecordProto();
    PlaintextTally tally = electionRecord.decryptedTally;
    assertThat(tally).isNotNull();

    PlaintextTally attack = new PlaintextTally( tally.object_id,
            messContests(tally.contests, this::messTally),
            tally.lagrange_coefficients,
            tally.guardianStates);

    TallyDecryptionVerifier tdv = new TallyDecryptionVerifier(electionRecord.election, attack);
    boolean tdvOk = tdv.verify_tally_decryption();
    assertThat(tdvOk).isFalse();
  }

  // Just change the tally and the value
  @Example
  public void attackTallyDecryptionWrongTallyAndValue() throws IOException {
    String topdir = TestParameterVerifier.topdirProto;
    Consumer consumer = new Consumer(topdir);
    ElectionRecord electionRecord = consumer.readElectionRecordProto();
    PlaintextTally tally = electionRecord.decryptedTally;
    assertThat(tally).isNotNull();

    PlaintextTally attack = new PlaintextTally( tally.object_id,
            messContests(tally.contests, this::messTallyAndValue),
            tally.lagrange_coefficients,
            tally.guardianStates);

    TallyDecryptionVerifier tdv = new TallyDecryptionVerifier(electionRecord.election, attack);
    boolean tdvOk = tdv.verify_tally_decryption();
    assertThat(tdvOk).isFalse();
  }

  // Change the tally and the value and the particla decryptions. Naive.
  @Example
  public void attackTallyDecryption() throws IOException {
    String topdir = TestParameterVerifier.topdirProto;
    Consumer consumer = new Consumer(topdir);
    ElectionRecord electionRecord = consumer.readElectionRecordProto();
    PlaintextTally tally = electionRecord.decryptedTally;
    assertThat(tally).isNotNull();

    PlaintextTally attack = new PlaintextTally( tally.object_id,
            messContests(tally.contests, this::messTallyMessage),
            tally.lagrange_coefficients,
            tally.guardianStates);

    System.out.println("------------ [box 11] Correctness of Decryption of Tallies ------------");
    TallyDecryptionVerifier tdv = new TallyDecryptionVerifier(electionRecord.election, attack);
    boolean tdvOk = tdv.verify_tally_decryption();
    assertThat(tdvOk).isTrue();

    System.out.println("------------ [box 8, 9] Correctness of Decryptions ------------");
    DecryptionVerifier dv = new DecryptionVerifier(electionRecord, attack);
    boolean dvOk = dv.verify_election_tally();
    assertThat(dvOk).isFalse();

    System.out.println("------------ [box 10] Correctness of Replacement Partial Decryptions ------------");
    PartialDecryptionVerifier pdv = new PartialDecryptionVerifier(electionRecord, attack);
    boolean pdvOk = pdv.verify_replacement_partial_decryptions();
    assertThat(pdvOk).isFalse();

    if (tdvOk && dvOk && pdvOk) {
      System.out.printf("HEY attack worked = %s%n", tdvOk);
      publish(topdir, "/home/snake/tmp/electionguard/publishAttackTallyDecryption", electionRecord, attack);
    }
  }

  private Map<String, PlaintextTally.Contest> messContests(ImmutableMap<String, PlaintextTally.Contest> org, Messer messer) {
    int size = org.size();
    int choose = random.nextInt(size);
    Map<String, PlaintextTally.Contest> result = new HashMap<>();
    int count = 0;
    for (PlaintextTally.Contest contest : org.values()) {
      result.put(contest.object_id(), (count == choose)  ? messContest(contest, messer) : contest);
      count++;
    }
    return result;
  }

  private PlaintextTally.Contest messContest(PlaintextTally.Contest org, Messer messer) {
    // String object_id, Map<String, PlaintextTally.Selection > selections
    return PlaintextTally.Contest.create(org.object_id(), messSelections(org, messer));
  }

  private Map<String, PlaintextTally.Selection> messSelections(PlaintextTally.Contest org, Messer messer) {
    int size = org.selections().size();
    int choose = random.nextInt(size);
    Map<String, PlaintextTally.Selection> result = new HashMap<>();
    int count = 0;
    for (PlaintextTally.Selection selection : org.selections().values()) {
      result.put(selection.object_id(), (count == choose)  ? messer.apply(selection) : selection);
      count++;
    }
    return result;
  }

  interface Messer extends Function<PlaintextTally.Selection, PlaintextTally.Selection> {}

  private PlaintextTally.Selection messTally(PlaintextTally.Selection org) {
    System.out.printf("---messTally with %s%n", org.object_id());
    return PlaintextTally.Selection.create(org.object_id(), org.tally() + 1, org.value(), org.message(), org.shares());
  }

  private PlaintextTally.Selection messTallyAndValue(PlaintextTally.Selection org) {
    System.out.printf("---messTallyAndValue with %s%n", org.object_id());
    int tally = org.tally() + 1;
    Group.ElementModP t = Group.int_to_p_unchecked(BigInteger.valueOf(tally));
    Group.ElementModP value = Group.g_pow_p(t);
    return PlaintextTally.Selection.create(org.object_id(), tally, value, org.message(), org.shares());
  }

  private PlaintextTally.Selection messTallyMessage(PlaintextTally.Selection org) {
    System.out.printf("---messTallyMessage with %s%n", org.object_id());
    int tally = org.tally() + 1;
    Group.ElementModP t = Group.int_to_p_unchecked(BigInteger.valueOf(tally));
    Group.ElementModP M = Group.g_pow_p(t);

    // List<ElementModP> partialDecryptions = selection.shares().stream().map(s -> s.share()).collect(Collectors.toList());
    // ElementModP productMi = Group.mult_p(partialDecryptions);
    // ElementModP M = selection.value();
    // ElementModP B = selection.message().data;
    // if (!B.equals(Group.mult_p(M, productMi))) {


    // if (!B.equals(Group.mult_p(M, productMi))) {
    // we need B = Group.mult_p(M, productMi);
    // can we just do
    Group.ElementModP B = org.message().data;
    Group.ElementModP productMi = Group.div_p(B, M);

    // String object_id,
    //            String guardian_id,
    //            ElementModP share,
    //            Optional<ChaumPedersen.ChaumPedersenProof> proof,
    //            Optional<Map<String, CiphertextCompensatedDecryptionSelection>> recovered_parts
    List<DecryptionShare.CiphertextDecryptionSelection> shares = new ArrayList<>();
    for (int i=0; i < org.shares().size(); i++) {
      DecryptionShare.CiphertextDecryptionSelection orgShare = org.shares().get(i);
      Group.ElementModP s = i == 0 ? productMi : Group.ONE_MOD_P;
      Optional<Map<String, DecryptionShare.CiphertextCompensatedDecryptionSelection>> recovered_parts =
              orgShare.recovered_parts().map(r -> r);
      DecryptionShare.CiphertextDecryptionSelection messShare = DecryptionShare.CiphertextDecryptionSelection.create(
              orgShare.object_id(), orgShare.guardian_id(), s, orgShare.proof(), recovered_parts);
      shares.add(messShare);
    }

    // Group.ElementModP productMi = Group.mult_p(partialDecryptions);
    return PlaintextTally.Selection.create(org.object_id(), tally, M, org.message(), shares);
  }

  boolean publish(String inputDir, String publishDir, ElectionRecord electionRecord, PlaintextTally decryptedTally) throws IOException {
    Publisher publisher = new Publisher(publishDir, true, false);
    publisher.writeDecryptedTallyProto(electionRecord, decryptedTally);
    publisher.copyAcceptedBallots(inputDir);
    return true;
  }

}
